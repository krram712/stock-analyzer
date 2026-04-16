import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { analyseStock, warmUpBackend } from '../api/stockApi';

const AppContext = createContext(null);

export function AppProvider({ children }) {
  const [screen, setScreen] = useState('search');       // 'search' | 'loading' | 'dashboard' | 'error'
  const [analysisData, setAnalysisData] = useState(null);
  const [responseMeta, setResponseMeta] = useState(null); // { dataSource, fetchedAt, asOfDate }
  const [errorMsg, setErrorMsg] = useState('');
  const [loadingStage, setLoadingStage] = useState(0);  // 0-4 progress stages
  const [lastQuery, setLastQuery] = useState(null);

  const LOADING_STAGES = [
    'Waking up backend service…',
    'Fetching live financial data & filings…',
    'Running sector-specific analysis…',
    'Comparing peers & ownership signals…',
    'Generating investment view…',
  ];

  // Warm up the Railway backend as soon as the app loads so the JVM is ready
  useEffect(() => { warmUpBackend(); }, []);

  const startAnalysis = useCallback(async (ticker, horizon, asOfDate = null) => {
    setScreen('loading');
    setLoadingStage(0);
    setErrorMsg('');
    setLastQuery({ ticker, horizon, asOfDate });

    // Cycle through loading stage messages while waiting
    const interval = setInterval(() => {
      setLoadingStage(prev => (prev < LOADING_STAGES.length - 1 ? prev + 1 : prev));
    }, 12_000);

    try {
      const result = await analyseStock(ticker, horizon, asOfDate);
      clearInterval(interval);
      setAnalysisData(result.data);
      setResponseMeta({
        dataSource: result.dataSource,
        fetchedAt: result.fetchedAt,
        asOfDate: result.asOfDate,
        processingTimeMs: result.processingTimeMs,
      });
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
    setResponseMeta(null);
    setErrorMsg('');
  }, []);

  const retry = useCallback(() => {
    if (lastQuery) startAnalysis(lastQuery.ticker, lastQuery.horizon, lastQuery.asOfDate);
  }, [lastQuery, startAnalysis]);

  return (
    <AppContext.Provider value={{
      screen,
      analysisData,
      responseMeta,
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
