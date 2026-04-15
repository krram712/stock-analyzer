import React from 'react';
import { useApp } from '../context/AppContext';
import Dashboard from '../components/Dashboard';

export default function DashboardScreen() {
  const { analysisData, goBack } = useApp();

  if (!analysisData) return null;

  return (
    <div className="dashboard-screen">
      {/* Top nav bar */}
      <div className="dash-nav">
        <button className="back-nav-btn" onClick={goBack}>← New Search</button>
        <span className="dash-nav-title">
          {analysisData.ticker} · {analysisData.exchange}
        </span>
        <span className="dash-nav-price">${analysisData.price}</span>
      </div>

      <Dashboard data={analysisData} />
    </div>
  );
}
