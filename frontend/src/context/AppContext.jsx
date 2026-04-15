import React, { createContext, useContext, useState, useCallback } from 'react';
import { analyseStock } from '../api/stockApi';

const AppContext = createContext(null);

export function AppProvider({ children }) {
  const [screen, setScreen] = useState('search');       // 'search' | 'loading' | 'dashboard' | 'error'
  const [analysisData, setAnalysisData] = useState(null);
  const [errorMsg, setErrorMsg] = useState('');
  const [loadingStage, setLoadingStage] = useState(0);  // 0-4 progress stages
  const [lastQuery, setLastQuery] = useState(null);

  const LOADING_STAGES = [
    'Verifying listing on NYSE / NASDAQ…',
    'Fetching live financial data & filings…',
    'Running sector-specific analysis…',
    'Comparing peers & ownership signals…',
    'Generating investment view…',
  ];

  const startAnalysis = useCallback(async (ticker, horizon) => {
    setScreen('loading');
    setLoadingStage(0);
    setErrorMsg('');
    setLastQuery({ ticker, horizon });

    // Cycle through loading stage messages while waiting
    const interval = setInterval(() => {
      setLoadingStage(prev => (prev < LOADING_STAGES.length - 1 ? prev + 1 : prev));
    }, 12_000);

    try {
      const data = await analyseStock(ticker, horizon);
      clearInterval(interval);
      setAnalysisData(data);
      setScreen('dashboard');
    } catch (err) {
      clearInterval(interval);
      setErrorMsg(err.message || 'An unexpected error occurred.');
      setScreen('error');
    }
  }, []);

  const goBack = useCallback(() => {
    setScreen('search');
    setAnalysisData(null);
    setErrorMsg('');
  }, []);

  const retry = useCallback(() => {
    if (lastQuery) startAnalysis(lastQuery.ticker, lastQuery.horizon);
  }, [lastQuery, startAnalysis]);

  return (
    <AppContext.Provider value={{
      screen,
      analysisData,
      errorMsg,
      loadingStage,
      loadingStages: LOADING_STAGES,
      startAnalysis,
      goBack,
      retry,
      lastQuery,
    }}>
      {children}
    </AppContext.Provider>
  );
}

export function useApp() {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error('useApp must be used within AppProvider');
  return ctx;
}
