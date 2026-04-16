import React, { useState } from 'react';
import Badge from './common/Badge';
import MetricCard from './common/MetricCard';
import DataTable from './common/DataTable';
import { useApp } from '../context/AppContext';

// ─── Tab components ───────────────────────────────────────────────────────────

function TabOverview({ d }) {
  return (
    <div className="tab-content">
      {/* Data confidence bar */}
      <div className="confidence-bar">
        <Badge text={`Data Confidence: ${d.dataConfidence}`} />
        <span className="conf-detail">
          {d.liveMetrics} live metrics · Updated {d.lastUpdated}
        </span>
      </div>

      {/* Key metrics row */}
      <div className="metrics-grid-4">
        <MetricCard label="Current Price" value={`$${d.price}`} />
        <MetricCard label="Market Cap" value={d.marketCap} />
        <MetricCard label="52W Range" value={`$${d.low52} – $${d.high52}`} />
        <MetricCard label="Exchange" value={d.exchange} sub={d.sp500 === 'Yes' ? 'S&P 500' : ''} />
      </div>

      {/* Description */}
      <div className="info-card">
        <p className="company-desc">{d.description}</p>
        <div className="listing-row">
          <span><strong>Sector:</strong> {d.sector}</span>
          <span><strong>Sub-industry:</strong> {d.subIndustry}</span>
        </div>
        <div className="listing-row">
          <span><strong>Ticker:</strong> {d.ticker}</span>
          <span><strong>S&P 500:</strong> {d.sp500}</span>
        </div>
      </div>

      {/* Quick metrics strip */}
      <div className="section-head">Quick Metrics</div>
      <div className="metrics-grid-5">
        <MetricCard label="Rev CAGR (3yr)" value={d.overview?.revCAGR3 || 'N/A'} small />
        <MetricCard label="EPS CAGR (3yr)" value={d.overview?.epsCAGR3 || 'N/A'} small />
        <MetricCard label="ROE" value={d.overview?.roe || 'N/A'} small />
        <MetricCard label="D/E Ratio" value={d.overview?.de || 'N/A'} small />
        <MetricCard label="Shr. Yield" value={d.overview?.shareholderYield || 'N/A'} small />
      </div>

      <div className="source-row">Sources: {(d.sources || []).join(' · ')}</div>
    </div>
  );
}

function TabValuation({ d }) {
  const v = d.valuation || {};
  return (
    <div className="tab-content">
      <div className="section-head">
        Sector: {d.subIndustry} — Primary: {v.sectorPrimary || 'N/A'}
      </div>

      <div className="scrollable-table">
        <table className="data-table">
          <thead>
            <tr>
              <th>Metric</th><th>Current</th><th>Sector Avg</th>
              <th>5-Yr Avg</th><th>Signal</th><th>Meaning</th>
            </tr>
          </thead>
          <tbody>
            {(v.metrics || []).map((m, i) => (
              <tr key={i} className={i % 2 === 0 ? 'row-even' : 'row-odd'}>
                <td><strong>{m.name}</strong></td>
                <td>{m.current}</td>
                <td>{m.sectorAvg}</td>
                <td>{m.fiveYrAvg}</td>
                <td><Badge text={m.signal} /></td>
                <td className="metric-why">{m.why}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="overall-valuation">
        <span className="overall-label">Overall Valuation:</span>
        <Badge text={v.overall || 'N/A'} />
      </div>
      {v.overallNote && <div className="note-card">{v.overallNote}</div>}
    </div>
  );
}

function TabGrowth({ d }) {
  const g = d.growth || {};
  return (
    <div className="tab-content">
      {/* Revenue */}
      <div className="section-head">A. Revenue</div>
      <div className="cagr-row">
        <span>3yr CAGR: <strong>{g.revCAGR3 || 'N/A'}</strong></span>
        <span>5yr CAGR: <strong>{g.revCAGR5 || 'N/A'}</strong></span>
      </div>
      <DataTable
        headers={['Fiscal Year', 'Revenue', 'YoY Growth']}
        rows={(g.revenue || []).map(r => [r.year, r.val, r.yoy])}
      />

      {/* Net Income */}
      <div className="section-head">B. Net Income</div>
      <div className="cagr-row">
        <span>3yr CAGR: <strong>{g.niCAGR3 || 'N/A'}</strong></span>
      </div>
      <DataTable
        headers={['Fiscal Year', 'Net Income', 'YoY Growth']}
        rows={(g.netIncome || []).map(r => [r.year, r.val, r.yoy])}
      />

      {/* EPS */}
      <div className="section-head">C. EPS (Diluted) — Recent Quarters</div>
      <DataTable
        headers={['Quarter', 'Diluted EPS', 'YoY Change']}
        rows={(g.eps || []).map(r => [r.qtr, r.eps, r.yoy])}
      />

      {/* Margins */}
      <div className="section-head">D. Margin Trend</div>
      <DataTable
        headers={['Year', 'Gross Margin', 'EBITDA Margin', 'Net Margin']}
        rows={(g.margins || []).map(r => [r.year, r.gross, r.ebitda, r.net])}
      />

      <div className="classification-row">
        <span>Growth Classification:</span>
        <Badge text={g.classification || 'N/A'} />
      </div>
      {g.classNote && <div className="note-card">{g.classNote}</div>}
    </div>
  );
}

function TabHealth({ d }) {
  const h = d.health || {};
  const scenarios = h.scenarios || {};
  return (
    <div className="tab-content">
      <div className="section-head">A. Financial Health</div>

      <div className="health-grid">
        {[
          ['Debt-to-Equity', h.de],
          ['Interest Coverage', h.interestCoverage],
          ['Current Ratio', h.currentRatio],
        ].map(([label, item]) => item ? (
          <div key={label} className="health-card">
            <div className="health-label">{label}</div>
            <div className="health-value">
              {item.val} <Badge text={item.badge} />
            </div>
            <div className="health-note">{item.note}</div>
          </div>
        ) : null)}
        <div className="health-card">
          <div className="health-label">Free Cash Flow</div>
          <div className="health-value">
            {(h.fcf || []).length > 0
              ? `${h.fcf[h.fcf.length - 1].val} (latest)`
              : 'N/A'
            }
            {h.fcfBadge && <Badge text={h.fcfBadge} />}
          </div>
          <div className="health-note">
            {(h.fcf || []).map(f => `${f.year}: ${f.val}`).join(' · ')}
          </div>
        </div>
      </div>

      {/* Scenarios */}
      <div className="section-head">B. Scenarios — {d.horizon} Horizon</div>
      <div className="scenarios-grid">
        {[
          ['🐻 Bear', 'scenario-bear', scenarios.bear],
          ['📊 Base', 'scenario-base', scenarios.base],
          ['🐂 Bull', 'scenario-bull', scenarios.bull],
        ].map(([label, cls, pts]) => (
          <div key={label} className={`scenario-card ${cls}`}>
            <div className="scenario-label">{label}</div>
            {(pts || []).map((p, i) => <div key={i} className="scenario-point">• {p}</div>)}
          </div>
        ))}
      </div>
      <div className="disclaimer-small">
        Scenarios are based on historical trends only — not forecasts or guarantees.
      </div>
    </div>
  );
}

function TabReturns({ d }) {
  const r = d.returns || {};
  return (
    <div className="tab-content">
      <div className="section-head">Capital Returns Quality</div>
      <DataTable
        headers={['Metric', 'Current', '3yr Avg', '5yr Avg', 'Signal']}
        rows={[
          ['ROE', r.roe?.current, r.roe?.yr3avg, r.roe?.yr5avg, <Badge text={r.roe?.badge} />],
          ['ROIC', r.roic?.current, r.roic?.yr3avg, r.roic?.yr5avg, <Badge text={r.roic?.badge} />],
          ['ROA', r.roa?.current, r.roa?.yr3avg, r.roa?.yr5avg, <Badge text={r.roa?.badge} />],
        ].filter(row => row[1])}
      />
      {r.roic?.note && <div className="note-small">ROIC note: {r.roic.note}</div>}

      <div className="section-head">Dividend &amp; Buyback History</div>
      <DataTable
        headers={['Year', 'Div/Share', 'Buyback $', 'Payout Ratio', 'Shr. Yield']}
        rows={(r.dividends || []).map(dv => [dv.year, dv.dps, dv.buyback, dv.payout, dv.yield])}
      />
      {r.buybackAuth && (
        <div className="note-small">Latest buyback: <strong>{r.buybackAuth}</strong></div>
      )}

      <div className="classification-row">
        <span>Return Quality:</span>
        <Badge text={r.classification || 'N/A'} />
      </div>
      {r.classNote && <div className="note-card">{r.classNote}</div>}
    </div>
  );
}

function TabPeers({ d }) {
  const peers = d.peers || [];
  const news = d.news || [];

  return (
    <div className="tab-content">
      <div className="section-head">Peer Comparison</div>
      <div className="scrollable-table">
        <table className="data-table">
          <thead>
            <tr>
              <th>Company</th><th>Ticker</th><th>Primary Metric</th>
              <th>ROE</th><th>Rev CAGR</th><th>D/E</th><th>Sector KPI</th><th>Standing</th>
            </tr>
          </thead>
          <tbody>
            {peers.map((p, i) => (
              <tr
                key={i}
                className={p.ticker === d.ticker ? 'row-highlight' : i % 2 === 0 ? 'row-even' : 'row-odd'}
              >
                <td>{p.name}</td>
                <td><strong>{p.ticker}</strong></td>
                <td>{p.primary}</td>
                <td>{p.roe}</td>
                <td>{p.revCagr}</td>
                <td>{p.de}</td>
                <td>{p.kpi}</td>
                <td><Badge text={p.standing} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="section-head" style={{ marginTop: 20 }}>Recent News (Long-Term Relevance)</div>
      {news.map((n, i) => (
        <div key={i} className="news-card">
          <div className="news-headline">{n.headline}</div>
          <div className="news-summary">{n.summary}</div>
          <div className="news-source">{n.source}</div>
        </div>
      ))}
    </div>
  );
}

function TabOwnership({ d }) {
  const o = d.ownership || {};
  return (
    <div className="tab-content">
      <div className="ownership-grid">
        <div className="info-card">
          <div className="section-head-sm">Insider Activity (Form 4)</div>
          <div className="ownership-row">
            Ownership: <strong>{o.insiderPct || 'N/A'}</strong>
          </div>
          <div className="ownership-row">
            Trend: <Badge text={o.insiderTrend || 'N/A'} />
          </div>
          {o.insiderNote && <div className="ownership-note">{o.insiderNote}</div>}
        </div>

        <div className="info-card">
          <div className="section-head-sm">Short Interest</div>
          <div className="ownership-row">
            {o.shortInterest || 'N/A'} <Badge text={o.shortBadge || 'N/A'} />
          </div>
          <div className="ownership-note">Flagged if &gt;10% of float</div>
        </div>
      </div>

      <div className="info-card" style={{ marginTop: 12 }}>
        <div className="section-head-sm">Institutional Ownership</div>
        <div className="ownership-row">
          Total: <strong>{o.institutionalPct || 'N/A'}</strong> —
          Trend: <Badge text={o.institutionalTrend || 'N/A'} />
        </div>
        <DataTable
          headers={['Top 5 Institutional Holders', '% Held']}
          rows={(o.top5 || []).map(h => [h.name, h.pct])}
        />
      </div>

      <div className="info-card" style={{ marginTop: 12 }}>
        <div className="section-head-sm">Management Tone</div>
        <div className="ownership-row">
          <Badge text={o.mgmtTone || 'N/A'} />
        </div>
        {o.mgmtNote && <div className="ownership-note">{o.mgmtNote}</div>}
        <div className="ownership-row" style={{ marginTop: 8 }}>
          Overall Signal: <Badge text={o.ownershipSignal || 'N/A'} />
        </div>
      </div>
    </div>
  );
}

function TabView({ d }) {
  const v = d.view || {};
  const verdictStyle = {
    background: v.verdictColor || '#f1f5f9',
    borderColor: v.verdictBorder || '#94a3b8',
    color: v.verdictText || '#334155',
  };

  return (
    <div className="tab-content">
      {/* VERDICT */}
      <div className="verdict-box" style={verdictStyle}>
        <div className="verdict-header">
          <span className="verdict-emoji">{v.verdictEmoji || '📊'}</span>
          <span className="verdict-label">{v.verdict || 'N/A'}</span>
          <span className="verdict-horizon">Horizon: {d.horizon}</span>
        </div>
        <div className="verdict-reason">{v.verdictReason || ''}</div>
      </div>

      {/* Quality */}
      <div className="info-card quality-card">
        <Badge text={v.quality || 'N/A'} />
        <p className="one-liner">{v.oneLiner || ''}</p>
      </div>

      {/* Strengths / Watch / Track */}
      <div className="swot-top-grid">
        <div className="swot-card strengths">
          <div className="swot-head">✓ STRENGTHS</div>
          {(v.strengths || []).map((s, i) => <div key={i} className="swot-item">{s}</div>)}
        </div>
        <div className="swot-card watch">
          <div className="swot-head">⚠ WATCH POINTS</div>
          {(v.watchPoints || []).map((w, i) => <div key={i} className="swot-item">{w}</div>)}
        </div>
        <div className="swot-card track">
          <div className="swot-head">→ TRACK</div>
          <div className="swot-item">{v.track || ''}</div>
        </div>
      </div>

      {/* Opportunities & Risks */}
      <div className="opp-risk-grid">
        <div className="opp-card">
          <div className="opp-head">OPPORTUNITIES</div>
          {(v.opportunities || []).map((o, i) => <div key={i} className="opp-item">{o}</div>)}
        </div>
        <div className="risk-card">
          <div className="risk-head">RISKS</div>
          {(v.risks || []).map((r, i) => <div key={i} className="risk-item">{r}</div>)}
        </div>
      </div>

      {/* Timeline */}
      <div className="timeline-card">
        <div className="timeline-head">⏳ TIMELINE MATCH — {d.horizon}</div>
        <p className="timeline-text">{v.timeline || ''}</p>
      </div>

      {/* Disclaimer */}
      <div className="legal-footer">
        This tool is for fundamental screening and educational purposes only. Data sourced from
        SEC EDGAR, Yahoo Finance, company filings (10-K, 10-Q), and public sources via Claude AI
        web search. This is NOT investment advice, a buy/sell recommendation, or advice from a
        registered investment adviser under the Investment Advisers Act of 1940. AI can make
        errors — verify all numbers independently before acting. Past performance does not
        guarantee future results. Investing involves risk, including possible loss of principal.
        Consult a licensed financial advisor before making any investment decision.
      </div>
    </div>
  );
}

// ─── Main Dashboard ───────────────────────────────────────────────────────────

const TABS = ['Overview', 'Valuation', 'Growth', 'Health', 'Returns', 'Peers', 'Ownership', 'View'];

export default function Dashboard({ data }) {
  const [activeTab, setActiveTab] = useState(7);
  const { responseMeta } = useApp();

  // Format fetchedAt epoch to human-readable
  function formatFetchedAt(epochMs) {
    if (!epochMs) return null;
    const d = new Date(epochMs);
    const now = new Date();
    const diffMs = now - d;
    const diffMin = Math.floor(diffMs / 60000);
    if (diffMin < 1) return 'just now';
    if (diffMin < 60) return `${diffMin} min ago`;
    const diffHr = Math.floor(diffMin / 60);
    if (diffHr < 24) return `${diffHr}h ago`;
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  const isLive = !responseMeta || responseMeta.dataSource === 'LIVE';
  const fetchedLabel = responseMeta ? formatFetchedAt(responseMeta.fetchedAt) : null;
  const asOfLabel = responseMeta?.asOfDate ? `Historical · As of ${responseMeta.asOfDate}` : null;

  const renderTab = () => {
    switch (activeTab) {
      case 0: return <TabOverview d={data} />;
      case 1: return <TabValuation d={data} />;
      case 2: return <TabGrowth d={data} />;
      case 3: return <TabHealth d={data} />;
      case 4: return <TabReturns d={data} />;
      case 5: return <TabPeers d={data} />;
      case 6: return <TabOwnership d={data} />;
      case 7: return <TabView d={data} />;
      default: return <TabView d={data} />;
    }
  };

  return (
    <div className="dashboard-wrapper">
      {/* Data freshness banner */}
      <div className={`data-freshness-banner ${isLive ? 'freshness-live' : 'freshness-cached'}`}>
        <span className="freshness-icon">{asOfLabel ? '📆' : isLive ? '🟢' : '🕐'}</span>
        <span className="freshness-label">
          {asOfLabel
            ? asOfLabel
            : isLive
              ? `Live Data · Fetched ${fetchedLabel || 'just now'}`
              : `Cached Data · Fetched ${fetchedLabel || 'recently'} (served from cache)`
          }
        </span>
        {data.priceDate && (
          <span className="freshness-price-date">Price date: {data.priceDate}</span>
        )}
      </div>

      {/* Company header */}
      <div className="company-header">
        <div className="company-info">
          <h2 className="company-name">{data.company}</h2>
          <div className="company-badges">
            <Badge text={data.ticker} type="blue" />
            <Badge text={data.exchange} type="blue" />
            <Badge text={data.subIndustry || data.sector} type="gray" />
          </div>
        </div>
        <div className="company-price">
          <span className="price-value">${data.price}</span>
          <span className="price-cap">Mkt Cap: {data.marketCap}</span>
        </div>
      </div>

      {/* Tab bar */}
      <div className="tab-bar">
        {TABS.map((t, i) => (
          <button
            key={i}
            className={`tab-btn ${activeTab === i ? 'active' : ''}`}
            onClick={() => setActiveTab(i)}
          >
            {t}
          </button>
        ))}
      </div>

      {/* Tab content */}
      <div className="tab-panel">
        {renderTab()}
      </div>
    </div>
  );
}
