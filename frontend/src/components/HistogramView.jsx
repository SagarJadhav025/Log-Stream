import React, { useRef, useEffect, useState, useCallback } from 'react';
import * as echarts from 'echarts';

const formatTime = (ts) => {
  if (!ts) return '--:--:--';
  const d = new Date(ts);
  return isNaN(d) ? ts : d.toLocaleTimeString('en-US', { hour12: false });
};

export default function HistogramView({ onNotify }) {
  const chartRef = useRef(null);
  const echartsInstance = useRef(null);
  const [minutes, setMinutes] = useState('60');
  const [bucketSeconds, setBucketSeconds] = useState('300');
  const [level, setLevel] = useState('');
  const [serviceName, setServiceName] = useState('');

  const fetchHistogram = useCallback(async () => {
    try {
      const params = new URLSearchParams({ minutes, bucketSeconds });
      if (level) params.set('level', level);
      if (serviceName.trim()) params.set('serviceName', serviceName.trim());

      const res = await fetch(`/api/logs/histogram?${params.toString()}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      renderChart(data);
    } catch (err) {
      onNotify(`Histogram error: ${err.message}`, 'error');
    }
  }, [minutes, bucketSeconds, level, serviceName]);

  const renderChart = (data) => {
    if (!chartRef.current) return;
    if (!echartsInstance.current) {
      echartsInstance.current = echarts.init(chartRef.current, null, { renderer: 'canvas' });
    }

    const xData = (data || []).map(d => formatTime(d.time || d.timestamp));
    const yData = (data || []).map(d => d.count || 0);

    const option = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: '#181e2b',
        borderColor: '#212b3d',
        borderWidth: 1,
        textStyle: { color: '#e2e8f5', fontFamily: 'Inter' },
        formatter: (params) => {
          const p = params[0];
          return `<strong>${p.name}</strong><br/>Log Volume: <span style="color:#4f8eff;font-weight:700">${p.value}</span>`;
        }
      },
      grid: { top: 24, right: 24, bottom: 44, left: 54, containLabel: true },
      xAxis: {
        type: 'category',
        data: xData,
        axisLine: { lineStyle: { color: '#212b3d' } },
        axisTick: { show: false },
        axisLabel: { color: '#7d8ea8', fontSize: 11 }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: '#1f2737', type: 'dashed' } },
        axisLabel: { color: '#7d8ea8', fontSize: 11 }
      },
      series: [
        {
          name: 'Logs',
          type: 'bar',
          data: yData,
          barMaxWidth: 36,
          itemStyle: {
            borderRadius: [6, 6, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0,   color: '#4f8eff' },
              { offset: 0.5, color: '#8a63ff' },
              { offset: 1,   color: 'rgba(79, 142, 255, 0.1)' }
            ]),
            shadowColor: 'rgba(79, 142, 255, 0.35)',
            shadowBlur: 10
          }
        }
      ]
    };

    echartsInstance.current.setOption(option);
  };

  useEffect(() => {
    fetchHistogram();
    const handleResize = () => echartsInstance.current && echartsInstance.current.resize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [fetchHistogram]);

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', padding: '16px 20px', gap: 14 }}>
      <div className="neu-raised" style={{ padding: '12px 18px', display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: 6 }}>
          📊 Aggregation Filters
        </span>

        <select className="neu-input" value={minutes} onChange={(e) => setMinutes(e.target.value)} style={{ width: 140 }}>
          <option value="15">Last 15 min</option>
          <option value="60">Last 1 hour</option>
          <option value="360">Last 6 hours</option>
          <option value="1440">Last 24 hours</option>
        </select>

        <select className="neu-input" value={bucketSeconds} onChange={(e) => setBucketSeconds(e.target.value)} style={{ width: 140 }}>
          <option value="60">1 min buckets</option>
          <option value="300">5 min buckets</option>
          <option value="900">15 min buckets</option>
          <option value="3600">1 hr buckets</option>
        </select>

        <select className="neu-input" value={level} onChange={(e) => setLevel(e.target.value)} style={{ width: 130 }}>
          <option value="">All Levels</option>
          <option value="ERROR">ERROR</option>
          <option value="WARN">WARN</option>
          <option value="INFO">INFO</option>
          <option value="DEBUG">DEBUG</option>
        </select>

        <input
          type="text"
          className="neu-input"
          placeholder="Service filter..."
          value={serviceName}
          onChange={(e) => setServiceName(e.target.value)}
          style={{ width: 150 }}
        />

        <button onClick={fetchHistogram} className="neu-btn neu-btn-sm" style={{ marginLeft: 'auto' }}>
          ↻ Refresh
        </button>
      </div>

      <div className="neu-sunken-deep" style={{ flex: 1, padding: 16, position: 'relative' }}>
        <div ref={chartRef} style={{ width: '100%', height: '100%' }} />
      </div>
    </div>
  );
}
