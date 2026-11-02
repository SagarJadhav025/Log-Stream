import React, { useRef, useEffect, useMemo } from 'react';

const formatTime = (ts) => {
  if (!ts) return '--:--:--';
  const d = new Date(ts);
  return isNaN(d) ? ts : d.toLocaleTimeString('en-US', { hour12: false });
};

export default function LiveTailView({ logs, paused, setPaused, clearLogs, filterText, setFilterText, levelFilter, setLevelFilter }) {
  const scrollRef = useRef(null);
  const isAutoScroll = useRef(true);

  const handleScroll = () => {
    if (!scrollRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = scrollRef.current;
    isAutoScroll.current = (scrollHeight - scrollTop - clientHeight) < 40;
  };

  useEffect(() => {
    if (isAutoScroll.current && scrollRef.current && !paused) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [logs, paused]);

  const filteredLogs = useMemo(() => {
    return logs.filter(log => {
      if (levelFilter && log.level !== levelFilter) return false;
      if (filterText) {
        const lower = filterText.toLowerCase();
        const matchMsg = log.message && log.message.toLowerCase().includes(lower);
        const matchSvc = log.service && log.service.toLowerCase().includes(lower);
        if (!matchMsg && !matchSvc) return false;
      }
      return true;
    });
  }, [logs, levelFilter, filterText]);

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', padding: '16px 20px', gap: 14 }}>
      <div className="neu-raised" style={{ padding: '12px 18px', display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: 6 }}>
          ⚡ Live Tail
        </span>

        <input
          type="text"
          className="neu-input"
          style={{ flex: 1, minWidth: 260 }}
          placeholder='Filter logs by text or service (e.g. "auth" or "timeout")...'
          value={filterText}
          onChange={(e) => setFilterText(e.target.value)}
        />

        <select
          className="neu-input"
          value={levelFilter}
          onChange={(e) => setLevelFilter(e.target.value)}
          style={{ width: 130 }}
        >
          <option value="">All Levels</option>
          <option value="ERROR">ERROR</option>
          <option value="WARN">WARN</option>
          <option value="INFO">INFO</option>
          <option value="DEBUG">DEBUG</option>
        </select>

        <button
          onClick={() => setPaused(!paused)}
          className={`neu-btn neu-btn-sm ${paused ? 'neu-btn-primary pressed' : ''}`}
        >
          {paused ? '▶ Resume' : '⏸ Pause'}
        </button>

        <button onClick={clearLogs} className="neu-btn neu-btn-sm">
          🗑 Clear
        </button>

        <div style={{ marginLeft: 'auto', fontSize: 12, color: 'var(--text-muted)' }}>
          Showing <strong>{filteredLogs.length}</strong> / {logs.length} lines
        </div>
      </div>

      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="neu-sunken-deep mono"
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '14px 18px',
          fontSize: 12.5,
          lineHeight: 1.65,
          display: 'flex',
          flexDirection: 'column',
          gap: 4
        }}
      >
        {filteredLogs.length === 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', flex: 1, color: 'var(--text-faint)', gap: 8 }}>
            <span style={{ fontSize: 32 }}>📡</span>
            <span>No logs matching current filter. Waiting for live events...</span>
          </div>
        ) : (
          filteredLogs.map((item, idx) => (
            <div
              key={idx}
              style={{
                display: 'flex',
                alignItems: 'baseline',
                gap: 12,
                padding: '2px 6px',
                borderRadius: 6
              }}
            >
              <span style={{ color: 'var(--text-faint)', fontSize: 11, flexShrink: 0 }}>
                {formatTime(item.timestamp)}
              </span>
              <span className={`neu-badge ${item.level}`} style={{ fontSize: 9.5, padding: '1px 7px', minWidth: 48, justifyContent: 'center' }}>
                {item.level}
              </span>
              <span style={{ color: 'var(--accent)', fontWeight: 600, flexShrink: 0, minWidth: 90 }}>
                [{item.service || 'system'}]
              </span>
              <span style={{
                color: item.level === 'ERROR' ? 'var(--c-error)' : item.level === 'WARN' ? 'var(--c-warn)' : 'var(--text-main)',
                wordBreak: 'break-all'
              }}>
                {item.message}
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
