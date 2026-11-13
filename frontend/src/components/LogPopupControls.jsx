import React, { useState, useRef, useEffect } from 'react';
import './LogPopups.css';

export default function LogPopupControls({
  filters = { ERROR: true, WARN: true, DEBUG: false },
  toggleFilter,
  isMuted = false,
  toggleMute,
  clearAll,
  activeCount = 0,
  simulateLog,
  simulateBurst
}) {
  const [showSimMenu, setShowSimMenu] = useState(false);
  const [isFloodRunning, setIsFloodRunning] = useState(false);
  const menuRef = useRef(null);
  const floodIntervalRef = useRef(null);

  // Close dropdown menu on outside click
  useEffect(() => {
    const handleOutsideClick = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setShowSimMenu(false);
      }
    };
    if (showSimMenu) {
      document.addEventListener('mousedown', handleOutsideClick);
    }
    return () => {
      document.removeEventListener('mousedown', handleOutsideClick);
    };
  }, [showSimMenu]);

  // Clean up flood test if unmounted
  useEffect(() => {
    return () => {
      if (floodIntervalRef.current) clearInterval(floodIntervalRef.current);
    };
  }, []);

  const handleStartFlood = () => {
    setShowSimMenu(false);
    if (isFloodRunning) {
      if (floodIntervalRef.current) clearInterval(floodIntervalRef.current);
      setIsFloodRunning(false);
      return;
    }

    setIsFloodRunning(true);
    let iterations = 0;
    // Emit 10 batches of 100 logs every 100ms (10,000 logs/sec rate) for 3 seconds
    floodIntervalRef.current = setInterval(() => {
      iterations++;
      simulateBurst(100);
      if (iterations >= 30) {
        clearInterval(floodIntervalRef.current);
        setIsFloodRunning(false);
      }
    }, 100);
  };

  return (
    <div className="log-controls-bar" role="toolbar" aria-label="Log Popup Controls">
      {/* Severity Filter Toggles */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
        <button
          className={`log-toggle-chip ERROR ${filters.ERROR ? 'active' : ''}`}
          onClick={() => toggleFilter('ERROR')}
          title="Toggle ERROR popups"
        >
          <span>🚨</span>
          <span>ERROR</span>
        </button>

        <button
          className={`log-toggle-chip WARN ${filters.WARN ? 'active' : ''}`}
          onClick={() => toggleFilter('WARN')}
          title="Toggle WARN popups"
        >
          <span>⚠️</span>
          <span>WARN</span>
        </button>

        <button
          className={`log-toggle-chip DEBUG ${filters.DEBUG ? 'active' : ''}`}
          onClick={() => toggleFilter('DEBUG')}
          title="Toggle DEBUG popups"
        >
          <span>🐞</span>
          <span>DEBUG</span>
        </button>
      </div>

      <div className="log-control-divider" />

      {/* Mute / Pause Popups */}
      <button
        className={`log-control-action-btn ${isMuted ? 'muted' : ''}`}
        onClick={toggleMute}
        title={isMuted ? 'Resume popup alerts' : 'Mute / pause incoming popups'}
      >
        <span>{isMuted ? '🔇' : '🔔'}</span>
        <span>{isMuted ? 'Muted' : 'Live'}</span>
      </button>

      {/* Clear All Popups */}
      <button
        className="log-control-action-btn"
        onClick={clearAll}
        title="Clear all active popups on screen"
        disabled={activeCount === 0}
        style={{ opacity: activeCount === 0 ? 0.45 : 1, cursor: activeCount === 0 ? 'default' : 'pointer' }}
      >
        <span>🧹</span>
        <span>Clear{activeCount > 0 ? ` (${activeCount})` : ''}</span>
      </button>

      <div className="log-control-divider" />

      {/* Simulator Quick Testing Menu */}
      <div className="log-sim-dropdown-container" ref={menuRef}>
        <button
          className={`log-control-action-btn ${isFloodRunning ? 'muted' : ''}`}
          onClick={() => setShowSimMenu(!showSimMenu)}
          title="Simulate test logs or high-throughput flood"
        >
          <span>⚡</span>
          <span>{isFloodRunning ? 'Simulating 10k/s...' : 'Simulate'}</span>
          <span style={{ fontSize: 9 }}>▼</span>
        </button>

        {showSimMenu && (
          <div className="log-sim-menu" role="menu">
            <div style={{ fontSize: 10, color: 'var(--text-muted)', fontWeight: 700, padding: '4px 10px', textTransform: 'uppercase' }}>
              Quick Test Ingestion
            </div>

            <button
              className="log-sim-menu-item"
              onClick={() => { simulateLog('ERROR'); setShowSimMenu(false); }}
            >
              <span>🚨</span>
              <span>Emit Single ERROR</span>
            </button>

            <button
              className="log-sim-menu-item"
              onClick={() => { simulateLog('WARN'); setShowSimMenu(false); }}
            >
              <span>⚠️</span>
              <span>Emit Single WARN</span>
            </button>

            <button
              className="log-sim-menu-item"
              onClick={() => { simulateLog('DEBUG'); setShowSimMenu(false); }}
            >
              <span>🐞</span>
              <span>Emit Single DEBUG</span>
            </button>

            <div style={{ height: 1, background: 'var(--neu-light)', margin: '4px 0' }} />

            <div style={{ fontSize: 10, color: 'var(--text-muted)', fontWeight: 700, padding: '4px 10px', textTransform: 'uppercase' }}>
              Flood & Coalescing Tests
            </div>

            <button
              className="log-sim-menu-item"
              onClick={() => { simulateBurst(15); setShowSimMenu(false); }}
            >
              <span>⚡</span>
              <span>Burst 15 Logs (Test "+14 more")</span>
            </button>

            <button
              className="log-sim-menu-item"
              onClick={() => { simulateBurst(250); setShowSimMenu(false); }}
            >
              <span>🌊</span>
              <span>Burst 250 Logs in 1s</span>
            </button>

            <button
              className="log-sim-menu-item"
              onClick={handleStartFlood}
              style={{ color: isFloodRunning ? 'var(--c-warn)' : 'var(--accent)' }}
            >
              <span>🔥</span>
              <span>{isFloodRunning ? 'Stop Flood Test' : 'Run 10,000 logs/s (3s)'}</span>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
