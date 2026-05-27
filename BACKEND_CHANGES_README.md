# Backend Changes — Authentication, Health Score Breakdown & Re-Analysis Diff

This document describes the backend features and all API changes the frontend needs to integrate.

---

## Table of Contents

1. [Authentication (User Accounts)](#1-authentication-user-accounts)
   - [New Endpoints](#11-new-endpoints)
   - [How Auth Works](#12-how-auth-works)
   - [Protecting Requests](#13-protecting-requests)
   - [Error Responses](#14-error-responses)
2. [Health Score Breakdown](#2-health-score-breakdown)
   - [Response Shape](#21-response-shape)
   - [Score Categories](#22-score-categories)
   - [Grade Scale](#23-grade-scale)
3. [Changed Endpoints (Breaking)](#3-changed-endpoints-breaking)
   - [Projects](#31-projects)
   - [Jobs](#32-jobs)
4. [Migration Notes](#4-migration-notes)

---

## 1. Authentication (User Accounts)

Every endpoint (except auth and Swagger) now requires a valid JWT.

### 1.1 New Endpoints

#### `POST /api/auth/register`

Create a new account. Returns a JWT immediately (auto-login).

**Request:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "secret123"
}
```

**Validation rules:**
| Field      | Rules                                      |
|------------|--------------------------------------------|
| `name`     | Required, max 100 chars                    |
| `email`    | Required, valid email format, max 255 chars|
| `password` | Required, 6–120 chars                      |

**Response `201 Created`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 86400000,
  "user": {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com"
  }
}
```

**Error `400 Bad Request`** (duplicate email):
```json
{
  "error": "BAD_REQUEST",
  "message": "Email is already registered",
  "timestamp": "2026-03-20T12:00:00"
}
```

---

#### `POST /api/auth/login`

Authenticate with existing credentials.

**Request:**
```json
{
  "email": "john@example.com",
  "password": "secret123"
}
```

**Response `200 OK`:** Same shape as register response.

**Error `401 Unauthorized`** (wrong credentials):
```json
{
  "error": "UNAUTHORIZED",
  "message": "Invalid email or password",
  "timestamp": "2026-03-20T12:00:00"
}
```

---

#### `GET /api/auth/me`

Get the currently authenticated user's info. Requires `Authorization` header.

**Response `200 OK`:**
```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john@example.com"
}
```

---

### 1.2 How Auth Works

1. User calls `/api/auth/register` or `/api/auth/login`.
2. Backend returns a JWT token.
3. Frontend stores the token (e.g. `localStorage`, in-memory, cookie — your choice).
4. Every subsequent request includes the header:
   ```
   Authorization: Bearer <token>
   ```
5. Token expires after **24 hours** (`expiresIn` is in milliseconds).

### 1.3 Protecting Requests

| Path Pattern                          | Auth Required |
|---------------------------------------|:------------:|
| `POST /api/auth/register`             | ❌           |
| `POST /api/auth/login`                | ❌           |
| `/swagger-ui/**`, `/api-docs/**`      | ❌           |
| `/actuator/health`, `/actuator/info`  | ❌           |
| **Everything else**                   | ✅           |

If a request hits a protected endpoint without a valid token, the backend returns:

```
HTTP 401
{
  "error": "UNAUTHORIZED",
  "message": "Authentication is required to access this resource",
  "timestamp": "2026-03-20T12:00:00"
}
```

### 1.4 Error Responses

All error responses now follow a consistent shape:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable message",
  "timestamp": "2026-03-20T12:00:00"
}
```

New error codes added:

| HTTP Status | Error Code           | When                                |
|-------------|----------------------|-------------------------------------|
| 401         | `UNAUTHORIZED`       | Missing/expired/invalid JWT token   |
| 401         | `UNAUTHORIZED`       | Wrong email or password on login    |
| 403         | `FORBIDDEN`          | Valid token but insufficient access |
| 400         | `VALIDATION_FAILED`  | Request body fails bean validation  |

---

## 2. Health Score Breakdown

The analysis result response now includes a **detailed breakdown** of how the health score was computed.

### 2.1 Response Shape

The `GET /api/jobs/{id}/results` response now includes a new `healthScoreBreakdown` field:

```json
{
  "id": 1,
  "jobId": 1,
  "healthScore": 62,
  "servicesAnalyzed": 5,
  "totalAntiPatterns": 3,
  "...other existing fields...": "...",
  "antiPatterns": [ "..." ],
  "dependencyGraph": { "..." },
  "healthScoreBreakdown": {
    "overallScore": 62,
    "grade": "C",
    "categories": [
      {
        "name": "Anti-Patterns",
        "description": "Detected architectural anti-patterns weighted by severity",
        "score": 25,
        "maxScore": 40,
        "deductions": [
          {
            "reason": "Cyclic Dependency (×2)",
            "count": 2,
            "pointsLost": 10
          },
          {
            "reason": "Shared Database (×1)",
            "count": 1,
            "pointsLost": 5
          }
        ]
      },
      {
        "name": "Code Quality",
        "description": "Code smell density from static analysis",
        "score": 17,
        "maxScore": 20,
        "deductions": [
          {
            "reason": "12 code smell(s) detected",
            "count": 12,
            "pointsLost": 3
          }
        ]
      },
      {
        "name": "Architecture",
        "description": "Coupling between services and dependency structure",
        "score": 10,
        "maxScore": 25,
        "deductions": [
          {
            "reason": "Coupling coefficient 0.45",
            "count": 1,
            "pointsLost": 7
          },
          {
            "reason": "2 dependency cycle(s)",
            "count": 2,
            "pointsLost": 10
          }
        ]
      },
      {
        "name": "Service Sizing",
        "description": "Appropriateness of individual service sizes",
        "score": 10,
        "maxScore": 15,
        "deductions": [
          {
            "reason": "1 god service(s)",
            "count": 1,
            "pointsLost": 5
          }
        ]
      }
    ]
  }
}
```

> **Note:** The top-level `healthScore` field now uses the same calculated value from the breakdown (`overallScore`), so they will always match.

### 2.2 Score Categories

The total budget is **100 points**, split into 4 categories:

| Category          | Max Points | What It Measures                                     |
|-------------------|:----------:|------------------------------------------------------|
| **Anti-Patterns** | 40         | Each detected anti-pattern deducts points by severity: CRITICAL=8, HIGH=5, MEDIUM=3, LOW=1 |
| **Code Quality**  | 20         | Code smell density (logarithmic scale — first smells hurt more) |
| **Architecture**  | 25         | Coupling coefficient (0–15 pts) + dependency cycles (5 pts each, capped at 10) |
| **Service Sizing**| 15         | Nano services (3 pts each, capped at 8) + God services (5 pts each, capped at 10) |

### 2.3 Grade Scale

| Score Range | Grade |
|-------------|:-----:|
| 90–100      | **A** |
| 80–89       | **B** |
| 65–79       | **C** |
| 50–64       | **D** |
| 0–49        | **F** |

### Frontend Suggestions

- **Hover/tooltip:** Show the `grade` and `overallScore` in a badge. On hover, show the category list with score/maxScore bars.
- **Detail panel/page:** Show each category as a collapsible card with its deductions listed.
- **Empty deductions:** If a category has no deductions, `deductions` will be an empty array `[]` and `score` will equal `maxScore` (perfect).

---

## 3. Changed Endpoints (Breaking)

All existing endpoints now require a JWT and are **scoped to the authenticated user** — users can only see their own projects and jobs.

### 3.1 Projects

| Endpoint | Change |
|----------|--------|
| `POST /api/projects/upload` | Now requires `Authorization` header. The project is automatically owned by the authenticated user. No body changes. |
| `POST /api/projects/clone`  | Same — requires auth, project owned by caller. No body changes. |
| `GET /api/projects`         | **Returns only the authenticated user's projects** (was: all projects). Sorted by `createdAt` descending. |
| `GET /api/projects/{id}`    | Returns 404 if the project doesn't belong to the user (was: any project). |
| `DELETE /api/projects/{id}` | Returns 404 if the project doesn't belong to the user. |

### 3.2 Jobs

| Endpoint | Change |
|----------|--------|
| `GET /api/jobs/{id}`         | Returns 404 if the job's project doesn't belong to the user. |
| `GET /api/jobs/{id}/results` | Same — scoped to user. Response now includes `healthScoreBreakdown` (see §2). |
| `GET /api/jobs/recent`       | **Returns only the authenticated user's recent jobs** (max 20, newest first). |
| `POST /api/jobs/{id}/cancel` | Returns 404 if the job doesn't belong to the user. |

---

## 4. Migration Notes

### For the frontend

1. **Add a login/register flow.** Before any API call (except auth), ensure you have a JWT stored.
2. **Attach the token to every request:**
   ```js
   // Example with fetch
   fetch('/api/projects', {
     headers: {
       'Authorization': `Bearer ${token}`,
       'Content-Type': 'application/json'
     }
   });

   // Example with axios interceptor
   axios.interceptors.request.use(config => {
     const token = localStorage.getItem('token');
     if (token) {
       config.headers.Authorization = `Bearer ${token}`;
     }
     return config;
   });
   ```
3. **Handle 401 globally.** If any response is 401, redirect to login (the token has likely expired).
4. **Update the results display** to render the new `healthScoreBreakdown` object (the old flat fields are still present for backward compatibility).

### Database

No new Flyway migration is needed — the existing `V1__initial_schema.sql` already has the `users` table and `owner_id` foreign key on `projects`.

### New files created

```
src/main/java/com/msadetector/
├── security/
│   ├── JwtTokenProvider.java          # JWT generation & validation
│   ├── JwtAuthenticationFilter.java   # Extracts JWT from Authorization header
│   ├── JwtAuthenticationEntryPoint.java # Returns JSON 401 for unauthenticated requests
│   ├── UserPrincipal.java             # Wraps User entity for Spring Security
│   └── CustomUserDetailsService.java  # Loads users from DB for Spring Security
├── service/
│   ├── AuthService.java               # Register & login logic
│   └── HealthScoreCalculator.java     # Computes the detailed score breakdown
├── controller/
│   └── AuthController.java            # /api/auth/* endpoints
└── dto/
    ├── RegisterRequest.java
    ├── LoginRequest.java
    ├── AuthResponse.java
    ├── UserResponse.java
    └── HealthScoreBreakdownResponse.java
```

### Modified files

```
config/SecurityConfig.java          — Rewrote: JWT filter, BCrypt, stateless sessions
controller/ProjectController.java   — All methods now receive @AuthenticationPrincipal
controller/JobController.java       — All methods now receive @AuthenticationPrincipal
service/ProjectService.java         — New user-scoped methods, accepts userId parameter
service/JobService.java             — User-scoped queries, added HealthScoreCalculator
dto/AnalysisResultResponse.java     — Added healthScoreBreakdown field
entity/AnalysisResult.java          — Javadoc on calculateHealthScore()
exception/GlobalExceptionHandler.java — Added handlers for auth + validation errors
application.yml                     — Updated default JWT secret length
```

---

## 5. Frontend Integration Review

Reviewed `FRONTEND_CHANGES_README.md` — **all contracts are aligned, no backend changes needed.**

| Concern | Status |
|---------|:------:|
| `AuthResponse` shape (`token`, `tokenType`, `expiresIn`, `user`) | ✅ Matches |
| `UserResponse` shape (`id`, `name`, `email`) | ✅ Matches |
| `HealthScoreBreakdownResponse` shape (`overallScore`, `grade`, `categories[]`) | ✅ Matches |
| `ScoreCategory` shape (`name`, `description`, `score`, `maxScore`, `deductions[]`) | ✅ Matches |
| `Deduction` shape (`reason`, `count`, `pointsLost`) | ✅ Matches |
| `healthScoreBreakdown` field on `AnalysisResultResponse` | ✅ Matches (nullable on frontend) |
| `Authorization: Bearer <token>` header via interceptor | ✅ Matches |
| 401 global handling (logout + redirect, skip for `/auth/*`) | ✅ Correct |
| Error shape `{ error, message, timestamp }` → `error.error?.message` | ✅ Correct |
| Public endpoints (`/api/auth/**`, Swagger, actuator) | ✅ Aligned |
| Protected routes (`/upload`, `/results/:jobId`, `/history`) with `authGuard` | ✅ Aligned |

---

## 6. Re-Analysis Diff — "New Code" Feature

Inspired by SonarQube's "New Code" concept. When a project is re-analyzed, the backend compares the latest analysis result with the previous one and produces a full diff showing what changed.

### 6.1 New Endpoints

#### `POST /api/projects/{id}/reanalyze`

Trigger a new analysis on an **existing** project. For Git-based projects, the repo is re-cloned (picks up latest commits). For uploaded projects, the existing files are reused.

**Response `202 Accepted`:**
```json
{
  "projectId": 1,
  "jobId": 5
}
```

Same shape as the original upload/clone response. The frontend should poll `GET /api/jobs/5` for progress, then fetch results as usual.

**Error `404 Not Found`:** Project doesn't exist or doesn't belong to the user.

---

#### `GET /api/jobs/{id}/diff`

Get the diff between this job's analysis result and the **previous** analysis for the same project.

**Response `200 OK`:** Full `AnalysisDiffResponse` (see §6.3).

**Error `404 Not Found`:** No previous analysis exists (this is the first analysis for the project).

**Error `400 Bad Request`:** Job is not completed yet.

---

#### `GET /api/projects/{id}/history`

Get the full analysis history for a project — all completed results ordered newest first. Each result includes its diff against the previous one (or `null` for the first analysis).

**Response `200 OK`:**
```json
[
  {
    "id": 10,
    "jobId": 5,
    "healthScore": 78,
    "...all existing AnalysisResultResponse fields...": "...",
    "healthScoreBreakdown": { "..." },
    "diff": { "...AnalysisDiffResponse..." }
  },
  {
    "id": 3,
    "jobId": 1,
    "healthScore": 52,
    "...": "...",
    "diff": null
  }
]
```

---

### 6.2 Changed Responses

#### `AnalysisResultResponse` — new `diff` field

The `GET /api/jobs/{id}/results` response now includes a `diff` field. It is `null` when this is the first analysis for the project.

```json
{
  "id": 10,
  "jobId": 5,
  "healthScore": 78,
  "...existing fields...": "...",
  "healthScoreBreakdown": { "..." },
  "diff": {
    "currentJobId": 5,
    "previousJobId": 1,
    "currentAnalysisDate": "2026-03-20T14:30:00",
    "previousAnalysisDate": "2026-03-15T10:00:00",
    "analysisNumber": 2,
    "currentHealthScore": 78,
    "previousHealthScore": 52,
    "healthScoreDelta": 26,
    "currentGrade": "C",
    "previousGrade": "D",
    "totalAntiPatterns": { "previous": 5, "current": 3, "delta": -2 },
    "totalCodeSmells": { "previous": 25, "current": 18, "delta": -7 },
    "criticalIssues": { "previous": 2, "current": 0, "delta": -2 },
    "highIssues": { "previous": 1, "current": 1, "delta": 0 },
    "mediumIssues": { "previous": 1, "current": 1, "delta": 0 },
    "lowIssues": { "previous": 1, "current": 1, "delta": 0 },
    "totalLinesOfCode": { "previous": 5000, "current": 5200, "delta": 200 },
    "servicesAnalyzed": { "previous": 5, "current": 5, "delta": 0 },
    "cycleCount": { "previous": 2, "current": 0, "delta": -2 },
    "totalDependencies": { "previous": 8, "current": 7, "delta": -1 },
    "couplingCoefficient": { "previous": 0.45, "current": 0.35, "delta": -0.10 },
    "resolvedAntiPatterns": [
      {
        "patternType": "CYCLIC_DEPENDENCY",
        "severity": "HIGH",
        "description": "Cyclic dependency between service-a → service-b → service-a",
        "affectedServices": ["service-a", "service-b"]
      },
      {
        "patternType": "SHARED_DATABASE",
        "severity": "MEDIUM",
        "description": "Services sharing database jdbc:postgresql://...",
        "affectedServices": ["service-a", "service-c"]
      }
    ],
    "newAntiPatterns": [],
    "unchangedAntiPatterns": [
      {
        "patternType": "GOD_SERVICE",
        "severity": "MEDIUM",
        "description": "Service 'api-gateway' handles too many responsibilities",
        "affectedServices": ["api-gateway"]
      }
    ],
    "categoryDeltas": [
      {
        "categoryName": "Anti-Patterns",
        "previousScore": 20,
        "currentScore": 32,
        "maxScore": 40,
        "delta": 12
      },
      {
        "categoryName": "Code Quality",
        "previousScore": 14,
        "currentScore": 17,
        "maxScore": 20,
        "delta": 3
      },
      {
        "categoryName": "Architecture",
        "previousScore": 8,
        "currentScore": 19,
        "maxScore": 25,
        "delta": 11
      },
      {
        "categoryName": "Service Sizing",
        "previousScore": 10,
        "currentScore": 10,
        "maxScore": 15,
        "delta": 0
      }
    ],
    "summary": "Health improved from 52 (D) → 78 (C). 2 anti-pattern(s) resolved."
  }
}
```

> When `diff` is `null`, this is the first analysis — no comparison is available.

#### `AnalysisJobResponse` — new `analysisNumber` field

```json
{
  "id": 5,
  "projectId": 1,
  "status": "COMPLETED",
  "...existing fields...": "...",
  "analysisNumber": 2
}
```

`analysisNumber` is `1` for the first analysis, `2` for the second, etc.

#### `ProjectResponse` — new fields

```json
{
  "id": 1,
  "name": "my-project",
  "sourceType": "GITHUB",
  "sourceUrl": "https://github.com/user/repo",
  "createdAt": "2026-03-15T10:00:00",
  "microservices": [ "..." ],
  "analysisCount": 2,
  "latestJobId": 5
}
```

| Field | Type | Description |
|-------|------|-------------|
| `analysisCount` | `Integer` | Total number of analysis jobs (of any status) for this project |
| `latestJobId` | `Long \| null` | ID of the most recent analysis job (useful for quick navigation) |

---

### 6.3 `AnalysisDiffResponse` Shape Reference

```typescript
interface AnalysisDiffResponse {
  // Reference info
  currentJobId: number;
  previousJobId: number;
  currentAnalysisDate: string;   // ISO datetime
  previousAnalysisDate: string;
  analysisNumber: number;

  // Score delta
  currentHealthScore: number;
  previousHealthScore: number;
  healthScoreDelta: number;       // positive = improved
  currentGrade: string;           // "A" | "B" | "C" | "D" | "F"
  previousGrade: string;

  // Metric deltas (each has previous, current, delta)
  totalAntiPatterns: MetricDelta;
  totalCodeSmells: MetricDelta;
  criticalIssues: MetricDelta;
  highIssues: MetricDelta;
  mediumIssues: MetricDelta;
  lowIssues: MetricDelta;
  totalLinesOfCode: MetricDelta;
  servicesAnalyzed: MetricDelta;
  cycleCount: MetricDelta;
  totalDependencies: MetricDelta;
  couplingCoefficient: DoubleDelta;

  // Anti-pattern changes
  resolvedAntiPatterns: AntiPatternChange[];   // was present, now gone ✅
  newAntiPatterns: AntiPatternChange[];         // not before, now detected ❌
  unchangedAntiPatterns: AntiPatternChange[];   // still present ⚠️

  // Per-category score deltas
  categoryDeltas: CategoryDelta[];

  // Human-readable summary
  summary: string;
}

interface MetricDelta {
  previous: number;
  current: number;
  delta: number;     // current - previous (negative = improved for issues)
}

interface DoubleDelta {
  previous: number;
  current: number;
  delta: number;
}

interface AntiPatternChange {
  patternType: string;          // e.g. "CYCLIC_DEPENDENCY"
  severity: string;             // "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
  description: string;
  affectedServices: string[];
}

interface CategoryDelta {
  categoryName: string;         // "Anti-Patterns" | "Code Quality" | "Architecture" | "Service Sizing"
  previousScore: number;
  currentScore: number;
  maxScore: number;
  delta: number;                // positive = improved
}
```

---

### 6.4 Frontend Integration Suggestions

#### Re-Analyze Button

On the project detail page or history page, add a **"Re-Analyze"** button that calls `POST /api/projects/{id}/reanalyze`. For Git projects this re-clones the repo; for uploaded projects, the existing files are reused.

```typescript
reanalyze(projectId: number): Observable<{ projectId: number; jobId: number }> {
  return this.http.post<{ projectId: number; jobId: number }>(
    `/api/projects/${projectId}/reanalyze`, {}
  );
}
```

After the call, redirect to the results/polling page with the returned `jobId`, same as the upload flow.

#### Diff Banner on Results Page

When `result.diff` is not `null`, show a "New Code" banner at the top of the results page:

- **Summary text:** Use `diff.summary` directly — e.g. *"Health improved from 52 (D) → 78 (C). 2 anti-pattern(s) resolved."*
- **Health score delta badge:** Show `+26 ↑` in green (or `-10 ↓` in red).
- **Resolved anti-patterns** with green checkmarks ✅
- **New anti-patterns** with red warning icons ❌
- **Unchanged anti-patterns** with yellow caution ⚠️

#### Category Comparison Bars

For each `categoryDelta`, show a comparison bar:

```
Anti-Patterns    [████████████░░░░░░░░] 20/40 → [████████████████░░░░] 32/40  (+12)
Code Quality     [██████████████░░░░░░] 14/20 → [█████████████████░░░] 17/20  (+3)
Architecture     [████████░░░░░░░░░░░░]  8/25 → [███████████████████░] 19/25  (+11)
Service Sizing   [██████████████░░░░░░] 10/15 → [██████████████░░░░░░] 10/15  (±0)
```

#### Metric Delta Cards

Show delta badges next to each metric:

| Metric | Display |
|--------|---------|
| Anti-patterns: 5 → 3 | `▼2` in green (fewer is better) |
| Code smells: 25 → 18 | `▼7` in green |
| Lines of code: 5000 → 5200 | `▲200` neutral |
| Cycles: 2 → 0 | `▼2` in green |

Convention: For issues, **negative delta = good** (fewer issues). Use green for improvements, red for regressions, gray for unchanged.

#### Analysis History Timeline

The `GET /api/projects/{id}/history` endpoint returns all past results. You could render a timeline:

```
Analysis #2  ─ Mar 20 ─ Score: 78 (C) ─ +26 ↑ ─ 2 resolved, 0 new
Analysis #1  ─ Mar 15 ─ Score: 52 (D) ─ (baseline)
```

#### When diff is null

If `diff` is `null` (first analysis), hide the diff section entirely and show the results normally. Optionally show a note: *"This is the first analysis. Re-analyze later to track changes."*

---

## 6.5 Endpoint Summary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/projects/{id}/reanalyze` | `POST` | Trigger re-analysis on existing project |
| `/api/projects/{id}/history` | `GET` | All analysis results for a project (newest first, each with diff) |
| `/api/jobs/{id}/diff` | `GET` | Standalone diff for a specific job vs. previous |
| `/api/jobs/{id}/results` | `GET` | **Updated** — now includes `diff` field |
| `/api/jobs/{id}` | `GET` | **Updated** — now includes `analysisNumber` field |
| `/api/projects` | `GET` | **Updated** — now includes `analysisCount`, `latestJobId` |
| `/api/projects/{id}` | `GET` | **Updated** — now includes `analysisCount`, `latestJobId` |

---

### 6.6 Database Migration

A new Flyway migration `V2__add_analysis_number.sql` adds the `analysis_number` column to `analysis_jobs`. Existing rows are back-filled with sequential numbers per project. **No frontend action required** — runs automatically on backend startup.

---

### 6.7 New & Modified Backend Files

**New files:**
```
src/main/java/com/msadetector/
├── dto/
│   └── AnalysisDiffResponse.java          # Full diff DTO (MetricDelta, DoubleDelta, AntiPatternChange, CategoryDelta)
├── service/
│   └── AnalysisDiffService.java           # Computes the diff between two AnalysisResult objects
└── resources/db/migration/
    └── V2__add_analysis_number.sql        # Adds analysis_number column
```

**Modified files:**
```
entity/AnalysisJob.java                    — Added analysisNumber field
dto/AnalysisResultResponse.java            — Added diff field (nullable)
dto/AnalysisJobResponse.java               — Added analysisNumber field
dto/ProjectResponse.java                   — Added analysisCount + latestJobId fields
controller/ProjectController.java          — Added /reanalyze and /history endpoints, injected JobService
controller/JobController.java              — Added /diff endpoint
service/JobService.java                    — Added getDiff(), getProjectHistory(), buildDiffIfAvailable(); injected AnalysisDiffService + ProjectRepository
service/ProjectService.java               — Added reanalyze() method; updated toResponse() for new fields
repository/AnalysisJobRepository.java      — Added countByProject(), findCompletedByProject()
repository/AnalysisResultRepository.java   — Added findPreviousResultForProject(), findAllByProjectWithAntiPatterns()
```

---

## 7. Frontend Re-Analysis Diff Integration Review

Reviewed `FRONTEND_CHANGES_README.md` (updated with §6 Re-Analysis Diff) — **all contracts are aligned, no backend changes needed.**

| Concern | Status |
|---------|:------:|
| `POST /api/projects/{id}/reanalyze` → returns `{ projectId, jobId }` | ✅ Matches `UploadResponse` |
| `GET /api/projects/{id}/history` → returns `AnalysisResultResponse[]` with `diff` | ✅ Matches |
| `GET /api/jobs/{id}/diff` → returns `AnalysisDiffResponse` | ✅ Matches |
| `GET /api/jobs/{id}/results` → now includes `diff` field (nullable) | ✅ Matches |
| `GET /api/jobs/{id}` → now includes `analysisNumber` | ✅ Matches |
| `GET /api/projects` → now includes `analysisCount`, `latestJobId` | ✅ Matches |
| `AnalysisDiffResponse` full shape (all 25+ fields) | ✅ Matches §6.3 exactly |
| `MetricDelta` shape (`previous`, `current`, `delta`) | ✅ Matches |
| `DoubleDelta` shape (`previous`, `current`, `delta`) | ✅ Matches |
| `AntiPatternChange` shape (`patternType`, `severity`, `description`, `affectedServices`) | ✅ Matches — enums serialize as strings via Jackson default |
| `CategoryDelta` shape (`categoryName`, `previousScore`, `currentScore`, `maxScore`, `delta`) | ✅ Matches |
| `LocalDateTime` fields → ISO string in JSON | ✅ `JavaTimeModule` registered |
| Re-analyze polling flow (POST → poll job status → navigate to results) | ✅ Same as upload flow |
| First-analysis handling (`diff` is `null` → hide diff, show info note) | ✅ Per §6.4 suggestion |
| History page: projects tab with expandable timeline + recent jobs tab | ✅ Uses `/projects`, `/projects/{id}/history`, `/jobs/recent` — all exist |
| Results page: diff banner + re-analyze button with progress tracker | ✅ Uses `/jobs/{id}/results` (includes diff), `/projects/{id}/reanalyze` |
| Error handling: `error.error?.message` for re-analysis failures | ✅ Backend returns `{ error, message, timestamp }` shape |

---

## 8. Re-Upload & Source-Type Switching

Users can now re-upload a new ZIP or switch a project's source to Git when re-analyzing. This enables the "New Code" diff for **all** project types — not just Git projects.

### 8.1 New Endpoint: Re-Upload ZIP

#### `POST /api/projects/{id}/reupload`

Upload a **new ZIP file** for an existing project, replacing the old source files. Works regardless of the project's original source type (ZIP, GitHub, GitLab). After the upload, the project's source type becomes `UPLOAD`.

**Content-Type:** `multipart/form-data`

**Request:**
| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| `file` | `MultipartFile` | ✅ | ZIP archive (max 500 MB) |

**Response `202 Accepted`:**
```json
{
  "projectId": 1,
  "jobId": 6
}
```

Same shape as upload/clone/reanalyze. Poll `GET /api/jobs/6` for progress, then fetch results.

**Errors:**
| Status | When |
|--------|------|
| `400 INVALID_FILE` | File is empty, not a ZIP, or exceeds 500 MB |
| `404 NOT_FOUND` | Project doesn't exist or doesn't belong to the user |

---

### 8.2 Updated Endpoint: Re-Analyze with Source Switch

#### `POST /api/projects/{id}/reanalyze`

**Previously:** Empty body, re-uses the project's current source.

**Now:** Accepts an **optional JSON body** to switch the source to a Git repo.

**Request (Option A — keep current source, same as before):**
```
POST /api/projects/1/reanalyze
Content-Type: application/json

{}
```
Or simply omit the body entirely:
```
POST /api/projects/1/reanalyze
```

**Request (Option B — switch to Git):**
```json
{
  "repoUrl": "https://github.com/user/repo",
  "branch": "main"
}
```

| Field | Type | Required | Description |
|-------|------|:--------:|-------------|
| `repoUrl` | `string` | ❌ | If provided, switches the project source to this Git repo. Must be a valid GitHub/GitLab HTTPS URL. |
| `branch` | `string` | ❌ | Branch to clone. If omitted, uses the remote's default branch. |

**Validation:** `repoUrl` must match `^https://(github\.com|gitlab\.com)/[\w.\-]+/[\w.\-]+(\.git)?$`

**Response `202 Accepted`:** Same `{ projectId, jobId }` shape.

**Behavior matrix:**

| Current Source | Body | What Happens |
|:-------------:|:----:|:-----------|
| Git (GitHub/GitLab) | empty/`{}` | Re-clones the **same** repo (picks up new commits) |
| Git | `{ repoUrl, branch }` | Switches to the **new** repo URL and clones it |
| Upload (ZIP) | empty/`{}` | Re-uses the **existing** local files (same result) |
| Upload | `{ repoUrl, branch }` | **Switches** the project to Git, clones the repo |

> To switch from Git → Upload, use `POST /api/projects/{id}/reupload` with a new ZIP file.

---

### 8.3 Source-Type Switching Summary

The project's `sourceType` is updated automatically:

| Action | Resulting `sourceType` | `sourceUrl` | `branch` |
|--------|:---------------------:|:-----------:|:--------:|
| `reanalyze` with `repoUrl` containing `github.com` | `GITHUB` | set | set (or null) |
| `reanalyze` with `repoUrl` containing `gitlab.com` | `GITLAB` | set | set (or null) |
| `reanalyze` without `repoUrl` | _(unchanged)_ | _(unchanged)_ | _(unchanged)_ |
| `reupload` with ZIP file | `UPLOAD` | `null` | `null` |

The `ProjectResponse` already includes `sourceType` and `sourceUrl`, so the frontend can display the current source and adjust the UI accordingly.

---

### 8.4 Frontend Integration: Re-Analyze Dropdown

On the results page or project card, replace the single "Re-Analyze" button with a **dropdown** offering three options:

```
┌─────────────────────────────────┐
│  Re-Analyze ▾                   │
├─────────────────────────────────┤
│  🔄 Re-analyze (current source) │  ← POST /reanalyze with {}
│  📁 Upload new ZIP              │  ← Opens file picker, then POST /reupload
│  🔗 Clone from GitHub/GitLab    │  ← Opens modal for URL+branch, then POST /reanalyze with { repoUrl, branch }
└─────────────────────────────────┘
```

**Option 1: Re-analyze (current source)**
```typescript
reanalyze(projectId: number) {
  return this.http.post<UploadResponse>(`/api/projects/${projectId}/reanalyze`, {});
}
```
For Git projects this re-clones; for ZIP projects this re-scans the same files. Best for Git projects after pushing new commits.

**Option 2: Upload new ZIP**
```typescript
reupload(projectId: number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return this.http.post<UploadResponse>(`/api/projects/${projectId}/reupload`, formData);
}
```
Opens a file picker → uploads the new ZIP → triggers re-analysis. The project switches to `UPLOAD` source type.

**Option 3: Clone from GitHub/GitLab**
```typescript
reanalyzeWithGit(projectId: number, repoUrl: string, branch?: string) {
  return this.http.post<UploadResponse>(`/api/projects/${projectId}/reanalyze`, {
    repoUrl,
    branch: branch || null
  });
}
```
Opens a small modal/form asking for the repo URL and optional branch → triggers clone + analysis. The project switches to `GITHUB`/`GITLAB` source type.

**After any option:** Poll `GET /api/jobs/{jobId}` for progress → navigate to results page on completion. Same flow as the initial upload.

**Conditional display:**
- For a **Git project**: Pre-fill option 3's URL/branch from `project.sourceUrl` and `project.branch`
- For a **ZIP project**: Highlight option 2 as the primary action
- All three options are always available for any project type

---

### 8.5 TypeScript Interface

```typescript
// New request type (optional body for reanalyze)
interface ReanalyzeRequest {
  repoUrl?: string;   // if provided, switches project source to this Git repo
  branch?: string;    // optional branch (defaults to remote HEAD)
}

// New API methods
reanalyze(projectId: number, request?: ReanalyzeRequest): Observable<UploadResponse>;
reuploadProject(projectId: number, file: File): Observable<UploadResponse>;
```

---

### 8.6 Endpoint Summary (updated)

| Endpoint | Method | Content-Type | Description |
|----------|--------|:------------:|-------------|
| `/api/projects/{id}/reanalyze` | `POST` | `application/json` | Re-analyze with current source, or switch to Git |
| `/api/projects/{id}/reupload` | `POST` | `multipart/form-data` | Upload new ZIP and re-analyze |

---

### 8.7 New & Modified Backend Files

**New files:**
```
dto/ReanalyzeRequest.java     — Optional body for /reanalyze (repoUrl, branch)
```

**Modified files:**
```
controller/ProjectController.java  — Updated /reanalyze to accept optional body; added /reupload endpoint
service/ProjectService.java        — Rewrote reanalyze() with source-switch support; added reuploadAndAnalyze();
                                     extracted createReanalysisJob() and cleanLocalPath() helpers
```

---

## 9. Frontend Re-Upload & Source-Switching Integration Review

Reviewed `FRONTEND_CHANGES_README.md` (updated with §7 Re-Upload & Source-Type Switching) — **one contract gap was found and fixed.**

### Fix Applied

The frontend expects a `branch` field on `ProjectResponse` to pre-fill the Git clone modal (§7.4: *"Pre-fills Git URL/branch from project's current `sourceUrl` and `branch`"*). This field was missing from the backend `ProjectResponse` record.

**Changed files:**

| File | Change |
|------|--------|
| `dto/ProjectResponse.java` | Added `String branch` field (nullable, between `sourceUrl` and `createdAt`) |
| `service/ProjectService.java` | Updated `toResponse()` to include `project.getBranch()` |

**Updated `ProjectResponse` shape:**

```json
{
  "id": 1,
  "name": "my-project",
  "sourceType": "GITHUB",
  "sourceUrl": "https://github.com/user/repo",
  "branch": "main",
  "createdAt": "2026-03-15T10:00:00",
  "microservices": [ "..." ],
  "analysisCount": 2,
  "latestJobId": 5
}
```

| Field | Type | Nullable | Description |
|-------|------|:--------:|-------------|
| `branch` | `String` | ✅ | Branch used for Git projects. `null` for upload projects or when using remote default. |

### Full Alignment Check

| Concern | Status |
|---------|:------:|
| `POST /api/projects/{id}/reupload` (multipart) → `{ projectId, jobId }` | ✅ Matches |
| `POST /api/projects/{id}/reanalyze` with optional `{ repoUrl, branch }` | ✅ Matches §8.2 |
| `ReanalyzeRequest` shape (`repoUrl?`, `branch?`) | ✅ Matches §8.5 |
| `SourceType` includes `UPLOAD`, `GITHUB`, `GITLAB` | ✅ Matches §8.3 |
| `ProjectResponse.branch` field (nullable) | ✅ **Fixed** — now included |
| `ProjectResponse.sourceUrl` field | ✅ Already present |
| Re-upload with progress tracking (`reportProgress: true`) | ✅ Same as initial upload |
| Git URL validation regex | ✅ Matches §8.2 validation |
| Dropdown menu with 3 options on results + history pages | ✅ Per §8.4 suggestion |
| Pre-fill Git URL/branch from project for Git projects | ✅ Now works with `branch` field |
| Source type badge display handles all variants | ✅ Backward + forward compatible |
| Error handling (`error.error?.message`) | ✅ Backend returns `{ error, message, timestamp }` |

