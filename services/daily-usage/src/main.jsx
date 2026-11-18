import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { api, today } from './api';
import { Button, Card, Heading, HStack, Icon, InputField, Link, Notice, Skeleton, Text, TextPairing, VStack, Help } from './ui';
import Controls from './controls';
import Users from './users';
import Account from './account';
import VersionShare from './version-share';
import { canAccess, navigation, PAGES } from '../public/access';
import { mountUsage } from './usage';
import usageMarkup from './usage.html?raw';
import './app.css';

const routes = [...PAGES, { id: 'users', href: '/users', label: 'Users', icon: 'users' },
  { id: 'account', href: '/account', label: 'My account', icon: 'account' }];
const path = window.location.pathname.replace(/\/$/, '') || '/';
const route = routes.find(item => item.href === path);
document.title = `${route?.label || 'Not found'} · Mobilism Material`;

function Login({ initialStage, onSignedIn, initialError = '', retryAfter = 0 }) {
  const [stage, setStage] = useState(initialStage || 'password');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(initialError);
  const [lockedUntil, setLockedUntil] = useState(() => Date.now() + retryAfter * 1000);
  const [now, setNow] = useState(Date.now());
  const remaining = Math.max(0, Math.ceil((lockedUntil - now) / 1000));
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  async function submit(event) {
    event.preventDefault(); if (busy || remaining) return;
    setBusy(true); setError('');
    try {
      const result = await api('/api/admin-session', { method: 'POST', body: stage === 'otp' ? { step: 'otp', code } : { step: 'password', username, password } });
      setPassword(''); setCode('');
      if (result.authenticated) onSignedIn(result); else setStage(result.stage || 'otp');
    } catch (failure) {
      setError(failure.message); setPassword(''); setCode('');
      if (failure.stage) setStage(failure.stage);
      if (failure.retryAfter) setLockedUntil(Date.now() + failure.retryAfter * 1000);
    } finally { setBusy(false); }
  }
  async function reset() {
    try { await api('/api/admin-session', { method: 'DELETE' }); setStage('password'); setCode(''); setError(''); }
    catch (failure) { setError(failure.message); }
  }
  return <main className="login-page"><Card style={{ width: '100%', maxWidth: 420, borderRadius: 28 }}><form onSubmit={submit} className="login-fields">
    <span className="brand-mark" aria-hidden="true">m</span><span className="eyebrow">MOBILISM MATERIAL</span>
    <Heading level={1}>{stage === 'otp' ? 'Verify your sign-in' : 'Welcome back'}</Heading>
    <Text>{stage === 'otp' ? 'Step 2 of 2' : 'Admin dashboard'}</Text>
    {stage === 'otp' ? <InputField key="otp" label="Authenticator code" value={code} onChange={e => setCode(e.target.value)} inputMode="numeric" pattern="[0-9]{6}" maxLength={6} autoComplete="one-time-code" autoFocus required /> : <>
      <InputField label="Username" value={username} onChange={e => setUsername(e.target.value)} autoComplete="username" maxLength={64} required autoFocus />
      <InputField label="Password" value={password} onChange={e => setPassword(e.target.value)} type="password" autoComplete="current-password" maxLength={256} required />
    </>}
    {error && <div role="alert"><Notice variant="danger">{error}</Notice></div>}
    <Button type="submit" variant="filled" loading={busy} disabled={remaining > 0}>{remaining ? `Try again in ${remaining}s` : stage === 'otp' ? 'Verify & sign in' : 'Continue'}</Button>
    {stage === 'otp' && <Button type="button" onClick={reset}>Use another account</Button>}
  </form></Card></main>;
}

function useOverviewSnapshot(url) {
  const [result, setResult] = useState(null);
  useEffect(() => {
    if (!url) return;
    const abort = new AbortController();
    api(url, { signal: abort.signal }).then(data => {
      if (!abort.signal.aborted) setResult({ url, data });
    }).catch(error => {
      if (!abort.signal.aborted) setResult({ url, error });
    });
    return () => abort.abort();
  }, [url]);
  return result?.url === url ? result : null;
}

function Overview({ user }) {
  const [day] = useState(today);
  const usageAllowed = canAccess(user, 'app-usage'), controlsAllowed = canAccess(user, 'app-controls');
  const stats = useOverviewSnapshot(usageAllowed ? `/api/stats?from=${day}&to=${day}` : null);
  const settings = useOverviewSnapshot(controlsAllowed ? '/api/control-config' : null);
  const showUsage = usageAllowed && stats?.error?.status !== 403;
  const showControls = controlsAllowed && settings?.error?.status !== 403;
  const usage = stats?.data, config = settings?.data;
  const count = usage?.daily.reduce((total, day) => total + BigInt(day.devices), 0n);
  const active = config?.notices.filter(n => n.enabled && (!n.startsAt || Date.parse(n.startsAt) <= Date.now()) && (!n.endsAt || Date.parse(n.endsAt) > Date.now())).length;
  return <div className="page overview-page"><header className="page-heading"><div><span className="eyebrow">ADMIN</span><Heading level={1}>Overview</Heading></div><span className="muted overview-date">{day} · UTC</span></header>
    <div className="overview-grid">
      {showUsage && <a href="/app-usage" className="destination-card"><Card style={{ borderRadius: 24, height: '100%' }}>
        <div className="destination-top"><span className="destination-icon"><Icon name="chart" size={26} /></span><Icon name="arrow" /></div>
        <Heading level={2}>App usage</Heading><Text>Activity, versions, and devices.</Text>
        {stats?.error ? <Notice variant="danger">{stats.error.message}</Notice> : <>
          <div className="destination-metrics"><div><span className="metric-value">{count === undefined ? <Skeleton width={90} height={38} /> : new Intl.NumberFormat().format(count)}</span><span className="muted">Active today</span></div><div><span className="metric-value">{usage ? new Set(usage.breakdown.map(r => r.appVersion)).size : '…'}</span><span className="muted">App versions</span></div></div>
          {usage && <VersionShare rows={usage.breakdown} />}
        </>}
        <span className="destination-action">View app usage <Icon name="arrow" size={17} /></span>
      </Card></a>}
      {showControls && <a href="/app-controls" className="destination-card"><Card style={{ borderRadius: 24, height: '100%' }}>
        <div className="destination-top"><span className="destination-icon violet"><Icon name="controls" size={26} /></span><Icon name="arrow" /></div>
        <Heading level={2}>App controls</Heading><Text>Notices, updates, and API limits.</Text>
        {settings?.error ? <Notice variant="danger">{settings.error.message}</Notice> :
          <div className="destination-metrics"><div><span className="metric-value">{active ?? <Skeleton width={60} height={38} />}</span><span className="muted">Live notices</span></div><div><span className="metric-value">{config ? Object.keys(config.limits).length : '…'}</span><span className="muted">API overrides</span></div></div>}
        <span className="destination-action">Manage app controls <Icon name="arrow" size={17} /></span>
      </Card></a>}
    </div>
    {!showUsage && !showControls ? <Card><Text>No app pages are assigned to your account.</Text><Link href="/account">Open My account</Link></Card> :
      <div className="overview-note"><Help label="About these metrics">{[showUsage && 'Activity counts installations active on the current UTC date.', showControls && 'A live notice can still target only selected accounts or builds.'].filter(Boolean).join(' ')}</Help><span>Today’s snapshot</span></div>}
  </div>;
}

function Usage() {
  const root = useRef(null);
  useEffect(() => mountUsage(root.current, () => window.dispatchEvent(new Event('admin-expired'))), []);
  return <div ref={root} className="admin-dashboard usage-page" dangerouslySetInnerHTML={{ __html: usageMarkup }} />;
}

function App() {
  const [auth, setAuth] = useState(null), [error, setError] = useState('');
  useEffect(() => {
    const abort = new AbortController();
    api('/api/admin-session', { signal: abort.signal }).then(setAuth).catch(e => { if (!abort.signal.aborted) { setError(e.message); setAuth({ isAdmin: false }); } });
    const expired = () => { setError('Your session expired. Sign in again.'); setAuth({ isAdmin: false }); };
    window.addEventListener('admin-expired', expired);
    return () => { abort.abort(); window.removeEventListener('admin-expired', expired); };
  }, []);
  async function signOut() {
    try { await api('/api/admin-session', { method: 'DELETE' }); setError(''); setAuth({ isAdmin: false }); }
    catch (e) { setError(e.message); }
  }
  if (!auth) return <main className="loading-page"><Skeleton width={180} height={26} /><Text>Opening dashboard…</Text></main>;
  const signedOut = message => { setError(message); setAuth({ authenticated: false }); };
  if (!auth.authenticated) return <Login initialStage={auth.stage} initialError={error} retryAfter={auth.retryAfter} onSignedIn={result => { setError(''); setAuth(result); }} />;
  const allowedRoutes = navigation(auth), allowed = route && canAccess(auth, route.id);
  return <><a className="skip-link" href="#main-content">Skip to content</a><header className="app-header"><div className="header-inner">
    <Link href={allowedRoutes[0].href} style={{ textDecoration: 'none' }}><HStack gap={10}><span className="brand-mark" aria-hidden="true">m</span><span className="brand-name">Mobilism <strong>Material</strong></span></HStack></Link>
    <nav aria-label="Dashboard navigation">{allowedRoutes.filter(item => item.id !== 'account').map(item => <Link key={item.href} href={item.href} aria-current={path === item.href ? 'page' : undefined} className="nav-link"><Icon name={item.icon} size={18} /><span>{item.label}</span></Link>)}</nav>
    <div className="account-actions"><Link href="/account" aria-current={path === '/account' ? 'page' : undefined} className="nav-link">My Account</Link>
      <Button onClick={signOut} size="sm" className="sign-out">Sign out</Button></div>
  </div></header><main id="main-content" className="main-content">
    {error && <Notice variant="danger">{error}</Notice>}
    {!allowed ? <VStack gap={16}><Heading level={1}>{route ? 'Access denied' : 'Page not found'}</Heading><Text>{route ? 'You do not have access to this page.' : 'This page does not exist.'}</Text><Link href={allowedRoutes[0].href}>Open an available page</Link></VStack>
      : path === '/' ? <Overview user={auth} /> : path === '/app-usage' ? <Usage /> : path === '/app-controls' ? <Controls />
        : path === '/users' ? <Users currentUser={auth} onSignedOut={signedOut} /> : <Account user={auth} onSignedOut={signedOut} />}
  </main></>;
}

createRoot(document.getElementById('root')).render(<App />);
