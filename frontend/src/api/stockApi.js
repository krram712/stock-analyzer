/**
 * Stock Analyser API Service
 * All calls go through the Spring Boot backend, which proxies to Gemini.
 *
 * In production (Vercel): relative /api/* calls are rewritten by vercel.json
 *   → https://stock-analyzer-production-736a.up.railway.app/api/*
 * In local dev: CRA's "proxy" in package.json forwards to http://localhost:8080
 */

// Call Railway directly from the browser — bypasses Vercel's ~30s proxy timeout.
// CORS on the Railway backend already allows https://*.vercel.app.
// vercel.json build.env sets REACT_APP_API_BASE_URL to the full https:// URL;
// if that env var is missing or wrong (no protocol), we fall back to the hardcoded URL.
const _env = (process.env.REACT_APP_API_BASE_URL || '').trim().replace(/\/$/, '');
const BASE_URL = /^https?:\/\//i.test(_env)
  ? _env
  : 'https://stock-analyzer-production-736a.up.railway.app';

const DEFAULT_TIMEOUT_MS = 180_000; // 180s — matches backend Gemini timeout
const COLD_START_WAIT_MS  = 12_000; // wait 12s for Railway JVM cold-start
const MAX_502_RETRIES     = 2;

// ─────────────────────────────────────────────────────────────────────────────

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));

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
 * Fire-and-forget health ping to wake up the Railway backend on page load.
 * Prevents the first real request from hitting a cold-start 502.
 */
export async function warmUpBackend() {
  try {
    await fetch(`${BASE_URL}/api/v1/health`);
  } catch {
    // Intentionally silent — just waking up the JVM
  }
}

/**
 * Analyse a stock ticker with the given investment horizon.
 * Automatically retries up to MAX_502_RETRIES times on 502 (Railway cold start).
 */
export async function analyseStock(ticker, horizon, _retry = 0) {
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

  // 502 = Railway backend is cold-starting — wait and retry automatically
  if (res.status === 502 && _retry < MAX_502_RETRIES) {
    await sleep(COLD_START_WAIT_MS);
    return analyseStock(ticker, horizon, _retry + 1);
  }

  // Detect HTML response (wrong URL / misconfiguration)
  const contentType = res.headers.get('content-type') || '';
  if (contentType.includes('text/html')) {
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
