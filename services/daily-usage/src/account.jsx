import { useState } from 'react';
import { api } from './api';
import { Button, Card, Heading, InputField, Notice, Text } from './ui';
import { CopyValue } from './users';

function CredentialForm({ kind, username, onSignedOut }) {
  const [currentPassword, setCurrentPassword] = useState(''), [currentCode, setCurrentCode] = useState('');
  const [newPassword, setNewPassword] = useState(''), [confirmPassword, setConfirmPassword] = useState('');
  const [totpSecret, setTotpSecret] = useState(''), [newCode, setNewCode] = useState('');
  const [visible, setVisible] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const isPassword = kind === 'password';
  async function generate() {
    setBusy(true); setError('');
    try {
      const result = await api('/api/account', { method: 'POST', body: { action: isPassword ? 'generate-password' : 'generate-totp' } });
      if (isPassword) { setNewPassword(result.password); setConfirmPassword(result.password); setVisible(true); }
      else { setTotpSecret(result.totpSecret); setNewCode(''); }
    } catch (e) { setError(e.message); } finally { setBusy(false); }
  }
  async function submit(event) {
    event.preventDefault(); if (busy) return;
    if (isPassword && newPassword !== confirmPassword) { setError('New passwords do not match.'); return; }
    setBusy(true); setError('');
    try {
      await api('/api/account', { method: 'POST', body: { action: kind, currentPassword, currentCode,
        ...(isPassword ? { newPassword } : { totpSecret, newCode }) } });
      onSignedOut(`${isPassword ? 'Password' : 'Authenticator'} updated. Sign in again with a fresh authenticator code.`);
    } catch (e) { setError(e.message); setCurrentCode(''); setNewCode(''); }
    finally { setBusy(false); setCurrentPassword(''); }
  }
  return <Card style={{ borderRadius: 24 }}><form className="account-form" onSubmit={submit}>
    <Heading level={2}>{isPassword ? 'Change password' : 'Change authenticator'}</Heading>
    <Text>{isPassword ? 'Your authenticator setup stays the same.' : 'Your password stays the same. Keep your current authenticator until the change is saved.'}</Text>
    <InputField label="Current password" type="password" value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} maxLength={256} autoComplete="current-password" required />
    <InputField label="Current authenticator code" value={currentCode} onChange={e => setCurrentCode(e.target.value)} inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" required />
    {isPassword ? <>
      <InputField label="New password" type={visible ? 'text' : 'password'} value={newPassword} onChange={e => setNewPassword(e.target.value)} minLength={12} maxLength={256} autoComplete="new-password" required />
      <InputField label="Confirm new password" type={visible ? 'text' : 'password'} value={confirmPassword} onChange={e => setConfirmPassword(e.target.value)} minLength={12} maxLength={256} autoComplete="new-password" required />
      <div className="user-actions"><Button type="button" disabled={busy} onClick={generate}>Generate 20-character password</Button><Button type="button" onClick={() => setVisible(!visible)}>{visible ? 'Hide' : 'Show'} password</Button></div>
    </> : <>
      <Button type="button" disabled={busy} onClick={generate}>Generate new setup key</Button>
      {totpSecret && <><CopyValue label="New authenticator setup key" value={totpSecret} />
        <Text>Add this key to your authenticator as a new time-based account for {username}. Enter its code below to confirm setup.</Text>
        <InputField label="New authenticator code" value={newCode} onChange={e => setNewCode(e.target.value)} inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="off" required /></>}
    </>}
    {error && <div role="alert"><Notice variant="danger">{error}</Notice></div>}
    <Text>Saving signs you out on every device. Use a fresh code if you just signed in.</Text>
    <Button type="submit" variant="filled" loading={busy} disabled={!isPassword && !totpSecret}>Save {isPassword ? 'password' : 'authenticator'}</Button>
  </form></Card>;
}

export default function Account({ user, onSignedOut }) {
  return <div className="page account-page"><header className="page-heading"><div><span className="eyebrow">{user.username}</span><Heading level={1}>My account</Heading>
    <Text>Update your password or authenticator separately.</Text></div></header>
    <div className="account-grid"><CredentialForm kind="password" username={user.username} onSignedOut={onSignedOut} />
      <CredentialForm kind="totp" username={user.username} onSignedOut={onSignedOut} /></div></div>;
}
