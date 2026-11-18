import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import { Action, Box, Button, Card, Chip, CollapsibleCard, DateTimePicker, Heading, Help, HStack, InputField, Modal, Notice, SelectField, Skeleton, Switch, Tabs, TextArea, TextPairing } from './ui';
import Audience, { NoticeTest } from './audience';
import NoticeWorkspace from './notice-workspace';
import UserAgentControls from './user-agent-controls';
import { collectUsernames } from '../public/audience.js';
import { defaultConfig, limitFields, validateConfig } from '../public/config-schema.js';
import { noticeForEditor, noticesWithoutVersionTargeting } from '../public/notice-editor.js';
import { followingControlsAvailable, followingMinimumVersion } from '../public/limit-availability.js';

const freshNotice = () => ({ id: `notice-${crypto.randomUUID()}`, title: '', message: '', mode: 'notice', frequency: 'once', enabled: false,
  startsAt: null, endsAt: null, minVersionCode: 1, maxVersionCode: null, requiredVersionCode: null, linkText: null, linkUrl: null, deviceNotification: false, audience: { type: 'everyone' } });
const groups = [
  { name: 'Search', hint: 'Search requests, result caching, and category scans.', keys: ['searchSpacingMs', 'searchCacheTtlMs', 'searchFailureCooldownMs', 'categorySearchRequestLimit', 'categorySearchConcurrency'] },
  { name: 'API reads', hint: 'Read pacing and cooldowns. Server Retry-After remains authoritative.', keys: ['readRetryDelayMs', 'readSpacingMs', 'readConcurrency', 'readFailureCooldownMs', 'readFailureMaxCooldownMs'] },
  { name: 'Discussions', hint: 'Cached category and first-page responses. Explicit refresh still requests fresh data.', keys: ['discussionCategoriesCacheTtlMs', 'discussionFirstPageCacheTtlMs'] },
  { name: 'Messages', hint: 'Foreground and background checks share these limits. Android background work runs no more often than every 15 minutes.', keys: ['messagePollIntervalMs', 'messageFailureMaxCooldownMs'] },
  { name: 'Member profiles', hint: 'Optional avatar and profile lookups. Failed requests back off exponentially.', keys: ['memberLookupSpacingMs', 'memberCacheTtlMs', 'memberMissingCacheTtlMs', 'memberFailureCooldownMs', 'memberFailureMaxCooldownMs'] },
  { name: 'Debrid services', hint: 'Minimum interval between automatic account and host refreshes.', keys: ['debridRefreshIntervalMs'] },
  { name: 'Following', minimumVersion: followingMinimumVersion, hint: 'Controls update-check batches, not how many apps someone can follow. Applies to manual and background checks. The user’s background schedule stays separate; search pacing and server Retry-After still apply.', keys: ['followBatchSize', 'followAppIntervalMs', 'followAttemptIntervalMs'] },
];
const labels = { searchSpacingMs: 'Request spacing', searchCacheTtlMs: 'Cache lifetime', searchFailureCooldownMs: 'Failure cooldown', categorySearchRequestLimit: 'Requests per category', categorySearchConcurrency: 'Concurrent categories', readRetryDelayMs: 'Retry delay', readSpacingMs: 'Request spacing', readConcurrency: 'Concurrent reads', readFailureCooldownMs: 'Initial cooldown', readFailureMaxCooldownMs: 'Maximum cooldown', discussionCategoriesCacheTtlMs: 'Category cache', discussionFirstPageCacheTtlMs: 'First-page cache', messagePollIntervalMs: 'Check interval', messageFailureMaxCooldownMs: 'Maximum cooldown', memberLookupSpacingMs: 'Lookup spacing', memberCacheTtlMs: 'Profile cache', memberMissingCacheTtlMs: 'Missing profile cache', memberFailureCooldownMs: 'Initial cooldown', memberFailureMaxCooldownMs: 'Maximum cooldown', debridRefreshIntervalMs: 'Refresh interval' };
const followingLabels = { followBatchSize: 'Apps per batch', followAppIntervalMs: 'Minimum interval per app', followAttemptIntervalMs: 'Minimum batch interval' };
function localDate(value) { if (!value) return ''; const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16); }
function utcDate(value) { return value ? new Date(value).toISOString() : null; }

function NoticeEditor({ notice, onChange, usernames, catalog }) {
  const set = (key, value) => onChange(noticeForEditor({ ...notice, [key]: value }));
  return <Card style={{ borderRadius: 20 }}>
    <div className="form-grid"><SelectField label="Type" value={notice.mode} options={[['notice', 'General notice'], ['update', 'Suggest an update'], ['required', 'Require an update']]} onChange={value => set('mode', value)} help="Required updates block normal navigation for matching users. Publish the target APK before enabling one." />
      <SelectField label="Repeat" value={notice.frequency} disabled={notice.mode === 'required'} options={[['once', 'Once'], ['launch', 'Each app launch'], ['always', 'Each foreground visit']]} onChange={value => set('frequency', value)} help="Required updates stay visible until the app is updated or the rule stops matching. Editing a notice makes it eligible again." />
      <div className="full-width"><InputField label="Title" maxLength={120} value={notice.title} onChange={e => set('title', e.target.value)} /></div>
      <div className="full-width"><TextArea label="Message" rows={3} maxLength={2000} value={notice.message} onChange={e => set('message', e.target.value)} /></div>
      <InputField label="Link text" maxLength={80} value={notice.linkText || ''} onChange={e => set('linkText', e.target.value || null)} />
      <InputField label="Link URL" type="url" placeholder="https://" value={notice.linkUrl || ''} onChange={e => set('linkUrl', e.target.value || null)} help="Optional HTTPS link displayed inline after the message. Notices and links are public." />
    </div>
    <div className="device-notification-field"><Switch label="Device notification" checked={notice.deviceNotification === true} onChange={event => set('deviceNotification', event.target.checked)} /><Help label="About device notifications">Also send an Android notification when an enabled notice matches. Checks run in the foreground and roughly every 15 minutes in the background. Each changed notice alerts once per account. Android notification permissions still apply.</Help></div>
    <Audience audience={notice.audience} onChange={value => set('audience', value)} usernames={usernames} catalog={catalog} />
    <div className="schedule-card"><CollapsibleCard title="Schedule" subtitle={notice.startsAt || notice.endsAt ? 'Scheduled' : 'Optional · No time limit'} defaultOpen={!!(notice.startsAt || notice.endsAt)} style={{ borderRadius: 16 }}>
      <div className="schedule-heading"><Help label="About schedules">Both dates are optional and use your browser's local time. Leave Starts blank to begin immediately. Leave Ends blank for no expiry. The notice must also be enabled and match its audience.</Help></div>
      <div className="form-grid"><DateTimePicker label="Starts" value={localDate(notice.startsAt)} onChange={value => set('startsAt', utcDate(value))} />
        <DateTimePicker label="Ends" value={localDate(notice.endsAt)} onChange={value => set('endsAt', utcDate(value))} />
      </div></CollapsibleCard>
    </div>
  </Card>;
}

export default function Controls() {
  const [published, setPublished] = useState(null), [draft, setDraft] = useState(defaultConfig), [tab, setTab] = useState('notices');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [status, setStatus] = useState('');
  const [testNotice, setTestNotice] = useState(null), [confirmReload, setConfirmReload] = useState(false);
  const [noticeDirty, setNoticeDirty] = useState(false), [workspaceKey, setWorkspaceKey] = useState(0);
  const [headersDirty, setHeadersDirty] = useState(false), [apiTab, setApiTab] = useState('limits');
  const [confirmation, setConfirmation] = useState(null);
  const [catalog, setCatalog] = useState({ versions: [], comparisonsAvailable: false }), [catalogError, setCatalogError] = useState('');
  const generation = useRef(0);
  const catalogGeneration = useRef(0);
  const dirty = noticeDirty || headersDirty || (!!published && JSON.stringify(draft) !== JSON.stringify(published));
  const usernames = collectUsernames(draft.notices);
  async function load() {
    const id = ++generation.current; setBusy(true); setError(''); setStatus('');
    try { const result = validateConfig(await api('/api/control-config')); result.notices = result.notices.map(noticeForEditor); if (id === generation.current) { setPublished(result); setDraft(result); setWorkspaceKey(key => key + 1); } }
    catch (failure) { if (id === generation.current) setError(failure.message); }
    finally { if (id === generation.current) setBusy(false); }
  }
  async function loadCatalog() {
    const id = ++catalogGeneration.current; setCatalogError('');
    try {
      const result = await api('/api/releases');
      if (id === catalogGeneration.current) {
        setCatalog(result);
        if (result.stale) setCatalogError('Using the last verified release list. GitHub could not be refreshed.');
        else if (!result.comparisonsAvailable) setCatalogError('Some release metadata could not be verified. Version lists work; new range comparisons are unavailable.');
      }
    } catch (failure) { if (id === catalogGeneration.current) setCatalogError(failure.message); }
  }
  useEffect(() => { loadCatalog(); return () => { catalogGeneration.current++; }; }, []);
  useEffect(() => { load(); return () => { generation.current++; }; }, []);
  useEffect(() => {
    if (!dirty) return;
    const warn = event => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', warn); return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);
  async function publish(candidate = draft, onSaved, preserveLimits = false, confirmed = false) {
    if (busy || !published) return;
    let validated; try { validated = validateConfig(candidate); } catch (failure) { setError(failure.message); return; }
    const unrestricted = noticesWithoutVersionTargeting(validated, published);
    if (!confirmed && unrestricted.length) { setError(''); setConfirmation({ notices: unrestricted, candidate: validated, onSaved, preserveLimits }); return; }
    setConfirmation(null);
    setBusy(true); setError(''); setStatus(''); const id = generation.current;
    try { const result = validateConfig(await api('/api/control-config', { method: 'PUT', body: validated })); if (id === generation.current) { setPublished(result); setDraft(old => preserveLimits ? { ...result, limits: old.limits } : result); setStatus(`Revision ${result.revision} saved`); onSaved?.(); } }
    catch (failure) { if (id === generation.current) setError(failure.status === 409 ? 'A newer revision exists. Your draft is preserved. Reload to use the published revision.' : failure.message); }
    finally { if (id === generation.current) setBusy(false); }
  }
  return <Box className="page controls-page">
    <header className="page-heading"><div><span className="eyebrow">ADMIN</span><Heading level={1}>App controls</Heading></div><div className="heading-meta"><Chip label={published ? `Revision ${published.revision}` : 'Loading'} size="small" /><Help label="About app controls">Changes stay in draft until published. Apps refresh within five minutes. Missing, invalid, offline, or expired configuration uses the hardcoded app defaults.</Help></div></header>
    <div className="controls-topbar"><Tabs value={tab} onChange={setTab} items={[{ key: 'notices', label: `Notices (${draft.notices.length})` }, { key: 'api', label: 'API controls' }]} /><Action icon="refresh" label="Reload published configuration" disabled={busy} onClick={() => dirty ? setConfirmReload(true) : load()} /></div>
    {tab === 'api' && <div className="api-control-tabs"><Tabs value={apiTab} onChange={setApiTab} items={[{ key: 'limits', label: 'Limits' }, { key: 'headers', label: 'User-Agent' }]} /></div>}
    <div className="control-feedback" aria-live="polite">{error && <Notice variant="danger">{error}</Notice>}{status && <Notice variant="success">{status}</Notice>}</div>
    {catalogError && <div className="catalog-feedback"><Notice variant="warning"><HStack gap={12} justify="between"><span>{catalogError}</span><Button size="sm" onClick={loadCatalog}>Retry</Button></HStack></Notice></div>}
    {!published ? <div className="control-loading">{busy ? <><Skeleton height={110} variant="rounded" /><Skeleton height={190} variant="rounded" /></> : <Button onClick={load}>Retry</Button>}</div> : <fieldset disabled={busy} inert={busy} className="control-workspace">
      <div hidden={tab !== 'notices'}><NoticeWorkspace key={workspaceKey} config={published} catalog={catalog} usernames={usernames} freshNotice={freshNotice} Editor={NoticeEditor} busy={busy}
        onDirtyChange={setNoticeDirty} onTest={setTestNotice} onSave={(notices, onSaved) => publish({ ...published, notices }, onSaved, true)} /></div>
      <div hidden={tab !== 'api' || apiTab !== 'headers'}><UserAgentControls key={workspaceKey} rules={published.userAgentRules} catalog={catalog} busy={busy} onDirtyChange={setHeadersDirty} onSave={(userAgentRules, onSaved) => publish({ ...published, userAgentRules }, onSaved, true)} /></div>
      <div hidden={tab !== 'api' || apiTab !== 'limits'}>
        <div className="workspace-heading"><HStack gap={6}><TextPairing title="API limits" /><Help label="About API limits">Leave a field blank to use its shipped default. Durations are milliseconds. Exponential cooldowns never cap below the initial interval. Server Retry-After is always respected.</Help></HStack><Button onClick={() => setDraft(old => ({ ...old, limits: {} }))} disabled={!Object.keys(draft.limits).length}>Use app defaults</Button></div>
        <div className="limits-grid">{groups.filter(group => !group.minimumVersion || followingControlsAvailable(catalog)).map(group => <Card key={group.name} title={group.name} headerEnd={<Help label={`About ${group.name}`}>{group.hint}</Help>} style={{ borderRadius: 20 }}>{group.minimumVersion && <p>Requires v{group.minimumVersion} or later.</p>}<div className="limit-fields">{group.keys.map(key => {
          const [, , fallback, min, max] = limitFields.find(f => f[0] === key);
          return <InputField key={key} label={`${followingLabels[key] || labels[key]}${key.endsWith('Ms') ? ' (ms)' : ''}`} help={`Default: ${fallback.toLocaleString()}. Range: ${min.toLocaleString()} to ${max.toLocaleString()}. Blank uses the default.`} type="number" min={min} max={max} step={1} placeholder={fallback.toLocaleString()} value={draft.limits[key] ?? ''} onChange={event => { const value = event.target.value; setDraft(old => { const limits = { ...old.limits }; if (value === '') delete limits[key]; else limits[key] = Number(value); return { ...old, limits }; }); }} />;
        })}</div></Card>)}</div>
      </div>
    </fieldset>}
    {tab === 'api' && apiTab === 'limits' && <div className="limit-save-actions"><Button variant="filled" disabled={!published || busy || JSON.stringify(draft.limits) === JSON.stringify(published.limits)} loading={busy && !!published} onClick={() => publish()}>Save API limits</Button></div>}
    {testNotice && <NoticeTest notice={testNotice} onClose={() => setTestNotice(null)} catalog={catalog} />}
    <Modal open={!!confirmation} onClose={() => setConfirmation(null)} title="Save without version targeting?" size="small" footer={<><Button onClick={() => setConfirmation(null)}>Review targeting</Button><Button variant="danger" onClick={() => publish(confirmation.candidate, confirmation.onSaved, confirmation.preserveLimits, true)}>Save anyway</Button></>}>
      <p>These notices can match users on any app version, including future releases.</p>
      <ul>{confirmation?.notices.map(notice => <li key={notice.id}>{notice.title}</li>)}</ul>
      {confirmation?.notices.some(notice => notice.mode === 'required') && <Notice variant="warning">A required update can keep blocking users after they update. Add an app version condition to stop it automatically.</Notice>}
    </Modal>
    <Modal open={confirmReload} onClose={() => setConfirmReload(false)} title="Discard this draft?" size="small" footer={<><Button onClick={() => setConfirmReload(false)}>Keep editing</Button><Button variant="danger" onClick={() => { setConfirmReload(false); load(); }}>Reload published</Button></>}>Your unpublished changes will be replaced by the latest published configuration.</Modal>
  </Box>;
}
