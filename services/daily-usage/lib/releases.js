import { normalizeVersion } from '../public/audience.js';

const repository = 'LosLonelyDevs/Mobilism-Material';
const cacheMs = 10 * 60_000;
export function compareVersions(a, b) {
  const left = normalizeVersion(a).split('.').map(Number), right = normalizeVersion(b).split('.').map(Number);
  for (let index = 0; index < 3; index++) if (left[index] !== right[index]) return left[index] - right[index];
  return 0;
}

export function releaseVersion(release, source) {
  const version = normalizeVersion(release.tag_name);
  const name = source.match(/^\s*versionName\s*=\s*"([^"]+)"/m)?.[1];
  const code = Number(source.match(/^\s*versionCode\s*=\s*(\d+)\s*$/m)?.[1]);
  if (name !== version || !Number.isInteger(code) || code < 1 || code > 2147483647)
    throw new Error('Release metadata does not match its Android version.');
  return { version, versionCode: code, prerelease: release.prerelease === true };
}

export function createReleaseCatalog(fetcher = fetch, now = Date.now) {
  let cached, expires = 0, pending;
  async function request(url) {
    const response = await fetcher(url, { headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'Mobilism-Material-Admin' }, signal: AbortSignal.timeout(8000) });
    if (!response.ok) throw new Error('GitHub releases are unavailable. Try again.');
    return response;
  }
  async function refresh() {
    const releases = [];
    for (let page = 1; page <= 5; page++) {
      const rows = await (await request(`https://api.github.com/repos/${repository}/releases?per_page=100&page=${page}`)).json();
      if (!Array.isArray(rows)) throw new Error('Invalid release list.');
      releases.push(...rows.filter(row => !row.draft && /^v?\d+\.\d+\.\d+$/.test(row.tag_name)));
      if (rows.length < 100) break;
      if (page === 5) throw new Error('Release list is too large.');
    }
    const versions = [], unavailable = [];
    for (let index = 0; index < releases.length; index += 5) {
      await Promise.all(releases.slice(index, index + 5).map(async release => {
        try {
          const source = await (await request(`https://raw.githubusercontent.com/${repository}/${encodeURIComponent(release.tag_name)}/app/build.gradle.kts`)).text();
          versions.push(releaseVersion(release, source));
        } catch { unavailable.push(normalizeVersion(release.tag_name)); }
      }));
    }
    versions.sort((a, b) => compareVersions(b.version, a.version));
    if (!versions.length) throw new Error('Could not verify any release versions. Try again.');
    // Existing APKs compare Android version codes. Only offer ranges when releases prove the same ordering.
    const comparisonsAvailable = !unavailable.length && versions.every((row, index) => !index || versions[index - 1].versionCode > row.versionCode);
    cached = { versions, comparisonsAvailable, unavailable, checkedAt: new Date(now()).toISOString() };
    expires = now() + cacheMs;
    return cached;
  }
  return async () => {
    if (cached && now() < expires) return cached;
    if (!pending) pending = refresh().finally(() => { pending = null; });
    try { return await pending; }
    catch (error) { if (cached) return { ...cached, stale: true }; throw error; }
  };
}

export const releaseCatalog = createReleaseCatalog();
