import React, { useState, useEffect } from 'react';

export default function AlertRulesView({ onNotify }) {
  const [rules, setRules] = useState([]);
  const [showNewRuleModal, setShowNewRuleModal] = useState(false);
  const [evaluating, setEvaluating] = useState(false);
  const [testWebhookUrl, setTestWebhookUrl] = useState('simulated://slack-alerts');

  const [name, setName] = useState('');
  const [query, setQuery] = useState('level:ERROR');
  const [threshold, setThreshold] = useState(5);
  const [windowMinutes, setWindowMinutes] = useState(5);
  const [cooldownMinutes, setCooldownMinutes] = useState(15);
  const [severity, setSeverity] = useState('HIGH');
  const [webhookUrl, setWebhookUrl] = useState('simulated://ops-channel');
  const [notifyEmail, setNotifyEmail] = useState('');

  const fetchRules = async () => {
    try {
      const res = await fetch('/api/alerts/rules');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      setRules(Array.isArray(data) ? data : Object.values(data));
    } catch (err) {
      onNotify(`Failed to fetch alert rules: ${err.message}`, 'error');
    }
  };

  useEffect(() => {
    fetchRules();
  }, []);

  const handleSaveRule = async (e) => {
    e.preventDefault();
    if (!name.trim() || !query.trim()) {
      onNotify('Name and query are required', 'warn');
      return;
    }

    const newRule = {
      name: name.trim(),
      query: query.trim(),
      threshold: Number(threshold),
      windowMinutes: Number(windowMinutes),
      cooldownMinutes: Number(cooldownMinutes),
      severity,
      webhookUrl: webhookUrl.trim() || null,
      notifyEmail: notifyEmail.trim() || null,
      enabled: true
    };

    try {
      const res = await fetch('/api/alerts/rules', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(newRule)
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      onNotify(`Rule "${name}" saved successfully!`, 'success');
      setShowNewRuleModal(false);
      setName('');
      fetchRules();
    } catch (err) {
      onNotify(`Save error: ${err.message}`, 'error');
    }
  };

  const handleToggleRule = async (rule) => {
    try {
      const updated = { ...rule, enabled: !rule.enabled };
      const res = await fetch('/api/alerts/rules', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updated)
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      fetchRules();
    } catch (err) {
      onNotify(`Toggle error: ${err.message}`, 'error');
    }
  };

  const handleDeleteRule = async (id) => {
    if (!confirm(`Delete alert rule ${id}?`)) return;
    try {
      const res = await fetch(`/api/alerts/rules/${id}`, { method: 'DELETE' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      onNotify('Rule deleted', 'info');
      fetchRules();
    } catch (err) {
      onNotify(`Delete error: ${err.message}`, 'error');
    }
  };

  const handleEvaluateNow = async () => {
    setEvaluating(true);
    try {
      const res = await fetch('/api/alerts/evaluate', { method: 'POST' });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const breachCount = (data.breaches || []).length;
      if (breachCount > 0) {
        onNotify(`Breach Detected: ${breachCount} threshold breach(es) found!`, 'error');
      } else {
        onNotify('Scan Complete: All queries within healthy thresholds.', 'success');
      }
    } catch (err) {
      onNotify(`Evaluation failed: ${err.message}`, 'error');
    } finally {
      setEvaluating(false);
    }
  };

  const handleTestWebhook = async () => {
    try {
      const res = await fetch('/api/alerts/test-webhook', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ webhookUrl: testWebhookUrl })
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      onNotify('Test webhook payload dispatched successfully!', 'success');
    } catch (err) {
      onNotify(`Test webhook failed: ${err.message}`, 'error');
    }
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', height: '100%', padding: '16px 20px', gap: 14, overflowY: 'auto' }}>
      <div className="neu-raised" style={{ padding: '12px 18px', display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <span style={{ fontWeight: 700, fontSize: 14, color: 'var(--text-main)', display: 'flex', alignItems: 'center', gap: 6 }}>
          🚨 Alerting Rules ({rules.length})
        </span>

        <button onClick={() => setShowNewRuleModal(!showNewRuleModal)} className="neu-btn neu-btn-primary neu-btn-sm">
          {showNewRuleModal ? '✕ Close Form' : '＋ New Alert Rule'}
        </button>

        <button onClick={handleEvaluateNow} className="neu-btn neu-btn-sm" disabled={evaluating}>
          {evaluating ? '⏳ Scanning Index...' : '⚡ Evaluate All Rules'}
        </button>

        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>
          <input
            type="text"
            className="neu-input"
            style={{ width: 220 }}
            value={testWebhookUrl}
            onChange={(e) => setTestWebhookUrl(e.target.value)}
            placeholder="Webhook URL..."
          />
          <button onClick={handleTestWebhook} className="neu-btn neu-btn-sm">
            🧪 Test Hook
          </button>
        </div>
      </div>

      {showNewRuleModal && (
        <form onSubmit={handleSaveRule} className="neu-raised-lg" style={{ padding: 22, display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ fontWeight: 800, fontSize: 15, color: 'var(--text-main)' }}>Configure New Alert Rule</div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
            <div>
              <label style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Rule Name</label>
              <input type="text" className="neu-input" style={{ width: '100%', marginTop: 4 }} placeholder="e.g. Excessive Payment Failures" value={name} onChange={(e) => setName(e.target.value)} required />
            </div>
            <div>
              <label style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Severity</label>
              <select className="neu-input" style={{ width: '100%', marginTop: 4 }} value={severity} onChange={(e) => setSeverity(e.target.value)}>
                <option value="CRITICAL">CRITICAL</option>
                <option value="HIGH">HIGH</option>
                <option value="MEDIUM">MEDIUM</option>
                <option value="LOW">LOW</option>
              </select>
            </div>
          </div>

          <div>
            <label style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Lucene Query</label>
            <input type="text" className="neu-input" style={{ width: '100%', marginTop: 4 }} placeholder='e.g. level:ERROR service:payment "timeout"' value={query} onChange={(e) => setQuery(e.target.value)} required />
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 14 }}>
            <div>
              <label style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Threshold (breach count)</label>
              <input type="number" className="neu-input" style={{ width: '100%', marginTop: 4 }} min="1" value={threshold} onChange={(e) => setThreshold(e.target.value)} />
            </div>
            <div>
              <label style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Time Window (minutes)</label>
              <input type="number" className="neu-input" style={{ width: '100%', marginTop: 4 }} min="1" value={windowMinutes} onChange={(e) => setWindowMinutes(e.target.value)} />
            </div>
            <div>
              <label style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Cooldown (minutes)</label>
              <input type="number" className="neu-input" style={{ width: '100%', marginTop: 4 }} min="1" value={cooldownMinutes} onChange={(e) => setCooldownMinutes(e.target.value)} />
            </div>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
            <div>
              <label style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Webhook URL</label>
              <input type="text" className="neu-input" style={{ width: '100%', marginTop: 4 }} placeholder="https://... or simulated://..." value={webhookUrl} onChange={(e) => setWebhookUrl(e.target.value)} />
            </div>
            <div>
              <label style={{ fontSize: 11, color: 'var(--text-muted)', fontWeight: 600 }}>Notification Email (optional)</label>
              <input type="email" className="neu-input" style={{ width: '100%', marginTop: 4 }} placeholder="alerts@axlero.internal" value={notifyEmail} onChange={(e) => setNotifyEmail(e.target.value)} />
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 6 }}>
            <button type="button" onClick={() => setShowNewRuleModal(false)} className="neu-btn neu-btn-sm">Cancel</button>
            <button type="submit" className="neu-btn neu-btn-primary neu-btn-sm">💾 Save Rule</button>
          </div>
        </form>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
        {rules.map((rule) => (
          <div key={rule.id} className="neu-raised" style={{ padding: 18, display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
              <div>
                <div style={{ fontWeight: 700, fontSize: 14, color: 'var(--text-main)' }}>{rule.name}</div>
                <div style={{ fontSize: 10.5, color: 'var(--text-faint)', marginTop: 2 }}>ID: {rule.id}</div>
              </div>
              <span className={`neu-badge ${rule.severity}`}>{rule.severity}</span>
            </div>

            <div className="neu-sunken mono" style={{ padding: '6px 10px', fontSize: 11, color: 'var(--accent)', wordBreak: 'break-all' }}>
              {rule.query}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, fontSize: 11.5, color: 'var(--text-muted)' }}>
              <div>⚡ Threshold: <strong style={{ color: 'var(--text-main)' }}>{rule.threshold} hits</strong></div>
              <div>⏱ Window: <strong style={{ color: 'var(--text-main)' }}>{rule.windowMinutes} min</strong></div>
              <div>🔕 Cooldown: <strong style={{ color: 'var(--text-main)' }}>{rule.cooldownMinutes} min</strong></div>
              <div>🔗 Webhook: <strong style={{ color: 'var(--text-main)' }}>{rule.webhookUrl ? 'Configured' : 'None'}</strong></div>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 4, paddingTop: 10, borderTop: '1px solid var(--neu-light)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div
                  className={`neu-toggle ${rule.enabled !== false ? 'checked' : ''}`}
                  onClick={() => handleToggleRule(rule)}
                >
                  <div className="neu-toggle-thumb" />
                </div>
                <span style={{ fontSize: 12, fontWeight: 600, color: rule.enabled !== false ? 'var(--c-success)' : 'var(--text-faint)' }}>
                  {rule.enabled !== false ? 'Enabled' : 'Disabled'}
                </span>
              </div>

              <button onClick={() => handleDeleteRule(rule.id)} className="neu-btn neu-btn-danger neu-btn-sm">
                🗑 Delete
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
