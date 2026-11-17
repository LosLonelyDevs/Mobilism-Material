import { useEffect, useRef, useState } from 'react';
import { Action, Button, Card, Chip, CollapsibleCard, Heading, Help, HStack, InputField, Modal, SelectField, Switch, TextPairing, Tooltip, VStack } from './ui';
import { VersionPicker } from './audience';
import { userAgentStatuses } from '../public/user-agent-rules.js';

const targets = rule => rule.allVersions ? 'All versions, including future releases' : rule.versions.join(', ');
const trigger = rule => rule.statuses.length ? `After HTTP ${rule.statuses.join(' or ')}` : 'Before every request';
function preview(rule, catalog) {
  const release = catalog.versions.find(row => rule.allVersions || rule.versions.includes(row.version));
  const version = release?.version || rule.versions[0] || '<installed version>';
  return `User-Agent: ${rule.userAgent || '<prefix>'} MobilismMaterial/${version} (Android; build ${release?.versionCode || '<installed build>'})`;
}

export default function UserAgentControls({ rules, catalog, busy, onSave, onDirtyChange }) {
  const [editor, setEditor] = useState(null), [confirmDelete, setConfirmDelete] = useState(false);
  const heading = useRef(null), returnToList = useRef(false);
  const original = editor?.id ? rules.find(rule => rule.id === editor.id) : null;
  const dirty = !!editor && (!original || JSON.stringify(original) !== JSON.stringify(editor.rule) || editor.position !== rules.indexOf(original));
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false); }, [dirty, onDirtyChange]);
  useEffect(() => { if (!editor && returnToList.current) { heading.current?.focus(); heading.current?.scrollIntoView({ block: 'start' }); returnToList.current = false; } }, [editor]);
  function close() { returnToList.current = true; setEditor(null); setConfirmDelete(false); }
  function set(key, value) { setEditor(old => ({ ...old, rule: { ...old.rule, [key]: value } })); }
  function create() { setEditor({ id: null, position: rules.length, rule: { id: crypto.randomUUID(), name: '', enabled: false,
    allVersions: false, versions: catalog.versions.slice(0, 1).map(row => row.version), userAgent: '', statuses: [] } }); }
  function save() {
    const updated = rules.filter(rule => rule.id !== editor.id);
    updated.splice(Math.min(editor.position, updated.length), 0, { ...editor.rule, enabled: original?.enabled ?? false });
    onSave(updated, close);
  }
  if (editor) return <section>
    <div className="workspace-heading"><HStack gap={10}><Action icon="back" label="Cancel and return to User-Agent rules" disabled={busy} onClick={close} /><Heading level={2}>{original ? 'Edit User-Agent rule' : 'Create User-Agent rule'}</Heading></HStack></div>
    <CollapsibleCard title={editor.rule.name || 'User-Agent rule'} subtitle={<Tooltip content={`${targets(editor.rule)} · ${trigger(editor.rule)}`}><span className="notice-summary">{targets(editor.rule)} · {trigger(editor.rule)}</span></Tooltip>} defaultOpen style={{ borderRadius: 20 }}>
      <div className="form-grid">
        <InputField label="Rule name" maxLength={80} value={editor.rule.name} onChange={event => set('name', event.target.value)} />
        <InputField label="User-Agent prefix" maxLength={256} value={editor.rule.userAgent} onChange={event => set('userAgent', event.target.value)} placeholder="MobilismClient" help="Replaces the browser portion of the header. The app always appends its actual version and build. Printable ASCII only; no line breaks." />
      </div>
      <div className="device-notification-field"><Switch label="All app versions" checked={editor.rule.allVersions} onChange={event => setEditor(old => ({ ...old, rule: { ...old.rule, allVersions: event.target.checked, versions: event.target.checked ? [] : catalog.versions.slice(0, 1).map(row => row.version) } }))} /><Help label="About version targets">All versions includes future releases. Otherwise, only the selected app versions use this rule.</Help></div>
      {!editor.rule.allVersions && <div className="header-version-picker"><VersionPicker label="App versions" versions={catalog.versions} multiple value={editor.rule.versions} onChange={value => set('versions', value)} /></div>}
      <div className="header-trigger"><SelectField label="Apply rule" value={editor.rule.statuses.length ? 'status' : 'always'} onChange={value => set('statuses', value === 'status' ? [403] : [])} options={[[ 'always', 'Before every request' ], [ 'status', 'After an HTTP response' ]]} help="Use Before every request to identify a new app to the server. The optional response condition activates only for later requests; it never retries the failed request." />
        {editor.rule.statuses.length > 0 && <SelectField label="HTTP statuses" multiple value={editor.rule.statuses.map(String)} options={userAgentStatuses.map(([code, label]) => [String(code), label])} onChange={values => { if (values.length) set('statuses', values.map(Number)); }} help="Select at least one response condition. These are not status codes sent by the app. Authentication failures, rate limits, and transient server errors do not activate header changes." />}
      </div>
      <div className="header-preview"><HStack gap={6}><TextPairing title="Request header preview" /><Help label="About the header preview">Version and build come from the installed APK, using the same Gradle version that selects the changelog. The preview uses a selected release. Existing clients need an APK containing remote header support.</Help></HStack><code>{preview(editor.rule, catalog)}</code></div>
    </CollapsibleCard>
    <div className="notice-edit-actions"><HStack gap={8}>{original && <><Action icon="trash" label="Delete User-Agent rule" disabled={busy} onClick={() => setConfirmDelete(true)} /><Action icon="up" label="Move User-Agent rule up" disabled={busy || editor.position === 0} onClick={() => setEditor(old => ({ ...old, position: old.position - 1 }))} /><Action icon="down" label="Move User-Agent rule down" disabled={busy || editor.position >= rules.length - 1} onClick={() => setEditor(old => ({ ...old, position: old.position + 1 }))} /></>}</HStack><HStack gap={8}><Button disabled={busy} onClick={close}>Cancel</Button><Button variant="filled" disabled={busy || !dirty} onClick={save}>{original ? 'Save rule' : 'Create rule'}</Button></HStack></div>
    <Modal open={confirmDelete} onClose={() => setConfirmDelete(false)} title="Delete this User-Agent rule?" size="small" footer={<><Button onClick={() => setConfirmDelete(false)}>Keep rule</Button><Button variant="danger" disabled={busy} onClick={() => onSave(rules.filter(rule => rule.id !== editor.id), close)}>Delete rule</Button></>}>The next matching rule or shipped header takes over on the app's next config check.</Modal>
  </section>;
  return <section>
    <div className="workspace-heading" ref={heading} tabIndex={-1}><HStack gap={6}><TextPairing title="User-Agent rules" /><Help label="About User-Agent rules">The first enabled, matching rule wins. New rules save disabled. Rules affect Mobilism API, forum, and hosted image requests, including verification windows. Config checks, downloads, debrid services, and third-party images keep their shipped headers. Missing, invalid, failed, or expired config restores the shipped header. Image support starts with v0.1.23.</Help></HStack><Button disabled={busy || rules.length >= 10} onClick={create}>Add rule</Button></div>
    {rules.length ? <VStack gap={16} style={{ alignItems: 'stretch' }}>{rules.map((rule, index) => <Card key={rule.id} style={{ borderRadius: 20 }}><div className="notice-preview-heading"><Heading level={2}>{rule.name}</Heading><Action icon="edit" label={`Edit ${rule.name}`} disabled={busy} onClick={() => setEditor({ id: rule.id, position: index, rule: structuredClone(rule) })} /></div><Tooltip content={targets(rule)}><p className="notice-summary">{targets(rule)}</p></Tooltip><Chip size="small" label={trigger(rule)} /><div className="header-preview"><code>{preview(rule, catalog)}</code></div><div className="notice-preview-actions"><Switch label={rule.enabled ? 'Enabled' : 'Disabled'} checked={rule.enabled} disabled={busy} onChange={event => onSave(rules.map(item => item.id === rule.id ? { ...item, enabled: event.target.checked } : item))} /></div></Card>)}</VStack> : <Card style={{ borderStyle: 'dashed', borderRadius: 20 }}><div className="empty-notices"><Heading level={2}>Using the shipped User-Agent</Heading><Button variant="filled" onClick={create}>Create a rule</Button></div></Card>}
  </section>;
}
