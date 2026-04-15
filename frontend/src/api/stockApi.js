/**
 * Stock Analyser API Service
 * All calls go through the Spring Boot backend, which proxies to Gemini.
 *
 * In production (Vercel): relative /api/* calls are rewritten by vercel.json
 *   → https://stock-analyzer-production-736a.up.railway.app/api/*
 * In local dev: CRA's "proxy" in package.json forwards to http://localhost:8080
 */

const BASE_URL = '';

const DEFAULT_TIMEOUT_MS = 180_000; // 180s — matches backend Gemini timeout

// ─────────────────────────────────────────────────────────────────────────────

async function fetchWithTimeout(url, options = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), DEFAULT_TIMEOUT_MS);

  try {
    const res = await fetch(url, { ...options, signal: controller.signal });
    clearTimeout(timeoutId);
    return res;
  } catch (err) {
    clearTimeout(timeoutId);
    if (err.name === 'AbortError') {
      throw new Error('Request timed out. The analysis is taking longer than expected. Please try again.');
    }
    throw err;
  }
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Analyse a stock ticker with the given investment horizon.
 */
export async function analyseStock(ticker, horizon) {
  const url = `${BASE_URL}/api/v1/analyze`;

  let res;
  try {
    res = await fetchWithTimeout(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ticker: ticker.trim().toUpperCase(), horizon }),
    });
  } catch (err) {
    if (err.message.includes('timed out')) throw err;
    throw new Error('Cannot reach the backend server. Check your connection and try again.');
  }

  // Detect HTML response (backend not configured / wrong URL)
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('text/html')) {
    if (!BASE_URL) {
      throw new Error(
        'Backend URL not configured. Set REACT_APP_API_BASE_URL in Vercel environment variables to your Railway backend URL.'
      );
    }
    throw new Error(`Backend returned HTML instead of JSON (status ${res.status}). Check the backend URL configuration.`);
  }

  let json;
  try {
    json = await res.json();
  } catch {
    throw new Error(`Server returned an unexpected response (status ${res.status}). Please try again.`);
  }

  if (!res.ok || !json.success) {
    throw new Error(json.message || `Server error ${res.status}. Please try again.`);
  }

  return json.data;
}

/**
 * Fetch the curated list of popular tickers for the search UI.
 */
export async function fetchPopularTickers() {
  try {
    const res = await fetch(`${BASE_URL}/api/v1/tickers/popular`);
    if (!res.ok) return [];
    const json = await res.json();
    return json.tickers || [];
  } catch {
    return ['AAPL', 'MSFT', 'NVDA', 'GOOGL', 'AMZN', 'META', 'TSLA', 'JPM'];
  }
}


