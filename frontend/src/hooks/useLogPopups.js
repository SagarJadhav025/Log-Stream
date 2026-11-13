import { useState, useEffect, useRef, useCallback } from 'react';

// Default auto-dismiss durations per severity (in ms)
export const POPUP_DURATIONS = {
  ERROR: 8000,
  WARN: 5000,
  DEBUG: 3000,
};

const MAX_VISIBLE_POPUPS = 4;
const GROUPING_WINDOW_MS = 1000;
const FLUSH_INTERVAL_MS = 100;
const MAX_BUFFER_CAPACITY = 10000;
const MAX_BURST_HISTORY = 50;

/**
 * High-performance hook for managing real-time log popups with flood protection.
 * Handles burst ingestion (up to 10k+ logs/sec) without freezing the React UI.
 *
 * @param {Object} options
 * @param {string} [options.wsUrl] - Optional WebSocket URL to connect automatically
 * @param {Object} [options.initialFilters] - Default severity filter flags
 */
export function useLogPopups(options = {}) {
  const {
    wsUrl = null,
    initialFilters = { ERROR: true, WARN: true, DEBUG: false }
  } = options;

  // Active visible popup cards (max 4)
  const [popups, setPopups] = useState([]);
  // Filter toggles: ERROR, WARN, DEBUG
  const [filters, setFilters] = useState(initialFilters);
  // Mute / pause state
  const [isMuted, setIsMuted] = useState(false);
  // Selected log for full inspection modal
  const [selectedLog, setSelectedLog] = useState(null);

  // High-throughput incoming logs buffer (in-memory ring buffer)
  const bufferRef = useRef([]);
  const filtersRef = useRef(filters);
  const isMutedRef = useRef(isMuted);
  const popupsRef = useRef(popups);
  const flushTimerRef = useRef(null);
  const countdownTimerRef = useRef(null);
  const wsRef = useRef(null);
  const reconnectTimeoutRef = useRef(null);

  // Sync refs to avoid stale closures in high-frequency timers
  useEffect(() => {
    filtersRef.current = filters;
  }, [filters]);

  useEffect(() => {
    isMutedRef.current = isMuted;
  }, [isMuted]);

  useEffect(() => {
    popupsRef.current = popups;
  }, [popups]);

  /**
   * Enforces max 4 cards with priority eviction:
   * Keeps sticky cards, then prefers ERROR > WARN > DEBUG, then newest.
   */
  const pruneToMax = useCallback((list) => {
    if (list.length <= MAX_VISIBLE_POPUPS) return list;

    // Severity weights for eviction (lowest weight evicted first)
    const severityWeight = { DEBUG: 1, WARN: 2, ERROR: 3 };

    // Separate sticky cards from non-sticky cards
    const sticky = list.filter(p => p.isSticky);
    const nonSticky = list.filter(p => !p.isSticky);

    if (sticky.length >= MAX_VISIBLE_POPUPS) {
      // If sticky cards alone hit or exceed limit, keep newest sticky cards
      return sticky.slice(-MAX_VISIBLE_POPUPS);
    }

    const availableSlots = MAX_VISIBLE_POPUPS - sticky.length;

    // Sort non-sticky by priority (higher weight first, then newer createdAt)
    const sortedNonSticky = [...nonSticky].sort((a, b) => {
      const weightA = severityWeight[a.level] || 0;
      const weightB = severityWeight[b.level] || 0;
      if (weightA !== weightB) return weightB - weightA;
      return b.createdAt - a.createdAt;
    });

    const keptNonSticky = sortedNonSticky.slice(0, availableSlots);
    // Return in chronological order
    return [...sticky, ...keptNonSticky].sort((a, b) => a.createdAt - b.createdAt);
  }, []);

  /**
   * High-throughput ingest function.
   * Can be called synchronously by WebSocket onmessage (even 10,000 times/sec).
   * Pushes directly to an internal memory buffer without triggering immediate React re-renders.
   */
  const ingest = useCallback((rawInput) => {
    if (isMutedRef.current) return;
    if (!rawInput) return;

    const items = Array.isArray(rawInput) ? rawInput : [rawInput];
    if (items.length === 0) return;

    // Guard against memory explosion if incoming rate exceeds ingestion capacity
    if (bufferRef.current.length >= MAX_BUFFER_CAPACITY) {
      // Drop lowest priority or oldest items to keep buffer healthy
      bufferRef.current.splice(0, items.length);
    }

    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      if (!item) continue;
      const level = (item.level || 'INFO').toUpperCase();

      // Discard immediately if not in enabled filters (e.g. INFO or disabled DEBUG)
      if (level !== 'ERROR' && level !== 'WARN' && level !== 'DEBUG') continue;
      if (!filtersRef.current[level]) continue;

      bufferRef.current.push({
        id: item.id || `log-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
        level,
        service: item.service || 'unknown-service',
        message: item.message || (typeof item === 'string' ? item : JSON.stringify(item)),
        timestamp: item.timestamp || Date.now(),
        stackTrace: item.stackTrace || item.stack || item.exception || null,
        raw: item,
        arrivedAt: Date.now()
      });
    }
  }, []);

  /**
   * Batch flush loop: flushes buffered logs at controlled FLUSH_INTERVAL_MS intervals.
   * Coalesces logs of the same severity within the 1-second grouping window.
   */
  useEffect(() => {
    const processBatch = () => {
      if (bufferRef.current.length === 0 || isMutedRef.current) return;

      // Drain buffer
      const batch = bufferRef.current;
      bufferRef.current = [];

      const now = Date.now();
      let currentPopups = [...popupsRef.current];

      // Group buffered items by severity
      const groupedByLevel = {
        ERROR: [],
        WARN: [],
        DEBUG: []
      };

      for (let i = 0; i < batch.length; i++) {
        const item = batch[i];
        if (groupedByLevel[item.level]) {
          groupedByLevel[item.level].push(item);
        }
      }

      // Process each severity group
      (['ERROR', 'WARN', 'DEBUG']).forEach(level => {
        if (!filtersRef.current[level]) return;
        const logsForLevel = groupedByLevel[level];
        if (logsForLevel.length === 0) return;

        const countInBatch = logsForLevel.length;
        const latestLog = logsForLevel[logsForLevel.length - 1];

        // Check if there is an existing popup for this severity within the 1-second window
        const existingIdx = currentPopups.findIndex(
          p => p.level === level && (now - p.createdAt <= GROUPING_WINDOW_MS || p.count > 1)
        );

        if (existingIdx !== -1) {
          // Collapse / Coalesce into existing popup
          const target = currentPopups[existingIdx];
          const updatedHistory = [
            ...target.burstHistory,
            ...logsForLevel.map(l => ({ timestamp: l.timestamp, message: l.message, raw: l.raw }))
          ].slice(-MAX_BURST_HISTORY);

          currentPopups[existingIdx] = {
            ...target,
            message: latestLog.message,
            timestamp: latestLog.timestamp,
            stackTrace: latestLog.stackTrace || target.stackTrace,
            raw: latestLog.raw,
            count: target.count + countInBatch,
            burstHistory: updatedHistory,
            // Refresh countdown duration if not sticky and not hovered
            remaining: target.isSticky ? target.remaining : Math.max(target.remaining, 2000),
            updatedAt: now
          };
        } else {
          // Create a brand new popup card
          const duration = POPUP_DURATIONS[level] || 5000;
          const newCard = {
            id: latestLog.id,
            level,
            service: latestLog.service,
            message: latestLog.message,
            timestamp: latestLog.timestamp,
            stackTrace: latestLog.stackTrace,
            raw: latestLog.raw,
            count: countInBatch,
            burstHistory: logsForLevel.map(l => ({ timestamp: l.timestamp, message: l.message, raw: l.raw })).slice(-MAX_BURST_HISTORY),
            createdAt: now,
            updatedAt: now,
            duration,
            remaining: duration,
            isSticky: false,
            isPaused: false
          };

          currentPopups.push(newCard);
        }
      });

      // Clamp to max 4 cards
      currentPopups = pruneToMax(currentPopups);
      popupsRef.current = currentPopups;
      setPopups(currentPopups);
    };

    flushTimerRef.current = setInterval(processBatch, FLUSH_INTERVAL_MS);
    return () => {
      if (flushTimerRef.current) clearInterval(flushTimerRef.current);
    };
  }, [pruneToMax]);

  /**
   * Countdown timer loop (runs every 100ms):
   * Ticks down `remaining` on cards that are not paused and not sticky.
   * Auto-dismisses when `remaining <= 0`.
   */
  useEffect(() => {
    const TICK_INTERVAL = 100;
    countdownTimerRef.current = setInterval(() => {
      if (popupsRef.current.length === 0) return;

      let changed = false;
      const updated = [];

      for (let i = 0; i < popupsRef.current.length; i++) {
        const card = popupsRef.current[i];
        if (card.isSticky || card.isPaused) {
          updated.push(card);
          continue;
        }

        const nextRemaining = card.remaining - TICK_INTERVAL;
        if (nextRemaining > 0) {
          updated.push({
            ...card,
            remaining: nextRemaining
          });
          changed = true;
        } else {
          // Card expired and will be auto-dismissed
          changed = true;
        }
      }

      if (changed) {
        popupsRef.current = updated;
        setPopups(updated);
      }
    }, TICK_INTERVAL);

    return () => {
      if (countdownTimerRef.current) clearInterval(countdownTimerRef.current);
    };
  }, []);

  /**
   * Optional direct WebSocket connection if wsUrl is provided
   */
  useEffect(() => {
    if (!wsUrl) return;

    let isMounted = true;
    const connect = () => {
      try {
        const token = localStorage.getItem('logstream_jwt') || '';
        const fullUrl = token ? `${wsUrl}?token=${encodeURIComponent(token)}` : wsUrl;
        const ws = new WebSocket(fullUrl);
        wsRef.current = ws;

        ws.onmessage = (event) => {
          if (!isMounted) return;
          try {
            const data = JSON.parse(event.data);
            ingest(data);
          } catch {
            ingest({ level: 'INFO', message: event.data, service: 'raw' });
          }
        };

        ws.onclose = () => {
          if (!isMounted) return;
          reconnectTimeoutRef.current = setTimeout(connect, 3000);
        };

        ws.onerror = () => {
          ws.close();
        };
      } catch (err) {
        if (isMounted) {
          reconnectTimeoutRef.current = setTimeout(connect, 3000);
        }
      }
    };

    connect();

    return () => {
      isMounted = false;
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      if (wsRef.current) wsRef.current.close();
    };
  }, [wsUrl, ingest]);

  // Card interaction handlers
  const dismissPopup = useCallback((id) => {
    setPopups(prev => prev.filter(p => p.id !== id));
  }, []);

  const clearAll = useCallback(() => {
    bufferRef.current = [];
    setPopups([]);
  }, []);

  const toggleSticky = useCallback((id) => {
    setPopups(prev => prev.map(p => {
      if (p.id !== id) return p;
      return { ...p, isSticky: !p.isSticky };
    }));
  }, []);

  const pauseTimer = useCallback((id) => {
    setPopups(prev => prev.map(p => {
      if (p.id !== id) return p;
      return { ...p, isPaused: true };
    }));
  }, []);

  const resumeTimer = useCallback((id) => {
    setPopups(prev => prev.map(p => {
      if (p.id !== id) return p;
      return { ...p, isPaused: false };
    }));
  }, []);

  const toggleFilter = useCallback((level) => {
    setFilters(prev => {
      const nextVal = !prev[level];
      if (!nextVal) {
        setPopups(current => current.filter(p => p.level !== level));
      }
      return {
        ...prev,
        [level]: nextVal
      };
    });
  }, []);

  const toggleMute = useCallback(() => {
    setIsMuted(prev => !prev);
  }, []);

  /**
   * Helper test simulator: injects sample ERROR, WARN, or DEBUG logs
   */
  const simulateLog = useCallback((level = 'ERROR') => {
    const errorSamples = [
      {
        message: 'NullPointerException: Unable to acquire database connection from HikariPool-Primary',
        service: 'payment-gateway',
        stackTrace: 'java.lang.NullPointerException: Cannot invoke "javax.sql.DataSource.getConnection()" because "this.dataSource" is null\n\tat com.axlero.payment.service.TransactionService.processPayment(TransactionService.java:142)\n\tat com.axlero.payment.controller.PaymentApiController.checkout(PaymentApiController.java:58)'
      },
      {
        message: 'OutOfMemoryError: Java heap space exceeded in Lucene SegmentMerger',
        service: 'indexing-service',
        stackTrace: 'java.lang.OutOfMemoryError: Java heap space\n\tat org.apache.lucene.index.SegmentMerger.mergeFields(SegmentMerger.java:194)\n\tat org.apache.lucene.index.IndexWriter.merge(IndexWriter.java:3120)'
      },
      {
        message: 'Downstream HTTP 504 Gateway Timeout contacting Auth0 verification endpoint',
        service: 'auth-service',
        stackTrace: 'feign.RetryableException: Read timed out executing POST https://auth0.axlero.internal/oauth/v2/verify\n\tat feign.FeignException.errorExecuting(FeignException.java:268)'
      }
    ];

    const warnSamples = [
      {
        message: 'High latency detected: query execution took 1,842ms (> threshold 500ms)',
        service: 'order-processor'
      },
      {
        message: 'Circuit breaker state changed from CLOSED to HALF_OPEN (failure rate: 42%)',
        service: 'billing-api'
      },
      {
        message: 'Redis cluster replication lag exceeded 1500ms on shard 3',
        service: 'cache-svc'
      }
    ];

    const debugSamples = [
      {
        message: 'Cache hit for key session:usr_49102 [TTL=1740s]',
        service: 'user-service'
      },
      {
        message: 'Kafka consumer poll returned 128 records from topic order-events-partition-4',
        service: 'order-processor'
      },
      {
        message: 'Evaluated Lucene boolean query term (+status:active +region:us-east)',
        service: 'search-api'
      }
    ];

    let pool = errorSamples;
    if (level === 'WARN') pool = warnSamples;
    if (level === 'DEBUG') pool = debugSamples;

    const sample = pool[Math.floor(Math.random() * pool.length)];
    ingest({
      level,
      service: sample.service,
      message: sample.message,
      stackTrace: sample.stackTrace,
      timestamp: Date.now()
    });
  }, [ingest]);

  /**
   * Helper test simulator: injects a burst of N logs in rapid succession to test flood protection & grouping
   */
  const simulateBurst = useCallback((count = 100) => {
    const burst = [];
    const levels = ['ERROR', 'WARN', 'DEBUG'];
    for (let i = 0; i < count; i++) {
      const level = levels[i % levels.length];
      burst.push({
        id: `burst-${Date.now()}-${i}`,
        level,
        service: `burst-node-${(i % 5) + 1}`,
        message: `Rapid burst log #${i + 1}: High ingestion volume stress test for ${level}`,
        timestamp: Date.now()
      });
    }
    ingest(burst);
  }, [ingest]);

  return {
    popups,
    filters,
    isMuted,
    selectedLog,
    setSelectedLog,
    ingest,
    dismissPopup,
    clearAll,
    toggleSticky,
    pauseTimer,
    resumeTimer,
    toggleFilter,
    toggleMute,
    simulateLog,
    simulateBurst
  };
}
