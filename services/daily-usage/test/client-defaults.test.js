import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { limitFields } from '../public/config-schema.js';

test('dashboard API placeholders match the shipped Android defaults', async () => {
  const source = await readFile(new URL('../../../core/data/src/main/java/org/mobilism/material/data/RemoteAppConfig.kt', import.meta.url), 'utf8');
  const constructor = source.split('companion object')[0];
  const defaults = Object.fromEntries([...constructor.matchAll(/val (\w+): (?:Long|Int) = ([0-9_* \t]+),/g)]
    .map(([, name, expression]) => [name, expression.replaceAll('_', '').split('*').map(Number).reduce((left, right) => left * right, 1)]));
  assert.deepEqual(Object.keys(defaults).sort(), limitFields.map(([name]) => name).sort());
  for (const [name, , placeholder] of limitFields) assert.equal(placeholder, defaults[name], name);
});
