import React from 'react';

export default function Header({ lps, totalLogs, totalErrors, wsConnected, wsConnecting }) {
  return (
    <header style={{
      height: 64,
      padding: '0 24px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      background: 'var(--neu-bg)',
      boxShadow: '0 4px 16px var(--neu-dark)',
      zIndex: 10
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
        <div className="neu-raised" style={{
          width: 38,
          height: 38,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 18,
          borderRadius: 12,
          background: 'linear-gradient(145deg, var(--neu-light), var(--neu-bg))'
        }}>
          📡
        </div>
        <div>
          <div style={{ fontWeight: 800, fontSize: 16, letterSpacing: '-0.3px', display: 'flex', alignItems: 'center', gap: 6 }}>
            LogStream
            <span className="neu-badge" style={{ fontSize: 9, padding: '2px 7px', color: 'var(--accent)' }}>REACT</span>
          </div>
          <div style={{ fontSize: 10.5, color: 'var(--text-muted)', fontWeight: 500 }}>
            High-Performance Observability
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 14 }}>
        <div className="neu-sunken" style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 16px', borderRadius: 'var(--rad-pill)' }}>
          <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>RATE</span>
          <strong style={{ fontSize: 14, color: 'var(--accent)', minWidth: 32, textAlign: 'right' }}>{lps}</strong>
          <span style={{ fontSize: 11, color: 'var(--text-faint)' }}>logs/s</span>
        </div>

        <div className="neu-sunken" style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 16px', borderRadius: 'var(--rad-pill)' }}>
          <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>TOTAL</span>
          <strong style={{ fontSize: 14, color: 'var(--text-main)' }}>{totalLogs.toLocaleString()}</strong>
        </div>

        <div className="neu-sunken" style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 16px', borderRadius: 'var(--rad-pill)' }}>
          <span style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>ERRORS</span>
          <strong style={{ fontSize: 14, color: 'var(--c-error)' }}>{totalErrors.toLocaleString()}</strong>
        </div>
      </div>

      <div className="neu-sunken" style={{
        display: 'flex',
        alignItems: 'center',
        gap: 9,
        padding: '7px 16px',
        borderRadius: 'var(--rad-pill)'
      }}>
        <div style={{ position: 'relative', width: 10, height: 10 }}>
          <div style={{
            width: 10,
            height: 10,
            borderRadius: '50%',
            backgroundColor: wsConnected ? 'var(--c-success)' : wsConnecting ? 'var(--c-warn)' : 'var(--c-error)',
            boxShadow: wsConnected ? '0 0 8px var(--c-success)' : 'none'
          }} />
          {wsConnected && (
            <div style={{
              position: 'absolute',
              inset: -2,
              borderRadius: '50%',
              border: '1.5px solid var(--c-success)',
              animation: 'pulse-ring 2s infinite'
            }} />
          )}
        </div>
        <span style={{ fontSize: 12, fontWeight: 600, color: wsConnected ? 'var(--c-success)' : wsConnecting ? 'var(--c-warn)' : 'var(--c-error)' }}>
          {wsConnected ? 'LIVE STREAM' : wsConnecting ? 'CONNECTING...' : 'OFFLINE'}
        </span>
      </div>
    </header>
  );
}
