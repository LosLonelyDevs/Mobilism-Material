export function percentage(count, total) {
  const value = BigInt(count), denominator = BigInt(total);
  return denominator > 0n ? Number((value * 1000n + denominator / 2n) / denominator) / 10 : 0;
}

export function groupUsage(rows, dimension) {
  const groups = new Map();
  const total = rows.reduce((sum, row) => sum + BigInt(row.devices), 0n);
  for (const row of rows) {
    const brand = row.manufacturer.trim();
    const model = row.model.trim();
    const api = Number(row.androidApi);
    const android = ({ 26: '8.0', 27: '8.1', 28: '9', 29: '10', 30: '11', 31: '12', 32: '12L' })[api] || (api >= 33 && api <= 37 ? String(api - 20) : null);
    const [key, label, detail] = dimension === 'versions' ? [row.appVersion, `v${row.appVersion}`, ''] :
      dimension === 'android' ? [String(api), android ? `Android ${android}` : `Android API ${api}`, `API ${api}`] :
      dimension === 'brands' ? [brand.toLowerCase(), brand.charAt(0).toUpperCase() + brand.slice(1), ''] :
      [JSON.stringify([brand.toLowerCase(), model.toLowerCase()]), model, brand];
    const group = groups.get(key) || { key, label, detail, devices: 0n, builds: new Set() };
    group.devices += BigInt(row.devices);
    group.builds.add(row.versionCode);
    groups.set(key, group);
  }
  return [...groups.values()].sort((a, b) => a.devices === b.devices ? a.label.localeCompare(b.label) : a.devices > b.devices ? -1 : 1)
    .map(group => ({ ...group, detail: dimension === 'versions' ? `Build ${[...group.builds].sort((a, b) => a - b).join(', ')}` : group.detail,
      share: percentage(group.devices, total) }));
}
