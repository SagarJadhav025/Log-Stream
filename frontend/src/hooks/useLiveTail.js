import { useEffect, useRef, useState, useCallback } from 'react';

// Day 4: Parse minified JSON keys {"t","l","s","m"} from backend
function parseLogMessage(raw) {
  try {
    const parsed = JSON.parse(raw);
    // Support both minified (Day 4) and legacy full-key format
    return {
      timestamp: parsed.t ?? parsed.timestamp,
      level:     parsed.l ?? parsed.level ?? 'INFO',
      service:   parsed.s ?? parsed.service ?? 'system',
      message:   parsed.m ?? parsed.message ?? raw,
    };
  } catch {
    return { level: 'INFO', message: raw, service: 'raw', timestamp: Date.now() };
  }
}

export function useLiveTail(onMessageReceived) {
  const [connected, setConnected] = useState(false);
  const wsRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);

  // Day 5: Batch incoming WS messages via requestAnimationFrame to avoid per-message re-renders
  const pendingBatchRef = useRef([]);
  const rafRef = useRef(null);

  const flushBatch = useCallback(() => {
    if (pendingBatchRef.current.length > 0) {
      const batch = pendingBatchRef.current.splice(0);
      batch.forEach(msg => onMessageReceived(msg));
    }
    rafRef.current = null;
  }, [onMessageReceived]);

  const connect = useCallback(() => {
    const token = localStorage.getItem('logstream_jwt') || 'demo-token';
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.port === '3000' ? 'localhost:8080' : window.location.host;
    const wsUrl = `${protocol}//${host}/ws/livetail?token=${encodeURIComponent(token)}`;

    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => setConnected(true);

    ws.onmessage = (event) => {
      const parsed = parseLogMessage(event.data);
      // Day 5: Queue into pending batch, flush via rAF (at most 60fps state updates)
      pendingBatchRef.current.push(parsed);
      if (!rafRef.current) {
        rafRef.current = requestAnimationFrame(flushBatch);
      }
    };

    ws.onclose = (event) => {
      setConnected(false);
      if (event.code !== 1008 && event.code !== 4401) {
        reconnectTimeoutRef.current = setTimeout(connect, 3000);
      }
    };

    ws.onerror = () => ws.close();
  }, [onMessageReceived, flushBatch]);

  useEffect(() => {
    connect();
    return () => {
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
      if (wsRef.current) wsRef.current.close();
    };
  }, [connect]);

  return { connected };
}

