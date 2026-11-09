import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.jsx';
import './index.css';

// Global Fetch Interceptor to automatically attach JWT token for REST APIs
const originalFetch = window.fetch;
window.fetch = async (...args) => {
  let [resource, config] = args;
  config = config || {};

  const isApi = typeof resource === 'string' && resource.startsWith('/api/') && !resource.startsWith('/api/auth/');
  if (isApi) {
    let token = localStorage.getItem('logstream_jwt');
    if (!token) {
      try {
        const tokenRes = await originalFetch('/api/auth/token', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: 'dashboard-admin', roles: ['ROLE_ADMIN', 'ROLE_VIEWER'] })
        });
        if (tokenRes.ok) {
          const data = await tokenRes.json();
          token = data.token;
          localStorage.setItem('logstream_jwt', token);
        }
      } catch (err) {
        console.warn('Auto token retrieval failed:', err);
      }
    }

    if (token) {
      const headers = new Headers(config.headers || {});
      if (!headers.has('Authorization')) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      config.headers = headers;
    }
  }

  return originalFetch(resource, config);
};

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
