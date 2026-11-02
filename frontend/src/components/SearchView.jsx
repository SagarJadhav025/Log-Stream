import React, { useState } from 'react';

const formatDateTime = (ts) => {
  if (!ts) return '--';
  const d = new Date(ts);
  return isNaN(d) ? ts : d.toLocaleString('en-US', {
    month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  });
};

export default function SearchView({ onNotify }) {
  const [query, setQuery] = useState('');
  const [level, setLevel] = useState('');
  const [service, setService] = useState('');
  const [sinceMinutes, setSinceMinutes] = useState('60');
  const [limit, setLimit] = useState(50);
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [metrics, setMetrics] = useState(null);

  const handleSearch = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (query.trim()) params.set('q', query.trim());
      if (level) params.set('level', level);
      if (service.trim()) params.set('service', service.trim());
      if (sinceMinutes) params.set('sinceMinutes', sinceMinutes);
      params.set('limit', limit);

      const res = await fetch(`/api/search?${params.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setResults(data.results || []);
      setMetrics({ count: data.count || 0, tookMs: data.tookMs || 0 });
    } catch (err) {
      onNotify(`Search error: ${err.message}`, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', padding: '16px 20px', gap: 14 }}>
      <div className="neu-raised" style={{ padding: '14px 20px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ display: 'flex', gap: 10 }}>
          <input
            type="text"
            className="neu-input"
            style={{ flex: 1, fontSize: 14 }}
            placeholder='Lucene query or search terms, e.g. "timeout" or "database error"'
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          />
          <button onClick={handleSearch} className="neu-btn neu-btn-primary" disabled={loading}>
            {loading ? 'Searching...' : '⚡ Search Lucene'}
          </button>
        </div>

        <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          <select className="neu-input" value={level} onChange={(e) => setLevel(e.target.value)} style={{ width: 130 }}>
            <option value="">Any Level</option>
            <option value="ERROR">ERROR</option>
            <option value="WARN">WARN</option>
            <option value="INFO">INFO</option>
            <option value="DEBUG">DEBUG</option>
          </select>

          <input
            type="text"
            className="neu-input"
            placeholder="Service name..."
            value={service}
            onChange={(e) => setService(e.target.value)}
            style={{ width: 150 }}
          />

          <select className="neu-input" value={sinceMinutes} onChange={(e) => setSinceMinutes(e.target.value)} style={{ width: 140 }}>
            <option value="">All Time</option>
            <option value="5">Last 5 min</option>
            <option value="15">Last 15 min</option>
            <option value="60">Last 1 hour</option>
            <option value="360">Last 6 hours</option>
            <option value="1440">Last 24 hours</option>
          </select>

          <select className="neu-input" value={limit} onChange={(e) => setLimit(Number(e.target.value))} style={{ width: 110 }}>
            <option value="25">Top 25</option>
            <option value="50">Top 50</option>
            <option value="100">Top 100</option>
            <option value="250">Top 250</option>
          </select>

          {metrics && (
            <div style={{ marginLeft: 'auto', display: 'flex', gap: 10 }}>
              <span className="neu-badge">Found: <strong>{metrics.count}</strong></span>
              <span className="neu-badge" style={{ color: 'var(--c-debug)' }}>Time: <strong>{metrics.tookMs} ms</strong></span>
            </div>
          )}
        </div>
      </div>

      <div className="neu-sunken-deep" style={{ flex: 1, overflowY: 'auto', padding: 12 }}>
        {results.length === 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'var(--text-faint)', gap: 8 }}>
            <span style={{ fontSize: 36 }}>🔍</span>
            <span>Enter search parameters and click "Search Lucene" to query the index.</span>
          </div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: 13 }}>
            <thead>
              <tr style={{ color: 'var(--text-muted)', borderBottom: '1px solid var(--neu-light)' }}>
                <th style={{ padding: '8px 12px' }}>Timestamp</th>
                <th style={{ padding: '8px 12px' }}>Level</th>
                <th style={{ padding: '8px 12px' }}>Service</th>
                <th style={{ padding: '8px 12px' }}>Message</th>
              </tr>
            </thead>
            <tbody>
              {results.map((row, idx) => (
                <tr key={idx} style={{ borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                  <td className="mono" style={{ padding: '10px 12px', color: 'var(--text-faint)', whiteSpace: 'nowrap' }}>
                    {formatDateTime(row.timestamp)}
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    <span className={`neu-badge ${row.level}`} style={{ fontSize: 10 }}>{row.level}</span>
                  </td>
                  <td style={{ padding: '10px 12px', color: 'var(--accent)', fontWeight: 600 }}>
                    {row.service}
                  </td>
                  <td className="mono" style={{ padding: '10px 12px', color: 'var(--text-main)', wordBreak: 'break-all' }}>
                    {row.message}
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
