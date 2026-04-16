import React from 'react';
import { useApp } from '../context/AppContext';

export default function LoadingScreen() {
  const { loadingStage, loadingStages, lastQuery } = useApp();

  return (
    <div className="loading-screen">
      <div className="loading-card">
        <div className="loading-logo">📊</div>

        {lastQuery && (
          <div className="loading-ticker">
            <span className="ticker-badge">{lastQuery.ticker}</span>
            <span className="horizon-badge">{lastQuery.horizon}</span>
          </div>
        )}

        <h2 className="loading-title">Analysing…</h2>

        {/* Spinner */}
        <div className="spinner-wrapper">
          <div className="spinner" />
        </div>

        {/* Stage progress */}
        <div className="loading-stages">
          {loadingStages.map((stage, i) => (
            <div
              key={i}
              className={`loading-stage ${
                i < loadingStage ? 'done' : i === loadingStage ? 'active' : 'pending'
              }`}
            >
              <span className="stage-icon">
                {i < loadingStage ? '✓' : i === loadingStage ? '→' : '○'}
              </span>
              <span className="stage-text">{stage}</span>
            </div>
          ))}
        </div>

        <p className="loading-note">
          Searching live financial data across 8 AI providers.<br/>
          Typically 30–120 seconds. If all providers are rate-limited,
          the backend will automatically wait 65s and retry — please keep this page open.
        </p>
      </div>
    </div>
  );
}
