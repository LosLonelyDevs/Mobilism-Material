import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, randomBytes, randomUUID, sign, webcrypto, X509Certificate } from 'node:crypto';
import { AsnSerializer, OctetString } from '@peculiar/asn1-schema';
import { AttestationApplicationId, AttestationPackageInfo, AuthorizationList, IntegerSet, KeyDescription, RootOfTrust, id_ce_keyDescription } from '@peculiar/asn1-android';
import { AttributeTypeAndValue, BasicConstraints, Certificate, Extension } from 'pkijs';
import { Integer, Utf8String } from 'asn1js';
import { PGlite } from '@electric-sql/pglite';
import { readFile } from 'node:fs/promises';
import { authorizeAppWrite, digest, issueTicket, signatureInput } from '../lib/app-writes.js';
import { grapheneBootKeys, verifyAndroidKey } from '../lib/android-attestation.js';
import { createHandler as enrollment } from '../api/app-attestation.js';
import { createHandler as latest } from '../api/latest-apps.js';
import { createHandler as ping } from '../api/ping.js';

async function certificate(keys, issuer, signer, ca, description) {
  const cert = new Certificate();
  cert.version = 2;
  cert.serialNumber = new Integer({ value: ca ? 1 : 2 });
  cert.subject.typesAndValues.push(new AttributeTypeAndValue({ type: '2.5.4.3', value: new Utf8String({ value: ca ? 'Test CA' : 'Test key' }) }));
  cert.issuer = issuer?.subject ?? cert.subject;
  cert.notBefore.value = new Date(Date.now() - 60000);
  cert.notAfter.value = new Date(Date.now() + 86400000);
  await cert.subjectPublicKeyInfo.importKey(keys.publicKey);
  cert.extensions = [new Extension({ extnID: '2.5.29.19', critical: true,
    extnValue: new BasicConstraints({ cA: ca }).toSchema().toBER(false) })];
  if (description) cert.extensions.push(new Extension({ extnID: id_ce_keyDescription, extnValue: AsnSerializer.serialize(description) }));
  await cert.sign(signer, 'SHA-256');
  return cert;
}
test('checks the published GrapheneOS signature before trusting any OS key', async () => {
  const { manifest, signature } = JSON.parse(await readFile(new URL('./fixtures/graphene-boot-keys.json', import.meta.url)));
  const verified = grapheneBootKeys(Buffer.from(manifest), signature);
  assert.ok(verified.keys.size > 0);
  assert.throws(() => grapheneBootKeys(Buffer.from(manifest + ' '), signature));
});
const wire = cert => Buffer.from(cert.toSchema().toBER(false)).toString('base64');
const ecKey = () => webcrypto.subtle.generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
const challenge = randomBytes(32), appSigner = randomBytes(32), osKey = randomBytes(32);
const signer = appSigner.toString('hex');
function description(state, locked = true) {
  const app = new AttestationApplicationId({ packageInfos: [new AttestationPackageInfo({
    packageName: new OctetString(Buffer.from('cum.ahfu.mobi.material')), version: 28 })], signatureDigests: [new OctetString(appSigner)] });
  return new KeyDescription({ attestationVersion: 400, keymasterVersion: 400, attestationSecurityLevel: 1,
    keymasterSecurityLevel: 1, attestationChallenge: new OctetString(challenge), softwareEnforced: new AuthorizationList({
      attestationApplicationId: new OctetString(AsnSerializer.serialize(app)) }), teeEnforced: new AuthorizationList({
      purpose: new IntegerSet([2]), origin: 0, rootOfTrust: new RootOfTrust({ deviceLocked: locked,
        verifiedBootState: state, verifiedBootKey: new OctetString(osKey), verifiedBootHash: new OctetString(randomBytes(32)) }) }) });
}
test('attests sideloaded stock and approved GrapheneOS keys, rejecting revoked, unlocked and unknown signers', async () => {
  const rootKey = await ecKey(), leafKey = await ecKey();
  const ca = await certificate(rootKey, null, rootKey.privateKey, true);
  const root = new X509Certificate(Buffer.from(wire(ca), 'base64'));
  const chain = async desc => [wire(await certificate(leafKey, ca, rootKey.privateKey, false, desc)), wire(ca)];
  const trust = async () => ({ roots: [root], revoked: {} });
  const graphene = async () => ({ keys: new Set([osKey.toString('hex')]) });
  const stock = await chain(description(0)), gos = await chain(description(1));
  const publicKey = await verifyAndroidKey(stock, challenge, signer, trust, graphene);
  const otherRoot = await ecKey();
  const otherCA = await certificate(otherRoot, null, otherRoot.privateKey, true);
  await assert.rejects(verifyAndroidKey(stock, challenge, signer, async () => ({
    roots: [new X509Certificate(Buffer.from(wire(otherCA), 'base64'))], revoked: {}
  }), graphene));
  assert.equal(await verifyAndroidKey(gos, challenge, signer, trust, graphene), publicKey);
  await assert.rejects(verifyAndroidKey(gos, challenge, signer, trust, async () => ({ keys: new Set() })));
  await assert.rejects(verifyAndroidKey(stock, randomBytes(32), signer, trust, graphene));
  await assert.rejects(verifyAndroidKey(stock, challenge, randomBytes(32).toString('hex'), trust, graphene));
  await assert.rejects(verifyAndroidKey(await chain(description(0, false)), challenge, signer, trust, graphene));
  await assert.rejects(verifyAndroidKey(stock, challenge, signer, async () => ({ roots: [root], revoked: { '2': {} } }), graphene));
});
const env = { APP_ATTESTATION_SECRET: randomBytes(32).toString('hex'), ANDROID_RELEASE_CERT_SHA256: signer, APP_WRITE_POLICY: 'enforce' };
const headers = { 'content-type': 'application/json', 'x-following-check': 'manual', 'user-agent': 'Fixture' };
async function call(handler, method, body) {
  const res = { setHeader() {}, end(value) { this.body = JSON.parse(value); } };
  await handler({ method, headers, body }, res);
  return res;
}
test('enforcement protects all write paths, leaves lookups readable, and rejects tampering and concurrent replay', async () => {
  const db = new PGlite();
  try {
    await db.exec(await readFile(new URL('../schema.sql', import.meta.url), 'utf8'));
    const query = async (sql, args) => (await db.query(sql, args)).rows;
    const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
    const key = publicKey.export({ format: 'der', type: 'spki' }).toString('base64url');
    const now = Math.floor(Date.now() / 1000);
    const register = enrollment(query, env, () => now, async (_chain, hash) => { assert.deepEqual(hash, digest(challengeToken)); return key; });
    const challengeToken = (await call(register, 'GET')).body.challenge;
    const enrolled = await call(register, 'POST', { challenge: challengeToken, chain: [] });
    assert.equal(enrolled.statusCode, 200);
    assert.equal((await call(register, 'POST', { challenge: challengeToken, chain: [] })).statusCode, 409);
    const feed = latest(query, () => now, env), reports = ping(query, () => new Date(), env);
    const body = { firstPage: true, adultFiltered: false, items: [{ id: 1, title: 'Example v1', name: 'Example',
      version: '1', postedAt: now, author: 'fixture' }], readAt: now };
    const signed = (value, purpose = 'following') => {
      const envelope = { payload: JSON.stringify(value), at: now, nonce: randomUUID(), ticket: enrolled.body.ticket };
      envelope.signature = sign('sha256', Buffer.from(signatureInput(purpose, headers, envelope)), privateKey).toString('base64url');
      return envelope;
    };
    assert.equal((await call(feed, 'POST', body)).statusCode, 403);
    assert.equal((await call(reports, 'POST', {})).statusCode, 428);
    const report = { schema: 1, day: new Date().toISOString().slice(0, 10), pingId: randomUUID(),
      appVersion: '0.1.27', versionCode: 28, androidApi: 35, manufacturer: 'test', model: 'fixture' };
    assert.equal((await call(reports, 'POST', signed(report, 'usage'))).statusCode, 200);
    assert.equal(Number((await query('SELECT devices FROM daily_usage'))[0].devices), 1);
    const observed = { ...env, APP_WRITE_POLICY: 'observe' };
    assert.equal((await authorizeAppWrite(body, { headers }, 'following', query, observed, now)).allowed, true);
    assert.equal((await authorizeAppWrite(body, { headers }, 'following', query,
      { APP_WRITE_POLICY: 'enforce' }, now)).allowed, false);
    const observation = signed(body);
    assert.equal((await authorizeAppWrite({ ...observation, payload: '{}' }, { headers }, 'following', query, observed, now)).allowed, false);
    assert.equal((await call(feed, 'POST', { ...observation, payload: JSON.stringify({ ...body, firstPage: false }) })).statusCode, 403);
    assert.equal((await call(feed, 'POST', observation)).statusCode, 200);
    const apps = { apps: [{ name: 'Example', receipt: randomUUID() }], adultFiltered: false, day: new Date().toISOString().slice(0, 10) };
    assert.equal((await call(feed, 'POST', apps)).body.items.length, 1);
    assert.equal((await query('SELECT * FROM daily_followed_apps')).length, 0);
    const lookup = signed(apps);
    const reads = await Promise.all([call(feed, 'POST', lookup), call(feed, 'POST', lookup)]);
    assert.ok(reads.every(r => r.statusCode === 200 && r.body.items.length === 1));
    assert.equal(Number((await query('SELECT hits FROM daily_followed_apps'))[0].hits), 1);
    const expired = { ...observation, ticket: issueTicket('write', { key, exp: now - 1 }, env.APP_ATTESTATION_SECRET) };
    assert.equal((await authorizeAppWrite(expired, { headers }, 'following', query, env, now)).allowed, false);
    assert.equal((await authorizeAppWrite(signed(apps), { headers }, 'usage', query, env, now)).allowed, false);
  } finally { await db.close(); }
});
