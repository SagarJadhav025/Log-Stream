import React, { useState, useEffect } from 'react';

const formatDateTime = (ts) => {
  if (!ts) return '--';
  const d = new Date(ts);
  return isNaN(d) ? ts : d.toLocaleString('en-US', {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  });
};

export default function NotificationHistoryView({ onNotify }) {
  const [history, setHistory] = useState([]);

  const fetchHistory = async () => {
    try {
      const res = await fetch('/api/alerts/history');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setHistory(Array.isArray(data) ? data : []);
    } catch (err) {
      onNotify(`History fetch error: ${err.message}`, 'error');
    }
  };

  useEffect(() => {
    fetchHistory();
    const interval = setInterval(fetchHistory, 15000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', padding: '16px 20px', gap: 14 }}>
      <div className="neu-raised" style={{ padding: '12px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--text-main)' }}>
          📬 Dispatch Audit Trail ({history.length} records)
        </span>
        <button onClick={fetchHistory} className="neu-btn neu-btn-sm">↻ Refresh</button>
      </div>

      <div className="neu-sunken-deep" style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
        {history.length === 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-faint)', gap: 8 }}>
            <span style={{ fontSize: 36 }}>📬</span>
            <span>No webhook or email deliveries recorded yet.</span>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: 12.5 }}>
            <thead>
              <tr style={{ color: 'var(--text-muted)', borderBottom: '1px solid var(--neu-light)' }}>
                <th style={{ padding: '8px 12px' }}>Timestamp</th>
                <th style={{ padding: '8px 12px' }}>Rule ID</th>
                <th style={{ padding: '8px 12px' }}>Channel</th>
                <th style={{ padding: '8px 12px' }}>Destination</th>
                <th style={{ padding: '8px 12px' }}>Status</th>
                <th style={{ padding: '8px 12px' }}>HTTP Code</th>
                <th style={{ padding: '8px 12px' }}>Audit Message</th>
              </tr>
            </thead>
            <tbody>
              {[...history].reverse().map((rec, idx) => (
                <tr key={idx} style={{ borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                  <td className="mono" style={{ padding: '9px 12px', color: 'var(--text-faint)' }}>
                    {formatDateTime(rec.dispatchedAt || rec.timestamp)}
                  </td>
                  <td className="mono" style={{ padding: '9px 12px', color: 'var(--accent)' }}>
                    {rec.ruleId}
                  </td>
                  <td style={{ padding: '9px 12px', fontWeight: 600 }}>
                    {rec.channel}
                  </td>
                  <td className="mono" style={{ padding: '9px 12px', color: 'var(--text-muted)' }}>
                    {rec.target}
                  </td>
                  <td style={{ padding: '9px 12px' }}>
                    <span className="neu-badge" style={{ color: rec.success ? 'var(--c-success)' : 'var(--c-error)' }}>
                      {rec.success ? '✓ Delivered' : '✗ Failed'}
                    </span>
                  </td>
                  <td style={{ padding: '9px 12px', fontWeight: 700, color: rec.httpStatusCode === 200 ? 'var(--c-success)' : 'var(--c-warn)' }}>
                    {rec.httpStatusCode || '--'}
                  </td>
                  <td style={{ padding: '9px 12px', color: 'var(--text-muted)' }}>
                    {rec.message}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
