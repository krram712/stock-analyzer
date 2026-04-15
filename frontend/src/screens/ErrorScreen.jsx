import React from 'react';
import { useApp } from '../context/AppContext';

export default function ErrorScreen() {
  const { errorMsg, retry, goBack, lastQuery } = useApp();

  return (
    <div className="error-screen">
      <div className="error-card">
        <div className="error-icon">⚠️</div>
        <h2 className="error-title">Analysis Failed</h2>

        {lastQuery && (
          <div className="error-query">
            Ticker: <strong>{lastQuery.ticker}</strong> · Horizon: <strong>{lastQuery.horizon}</strong>
          </div>
        )}

        <p className="error-message">{errorMsg || 'An unexpected error occurred.'}</p>

        <div className="error-actions">
          <button className="retry-btn" onClick={retry}>
            🔄 Try Again
          </button>
          <button className="back-btn" onClick={goBack}>
            ← New Search
          </button>
        </div>

        <div className="error-tips">
          <p><strong>Common causes:</strong></p>
          <ul>
            <li>Invalid or unlisted ticker symbol</li>
            <li>Anthropic API key missing or invalid</li>
            <li>Network timeout (analysis can take up to 2 mins)</li>
            <li>Rate limit reached – wait a moment and retry</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
