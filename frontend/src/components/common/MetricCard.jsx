import React from 'react';

export default function MetricCard({ label, value, sub, small }) {
  return (
    <div className={`metric-card ${small ? 'metric-card-sm' : ''}`}>
      <div className="metric-label">{label}</div>
      <div className={`metric-value ${small ? 'metric-value-sm' : ''}`}>{value || 'N/A'}</div>
      {sub && <div className="metric-sub">{sub}</div>}
    </div>
  );
}
