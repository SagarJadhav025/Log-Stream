import React, { useState, useEffect, useRef, useCallback } from 'react';
import Header from './components/Header';
import Sidebar from './components/Sidebar';
import LiveTailView from './components/LiveTailView';
import SearchView from './components/SearchView';
import HistogramView from './components/HistogramView';
import AlertRulesView from './components/AlertRulesView';
import NotificationHistoryView from './components/NotificationHistoryView';
import LogPopupContainer from './components/LogPopupContainer';
import LogPopupControls from './components/LogPopupControls';
import { useLogPopups } from './hooks/useLogPopups';

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

  // Initialize Real-Time Log Popups with High-Throughput Flood Protection
  const {
    popups,
    filters: popupFilters,
    isMuted: isPopupMuted,
    selectedLog,
    setSelectedLog,
    ingest: ingestLogPopups,
    dismissPopup,
    clearAll: clearAllPopups,
    toggleSticky,
    pauseTimer,
    resumeTimer,
    toggleFilter: togglePopupFilter,
    toggleMute: togglePopupMute,
    simulateLog,
    simulateBurst
  } = useLogPopups();

  const lpsCounter = useRef(0);
  const wsRef = useRef(null);
  const pausedRef = useRef(paused);
  const ingestRef = useRef(ingestLogPopups);

  useEffect(() => {
    pausedRef.current = paused;
  }, [paused]);

  useEffect(() => {
    ingestRef.current = ingestLogPopups;
  }, [ingestLogPopups]);

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

  // Shared WebSocket connection to Spring Boot LiveTailWebSocketHandler (/ws/logs)
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

            // Feed Live Tail table if unpaused
            if (!pausedRef.current) {
              setLogs(prev => {
                const combined = [...prev, ...items];
                return combined.length > MAX_TAIL_LINES ? combined.slice(combined.length - MAX_TAIL_LINES) : combined;
              });
            }

            // Pipe into high-throughput flood protection popup buffer
            if (ingestRef.current) {
              ingestRef.current(items);
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
            if (!pausedRef.current) {
              setLogs(prev => [...prev.slice(-MAX_TAIL_LINES), fallback]);
            }
            if (ingestRef.current) {
              ingestRef.current(fallback);
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
  }, [showToast]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Dashboard Header with Live Rate & Popup Controls */}
      <Header
        lps={lps}
        totalLogs={totalLogs}
        totalErrors={totalErrors}
        wsConnected={wsConnected}
        wsConnecting={wsConnecting}
        popupControls={
          <LogPopupControls
            filters={popupFilters}
            toggleFilter={togglePopupFilter}
            isMuted={isPopupMuted}
            toggleMute={togglePopupMute}
            clearAll={clearAllPopups}
            activeCount={popups.length}
            simulateLog={simulateLog}
            simulateBurst={simulateBurst}
          />
        }
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

      {/* High-Performance Real-Time Log Popups Container & Modal */}
      <LogPopupContainer
        popups={popups}
        dismissPopup={dismissPopup}
        toggleSticky={toggleSticky}
        pauseTimer={pauseTimer}
        resumeTimer={resumeTimer}
        selectedLog={selectedLog}
        setSelectedLog={setSelectedLog}
      />

      {/* System Toast Notifications (e.g. WebSocket connection, copies) */}
      <div style={{ position: 'fixed', bottom: 20, left: 20, display: 'flex', flexDirection: 'column', gap: 10, zIndex: 9998 }}>
        {toasts.map(t => (
          <div
            key={t.id}
            className="neu-raised"
            style={{
              padding: '10px 18px',
              borderRadius: 'var(--rad-md)',
              fontSize: 12.5,
              fontWeight: 600,
              display: 'flex',
              alignItems: 'center',
              gap: 10,
              color: t.type === 'error' ? 'var(--c-error)' : t.type === 'warn' ? 'var(--c-warn)' : t.type === 'success' ? 'var(--c-success)' : 'var(--accent)',
              boxShadow: 'var(--neu-extruded)'
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
