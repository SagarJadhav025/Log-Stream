import React, { useRef, useEffect, useMemo, memo, useCallback } from 'react';

const formatTime = (ts) => {
  if (!ts) return '--:--:--';
  const d = new Date(ts);
  return isNaN(d) ? ts : d.toLocaleTimeString('en-US', { hour12: false });
};

// Day 5: Memoized single log row — only re-renders if the log object reference changes
const LogRow = memo(function LogRow({ item }) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'baseline',
        gap: 12,
        padding: '2px 6px',
        borderRadius: 6,
        contain: 'content', // CSS containment for paint performance
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
  );
});

// Day 5: Virtual log list — only renders rows in the visible viewport
const ITEM_HEIGHT = 28;
const OVERSCAN = 10;

function VirtualLogList({ logs }) {
  const containerRef = useRef(null);
  const [scrollTop, setScrollTop] = React.useState(0);
  const [height, setHeight] = React.useState(400);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const ro = new ResizeObserver(entries => {
      setHeight(entries[0].contentRect.height);
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  const totalHeight = logs.length * ITEM_HEIGHT;
  const startIndex = Math.max(0, Math.floor(scrollTop / ITEM_HEIGHT) - OVERSCAN);
  const endIndex = Math.min(logs.length, Math.ceil((scrollTop + height) / ITEM_HEIGHT) + OVERSCAN);
  const visibleLogs = logs.slice(startIndex, endIndex);

  const handleScroll = useCallback((e) => {
    setScrollTop(e.currentTarget.scrollTop);
  }, []);

  return (
    <div
      ref={containerRef}
      onScroll={handleScroll}
      className="neu-sunken-deep mono"
      style={{ flex: 1, overflowY: 'auto', position: 'relative', fontSize: 12.5, lineHeight: 1.65 }}
    >
      {/* Full height spacer so scrollbar represents all logs */}
      <div style={{ height: totalHeight, position: 'relative' }}>
        {/* Rendered slice — offset to visible position */}
        <div style={{ position: 'absolute', top: startIndex * ITEM_HEIGHT, width: '100%', padding: '0 18px' }}>
          {visibleLogs.map((item, i) => (
            <LogRow key={startIndex + i} item={item} />
          ))}
        </div>
      </div>
    </div>
  );
}

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

  // Day 5: useMemo filters to avoid recomputing on every parent render
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
          placeholder='Filter logs by text or service...'
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

      {filteredLogs.length === 0 ? (
        <div className="neu-sunken-deep" style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: 'var(--text-faint)', gap: 8 }}>
          <span style={{ fontSize: 32 }}>📡</span>
          <span>No logs matching current filter. Waiting for live events...</span>
        </div>
      ) : (
        <VirtualLogList logs={filteredLogs} />
      )}
    </div>
  );
}
