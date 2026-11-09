import React, { useState, useEffect, useRef, useCallback } from 'react';
import Header from './components/Header';
import Sidebar from './components/Sidebar';
import LiveTailView from './components/LiveTailView';
import SearchView from './components/SearchView';
import HistogramView from './components/HistogramView';
import AlertRulesView from './components/AlertRulesView';
import NotificationHistoryView from './components/NotificationHistoryView';

const WS_URL = typeof window !== 'undefined' && window.location.port === '3000'
  ? 'ws://localhost:8080/ws/logs'
  : ((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws/logs');
const MAX_TAIL_LINES = 600;

export default function App() {
  const [activeTab, setActiveTab] = useState('live-tail');
  const [logs, setLogs] = useState([]);
  const [paused, setPaused] = useState(false);
  const [filterText, setFilterText] = useState('');
  const [levelFilter, setLevelFilter] = useState('');

  const [lps, setLps] = useState(0);
  const [totalLogs, setTotalLogs] = useState(0);
  const [totalErrors, setTotalErrors] = useState(0);
  const [wsConnected, setWsConnected] = useState(false);
  const [wsConnecting, setWsConnecting] = useState(true);
  const [toasts, setToasts] = useState([]);

  const lpsCounter = useRef(0);
  const wsRef = useRef(null);

  const showToast = useCallback((msg, type = 'info') => {
    const id = Date.now() + Math.random();
    setToasts(prev => [...prev, { id, msg, type }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 4000);
  }, []);

  useEffect(() => {
    const interval = setInterval(() => {
      setLps(lpsCounter.current);
      lpsCounter.current = 0;
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    let isMounted = true;
    let reconnectTimer = null;

    const connect = async () => {
      setWsConnecting(true);
      try {
        let token = localStorage.getItem('logstream_jwt');
        if (!token) {
          try {
            const tokenRes = await fetch('/api/auth/token', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ username: 'dashboard-admin', roles: ['ROLE_ADMIN', 'ROLE_VIEWER'] })
            });
            if (tokenRes.ok) {
              const data = await tokenRes.json();
              token = data.token;
              localStorage.setItem('logstream_jwt', token);
            }
          } catch (_) {}
        }

        const wsEndpoint = token ? `${WS_URL}?token=${encodeURIComponent(token)}` : WS_URL;
        const ws = new WebSocket(wsEndpoint);
        wsRef.current = ws;

        ws.onopen = () => {
          if (!isMounted) return;
          setWsConnected(true);
          setWsConnecting(false);
          showToast('WebSocket connected to live logstream', 'success');
        };

        ws.onmessage = (event) => {
          if (!isMounted) return;
          try {
            const parsed = JSON.parse(event.data);
            const items = Array.isArray(parsed) ? parsed : [parsed];

            lpsCounter.current += items.length;
            setTotalLogs(prev => prev + items.length);

            let newErrors = 0;
            items.forEach(it => { if (it.level === 'ERROR') newErrors++; });
            if (newErrors > 0) setTotalErrors(prev => prev + newErrors);

            if (!paused) {
              setLogs(prev => {
                const combined = [...prev, ...items];
                return combined.length > MAX_TAIL_LINES ? combined.slice(combined.length - MAX_TAIL_LINES) : combined;
              });
            }
          } catch (_) {
            const fallback = {
              level: 'INFO',
              service: 'ws-stream',
              message: event.data,
              timestamp: new Date().toISOString()
            };
            lpsCounter.current += 1;
            setTotalLogs(prev => prev + 1);
            if (!paused) {
              setLogs(prev => [...prev.slice(-MAX_TAIL_LINES), fallback]);
            }
          }
        };

        ws.onerror = () => {
          if (!isMounted) return;
          setWsConnected(false);
          setWsConnecting(false);
        };

        ws.onclose = () => {
          if (!isMounted) return;
          setWsConnected(false);
          setWsConnecting(false);
          reconnectTimer = setTimeout(connect, 3000);
        };
      } catch (e) {
        setWsConnected(false);
        setWsConnecting(false);
        reconnectTimer = setTimeout(connect, 3000);
      }
    };

    connect();

    return () => {
      isMounted = false;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      if (wsRef.current) wsRef.current.close();
    };
  }, [paused, showToast]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Header
        lps={lps}
        totalLogs={totalLogs}
        totalErrors={totalErrors}
        wsConnected={wsConnected}
        wsConnecting={wsConnecting}
      />

      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} unreadAlerts={0} />

        <main style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          {activeTab === 'live-tail' && (
            <LiveTailView
              logs={logs}
              paused={paused}
              setPaused={setPaused}
              clearLogs={() => setLogs([])}
              filterText={filterText}
              setFilterText={setFilterText}
              levelFilter={levelFilter}
              setLevelFilter={setLevelFilter}
            />
          )}

          {activeTab === 'search' && (
            <SearchView onNotify={showToast} />
          )}

          {activeTab === 'histogram' && (
            <HistogramView onNotify={showToast} />
          )}

          {activeTab === 'alerts' && (
            <AlertRulesView onNotify={showToast} />
          )}

          {activeTab === 'history' && (
            <NotificationHistoryView onNotify={showToast} />
          )}
        </main>
      </div>

      <div style={{ position: 'fixed', bottom: 20, right: 20, display: 'flex', flexDirection: 'column', gap: 10, zIndex: 9999 }}>
        {toasts.map(t => (
          <div
            key={t.id}
            className="neu-raised"
            style={{
              padding: '12px 20px',
              borderRadius: 'var(--rad-md)',
              fontSize: 13,
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              color: t.type === 'error' ? 'var(--c-error)' : t.type === 'warn' ? 'var(--c-warn)' : t.type === 'success' ? 'var(--c-success)' : 'var(--accent)',
              boxShadow: 'var(--neu-extruded-lg)'
            }}
          >
            <span>{t.type === 'error' ? '🚨' : t.type === 'success' ? '✅' : 'ℹ️'}</span>
            <span>{t.msg}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
