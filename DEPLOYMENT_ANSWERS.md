# MSA Detector — Frontend Integration Answers

> Responses to every question in `FRONTEND_INTEGRATION_README.md`.

---

## 🆕 Backend Answers Acknowledged (2026-03-19)

The backend team has confirmed all integration items (§1–§15) are resolved.
Key updates applied below.

### Backend directory

The backend repo is **`../dissertation`** (not `../dissertation_backend`).
All paths in `docker-compose.yml` have been corrected.

### §14 — Startup Ordering (confirmed by backend)

The backend's primary `docker-compose.yml` implements a three-stage startup
chain with health-check gating:

```
postgres (healthy) → backend (healthy) → frontend
```

The frontend's `depends_on: backend: condition: service_healthy` is used in
both the backend's primary compose file and in this repo's standalone compose.

### §15 — Nginx DNS Resilience (confirmed by backend)

Frontend `nginx.conf` uses `resolver 127.0.0.11` + variable-based
`proxy_pass` — the backend team has acknowledged this and it is compatible
with their Compose network configuration.

---

## Deployment Architecture (full picture)

The **backend repo** (`../dissertation`) owns the primary `docker-compose.yml`
that orchestrates all three services: postgres, backend, and frontend.

```
                        docker-compose up --build
                        (run from ../dissertation)
                                 │
                    ┌────────────┼────────────┐
                    ▼            ▼             │
              ┌──────────┐ ┌──────────┐       │
              │ postgres │ │  (build) │       │
              │ :5432    │ │ backend  │       │
              └────┬─────┘ │ frontend │       │
                   │       └──────────┘       │
          healthy? │                          │
              ┌────▼─────┐                    │
              │ backend  │                    │
              │ :8080    │                    │
              └────┬─────┘                    │
                   │ /actuator/health OK      │
          healthy? │                          │
              ┌────▼─────┐                    │
              │ frontend │                    │
              │ :80 Nginx│                    │
              └────┬─────┘                    │
                   │                          │
            ┌──────┴──────┐                   │
            │  Browser    │                   │
            │  /          │ ← SPA             │
            │  /api/*     │ ← proxy → :8080   │
            └─────────────┘                   │
```

- **Startup chain**: postgres (healthy) → backend (healthy) → frontend
- **Only port 80** is exposed to the host in production. Backend 8080 is internal.
- Nginx uses `resolver 127.0.0.11` + variable-based `proxy_pass` for DNS resilience.

### Which docker-compose to use?

| Compose file | Location | Use case |
|---|---|---|
| **Primary** (backend's) | `../dissertation/docker-compose.yml` | Full stack: postgres + backend + frontend. **Use this for deployment.** |
| **Standalone** (this repo) | `./docker-compose.yml` | Frontend-only testing against the backend (no postgres — backend must manage its own DB). |

---

## How to Run

### Production (recommended — from backend repo)
```bash
cd ../dissertation
docker-compose up --build
# → http://localhost   (Nginx on :80, proxies /api to backend :8080)
```

### Standalone frontend (from this repo)
```bash
docker-compose up --build
# Expects ../dissertation to have a Dockerfile
# → http://localhost
```

### Development (no Docker, hot-reload)
```bash
npm install
npm start
# → http://localhost:4200  (expects backend on http://localhost:8080)
```

---

## ✅ Original Answers (sections 1–8)

| Question | Answer |
|----------|--------|
| **Build output directory** | `dist/msadetector-frontend/browser/` (Angular 19 application builder outputs to a `browser/` subfolder) |
| **Build command** | `npm run build -- --configuration production` |
| **Node.js version** | **20 LTS** |
| **Package manager** | **npm** |

---

## 2. Routing & Base Href

| Question | Answer |
|----------|--------|
| **HTML5 routing used?** | **Yes** — uses Angular `provideRouter()` with `PathLocationStrategy` (default). Nginx must fall back to `index.html`. |
| **`<base href>`** | `/` |

---

## 3. API Proxy / CORS Configuration

| Question | Answer |
|----------|--------|
| **API base URL (dev)** | `http://localhost:8080/api` (hardcoded in `environment.ts`) |
| **API base URL (prod)** | `/api` (relative — via `environment.prod.ts`). Nginx proxies `/api` → `http://backend:8080/api`. |
| **Configurable `environment.ts`?** | **Yes** — `environment.ts` (dev) and `environment.prod.ts` (prod) with Angular file replacements in `angular.json`. |
| **WebSocket / SSE?** | **No** — the frontend polls `GET /api/jobs/{id}` for job progress; no WebSocket or SSE connections. |

---

## 4. Authentication / Token Handling

| Question | Answer |
|----------|--------|
| **JWT tokens?** | **None** — there is no authentication in the frontend currently. No `Authorization` header is sent. |
| **Token storage?** | N/A |
| **Auth routes?** | N/A |

---

## 5. Static Assets & File Upload

| Question | Answer |
|----------|--------|
| **Max upload size in UI** | No client-side limit enforced. The Nginx config sets `client_max_body_size 500m` to match the backend's 500 MB limit. |
| **Assets from backend?** | **No** — the frontend does not request any static files from the backend; all data is JSON via the API. |

---

## 6. Deployment Topology

**→ Option A — Nginx serves the SPA, proxies `/api` to backend.**

```
Browser ──► Nginx (:80)
               ├── static files → /usr/share/nginx/html (Angular build)
               └── /api/*       → http://backend:8080    (Spring Boot)
```

All files are provided:
- `Dockerfile` — multi-stage build (Node 20 → Nginx Alpine)
- `nginx.conf` — SPA fallback + `/api` reverse proxy
- `docker-compose.yml` — orchestrates frontend + backend

---

## 7. Environment Variables / Runtime Config

| Mechanism | Details |
|-----------|---------|
| **Build-time file replacement** | `environment.ts` → `environment.prod.ts` via Angular's `fileReplacements` in `angular.json`. |
| **Runtime config?** | Not currently used. If needed, a `config.json` or `window.__env` approach can be added later. |

For now a single Docker image works for any environment where the backend is reachable as the Docker Compose service named `backend`.

---

## 8. Repository & Integration

| Question | Answer |
|----------|--------|
| **Frontend repo** | This repository (`dissertation_frontend/`). |
| **Backend repo** | Sibling directory (`../dissertation/`). |
| **Same repo as backend?** | **No** — separate repositories, sibling directories. |
| **Primary compose file** | Lives in the backend repo (`../dissertation/docker-compose.yml`) — includes postgres, backend, and frontend. |
| **CI/CD** | No preference yet. GitHub Actions recommended. |

---

## Summary Checklist

- [x] **Build output directory** — `dist/msadetector-frontend/browser/`
- [x] **Build command + Node version** — `npm run build -- --configuration production` · Node 20
- [x] **Base href** — `/`
- [x] **API base URL strategy** — Relative `/api` in production, Nginx reverse proxy (Option A)
- [x] **Auth token transport** — None (no auth)
- [x] **Deployment topology** — Option A (Nginx SPA + proxy)
- [x] **Runtime config mechanism** — Build-time `fileReplacements`
- [x] **Frontend repo** — `dissertation_frontend/` (separate repo)
- [x] **Nginx `proxy_pass` target** — `http://backend:8080` (matches Compose service name)
- [x] **Upload size (`client_max_body_size`)** — `500m` (matches backend 500 MB)
- [x] **Docker HEALTHCHECK** — `wget -qO- http://localhost:80/ || exit 1`
- [x] **`.dockerignore`** — `node_modules`, `dist`, `.angular`, `.git` all excluded
- [x] **`environment.prod.ts` apiUrl** — `/api` (relative, proxied by Nginx)
- [x] **§14 Startup ordering** — postgres → backend → frontend, all health-check gated
- [x] **§15 Nginx DNS resilience** — `resolver 127.0.0.11` + variable-based `proxy_pass`
- [x] **Backend directory** — `../dissertation` (corrected from `../dissertation_backend`)

---

## Files Created / Modified for Deployment

| File | Purpose |
|------|---------|
| `src/environments/environment.prod.ts` | Production config — API calls go to relative `/api` |
| `nginx.conf` | Nginx config — SPA routing + `/api` reverse proxy + `client_max_body_size 500m` |
| `Dockerfile` | Multi-stage build: Node 20 → Nginx Alpine + `HEALTHCHECK` |
| `.dockerignore` | Excludes `node_modules`, `dist`, `.angular`, `.git`, etc. |
| `docker-compose.yml` | Orchestrates frontend + backend services |

## Quick Start

```bash
# ─── Production (full stack — from backend repo) ───
cd ../dissertation
docker-compose up --build
# → http://localhost   (postgres → backend → frontend, all automated)

# ─── Standalone frontend (from this repo) ───
docker-compose up --build
# → http://localhost   (builds backend from ../dissertation)

# ─── Development (no Docker, hot-reload) ───
npm install
npm start
# → http://localhost:4200  (expects backend running on http://localhost:8080)


# ─── Useful commands ───
docker-compose up --build -d      # detached mode (background)
docker-compose logs -f frontend   # tail frontend logs
docker-compose logs -f backend    # tail backend logs
docker-compose down               # stop everything
docker-compose down -v            # stop + remove volumes
```