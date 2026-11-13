import React from 'react';
import LogDetailModal from './LogDetailModal';
import './LogPopups.css';

const formatCardTime = (ts) => {
  if (!ts) return '';
  const d = new Date(ts);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
};

export default function LogPopupContainer({
  popups = [],
  dismissPopup,
  toggleSticky,
  pauseTimer,
  resumeTimer,
  selectedLog,
  setSelectedLog
}) {
  if (popups.length === 0 && !selectedLog) {
    return null;
  }

  const renderBadgeIcon = (level) => {
    switch (level) {
      case 'ERROR':
        return (
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="8" x2="12" y2="12"></line>
            <line x1="12" y1="16" x2="12.01" y2="16"></line>
          </svg>
        );
      case 'WARN':
        return (
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"></path>
            <line x1="12" y1="9" x2="12" y2="13"></line>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
        );
      case 'DEBUG':
        return (
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="m8 2 1.88 1.88"></path>
            <path d="M14.12 3.88 16 2"></path>
            <path d="M9 7.13v-1a3.003 3.003 0 1 1 6 0v1"></path>
            <path d="M12 20c-3.3 0-6-2.7-6-6v-3a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4v3c0 3.3-2.7 6-6 6"></path>
            <path d="M12 20v-9"></path>
            <path d="M6.53 9C4.6 8.8 3 7.1 3 5"></path>
            <path d="M6 13H2"></path>
            <path d="M3 21c0-2.1 1.7-3.9 3.8-4"></path>
            <path d="M20.97 5c0 2.1-1.6 3.8-3.5 4"></path>
            <path d="M22 13h-4"></path>
            <path d="M17.2 17c2.1.1 3.8 1.9 3.8 4"></path>
          </svg>
        );
      default:
        return (
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="16" x2="12" y2="12"></line>
            <line x1="12" y1="8" x2="12.01" y2="8"></line>
          </svg>
        );
    }
  };

  return (
    <>
      <div className="log-popups-container" role="region" aria-label="Live Log Popups">
        {popups.map((popup) => {
          const level = (popup.level || 'INFO').toUpperCase();
          const progressPercent = popup.duration > 0
            ? Math.max(0, Math.min(100, (popup.remaining / popup.duration) * 100))
            : 0;

          return (
            <div
              key={popup.id}
              className={`log-popup-card ${level}`}
              onMouseEnter={() => pauseTimer?.(popup.id)}
              onMouseLeave={() => resumeTimer?.(popup.id)}
              onClick={() => setSelectedLog?.(popup)}
              role="alert"
            >
              <div className="log-popup-card-wrapper">
                {/* Clean left accent vertical line */}
                <div className={`log-popup-accent-strip ${level}`} />

                <div className="log-popup-inner">
                  {/* Header row: Badge, Service, Time, Pin & Close actions */}
                  <div className="log-popup-header">
                    <div className="log-popup-meta">
                      <span className={`log-popup-badge ${level}`}>
                        {renderBadgeIcon(level)}
                        <span>{level}</span>
                      </span>

                      <span className="log-popup-service" title={popup.service}>
                        {popup.service}
                      </span>
                    </div>

                    <div className="log-popup-actions" onClick={(e) => e.stopPropagation()}>
                      <span className="log-popup-time">{formatCardTime(popup.timestamp)}</span>

                      {/* Clean SVG Pin Toggle */}
                      <button
                        className={`log-popup-action-btn ${popup.isSticky ? 'active-pin' : ''}`}
                        onClick={() => toggleSticky?.(popup.id)}
                        title={popup.isSticky ? 'Pinned: click to unpin' : 'Click to pin popup'}
                        aria-label={popup.isSticky ? 'Unpin' : 'Pin'}
                      >
                        <svg
                          width="13"
                          height="13"
                          viewBox="0 0 24 24"
                          fill={popup.isSticky ? 'currentColor' : 'none'}
                          stroke="currentColor"
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        >
                          <line x1="12" y1="17" x2="12" y2="22"></line>
                          <path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a1 1 0 0 0 0-2H8a1 1 0 0 0 0 2h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24Z"></path>
                        </svg>
                      </button>

                      {/* Clean SVG Dismiss Button */}
                      <button
                        className="log-popup-action-btn"
                        onClick={() => dismissPopup?.(popup.id)}
                        title="Dismiss"
                        aria-label="Dismiss"
                      >
                        <svg
                          width="12"
                          height="12"
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="2.5"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        >
                          <line x1="18" y1="6" x2="6" y2="18"></line>
                          <line x1="6" y1="6" x2="18" y2="18"></line>
                        </svg>
                      </button>
                    </div>
                  </div>

                  {/* Body: 2-line truncated message */}
                  <div className="log-popup-body mono">
                    {popup.message}
                  </div>

                  {/* Footer: Grouped Burst Counter & Inspect hint */}
                  <div className="log-popup-footer">
                    {popup.count > 1 ? (
                      <span className={`log-popup-burst-badge ${level}`}>
                        ⚡ +{popup.count - 1} more {level} logs
                      </span>
                    ) : (
                      <span style={{ fontSize: 10.5, color: 'var(--text-faint)' }}>
                        Single event
                      </span>
                    )}

                    <span className="log-popup-inspect-hint">
                      Inspect payload ↗
                    </span>
                  </div>
                </div>
              </div>

              {/* Progress Countdown Bar (hidden when pinned) */}
              {!popup.isSticky && (
                <div className="log-popup-progress-track">
                  <div
                    className={`log-popup-progress-fill ${level}`}
                    style={{ width: `${progressPercent}%` }}
                  />
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Quick Detail Modal when a popup card is clicked */}
      {selectedLog && (
        <LogDetailModal
          log={selectedLog}
          onClose={() => setSelectedLog(null)}
        />
      )}
    </>
  );
}
