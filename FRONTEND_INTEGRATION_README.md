# MSA Detector — Frontend Integration

> Living document for backend ↔ frontend integration.
> **Frontend answers**: see `DEPLOYMENT_ANSWERS.md`.

---

## ✅ Integration Status — All Questions Resolved

All questions (§1–§16) have been answered by the frontend team and applied
to the backend Docker Compose configuration.

| # | Topic | Resolution |
|---|-------|------------|
| 1 | Build artefact | `dist/msadetector-frontend/browser/`, `npm run build`, Node 20 |
| 2 | Routing | HTML5 routing, `<base href="/">` — Nginx falls back to `index.html` |
| 3 | API proxy | Relative `/api` in prod, Nginx reverse proxy (Option A) |
| 4 | Auth / JWT | None currently — `permitAll()` on backend, no token sent |
| 5 | Static assets | No backend-served assets; upload limit aligned at 500 MB |
| 6 | Topology | **Option A** — Nginx SPA + `/api` proxy |
| 7 | Runtime config | Build-time `fileReplacements` in `angular.json` |
| 8 | Repository | Separate repos — backend: `dissertation/`, frontend: `dissertation_frontend/` (siblings). Primary compose lives in the backend repo. |
| 9 | Nginx `proxy_pass` | ✅ Confirmed — `http://backend:8080` matches Compose service name |
| 10 | Upload size | ✅ Confirmed — `client_max_body_size 500m` in `location /api/` |
| 11 | Health check | ✅ Added — `wget -qO- http://localhost:80/`; used in `depends_on: condition: service_healthy` |
| 12 | `.dockerignore` | ✅ Confirmed — `node_modules`, `dist`, `.angular`, `.git` excluded |
| 13 | `environment.prod.ts` | ✅ Confirmed — `apiUrl: '/api'` (relative, proxied by Nginx) |
| 14 | Startup ordering | ✅ Backend healthcheck (`/actuator/health`) gates frontend startup in both dev and prod compose |
| 15 | Nginx DNS resilience | ✅ Frontend uses `resolver 127.0.0.11` + variable `proxy_pass` — tolerates backend DNS delays |
| 16 | Directory name | ✅ Corrected — backend is `../dissertation` (not `../dissertation_backend`). Frontend build context in compose: `../dissertation_frontend`. |

> **Note for frontend**: `DEPLOYMENT_ANSWERS.md` still contains three
> references to `../dissertation_backend` (lines ~60, ~68, ~290). These
> should be updated to `../dissertation` for consistency.

---

## Deployment Architecture

```
                        docker-compose up --build
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
- **Only port 80** is exposed to the host (production). Backend 8080 is internal.
- Nginx uses `resolver 127.0.0.11` + variable-based `proxy_pass` for DNS resilience.

### Directory Layout

```
parent/
  dissertation/              ← backend repo (this repo)
    docker-compose.yml       ← primary compose (full stack)
    docker-compose.prod.yml  ← production compose
    Dockerfile               ← Spring Boot multi-stage build
  dissertation_frontend/     ← frontend repo
    docker-compose.yml       ← standalone frontend compose
    Dockerfile               ← Angular multi-stage build (Node → Nginx)
    nginx.conf               ← SPA fallback + /api proxy
```

### Which Compose File to Use

| Compose file | Location | Use case |
|---|---|---|
| **Primary** | `dissertation/docker-compose.yml` | Full stack: postgres + backend + frontend. **Use this for deployment.** |
| **Production** | `dissertation/docker-compose.prod.yml` | Same but with resource limits, env-var secrets, and `expose` instead of `ports` for backend. |
| **Standalone frontend** | `dissertation_frontend/docker-compose.yml` | Frontend-only testing against the backend (backend must manage its own DB). |

---

## How to Run

### Full Stack — Development (from this repo)
```bash
docker-compose up --build
```

| Service | URL (from the machine running Docker) |
|---------|---------------------------------------|
| Frontend (Nginx SPA) | `http://<host>:4200` |
| Backend API (direct) | `http://<host>:8080` |
| Swagger UI | `http://<host>:8080/swagger-ui.html` |
| pgAdmin (optional) | `http://<host>:5050` — requires `docker-compose --profile tools up` |

Where `<host>` is:
- **`localhost`** if Docker is running on your local machine / WSL2
- **The server's IP or domain** if deployed remotely (e.g. `192.168.1.50`, `msadetector.example.com`)

> **Note:** Dev uses port **4200** to avoid conflicts with IIS on Windows (port 80).
> Production compose defaults to port 80 (configurable via `FRONTEND_PORT` env var).

### Full Stack — Production (from this repo)
```bash
cp .env.example .env
# Edit .env — set DB_PASSWORD and JWT_SECRET
docker-compose -f docker-compose.prod.yml up -d --build
```
Only port **80** is exposed. Backend is internal (no `:8080` from outside).
Access: `http://<host>` or `https://<domain>` if you add TLS termination.

### Frontend Only — Hot Reload (from dissertation_frontend/)
```bash
cd ../dissertation_frontend
npm install
npm start
```
Angular dev server on `http://localhost:4200` — only works on your local
machine. Expects the backend already running at `http://localhost:8080`.

### Known Issues

| Issue | Cause | Fix |
|-------|-------|-----|
| `KeyError: 'id'` Python traceback at end of `docker-compose up` logs | Bug in docker-compose v1 (Python). Cosmetic only — all services run fine. | Upgrade to **Docker Compose V2** (`docker compose` without the hyphen). WSL2/Docker Desktop includes V2 by default. |
| `PostgreSQLDialect does not need to be specified explicitly` | Hibernate 7.x auto-detects the dialect. | ✅ Fixed — removed `hibernate.dialect` from `application.yml`. |
| `Using generated security password` | Spring Security auto-creates an in-memory user even though `permitAll()` is configured. | ✅ Fixed — suppressed via logging config. |

---

## Original Questions (archived)

<details>
<summary>Click to expand original questions (§1–§8)</summary>

### 1. Build Artefact Details

| Question | Why we need it |
|----------|---------------|
| **What is the build output directory?** (e.g. `dist/msa-detector-frontend/`) | The Nginx container (or the backend's static-resource handler) needs to know where to serve files from. |
| **What is the build command?** (e.g. `npm run build -- --configuration production`) | Required for the CI/CD pipeline and the Docker multi-stage build. |
| **Node.js version required?** (e.g. 20 LTS) | For the Dockerfile `FROM node:20-alpine AS frontend-build` stage. |
| **Package manager?** (`npm`, `yarn`, `pnpm`) | Determines the install/build commands in Docker. |

### 2. Routing & Base Href

| Question | Why we need it |
|----------|---------------|
| **Is Angular HTML5 routing used?** (i.e. `PathLocationStrategy`) | Nginx must be configured to fall back to `index.html` for all unknown routes so client-side routing works. |
| **What `<base href>` is used?** (`/`, `/app/`, etc.) | Must match the Nginx `location` block and any reverse-proxy prefix. |

### 3. API Proxy / CORS Configuration

| Question | Why we need it |
|----------|---------------|
| **What base URL does the frontend use to call the backend API?** (e.g. `/api`, `http://localhost:8080/api`, environment-driven?) | Determines whether we need Nginx to proxy `/api` → backend, or whether CORS headers must be set. |
| **Is there an `environment.ts` / `environment.prod.ts` with a configurable `apiUrl`?** | If so, it can be overridden at build time or via Docker env vars. |
| **Are there any WebSocket / SSE connections?** (e.g. for live job-progress updates) | Nginx needs `proxy_set_header Upgrade` and `Connection` headers for WebSocket support. |

### 4. Authentication / Token Handling

| Question | Why we need it |
|----------|---------------|
| **How are JWT tokens sent?** (`Authorization: Bearer …` header, cookie, etc.) | The backend `SecurityConfig` and CORS policy must allow the chosen method. |
| **Where is the token stored?** (`localStorage`, `sessionStorage`, HTTP-only cookie) | Impacts CSRF requirements and cookie configuration on the backend. |
| **Are there auth-related routes?** (`/login`, `/register`, `/oauth/callback`) | The backend currently has `permitAll()` — once real auth is enabled, these paths must be whitelisted. |

### 5. Static Assets & File Upload

| Question | Why we need it |
|----------|---------------|
| **Max expected upload size in the UI?** | The backend currently allows up to `500 MB`. If the frontend has a lower practical limit or a chunked-upload strategy, we should align. |
| **Does the frontend need to serve any assets from the backend?** (user avatars, generated reports, etc.) | If yes, we need to configure static-resource mapping or an object-storage integration. |

### 6. Deployment Topology Preference

#### Option A — Nginx serves the SPA, proxies `/api` to backend
```
Browser ──► Nginx (:80/:443)
               ├── static files  → /usr/share/nginx/html  (Angular build)
               └── /api/*        → http://backend:8080     (Spring Boot)
```

#### Option B — Two separate origins (CORS)
```
Browser ──► Frontend dev server (:4200)   → Angular
        ──► Backend (:8080)               → Spring Boot
```

#### Option C — Backend serves the SPA (embedded)

### 7. Environment Variables / Runtime Config

- **Build-time replacement** (`environment.prod.ts`)
- **Runtime `config.json`** fetched at app startup
- **`window.__env`** injected via `index.html` script tag

### 8. Repository & Integration

| Question | Details |
|----------|---------|
| **Where is the frontend repo?** | URL so we can set up the Docker build and CI pipeline. |
| **Or will it live in this same repo?** | If yes, provide the subdirectory (e.g. `frontend/`). |
| **Any CI/CD preferences?** | GitHub Actions, GitLab CI, Jenkins, etc. |

</details>

<details>
<summary>Click to expand follow-up questions (§9–§13)</summary>

### 9. Frontend Nginx `proxy_pass` Host Name

The backend Docker Compose service is named **`backend`**.
The frontend's `nginx.conf` must proxy to exactly:

```nginx
proxy_pass http://backend:8080;
```

### 10. Upload Size in Frontend Nginx

The backend accepts up to **500 MB** (`spring.servlet.multipart.max-file-size`).
The frontend's `nginx.conf` must include:

```nginx
client_max_body_size 500m;
```

### 11. Frontend Health Check

Does the frontend Dockerfile include a `HEALTHCHECK`?

### 12. Frontend `.dockerignore`

Please ensure your `.dockerignore` excludes at minimum:
`node_modules`, `dist`, `.angular`, `.git`

### 13. Angular `environment.prod.ts` — Exact `apiUrl` Value

Expected: `apiUrl: '/api'`

</details>
