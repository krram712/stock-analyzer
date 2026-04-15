/**
 * Stock Analyser API Service
 * All calls go through the Spring Boot backend, which proxies to Anthropic.
 */

const BASE_URL = process.env.REACT_APP_API_BASE_URL || '';

const DEFAULT_TIMEOUT_MS = 130_000; // 130 s (backend has 120 s Anthropic timeout)

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
 * @param {string} ticker   e.g. "NVDA"
 * @param {string} horizon  e.g. "5 years"
 * @returns {Promise<Object>} The full analysis data object
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
    throw new Error(err.message || 'Network error — check your connection and try again.');
  }

  let json;
  try {
    json = await res.json();
  } catch {
    throw new Error('Server returned an unexpected response. Please try again.');
  }

  if (!res.ok || !json.success) {
    throw new Error(json.message || `Server error ${res.status}. Please try again.`);
  }

  return json.data; // the parsed stock analysis object
}

/**
 * Fetch the curated list of popular tickers for the search UI.
 * @returns {Promise<string[]>}
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
