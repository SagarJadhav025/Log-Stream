import { useEffect, useRef, useState, useCallback } from 'react';

export function useLiveTail(onMessageReceived) {
  const [connected, setConnected] = useState(false);
  const wsRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);

  const connect = useCallback(() => {
    const token = localStorage.getItem('logstream_jwt') || 'demo-token';

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.port === '3000' ? 'localhost:8080' : window.location.host;
    const wsUrl = `${protocol}//${host}/ws/livetail?token=${encodeURIComponent(token)}`;

    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      setConnected(true);
    };

    ws.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data);
        onMessageReceived(parsed);
      } catch {
        onMessageReceived({ level: 'INFO', message: event.data, service: 'raw' });
      }
    };

    ws.onclose = (event) => {
      setConnected(false);
      // If unauthorized, do not retry blindly
      if (event.code !== 1008 && event.code !== 4401) {
        reconnectTimeoutRef.current = setTimeout(connect, 3000);
      }
    };

    ws.onerror = () => {
      ws.close();
    };
  }, [onMessageReceived]);

  useEffect(() => {
    connect();
    return () => {
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) wsRef.current.close();
    };
  }, [connect]);

  return { connected };
}
