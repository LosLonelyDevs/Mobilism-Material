export async function api(path, { method = 'GET', body, signal } = {}) {
  const response = await fetch(path, { method, signal, credentials: 'same-origin', cache: 'no-store',
    ...(body ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {}) });
  const result = await response.json();
  if (!response.ok) {
    if (response.status === 401 && path !== '/api/admin-session') window.dispatchEvent(new Event('admin-expired'));
    throw Object.assign(new Error(result.error || 'Unable to load. Try again.'), result, { status: response.status });
  }
  return result;
}
export const today = () => new Date().toISOString().slice(0, 10);
