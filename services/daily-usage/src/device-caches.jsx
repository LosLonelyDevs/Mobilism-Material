import { Button, Card, Help, InputField } from './ui';

export const cacheLimitKeys = ['releaseLatestCacheTtlMs', 'releaseTopCacheTtlMs', 'premiumHostCacheTtlMs'];
export function selectLimits(limits, deviceCaches) {
  return Object.fromEntries(Object.entries(limits).filter(([key]) => cacheLimitKeys.includes(key) === deviceCaches));
}

export default function DeviceCaches({ limits, published, change, reset, save, busy }) {
  function duration(key, label, unit, max, placeholder) {
    const ms = limits[key];
    return <InputField label={label} type="number" min={0} max={max} step="any" placeholder={placeholder}
      value={ms == null ? '' : ms / unit} onChange={event => {
        const raw = event.target.value;
        change(key, raw === '' ? undefined : Math.round(Number(raw) * unit));
      }} />;
  }
  const dirty = cacheLimitKeys.some(key => limits[key] !== published.limits[key]);
  const valid = cacheLimitKeys.every(key => limits[key] == null || (Number.isSafeInteger(limits[key]) &&
    limits[key] >= 0 && limits[key] <= (key === 'premiumHostCacheTtlMs' ? 604800000 : 86400000)));
  function resetButton(target, label) {
    return <Button size="sm" aria-label={`Clear ${label} cache`} onClick={() => reset(target)}>Clear {label}</Button>;
  }
  return <>
    <div className="limits-grid device-caches">
      <Card title="Release lists" headerEnd={<Help label="About release list caches">Applies to the lists and their Home shelves on updated phones. Zero bypasses the saved page on the next visit. Reset takes effect on next use.</Help>}>
        <div className="cache-release-fields">
          {duration('releaseLatestCacheTtlMs', 'Latest (minutes)', 60000, 1440, '5')}
          {resetButton('Latest', 'Latest')}
          {duration('releaseTopCacheTtlMs', 'Top (minutes)', 60000, 1440, '15')}
          {resetButton('Top', 'Top')}
        </div>
      </Card>
      <Card title="Premium host lists" headerEnd={<Help label="About Premium host lists">Zero keeps a catalog until a refresh or reset. Reset clears only the selected provider’s hosts. Its saved login stays connected.</Help>}>
        {duration('premiumHostCacheTtlMs', 'Maximum age (hours)', 3600000, 168, 'Until refreshed')}
        <div className="cache-provider-actions">{[['RealDebrid', 'Real-Debrid'], ['MegaDebrid', 'MegaDebrid'], ['Mobilism', 'Mobilism Premium']]
          .map(([target, label]) => <span key={target}>{resetButton(target, label)}</span>)}</div>
      </Card>
    </div>
    <div className="cache-save-actions"><span className="muted">Blank fields use the app defaults.</span>
      <Button variant="filled" disabled={busy || !dirty || !valid} onClick={save}>Save cache lifetimes</Button></div>
  </>;
}
