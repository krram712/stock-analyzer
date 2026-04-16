import React, { useState, useEffect } from 'react';
import { useApp } from '../context/AppContext';
import { fetchPopularTickers } from '../api/stockApi';

const HORIZONS = ['1 year', '3 years', '5 years', '7 years', '10 years'];

export default function SearchScreen() {
  const { startAnalysis } = useApp();
  const [ticker, setTicker] = useState('');
  const [horizon, setHorizon] = useState('5 years');
  const [customHorizon, setCustomHorizon] = useState('');
  const [showCustom, setShowCustom] = useState(false);
  const [popularTickers, setPopularTickers] = useState([]);
  const [validationError, setValidationError] = useState('');
  const [asOfDate, setAsOfDate] = useState('');
  const [showDatePicker, setShowDatePicker] = useState(false);

  useEffect(() => {
    fetchPopularTickers().then(setPopularTickers);
  }, []);

  const effectiveHorizon = showCustom ? customHorizon : horizon;
  const today = new Date().toISOString().split('T')[0];

  function validate() {
    if (!ticker.trim()) { setValidationError('Please enter a stock ticker.'); return false; }
    if (!/^[A-Za-z.\-]{1,10}$/.test(ticker.trim())) {
      setValidationError('Ticker must be 1-10 letters (e.g. AAPL, BRK.B).');
      return false;
    }
    if (!effectiveHorizon.trim()) { setValidationError('Please specify an investment horizon.'); return false; }
    if (asOfDate && !/^\d{4}-\d{2}-\d{2}$/.test(asOfDate)) {
      setValidationError('Date must be in YYYY-MM-DD format.');
      return false;
    }
    setValidationError('');
    return true;
  }

  function handleSubmit(e) {
    e.preventDefault();
    if (validate()) startAnalysis(ticker.trim(), effectiveHorizon.trim(), asOfDate || null);
  }

  function handlePopularTicker(t) {
    setTicker(t);
    setValidationError('');
  }

  return (
    <div className="search-screen">
      <div className="search-header">
        <div className="logo-icon">&#x1F4CA;</div>
        <h1 className="app-title">Stock Fundamental Analyser</h1>
        <p className="app-subtitle">AI-powered fundamental analysis for long-term investors</p>
        <div className="powered-badge">Powered by AI · Live & Historical Data</div>
      </div>

      <form className="search-form" onSubmit={handleSubmit}>

        {/* Ticker */}
        <div className="form-group">
          <label className="form-label">Stock Ticker</label>
          <input
            type="text"
            className="form-input ticker-input"
            placeholder="e.g. NVDA, AAPL, MSFT, JPM"
            value={ticker}
            onChange={e => { setTicker(e.target.value.toUpperCase()); setValidationError(''); }}
            maxLength={10}
            autoCapitalize="characters"
            autoComplete="off"
            spellCheck="false"
          />
        </div>

        {/* Popular tickers */}
        {popularTickers.length > 0 && (
          <div className="popular-tickers">
            {popularTickers.map(t => (
              <button
                key={t}
                type="button"
                className={`ticker-chip ${ticker === t ? 'active' : ''}`}
                onClick={() => handlePopularTicker(t)}
              >
                {t}
              </button>
            ))}
          </div>
        )}

        {/* Horizon */}
        <div className="form-group">
          <label className="form-label">Investment Horizon</label>
          <div className="horizon-options">
            {HORIZONS.map(h => (
              <button
                key={h}
                type="button"
                className={`horizon-chip ${!showCustom && horizon === h ? 'active' : ''}`}
                onClick={() => { setHorizon(h); setShowCustom(false); }}
              >
                {h}
              </button>
            ))}
            <button
              type="button"
              className={`horizon-chip ${showCustom ? 'active' : ''}`}
              onClick={() => setShowCustom(true)}
            >
              Custom
            </button>
          </div>
          {showCustom && (
            <input
              type="text"
              className="form-input"
              placeholder="e.g. 15 years, 18 months"
              value={customHorizon}
              onChange={e => setCustomHorizon(e.target.value)}
              autoFocus
            />
          )}
        </div>

        {/* Data Date */}
        <div className="form-group">
          <label className="form-label">Data Date</label>
          <div className="horizon-options">
            <button
              type="button"
              className={`horizon-chip ${!showDatePicker ? 'active' : ''}`}
              onClick={() => { setShowDatePicker(false); setAsOfDate(''); }}
            >
              Live / Latest
            </button>
            <button
              type="button"
              className={`horizon-chip ${showDatePicker ? 'active' : ''}`}
              onClick={() => setShowDatePicker(true)}
            >
              Specific Date
            </button>
          </div>
          {!showDatePicker && (
            <div className="date-hint">Fetches the most current available data</div>
          )}
          {showDatePicker && (
            <input
              type="date"
              className="form-input"
              value={asOfDate}
              max={today}
              min="2010-01-01"
              onChange={e => setAsOfDate(e.target.value)}
              style={{ marginTop: '4px' }}
            />
          )}
          {showDatePicker && asOfDate && (
            <div className="date-hint">Data as of: <strong>{asOfDate}</strong></div>
          )}
        </div>

        {validationError && (
          <div className="validation-error">{validationError}</div>
        )}

        <button type="submit" className="analyse-btn">
          Analyse Stock
        </button>
      </form>

      <div className="search-footer">
        <p>Data sourced from SEC EDGAR, Yahoo Finance &amp; company filings.</p>
        <p>For educational purposes only. Not investment advice.</p>
      </div>
    </div>
  );
}
