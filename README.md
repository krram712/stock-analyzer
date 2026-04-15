# US Stock Fundamental Analyser
### React Mobile App + Spring Boot Backend + Google Gemini AI (Free Tier)

---

## Architecture

```
Browser (React PWA)
        │  HTTP  (port 80 in prod, 3000 in dev)
        ▼
  nginx (Docker)  ──proxy /api/──►  Spring Boot (port 8080)
                                            │
                                   Google Gemini API
                                   (gemini-1.5-flash + Google Search grounding)
```

| Layer | Tech | Notes |
|-------|------|-------|
| Frontend | React 18 · mobile-first CSS | 8-tab dashboard, no UI library |
| Backend | Spring Boot 3.3 · Java 17 | REST API, rate limiting, 1-hr cache |
| AI | Google `gemini-1.5-flash` | Google Search grounding enabled |
| Infra | Docker Compose · nginx | Single `docker compose up` deploy |

---

## Quick Start (Docker – Recommended)

### Prerequisites
- Docker Desktop installed
- A free Gemini API key → [aistudio.google.com/apikey](https://aistudio.google.com/apikey)

### Steps

```bash
# 1. Clone / download the project
cd stock-analyzer

# 2. Create your .env file
cp .env.example .env
# Edit .env and set:  GEMINI_API_KEY=AIza...

# 3. Build and start everything
docker compose up --build

# 4. Open http://localhost  →  stock analyser is live!
```

---

## Local Development (without Docker)

### Backend (Spring Boot)

```bash
cd backend

# Set env var (Windows PowerShell)
$env:GEMINI_API_KEY="AIza..."

# Run pre-built JAR
java -jar target/stock-analyzer-backend-1.0.0.jar

# OR build and run via Maven
./mvnw spring-boot:run
# → listening on http://localhost:8080
```

### Frontend (React)

```bash
cd frontend

# Install deps
npm install

# Start
npm start
# → http://localhost:3000  (proxies /api/* to backend automatically)
```

---

## API Reference

### `POST /api/v1/analyze`

**Request:**
```json
{
  "ticker": "NVDA",
  "horizon": "5 years"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Analysis completed successfully",
  "data": { /* full stock analysis JSON */ },
  "processingTimeMs": 45230,
  "ticker": "NVDA",
  "horizon": "5 years"
}
```

### `GET /api/v1/health`
Returns `{ "status": "UP" }`.

### `GET /api/v1/tickers/popular`
Returns a curated list of popular tickers for the search UI.

---

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GEMINI_API_KEY` | ✅ | — | Free Gemini API key from [aistudio.google.com](https://aistudio.google.com/apikey) |
| `GEMINI_MODEL` | ❌ | `gemini-1.5-flash` | Gemini model to use |
| `ALLOWED_ORIGINS` | ❌ | `http://localhost:3000` | CORS allowed origins |
| `SPRING_PROFILES_ACTIVE` | ❌ | default | Set to `prod` in production |

---

## Production Deployment Notes

1. **API Key Security** — The `GEMINI_API_KEY` is stored only in the Spring Boot backend. The React frontend never touches the key.

2. **HTTPS** — In production, put a TLS-terminating load balancer (AWS ALB, Cloudflare, Caddy) in front of nginx.

3. **Caching** — Results are cached in-memory for 1 hour. For a multi-instance deployment, replace with Redis:
   ```properties
   spring.cache.type=redis
   spring.data.redis.host=your-redis-host
   ```

4. **Rate Limiting** — Currently 10 rpm per IP (in-memory). For production scale, use [bucket4j](https://github.com/bucket4j/bucket4j) with Redis.

5. **Model** — Uses `gemini-1.5-flash` by default (free tier: 15 RPM / 1,500 RPD). For higher quality, set `GEMINI_MODEL=gemini-1.5-pro` (paid tier).

---

## Gemini Free Tier Limits

| Model | Requests/Min | Requests/Day | Cost |
|-------|-------------|-------------|------|
| `gemini-1.5-flash` | 15 | 1,500 | **Free** |
| `gemini-1.5-pro` | 2 | 50 | Free (low limits) |

Get your key at **https://aistudio.google.com/apikey** — no credit card required.

---

## Changelog

### v1.1.0 — April 15, 2026
- **Switched AI provider from Anthropic Claude → Google Gemini** (free tier)
- Added `GeminiClient.java` with Google Search grounding support (replaces Claude web-search tool)
- Renamed `anthropicWebClient` bean → `geminiWebClient` in `WebClientConfig`
- Replaced all `anthropic.*` properties with `gemini.*` in `application.properties`
- Updated `StockAnalysisService` to inject `GeminiClient`
- Upgraded Spring Boot `3.2.5` → `3.3.6`
- Downgraded Java target `21` → `17` to match local Maven JDK (JDK 17)
- Updated `.env` template: `ANTHROPIC_API_KEY` → `GEMINI_API_KEY`

### v1.0.0 — April 15, 2026
- Initial release
- React 18 mobile-first frontend with 8-tab dashboard
- Spring Boot 3.2 backend with Anthropic Claude (claude-haiku-4-5) + web-search tool
- Docker Compose single-command deployment (nginx + Spring Boot)
- In-memory cache (1 hr TTL), per-IP rate limiting (10 rpm)
- Pushed to GitHub: [github.com/krram712/stock-analyzer](https://github.com/krram712/stock-analyzer)

---

## Disclaimer

This tool is for fundamental screening and financial education only. Not investment advice. Not a buy/sell recommendation. Not advice from a registered investment adviser under the Investment Advisers Act of 1940. AI can make errors — verify all data independently. Consult a licensed financial advisor before investing.
