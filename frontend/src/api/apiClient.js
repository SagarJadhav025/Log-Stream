const API_BASE = '';

export async function secureFetch(url, options = {}) {
  const token = localStorage.getItem('logstream_jwt');

  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${url}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    localStorage.removeItem('logstream_jwt');
    // Dispatch custom event for app auth state
    window.dispatchEvent(new CustomEvent('auth:expired'));
    throw new Error('Authentication session expired (401)');
  }

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `HTTP error ${response.status}`);
  }

  return response.json();
}
