export const followingMinimumVersion = '0.1.24';

export function followingControlsAvailable(catalog) {
  const minimum = followingMinimumVersion.split('.').map(Number);
  return Array.isArray(catalog?.versions) && catalog.versions.some(release => {
    if (release?.prerelease !== false || !Number.isInteger(release.versionCode) || release.versionCode < 1 ||
        typeof release.version !== 'string' || !/^\d+\.\d+\.\d+$/.test(release.version)) return false;
    const parts = release.version.split('.').map(Number);
    const difference = parts.map((part, index) => part - minimum[index]).find(value => value !== 0);
    return difference === undefined || difference > 0;
  });
}
