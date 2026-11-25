const TOKEN_KEY = 'logstream_jwt';

async function fetchNewToken() {
  const res = await fetch('/api/auth/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: 'dashboard-admin',
      roles: ['ROLE_ADMIN', 'ROLE_VIEWER', 'ROLE_INGESTOR'],
    }),
  });
  if (!res.ok) return null;
  const data = await res.json();
  if (data.token) {
    localStorage.setItem(TOKEN_KEY, data.token);
  }
  return data.token || null;
}

export async function getAuthToken() {
  let token = localStorage.getItem(TOKEN_KEY);
  if (!token) {
    token = await fetchNewToken();
  }
  return token;
}

export async function authFetch(url, options = {}) {
  let token = await getAuthToken();
  const buildHeaders = (t) => ({
    ...(options.headers || {}),
    ...(t ? { Authorization: `Bearer ${t}` } : {}),
  });

  let res = await fetch(url, { ...options, headers: buildHeaders(token) });

  // Refresh token once if expired/rejected
  if (res.status === 401 || res.status === 403) {
    token = await fetchNewToken();
    if (token) {
      res = await fetch(url, { ...options, headers: buildHeaders(token) });
    }
  }
  return res;
}
