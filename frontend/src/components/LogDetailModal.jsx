import React, { useState, useEffect, useCallback } from 'react';

const formatTimestamp = (ts) => {
  if (!ts) return 'Unknown';
  try {
    const d = new Date(ts);
    return isNaN(d.getTime()) ? String(ts) : `${d.toLocaleString()} (${d.toISOString()})`;
  } catch {
    return String(ts);
  }
};

export default function LogDetailModal({ log, onClose }) {
  const [activeTab, setActiveTab] = useState(log?.stackTrace ? 'stack' : 'message');
  const [copied, setCopied] = useState(false);

  // Close on Escape key
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const copyToClipboard = useCallback((text) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }, []);

  if (!log) return null;

  const level = (log.level || 'INFO').toUpperCase();
  const rawPayload = log.raw || log;
  const jsonString = JSON.stringify(rawPayload, null, 2);

  const getLevelColor = () => {
    if (level === 'ERROR') return 'var(--c-error)';
    if (level === 'WARN') return 'var(--c-warn)';
    if (level === 'DEBUG') return '#38bdf8';
    return 'var(--accent)';
  };

  const getLevelIcon = () => {
    if (level === 'ERROR') return '🚨';
    if (level === 'WARN') return '⚠️';
    if (level === 'DEBUG') return '🐞';
    return 'ℹ️';
  };

  return (
    <div className="log-modal-backdrop" onClick={onClose}>
      <div
        className="log-modal-window"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        {/* Modal Header */}
        <div className="log-modal-header">
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ fontSize: 20 }}>{getLevelIcon()}</span>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span className={`neu-badge ${level}`} style={{ fontSize: 11 }}>
                  {level}
                </span>
                <span style={{ fontWeight: 800, fontSize: 15, color: 'var(--text-main)' }}>
                  {log.service}
                </span>
                {log.count > 1 && (
                  <span className={`log-popup-burst-badge ${level}`}>
                    ⚡ {log.count} Events Coalesced
                  </span>
                )}
              </div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)', marginTop: 3 }}>
                Event Time: {formatTimestamp(log.timestamp)}
              </div>
            </div>
          </div>

          <button
            onClick={onClose}
            className="neu-btn neu-btn-sm"
            style={{ width: 32, height: 32, padding: 0, justifyContent: 'center' }}
            title="Close (Esc)"
          >
            ✕
          </button>
        </div>

        {/* Modal Navigation Tabs */}
        <div className="log-modal-tabs">
          <button
            className={`log-modal-tab ${activeTab === 'message' ? 'active' : ''}`}
            onClick={() => setActiveTab('message')}
          >
            Log Message
          </button>

          {log.stackTrace && (
            <button
              className={`log-modal-tab ${activeTab === 'stack' ? 'active' : ''}`}
              onClick={() => setActiveTab('stack')}
            >
              Stack Trace
            </button>
          )}

          <button
            className={`log-modal-tab ${activeTab === 'json' ? 'active' : ''}`}
            onClick={() => setActiveTab('json')}
          >
            JSON Payload
          </button>

          {log.burstHistory && log.burstHistory.length > 1 && (
            <button
              className={`log-modal-tab ${activeTab === 'burst' ? 'active' : ''}`}
              onClick={() => setActiveTab('burst')}
            >
              Burst History ({log.burstHistory.length})
            </button>
          )}
        </div>

        {/* Modal Content */}
        <div className="log-modal-content">
          {activeTab === 'message' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', marginBottom: 6, textTransform: 'uppercase' }}>
                  Full Message
                </div>
                <div
                  className="mono"
                  style={{
                    background: 'var(--neu-dark)',
                    boxShadow: 'var(--neu-sunken-deep)',
                    borderRadius: 'var(--rad-md)',
                    padding: '14px 18px',
                    fontSize: 13,
                    lineHeight: 1.6,
                    color: getLevelColor(),
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                  }}
                >
                  {log.message}
                </div>
              </div>

              <div>
                <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', marginBottom: 6, textTransform: 'uppercase' }}>
                  Log Metadata
                </div>
                <div className="neu-sunken" style={{ padding: '12px 16px', borderRadius: 'var(--rad-md)', display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
                  <div>
                    <div style={{ fontSize: 10.5, color: 'var(--text-muted)' }}>SERVICE</div>
                    <div style={{ fontSize: 12.5, fontWeight: 600 }}>{log.service}</div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10.5, color: 'var(--text-muted)' }}>SEVERITY LEVEL</div>
                    <div style={{ fontSize: 12.5, fontWeight: 600, color: getLevelColor() }}>{level}</div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10.5, color: 'var(--text-muted)' }}>BURST COUNT</div>
                    <div style={{ fontSize: 12.5, fontWeight: 600 }}>{log.count || 1} logs</div>
                  </div>
                  <div>
                    <div style={{ fontSize: 10.5, color: 'var(--text-muted)' }}>STATUS</div>
                    <div style={{ fontSize: 12.5, fontWeight: 600 }}>{log.isSticky ? '📌 Pinned' : '⏱ Auto-Dismissing'}</div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {activeTab === 'stack' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                  Exception Stack Trace
                </span>
                <button
                  onClick={() => copyToClipboard(log.stackTrace)}
                  className="neu-btn neu-btn-sm"
                  style={{ fontSize: 11 }}
                >
                  {copied ? '✓ Copied' : '📋 Copy Stack Trace'}
                </button>
              </div>
              <pre className="log-modal-code-block mono" style={{ color: 'var(--c-error)' }}>
                {log.stackTrace}
              </pre>
            </div>
          )}

          {activeTab === 'json' && (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', textTransform: 'uppercase' }}>
                  Raw WebSocket Event (JSON)
                </span>
                <button
                  onClick={() => copyToClipboard(jsonString)}
                  className="neu-btn neu-btn-sm"
                  style={{ fontSize: 11 }}
                >
                  {copied ? '✓ Copied' : '📋 Copy JSON'}
                </button>
              </div>
              <pre className="log-modal-code-block mono">
                {jsonString}
              </pre>
            </div>
          )}

          {activeTab === 'burst' && log.burstHistory && (
            <div>
              <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-muted)', marginBottom: 8, textTransform: 'uppercase' }}>
                Recent Burst Logs in this Cluster (showing last {log.burstHistory.length})
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, maxHeight: 380, overflowY: 'auto' }}>
                {log.burstHistory.map((item, idx) => (
                  <div
                    key={idx}
                    className="neu-sunken"
                    style={{ padding: '10px 14px', borderRadius: 'var(--rad-sm)', display: 'flex', flexDirection: 'column', gap: 4 }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 10.5, color: 'var(--text-muted)' }}>
                      <span>#{idx + 1}</span>
                      <span>{formatTimestamp(item.timestamp)}</span>
                    </div>
                    <div className="mono" style={{ fontSize: 12, color: 'var(--text-main)', wordBreak: 'break-word' }}>
                      {item.message}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="log-modal-footer">
          <div style={{ fontSize: 11.5, color: 'var(--text-muted)' }}>
            Press <kbd style={{ padding: '2px 5px', background: 'var(--neu-dark)', borderRadius: 4, border: '1px solid var(--neu-light)' }}>Esc</kbd> to exit
          </div>
          <div style={{ display: 'flex', gap: 10 }}>
            <button
              onClick={() => copyToClipboard(log.message)}
              className="neu-btn neu-btn-sm"
            >
              {copied ? '✓ Copied' : '📋 Copy Message'}
            </button>
            <button
              onClick={onClose}
              className="neu-btn neu-btn-sm neu-btn-primary"
            >
              Done
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
