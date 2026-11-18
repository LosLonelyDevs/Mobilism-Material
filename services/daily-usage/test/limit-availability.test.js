import test from 'node:test';
import assert from 'node:assert/strict';
import { followingControlsAvailable, followingMinimumVersion } from '../public/limit-availability.js';

const catalog = (version, extra = {}) => ({ versions: [{ version, versionCode: 25, prerelease: false, ...extra }] });

test('Following stays hidden until a verified stable supporting release exists', () => {
  for (const value of [undefined, {}, { versions: [] }, { unavailable: [followingMinimumVersion] },
    catalog('0.1.23'), catalog('0.1.9'), catalog(followingMinimumVersion, { prerelease: true }),
    catalog(followingMinimumVersion, { versionCode: null }), catalog('invalid')]) {
    assert.equal(followingControlsAvailable(value), false);
  }
  assert.equal(followingControlsAvailable(catalog(followingMinimumVersion)), true);
  assert.equal(followingControlsAvailable(catalog('0.1.100')), true);
  assert.equal(followingControlsAvailable(catalog('0.2.0')), true);
  assert.equal(followingControlsAvailable({ ...catalog(followingMinimumVersion), stale: true }), true);
});
