import { useEffect, useState } from 'react';
import { api } from './api';
import { Button, Card, Heading, InputField, Modal, Notice, Switch, Text } from './ui';
import { PAGES } from '../public/access';
import './users.css';

export function CopyValue({ label, value }) {
  const [message, setMessage] = useState('');
  useEffect(() => setMessage(''), [value]);
  async function copy() {
    try { await navigator.clipboard.writeText(value); setMessage('Copied'); }
    catch { setMessage('Select the field and copy it.'); }
  }
  return <div className="copy-value"><InputField label={label} value={value} readOnly spellCheck={false} autoComplete="off" />
    <Button type="button" onClick={copy}>Copy</Button><span role="status">{message}</span></div>;
}

function Permissions({ value, onChange, disabled = false }) {
  return <fieldset className="permissions" disabled={disabled}><legend>Role and page access</legend>
    <Switch label="Admin" checked={value.isAdmin} disabled={disabled} onChange={e => onChange({ ...value, isAdmin: e.target.checked })} />
    <Text>Admins can manage other users. Page access includes editing.</Text>
    {PAGES.map(page => <Switch key={page.id} label={page.label} checked={value.pages.includes(page.id)} disabled={disabled}
      onChange={e => onChange({ ...value, pages: e.target.checked ? [...value.pages, page.id] : value.pages.filter(id => id !== page.id) })} />)}
    <Text>Every user can open My account.</Text>
  </fieldset>;
}

export default function Users({ currentUser, onSignedOut }) {
  const [users, setUsers] = useState(null), [error, setError] = useState(''), [status, setStatus] = useState('');
  const [draft, setDraft] = useState(null), [created, setCreated] = useState(false), [busy, setBusy] = useState(false);
  const [deleting, setDeleting] = useState(null);
  async function load() {
    const result = await api('/api/users'); setUsers(result.users);
  }
  useEffect(() => { load().catch(e => setError(e.message)); }, []);
  async function create() {
    setBusy(true); setError(''); setStatus('');
    try {
      const credentials = await api('/api/users', { method: 'POST', body: { action: 'generate' } });
      setCreated(false); setDraft({ username: '', isAdmin: false, pages: [], ...credentials });
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function save(event) {
    event.preventDefault(); if (busy) return;
    setBusy(true); setError('');
    try {
      const result = await api('/api/users', { method: draft.id ? 'PUT' : 'POST', body: draft });
      if (result.signInRequired) { onSignedOut('Access updated. Sign in again.'); return; }
      if (draft.id) { setDraft(null); setStatus('Access saved. Existing sessions were revoked.'); }
      else { setCreated(true); setStatus('User created.'); }
      await load();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function remove() {
    setBusy(true); setError('');
    try {
      await api('/api/users', { method: 'DELETE', body: { id: deleting.id, version: deleting.version } });
      setDeleting(null); setStatus('User deleted. Existing sessions were revoked.'); await load();
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  function close() { if (!busy) { setDraft(null); setCreated(false); setError(''); } }
  return <div className="page users-page"><header className="page-heading"><div><span className="eyebrow">ADMIN</span><Heading level={1}>Users</Heading>
    <Text>Create logins and choose what each user can access.</Text></div><Button variant="filled" onClick={create} loading={busy && !draft && !deleting}>Add user</Button></header>
    {error && !draft && !deleting && <div role="alert"><Notice variant="danger">{error}</Notice></div>}
    {status && <div role="status"><Notice>{status}</Notice></div>}
    {!users ? <Text>Loading users…</Text> : <div className="user-list">{users.map(user => <Card key={user.id} style={{ borderRadius: 24 }}>
      <div className="user-row"><div><Heading level={2}>{user.username}</Heading><Text>{user.isOwner ? 'Owner · Protected admin' : user.isAdmin ? 'Admin' : 'User'}</Text>
        <p className="muted">{user.pages.map(id => PAGES.find(page => page.id === id)?.label).join(' · ') || 'My account only'}{user.isAdmin ? ' · User management' : ''}</p></div>
      {user.isOwner ? <span className="owner-badge">Cannot be deleted or restricted</span> : <div className="user-actions">
        <Button onClick={() => { setError(''); setCreated(false); setDraft({ ...user }); }}>Edit access</Button>
        {user.id !== currentUser.id && <Button onClick={() => { setError(''); setDeleting(user); }}>Delete</Button>}
      </div>}</div></Card>)}</div>}
    <Modal open={!!draft} onClose={close} title={created ? 'User created' : draft?.id ? 'Edit access' : 'Create user'} size="medium" closeOnOverlay={false}>
      {draft && <form className="account-form" onSubmit={save}>
        {created ? <Notice>Copy these credentials now and share them privately. The password and setup key cannot be viewed here again.</Notice> : null}
        {draft.id || created ? <Heading level={3}>{draft.username}</Heading> : <InputField label="Username" value={draft.username}
          onChange={e => setDraft({ ...draft, username: e.target.value })} minLength={3} maxLength={64} pattern="[A-Za-z0-9._-]{3,64}" autoComplete="off" required autoFocus />}
        {!draft.id && <><CopyValue label="Generated password (20 characters)" value={draft.password} />
          <CopyValue label="Authenticator setup key" value={draft.totpSecret} /><Text>Add this key to an authenticator as a time-based account named {draft.username || 'the new username'}.</Text></>}
        {!created && <Permissions value={draft} onChange={setDraft} disabled={busy} />}
        {error && <div role="alert"><Notice variant="danger">{error}</Notice></div>}
        <div className="user-actions">{created ? <Button onClick={close} type="button" variant="filled">Done</Button> : <>
          <Button onClick={close} type="button" disabled={busy}>Cancel</Button><Button type="submit" variant="filled" loading={busy}>{draft.id ? 'Save access' : 'Create user'}</Button></>}</div>
      </form>}
    </Modal>
    <Modal open={!!deleting} onClose={() => { if (!busy) setDeleting(null); }} title="Delete user?" size="small">
      <div className="account-form"><Text>Delete {deleting?.username}? Their access ends immediately. This cannot be undone.</Text>
        {error && <div role="alert"><Notice variant="danger">{error}</Notice></div>}
        <div className="user-actions"><Button disabled={busy} onClick={() => setDeleting(null)}>Cancel</Button><Button variant="filled" loading={busy} onClick={remove}>Delete user</Button></div></div>
    </Modal>
  </div>;
}
