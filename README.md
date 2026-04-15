# US Stock Fundamental Analyser
### React Mobile App + Spring Boot Backend + Anthropic Claude AI

---

## Architecture

```
Browser (React PWA)
        │  HTTP  (port 80 in prod, 3000 in dev)
        ▼
  nginx (Docker)  ──proxy /api/──►  Spring Boot (port 8080)
                                            │
                                   Anthropic Claude API
                                   (claude-opus-4-6 + web_search)
```

| Layer | Tech | Notes |
|-------|------|-------|
| Frontend | React 18 · mobile-first CSS | 8-tab dashboard, no UI library |
| Backend | Spring Boot 3.2 · Java 21 | REST API, rate limiting, 1-hr cache |
| AI | Anthropic `claude-opus-4-6` | Web search tool enabled |
| Infra | Docker Compose · nginx | Single `docker compose up` deploy |

---

## Quick Start (Docker – Recommended)

### Prerequisites
- Docker Desktop installed
- An Anthropic API key → [console.anthropic.com](https://console.anthropic.com)

### Steps

```bash
# 1. Clone / download the project
cd stock-analyzer

# 2. Create your .env file
cp .env.example .env
# Edit .env and set:  ANTHROPIC_API_KEY=sk-ant-xxxxxxxx

# 3. Build and start everything
docker compose up --build

# 4. Open http://localhost  →  stock analyser is live!
```

---

## Local Development (without Docker)

### Backend (Spring Boot)

```bash
cd backend

# Set env var
export ANTHROPIC_API_KEY=sk-ant-xxxxxxxx

# Run
./mvnw spring-boot:run
# → listening on http://localhost:8080
```

### Frontend (React)

```bash
cd frontend

# Install deps
npm install

# Create local env
cp .env.example .env.local
# Set REACT_APP_API_BASE_URL=http://localhost:8080
# (or leave blank – package.json proxy handles it)

# Start
npm start
# → http://localhost:3000
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
| `ANTHROPIC_API_KEY` | ✅ | — | Your Anthropic API key |
| `ALLOWED_ORIGINS` | ❌ | `http://localhost:3000` | CORS allowed origins |
| `SPRING_PROFILES_ACTIVE` | ❌ | default | Set to `prod` in production |

---

## Production Deployment Notes

1. **API Key Security** — The `ANTHROPIC_API_KEY` is stored only in the Spring Boot backend. The React frontend never touches the key.

2. **HTTPS** — In production, put a TLS-terminating load balancer (AWS ALB, Cloudflare, Caddy) in front of nginx.

3. **Caching** — Results are cached in-memory for 1 hour. For a multi-instance deployment, replace with Redis:
   ```properties
   spring.cache.type=redis
   spring.data.redis.host=your-redis-host
   ```

4. **Rate Limiting** — Currently 10 rpm per IP (in-memory). For production scale, use [bucket4j](https://github.com/bucket4j/bucket4j) with Redis.

5. **Model** — Uses `claude-opus-4-6` by default. Change `anthropic.model` in `application.properties` for faster/cheaper analysis (`claude-sonnet-4-6`).

---

## Disclaimer

This tool is for fundamental screening and financial education only. Not investment advice. Not a buy/sell recommendation. Not advice from a registered investment adviser under the Investment Advisers Act of 1940. AI can make errors — verify all data independently. Consult a licensed financial advisor before investing.
