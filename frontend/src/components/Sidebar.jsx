import React from 'react';

export default function Sidebar({ activeTab, setActiveTab, unreadAlerts }) {
  const navItems = [
    { id: 'live-tail',  label: 'Live Tail',      icon: '⚡', category: 'MONITORING' },
    { id: 'search',     label: 'Search Logs',    icon: '🔍', category: 'MONITORING' },
    { id: 'histogram',  label: 'Metrics & Chart',icon: '📊', category: 'MONITORING' },
    { id: 'alerts',     label: 'Alert Rules',    icon: '🚨', category: 'AUTOMATION', badge: unreadAlerts > 0 ? unreadAlerts : null },
    { id: 'history',    label: 'Audit History',  icon: '📬', category: 'AUTOMATION' },
  ];

  return (
    <aside style={{
      width: 220,
      background: 'var(--neu-bg)',
      padding: '24px 16px',
      display: 'flex',
      flexDirection: 'column',
      gap: 6,
      boxShadow: '4px 0 16px var(--neu-dark)',
      zIndex: 5
    }}>
      {navItems.map((item, idx) => {
        const showCategory = idx === 0 || navItems[idx - 1].category !== item.category;
        const isActive = activeTab === item.id;
        return (
          <React.Fragment key={item.id}>
            {showCategory && (
              <div style={{
                fontSize: 10,
                fontWeight: 700,
                letterSpacing: '0.08em',
                color: 'var(--text-faint)',
                padding: '12px 10px 4px'
              }}>
                {item.category}
              </div>
            )}
            <button
              onClick={() => setActiveTab(item.id)}
              className={`neu-btn ${isActive ? 'pressed' : ''}`}
              style={{
                width: '100%',
                justifyContent: 'flex-start',
                padding: '11px 14px',
                borderRadius: 'var(--rad-md)',
                color: isActive ? 'var(--accent)' : 'var(--text-muted)',
                boxShadow: isActive ? 'var(--neu-active)' : 'var(--neu-extruded)',
                fontWeight: isActive ? 700 : 500
              }}
            >
              <span style={{ fontSize: 16 }}>{item.icon}</span>
              <span style={{ flex: 1, textAlign: 'left' }}>{item.label}</span>
              {item.badge && (
                <span className="neu-badge" style={{ background: 'var(--c-error)', color: '#fff', fontSize: 10 }}>
                  {item.badge}
                </span>
              )}
            </button>
          </React.Fragment>
        );
      })}
    </aside>
  );
}
