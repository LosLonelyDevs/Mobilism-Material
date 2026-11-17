import { useEffect, useRef, useState } from 'react';
import { api } from './api';
import { Action, Box, Button, Card, Chip, CollapsibleCard, DateTimePicker, Heading, Help, HStack, Icon, InputField, Modal, Notice, SelectField, Skeleton, Switch, Tabs, TextArea, TextPairing, Tooltip, VStack } from './ui';
import Audience, { NoticeTest } from './audience';
import { collectUsernames } from '../public/audience.js';
import { defaultConfig, limitFields, validateConfig } from '../public/config-schema.js';
import { noticeForEditor, noticesWithoutVersionTargeting, noticeSummary } from '../public/notice-editor.js';

const freshNotice = () => ({ id: `notice-${crypto.randomUUID()}`, title: '', message: '', mode: 'notice', frequency: 'once', enabled: false,
  startsAt: null, endsAt: null, minVersionCode: 1, maxVersionCode: null, requiredVersionCode: null, linkText: null, linkUrl: null, audience: { type: 'everyone' } });
const groups = [
  { name: 'Search', hint: 'Search requests, result caching, and category scans.', keys: ['searchSpacingMs', 'searchCacheTtlMs', 'searchFailureCooldownMs', 'categorySearchRequestLimit', 'categorySearchConcurrency'] },
  { name: 'API reads', hint: 'Read pacing and cooldowns. Server Retry-After remains authoritative.', keys: ['readRetryDelayMs', 'readSpacingMs', 'readConcurrency', 'readFailureCooldownMs', 'readFailureMaxCooldownMs'] },
  { name: 'Discussions', hint: 'Cached category and first-page responses. Explicit refresh still requests fresh data.', keys: ['discussionCategoriesCacheTtlMs', 'discussionFirstPageCacheTtlMs'] },
  { name: 'Messages', hint: 'Foreground and background checks share these limits. Android background work runs no more often than every 15 minutes.', keys: ['messagePollIntervalMs', 'messageFailureMaxCooldownMs'] },
  { name: 'Member profiles', hint: 'Optional avatar and profile lookups. Failed requests back off exponentially.', keys: ['memberLookupSpacingMs', 'memberCacheTtlMs', 'memberMissingCacheTtlMs', 'memberFailureCooldownMs', 'memberFailureMaxCooldownMs'] },
  { name: 'Debrid services', hint: 'Minimum interval between automatic account and host refreshes.', keys: ['debridRefreshIntervalMs'] },
];
const labels = { searchSpacingMs: 'Request spacing', searchCacheTtlMs: 'Cache lifetime', searchFailureCooldownMs: 'Failure cooldown', categorySearchRequestLimit: 'Requests per category', categorySearchConcurrency: 'Concurrent categories', readRetryDelayMs: 'Retry delay', readSpacingMs: 'Request spacing', readConcurrency: 'Concurrent reads', readFailureCooldownMs: 'Initial cooldown', readFailureMaxCooldownMs: 'Maximum cooldown', discussionCategoriesCacheTtlMs: 'Category cache', discussionFirstPageCacheTtlMs: 'First-page cache', messagePollIntervalMs: 'Check interval', messageFailureMaxCooldownMs: 'Maximum cooldown', memberLookupSpacingMs: 'Lookup spacing', memberCacheTtlMs: 'Profile cache', memberMissingCacheTtlMs: 'Missing profile cache', memberFailureCooldownMs: 'Initial cooldown', memberFailureMaxCooldownMs: 'Maximum cooldown', debridRefreshIntervalMs: 'Refresh interval' };
function localDate(value) { if (!value) return ''; const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16); }
function utcDate(value) { return value ? new Date(value).toISOString() : null; }

function NoticeEditor({ notice, index, count, onChange, onRemove, onMove, onTest, usernames, catalog }) {
  const set = (key, value) => onChange({ ...notice, [key]: value });
  const summary = `${String(index + 1).padStart(2, '0')} · ${noticeSummary(notice, catalog.versions)}`;
  return <div className="notice-card"><CollapsibleCard title={notice.title || 'Untitled notice'} defaultOpen={!notice.title} style={{ borderRadius: 20 }}
    subtitle={<Tooltip content={summary}><span className="notice-summary">{summary}</span></Tooltip>}
    headerEnd={<Chip label={notice.enabled ? 'Enabled' : 'Disabled'} color={notice.enabled ? 'success' : 'default'} size="small" />}>
    <div className="notice-toolbar"><Switch label="Enabled" checked={notice.enabled} onChange={e => set('enabled', e.target.checked)} /><HStack gap={4} style={{ flexWrap: 'wrap' }}>
      <Action icon="eye" label="Test this notice" onClick={onTest} /><Action icon="up" label="Move notice up" disabled={index === 0} onClick={() => onMove(-1)} /><Action icon="down" label="Move notice down" disabled={index === count - 1} onClick={() => onMove(1)} /><Action icon="trash" label="Remove notice" onClick={onRemove} />
    </HStack></div>
    <div className="form-grid"><SelectField label="Type" value={notice.mode} options={[['notice', 'General notice'], ['update', 'Suggest an update'], ['required', 'Require an update']]} onChange={value => set('mode', value)} help="Required updates block normal navigation for matching users. Publish the target APK before enabling one." />
      <SelectField label="Repeat" value={notice.frequency} disabled={notice.mode === 'required'} options={[['once', 'Once'], ['launch', 'Each app launch'], ['always', 'Each foreground visit']]} onChange={value => set('frequency', value)} help="Required updates stay visible until the app is updated or the rule stops matching. Editing a notice makes it eligible again." />
      <div className="full-width"><InputField label="Title" maxLength={120} value={notice.title} onChange={e => set('title', e.target.value)} /></div>
      <div className="full-width"><TextArea label="Message" rows={3} maxLength={2000} value={notice.message} onChange={e => set('message', e.target.value)} /></div>
      <InputField label="Link text" maxLength={80} value={notice.linkText || ''} onChange={e => set('linkText', e.target.value || null)} />
      <InputField label="Link URL" type="url" placeholder="https://" value={notice.linkUrl || ''} onChange={e => set('linkUrl', e.target.value || null)} help="Optional HTTPS link displayed inline after the message. Notices and links are public." />
    </div>
    <Audience audience={notice.audience} onChange={value => set('audience', value)} usernames={usernames} catalog={catalog} />
    <div className="schedule-card"><CollapsibleCard title="Schedule" subtitle={notice.startsAt || notice.endsAt ? 'Scheduled' : 'Optional · No time limit'} defaultOpen={!!(notice.startsAt || notice.endsAt)} style={{ borderRadius: 16 }}>
      <div className="schedule-heading"><Help label="About schedules">Both dates are optional and use your browser's local time. Leave Starts blank to begin immediately. Leave Ends blank for no expiry. The notice must also be enabled and match its audience.</Help></div>
      <div className="form-grid"><DateTimePicker label="Starts" value={localDate(notice.startsAt)} onChange={value => set('startsAt', utcDate(value))} />
        <DateTimePicker label="Ends" value={localDate(notice.endsAt)} onChange={value => set('endsAt', utcDate(value))} />
      </div></CollapsibleCard>
    </div>
  </CollapsibleCard></div>;
}

export default function Controls() {
  const [published, setPublished] = useState(null), [draft, setDraft] = useState(defaultConfig), [tab, setTab] = useState('notices');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [status, setStatus] = useState('');
  const [testId, setTestId] = useState(null), [confirmReload, setConfirmReload] = useState(false), [removed, setRemoved] = useState(null);
  const [confirmation, setConfirmation] = useState(null);
  const [catalog, setCatalog] = useState({ versions: [], comparisonsAvailable: false }), [catalogError, setCatalogError] = useState('');
  const generation = useRef(0);
  const catalogGeneration = useRef(0);
  const dirty = !!published && JSON.stringify(draft) !== JSON.stringify(published);
  const usernames = collectUsernames(draft.notices);
  const testNotice = draft.notices.find(n => n.id === testId);
  async function load() {
    const id = ++generation.current; setBusy(true); setError(''); setStatus('');
    try { const result = validateConfig(await api('/api/control-config')); result.notices = result.notices.map(noticeForEditor); if (id === generation.current) { setPublished(result); setDraft(result); setRemoved(null); } }
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
  async function publish(confirmed = false) {
    if (busy || !published) return;
    let validated; try { validated = validateConfig(draft); } catch (failure) { setError(failure.message); return; }
    const unrestricted = noticesWithoutVersionTargeting(validated, published);
    if (!confirmed && unrestricted.length) { setError(''); setConfirmation(unrestricted); return; }
    setConfirmation(null);
    setBusy(true); setError(''); setStatus(''); const id = generation.current;
    try { const result = validateConfig(await api('/api/control-config', { method: 'PUT', body: validated })); if (id === generation.current) { setPublished(result); setDraft(result); setRemoved(null); setStatus(`Revision ${result.revision} published`); } }
    catch (failure) { if (id === generation.current) setError(failure.status === 409 ? 'A newer revision exists. Your draft is preserved. Reload to use the published revision.' : failure.message); }
    finally { if (id === generation.current) setBusy(false); }
  }
  function changeNotice(index, value) { setDraft(old => ({ ...old, notices: old.notices.map((n, i) => i === index ? noticeForEditor(value) : n) })); }
  function remove(index) { setRemoved({ index, notice: draft.notices[index] }); setDraft(old => ({ ...old, notices: old.notices.filter((_, i) => i !== index) })); }
  function undo() { if (!removed || draft.notices.length >= 10) return; const notices = [...draft.notices]; notices.splice(removed.index, 0, removed.notice); setDraft(old => ({ ...old, notices })); setRemoved(null); }
  function move(index, delta) { const notices = [...draft.notices]; [notices[index], notices[index + delta]] = [notices[index + delta], notices[index]]; setDraft(old => ({ ...old, notices })); }
  return <Box className="page controls-page">
    <header className="page-heading"><div><span className="eyebrow">ADMIN</span><Heading level={1}>App controls</Heading></div><div className="heading-meta"><Chip label={published ? `Revision ${published.revision}` : 'Loading'} size="small" /><Help label="About app controls">Changes stay in draft until published. Apps refresh within five minutes. Missing, invalid, offline, or expired configuration uses the hardcoded app defaults.</Help></div></header>
    <div className="controls-topbar"><Tabs value={tab} onChange={setTab} items={[{ key: 'notices', label: `Notices (${draft.notices.length})` }, { key: 'limits', label: 'API limits' }]} /><Action icon="refresh" label="Reload published configuration" disabled={busy} onClick={() => dirty ? setConfirmReload(true) : load()} /></div>
    <div className="control-feedback" aria-live="polite">{error && <Notice variant="danger">{error}</Notice>}{status && <Notice variant="success">{status}</Notice>}{removed && <Notice variant="info"><HStack justify="between" gap={12}><span>Notice removed from draft.</span><Button size="sm" onClick={undo} disabled={draft.notices.length >= 10}>Undo</Button></HStack></Notice>}</div>
    {catalogError && <div className="catalog-feedback"><Notice variant="warning"><HStack gap={12} justify="between"><span>{catalogError}</span><Button size="sm" onClick={loadCatalog}>Retry</Button></HStack></Notice></div>}
    {!published ? <div className="control-loading">{busy ? <><Skeleton height={110} variant="rounded" /><Skeleton height={190} variant="rounded" /></> : <Button onClick={load}>Retry</Button>}</div> : <fieldset disabled={busy} inert={busy} className="control-workspace">
      {tab === 'notices' ? <>
        <div className="workspace-heading"><HStack gap={6}><TextPairing title="In-app notices" /><Help label="Notice priority">Required updates take priority. Other notices appear in the order shown. Test a username allowlist before enabling a notice for everyone.</Help></HStack><Button onClick={() => setDraft(old => ({ ...old, notices: [...old.notices, freshNotice()] }))} disabled={draft.notices.length >= 10}><Icon name="plus" size={17} /> Add notice</Button></div>
        {draft.notices.length ? <VStack gap={16} style={{ alignItems: 'stretch' }}>{draft.notices.map((notice, index) => <NoticeEditor key={notice.id} notice={notice} index={index} count={draft.notices.length} onChange={value => changeNotice(index, value)} onRemove={() => remove(index)} onMove={delta => move(index, delta)} onTest={() => setTestId(notice.id)} usernames={usernames} catalog={catalog} />)}</VStack> :
          <Card style={{ borderStyle: 'dashed', borderRadius: 24 }}><div className="empty-notices"><span className="destination-icon"><Icon name="controls" size={26} /></span><Heading level={2}>No notices yet</Heading><Button variant="filled" onClick={() => setDraft(old => ({ ...old, notices: [freshNotice()] }))}>Create a notice</Button></div></Card>}
      </> : <>
        <div className="workspace-heading"><HStack gap={6}><TextPairing title="API limits" /><Help label="About API limits">Leave a field blank to use its shipped default. Durations are milliseconds. Exponential cooldowns never cap below the initial interval. Server Retry-After is always respected.</Help></HStack><Button onClick={() => setDraft(old => ({ ...old, limits: {} }))} disabled={!Object.keys(draft.limits).length}>Use app defaults</Button></div>
        <div className="limits-grid">{groups.map(group => <Card key={group.name} title={group.name} headerEnd={<Help label={`About ${group.name}`}>{group.hint}</Help>} style={{ borderRadius: 20 }}><div className="limit-fields">{group.keys.map(key => {
          const [, , fallback, min, max] = limitFields.find(f => f[0] === key);
          return <InputField key={key} label={`${labels[key]}${key.endsWith('Ms') ? ' (ms)' : ''}`} help={`Default: ${fallback.toLocaleString()}. Range: ${min.toLocaleString()} to ${max.toLocaleString()}. Blank uses the default.`} type="number" min={min} max={max} step={1} placeholder={fallback.toLocaleString()} value={draft.limits[key] ?? ''} onChange={event => { const value = event.target.value; setDraft(old => { const limits = { ...old.limits }; if (value === '') delete limits[key]; else limits[key] = Number(value); return { ...old, limits }; }); }} />;
        })}</div></Card>)}</div>
      </>}
    </fieldset>}
    <footer className="publish-bar"><div><span className={`status-dot ${dirty ? 'draft' : ''}`} /><strong>{dirty ? 'Unpublished changes' : 'Up to date'}</strong><span className="revision-label">{Object.keys(draft.limits).length} API overrides</span></div><Button variant="filled" disabled={!dirty || busy} loading={busy && !!published} onClick={() => publish()}>Publish changes</Button></footer>
    {testNotice && <NoticeTest notice={testNotice} onClose={() => setTestId(null)} catalog={catalog} />}
    <Modal open={!!confirmation} onClose={() => setConfirmation(null)} title="Publish without version targeting?" size="small" footer={<><Button onClick={() => setConfirmation(null)}>Review targeting</Button><Button variant="danger" onClick={() => publish(true)}>Publish anyway</Button></>}>
      <p>These notices can match users on any app version, including future releases.</p>
      <ul>{confirmation?.map(notice => <li key={notice.id}>{notice.title}</li>)}</ul>
      {confirmation?.some(notice => notice.mode === 'required') && <Notice variant="warning">A required update can keep blocking users after they update. Add an app version condition to stop it automatically.</Notice>}
    </Modal>
    <Modal open={confirmReload} onClose={() => setConfirmReload(false)} title="Discard this draft?" size="small" footer={<><Button onClick={() => setConfirmReload(false)}>Keep editing</Button><Button variant="danger" onClick={() => { setConfirmReload(false); load(); }}>Reload published</Button></>}>Your unpublished changes will be replaced by the latest published configuration.</Modal>
  </Box>;
}
