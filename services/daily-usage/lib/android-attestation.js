import { createHash, createPublicKey, verify, X509Certificate } from 'node:crypto';
import { Certificate, CertificateChainValidationEngine } from 'pkijs';
import { AsnParser } from '@peculiar/asn1-schema';
import { AttestationApplicationId, KeyDescription, id_ce_keyDescription } from '@peculiar/asn1-android';

// SHA-256 of the SSH wire-format key in GrapheneOS's published allowed_signers file.
const grapheneSignerFingerprint = '02180789fd267a2fbd68dc8a2df319061db2a6d1ddc3f68ded3961fe3d9e1703';
function sshField(value) {
  const bytes = Buffer.from(value), size = Buffer.alloc(4);
  size.writeUInt32BE(bytes.length);
  return Buffer.concat([size, bytes]);
}

// OpenSSH's signed namespace prevents signatures on other GrapheneOS documents from being accepted here.
export function grapheneBootKeys(manifest, armored) {
  const signature = Buffer.from(armored.replace(/-----[^\n]+-----|\s/g, ''), 'base64');
  const magic = Buffer.from('SSHSIG'), namespace = sshField('attestation keys'), reserved = sshField('');
  const hash = sshField('sha512'), version = Buffer.from([0, 0, 0, 1]);
  const signerOffset = magic.length + version.length;
  if (signature.length < signerOffset + 4 || signature.readUInt32BE(signerOffset) !== 51) throw Error('Invalid OS signer');
  const signer = signature.subarray(signerOffset + 4, signerOffset + 4 + 51);
  if (createHash('sha256').update(signer).digest('hex') !== grapheneSignerFingerprint) throw Error('Unknown OS signer');
  const signingKey = createPublicKey({ format: 'der', type: 'spki', key: Buffer.concat([
    Buffer.from('302a300506032b6570032100', 'hex'), signer.subarray(-32)]) });
  const prefix = Buffer.concat([magic, version, sshField(signer), namespace, reserved, hash]);
  const bytes = signature.subarray(-64);
  const expected = Buffer.concat([prefix, sshField(Buffer.concat([sshField('ssh-ed25519'), sshField(bytes)]))]);
  const message = Buffer.concat([magic, namespace, reserved, hash, sshField(createHash('sha512').update(manifest).digest())]);
  if (!signature.equals(expected) || !verify(null, message, signingKey, bytes)) throw Error('Invalid OS key list');
  const { timestamp, verifiedBootKeys } = JSON.parse(manifest);
  if (!Number.isSafeInteger(timestamp) || !Array.isArray(verifiedBootKeys) || !verifiedBootKeys.length ||
      !verifiedBootKeys.every(key => /^[a-f0-9]{64}$/.test(key))) throw Error('Invalid boot keys');
  return { timestamp, keys: new Set(verifiedBootKeys) };
}

async function trustedFile(url, maxBytes) {
  const response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(4500) });
  if (!response.ok) throw Error('Trust service unavailable');
  const parts = [];
  let size = 0;
  for await (const part of response.body) {
    size += part.length;
    if (size > maxBytes) throw Error('Oversized trust data');
    parts.push(part);
  }
  return Buffer.concat(parts);
}

function cached(load, ttl) {
  let pending, value, expires = 0, retryAfter = 0;
  return async () => {
    if (value && Date.now() < expires) return value;
    if (Date.now() < retryAfter) throw Error('Trust service unavailable');
    pending ??= load(value).then(result => {
      value = result;
      expires = Date.now() + ttl;
      return value;
    }).catch(error => { retryAfter = Date.now() + 60000; throw error; }).finally(() => { pending = undefined; });
    return pending;
  };
}

const googleTrust = cached(async () => {
  const [roots, status] = await Promise.all([
    trustedFile('https://android.googleapis.com/attestation/root', 65536),
    trustedFile('https://android.googleapis.com/attestation/status', 2 * 1024 * 1024)
  ]);
  const entries = JSON.parse(status).entries;
  if (!entries || typeof entries !== 'object' || Array.isArray(entries)) throw Error('Invalid status list');
  return { roots: JSON.parse(roots).map(pem => new X509Certificate(pem)), revoked: entries };
}, 3600000);

export const grapheneTrust = cached(async previous => {
  const [manifest, signature] = await Promise.all([
    trustedFile('https://grapheneos.org/attestation.json', 16384),
    trustedFile('https://grapheneos.org/attestation.json.sig', 4096)
  ]);
  const next = grapheneBootKeys(manifest, signature.toString());
  // Also reject lists older than the signed snapshot checked for this build on a cold function start.
  if (next.timestamp < Math.max(previous?.timestamp || 0, 1776709772)) throw Error('Older OS key list');
  return next;
}, 1800000);

export async function verifyAndroidKey(chain, challenge, signer, trust = googleTrust, bootKeys = grapheneTrust) {
  if (!Array.isArray(chain) || chain.length < 2 || chain.length > 8 || !chain.every(cert =>
    typeof cert === 'string' && cert.length <= 16384 && /^[A-Za-z0-9+/]+=*$/.test(cert))) throw Error('Invalid chain');
  const { roots, revoked } = await trust();
  const offered = chain.map(s => new X509Certificate(Buffer.from(s, 'base64')));
  const leaf = offered[0];
  if (leaf.ca || leaf.publicKey.asymmetricKeyDetails?.namedCurve !== 'prime256v1') throw Error('Invalid signing key');
  const engine = new CertificateChainValidationEngine({
    trustedCerts: roots.map(c => Certificate.fromBER(c.raw)),
    certs: [...offered].reverse().map(c => Certificate.fromBER(c.raw))
  });
  const path = await engine.verify();
  if (!path.result || !path.certificatePath?.length) throw Error('Untrusted attestation');
  const certificates = path.certificatePath;
  if (!Buffer.from(certificates[0].toSchema().toBER(false)).equals(leaf.raw)) throw Error('Wrong signing key');
  for (const certificate of certificates) {
    const serial = Buffer.from(certificate.serialNumber.valueBlock.valueHexView).toString('hex').replace(/^0+/, '');
    if (Object.hasOwn(revoked, serial)) throw Error('Revoked certificate');
  }
  // Only a key vouched for directly by the first attestation extension from the trusted root may sign writes.
  const attested = [...certificates].reverse().find(cert => cert.extensions?.some(ext => ext.extnID === id_ce_keyDescription));
  if (attested !== certificates[0]) throw Error('Invalid attested leaf');
  const extension = attested.extensions.find(ext => ext.extnID === id_ce_keyDescription);
  const data = AsnParser.parse(extension.extnValue.valueBlock.valueHexView, KeyDescription);
  const hw = data.teeEnforced, boot = hw.rootOfTrust;
  if (![1, 2].includes(data.attestationSecurityLevel) || ![1, 2].includes(data.keymasterSecurityLevel) ||
      !Buffer.from(data.attestationChallenge.buffer).equals(challenge) || hw.origin !== 0 ||
      !hw.purpose?.includes(2) || !boot?.deviceLocked) throw Error('Invalid hardware proof');
  if (boot.verifiedBootState !== 0 && (boot.verifiedBootState !== 1 ||
      !(await bootKeys()).keys.has(Buffer.from(boot.verifiedBootKey.buffer).toString('hex')))) throw Error('Untrusted OS');
  const app = AsnParser.parse(data.softwareEnforced.attestationApplicationId.buffer, AttestationApplicationId);
  if (app.packageInfos.length !== 1 || Buffer.from(app.packageInfos[0].packageName).toString() !== 'cum.ahfu.mobi.material' ||
      app.signatureDigests.length !== 1 || Buffer.from(app.signatureDigests[0]).toString('hex') !== signer)
    throw Error('Unrecognized app');
  return leaf.publicKey.export({ type: 'spki', format: 'der' }).toString('base64url');
}
