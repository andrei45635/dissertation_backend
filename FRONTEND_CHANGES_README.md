# Frontend Integration — Authentication, Health Score Breakdown, Re-Analysis Diff & Source Switching

This document describes all frontend changes made to integrate the backend's authentication system, health score breakdown, re-analysis diff ("New Code"), and re-upload/source-switching features.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Authentication Flow](#2-authentication-flow)
3. [New Files](#3-new-files)
4. [Modified Files](#4-modified-files)
5. [Health Score Breakdown](#5-health-score-breakdown)
6. [Re-Analysis Diff Feature](#6-re-analysis-diff-feature)
7. [Re-Upload & Source-Type Switching](#7-re-upload--source-type-switching)
8. [Error Handling](#8-error-handling)
9. [How It Works](#9-how-it-works)
10. [Backend Contract Alignment](#10-backend-contract-alignment)

---

## 1. Overview

Four major backend features have been integrated:

| Feature | Summary |
|---------|---------|
| **JWT Authentication** | All API endpoints (except `/api/auth/*`) require a `Bearer` token. Login/register pages, HTTP interceptor, route guards. |
| **Health Score Breakdown** | Results include a `healthScoreBreakdown` object. Collapsible category cards with progress bars and deduction lists. |
| **Re-Analysis Diff** | When a project is re-analyzed, the backend produces a diff. Diff banner on results page, project history timeline on history page. |
| **Re-Upload & Source Switching** | Users can re-upload a new ZIP or switch a project's source to Git via a dropdown menu on both the results and history pages. |

---

## 2. Authentication Flow

```
┌──────────┐     POST /api/auth/login      ┌──────────┐
│  Login   │ ──────────────────────────────▶│ Backend  │
│  Page    │◀────────────────────────────── │          │
└──────────┘   { token, user, expiresIn }  └──────────┘
      │
      │  Store token in localStorage
      │  Redirect to /upload
      ▼
┌──────────┐   Authorization: Bearer <token>  ┌──────────┐
│ Protected│ ────────────────────────────────▶│ Backend  │
│  Pages   │◀──────────────────────────────── │          │
└──────────┘        (normal responses)        └──────────┘
```

1. User visits `/login` or `/register`.
2. On success, `AuthService` stores JWT in `localStorage` and user object in a `BehaviorSubject`.
3. `authInterceptor` attaches `Authorization: Bearer <token>` to every outgoing request (except auth endpoints).
4. `errorInterceptor` catches `401` responses globally — clears the token and redirects to `/login`.
5. `authGuard` prevents navigation to protected routes when not authenticated.

### Token Storage

| Key | Value | Purpose |
|-----|-------|---------|
| `auth_token` | JWT string | Sent in `Authorization` header |
| `auth_user` | JSON `{ id, name, email }` | Displayed in header |

### Logout

Calling `AuthService.logout()` removes both keys from `localStorage`, resets the `currentUser$` observable to `null`, and navigates to `/login`.

---

## 3. New Files

### Services

| File | Description |
|------|-------------|
| `services/auth.service.ts` | Login, register, logout, token storage. Exposes `currentUser$` observable. |

### Interceptors

| File | Description |
|------|-------------|
| `interceptors/auth.interceptor.ts` | Attaches `Bearer` token to all requests except `/auth/*`. |
| `interceptors/error.interceptor.ts` | Catches `401` errors, calls `logout()`, redirects to `/login`. |

### Guards

| File | Description |
|------|-------------|
| `guards/auth.guard.ts` | `CanActivateFn` — checks `isAuthenticated()`, redirects to `/login` if false. |

### Pages

| File | Description |
|------|-------------|
| `pages/login/login-page.component.*` | Login form with email/password. |
| `pages/register/register-page.component.*` | Registration form with name/email/password. |

### Components

| File | Description |
|------|-------------|
| `components/health-score-breakdown/*` | Collapsible score category cards with progress bars and deduction lists. |
| `components/diff-banner/*` | **New.** Full re-analysis diff display — health score delta, score comparison, anti-pattern changes, category bars, metric deltas. |

---

## 4. Modified Files

### `src/main.ts`
- Registered `authInterceptor` and `errorInterceptor`.

### `src/app/app.routes.ts`
- Added `/login` and `/register` routes.
- Applied `authGuard` to `/upload`, `/results/:jobId`, `/history`.

### `src/app/app.component.*`
- Auth-aware navigation (Sign In/Sign Up vs Upload/History/Sign Out).

### `src/app/models/index.ts`
- **Updated `SourceType`** — now includes `'UPLOAD' | 'GITHUB' | 'GITLAB' | 'ZIP_UPLOAD' | 'GIT_CLONE'` for backward + forward compatibility.
- **Added `branch: string | null`** to `Project` interface.
- Added `healthScoreBreakdown` and `diff` fields to `AnalysisResult`.
- Added `analysisNumber` to `AnalysisJob`.
- Added `analysisCount` and `latestJobId` to `Project`.
- Added interfaces: `LoginRequest`, `RegisterRequest`, `AuthResponse`, `UserResponse`, `ApiError`.
- Added interfaces: `HealthScoreBreakdown`, `ScoreCategory`, `ScoreDeduction`.
- Added interfaces: `AnalysisDiffResponse`, `MetricDelta`, `DoubleDelta`, `AntiPatternChange`, `CategoryDelta`.
- **Added `ReanalyzeRequest`** — optional body for re-analyze with `repoUrl?` and `branch?`.

### `src/app/services/api.service.ts`
- **Updated `reanalyzeProject(projectId, request?)`** — now accepts optional `ReanalyzeRequest` to switch project source to Git.
- **Added `reuploadProject(projectId, file)`** — `POST /api/projects/{id}/reupload` with multipart/form-data and upload progress.
- Added `getProjectHistory(projectId)` → `GET /api/projects/{id}/history`.
- Added `getJobDiff(jobId)` → `GET /api/jobs/{id}/diff`.

### `src/app/components/health-score/health-score.component.*`
- Added `@Input() grade` and grade badge rendering.

### `src/app/pages/results/results-page.component.ts`
- Imported `FormsModule`, `FileUploadComponent`, `DiffBannerComponent`, `ProgressTrackerComponent`.
- Added `project: Project` — fetched to determine source type and pre-fill Git fields.
- **Replaced single Re-Analyze button with a dropdown menu** offering 3 options.
- Added **re-upload modal** with `FileUploadComponent`, upload progress tracking via `HttpEventType`.
- Added **Git clone modal** with URL validation and branch field (pre-filled from project).
- Added `isGitProject`, `isUploadProject`, `sourceLabel` getters for conditional display.
- `@HostListener('document:click')` to close dropdown on outside click.

### `src/app/pages/results/results-page.component.html`
- **Re-Analyze dropdown** with 3 options:
  1. 🔄 Re-analyze (current source) — re-clone Git or re-scan ZIP
  2. 📁 Upload new ZIP — opens modal with file picker
  3. 🔗 Clone from GitHub/GitLab — opens modal with URL + branch form
- **Re-upload modal** — file upload zone, progress bar, Upload & Analyze button.
- **Git clone modal** — repo URL input (validated), branch input, Clone & Analyze button.
- Diff banner shown when `result.diff` is not null.
- First-analysis info note shown when `diff` is null.
- Progress tracker shown during re-analysis polling.

### `src/app/pages/results/results-page.component.css`
- Added styles for: `.reanalyze-dropdown`, `.dropdown-menu`, `.dropdown-item`, `.dropdown-item-text`.
- Added styles for: `.modal-overlay`, `.modal-card`, `.modal-header`, `.modal-body`, `.modal-footer`, `.modal-close`.
- Added styles for: `.form-group`, `.form-label`, `.form-input`, `.validation-msg`.
- Added styles for: `.upload-progress`, `.upload-progress-bar`, `.upload-progress-text`.
- Added styles for: `.first-analysis-note`, `.reanalyze-error`.

### `src/app/pages/history/history-page.component.ts`
- Imported `FormsModule`, `FileUploadComponent`, `HttpEventType`.
- Added **dropdown state** (`openDropdownProjectId`) with `@HostListener` for outside click.
- Added `isGitProject(project)` and `sourceLabel(project)` helpers.
- **Re-upload modal state**: `showReuploadModal`, `reuploadProjectId`, `reuploadFile`, `isUploading`, `uploadProgress`.
- **Git clone modal state**: `showGitModal`, `gitProjectId`, `gitRepoUrl`, `gitBranch`.
- Added methods: `toggleDropdown()`, `openReuploadModal()`, `onReuploadFileSelected()`, `submitReupload()`, `openGitModal()`, `submitGitClone()`, `closeModals()`.
- Git URL validation via `isValidGitUrl` getter.

### `src/app/pages/history/history-page.component.html`
- **Replaced single Re-Analyze button** on each project card with a dropdown menu (same 3 options as results page).
- Updated source type badge display to handle `GITHUB`/`GITLAB`/`UPLOAD` values.
- Added **re-upload modal** and **Git clone modal** at page bottom.
- Pre-fills Git URL/branch from project's current `sourceUrl` and `branch`.

### `src/app/pages/history/history-page.component.css`
- Added styles for: `.type-github`, `.type-gitlab`, `.type-upload` source badges.
- Added styles for: `.reanalyze-dropdown`, `.dropdown-menu`, `.dropdown-item`.
- Added styles for: `.modal-overlay`, `.modal-card`, `.modal-header`, `.modal-body`, `.modal-footer`.
- Added styles for: `.form-group`, `.form-label`, `.form-input`, `.validation-msg`.
- Added styles for: `.upload-progress`, `.upload-progress-bar`, `.upload-progress-text`.

### `src/app/pages/upload/upload-page.component.ts`
- Added `uploadError` property.
- Updated `startUploadAnalysis()` error handler to use `error.error?.message`.
- Clears `uploadError` on reset and when starting a new upload.

### `src/app/pages/upload/upload-page.component.html`
- Displays `uploadError` message below the upload progress area.

---

## 5. Health Score Breakdown

The breakdown component renders 4 scoring categories:

| Category | Max Points | What It Measures |
|----------|:----------:|------------------|
| Anti-Patterns | 40 | Anti-patterns weighted by severity |
| Code Quality | 20 | Code smell density |
| Architecture | 25 | Coupling + dependency cycles |
| Service Sizing | 15 | Nano/God service detection |

### Grade Scale

| Score | Grade | Color |
|-------|:-----:|-------|
| 90–100 | A | Green |
| 80–89 | B | Green |
| 65–79 | C | Yellow |
| 50–64 | D | Orange |
| 0–49 | F | Red |

Each category card is **collapsible**. When expanded, it shows:
- A description of what the category measures.
- A list of deductions (reason + points lost).
- If no deductions exist, a "Perfect score — no issues found" message.

---

## 6. Re-Analysis Diff Feature

### 6.1 Overview

The re-analysis diff feature (inspired by SonarQube's "New Code") allows users to:

1. **Re-analyze** a project to pick up code changes (Git repos are re-cloned; ZIP projects are re-scanned).
2. **View a diff** comparing the latest analysis with the previous one.
3. **Browse the project history** to see how the architecture health has changed over time.

### 6.2 New API Calls

| Method | Frontend Method | Backend Endpoint |
|--------|----------------|------------------|
| Re-analyze project | `apiService.reanalyzeProject(projectId, request?)` | `POST /api/projects/{id}/reanalyze` |
| Re-upload project | `apiService.reuploadProject(projectId, file)` | `POST /api/projects/{id}/reupload` |
| Get project history | `apiService.getProjectHistory(projectId)` | `GET /api/projects/{id}/history` |
| Get standalone diff | `apiService.getJobDiff(jobId)` | `GET /api/jobs/{id}/diff` |

### 6.3 Diff Banner Component (`app-diff-banner`)

Renders when `result.diff` is not null. Has four collapsible sections:

| Section | Description |
|---------|-------------|
| **Header** | Analysis number, summary text, health score delta badge (green `+26 ↑` / red `-10 ↓`). |
| **Score Comparison** | Side-by-side Previous → Current scores with grade pills. |
| **Anti-Pattern Changes** | ✓ Resolved (green), ✗ New (red), ⚠ Unchanged (gray). Each shows severity badge, pattern name, description. |
| **Category Comparison** | For each `categoryDelta`: before/after progress bars with delta badge. |
| **Metric Changes** | Grid of all metric deltas with color-coded delta badges. Green = improvement, red = regression, gray = unchanged. |

### 6.4 Results Page Changes

- **Diff banner** rendered when `result.diff !== null`.
- **First-analysis info note** shown when `diff` is null: *"This is the first analysis for this project. Re-analyze later to track changes over time."*

### 6.5 History Page Timeline

Projects tab shows expandable cards. When expanded, loads `GET /api/projects/{id}/history` and renders each analysis with:
- Analysis number, health score, grade
- Delta badge (+/- with color)
- Resolved/new anti-pattern mini-badges
- "View details →" link

---

## 7. Re-Upload & Source-Type Switching

### 7.1 Re-Analyze Dropdown

On both the **results page** and **history page**, the single "Re-Analyze" button has been replaced with a **dropdown menu** offering three options:

```
┌─────────────────────────────────┐
│  Re-Analyze ▾                   │
├─────────────────────────────────┤
│  🔄 Re-analyze (current source) │  ← POST /reanalyze with {}
│  📁 Upload new ZIP              │  ← Opens modal, then POST /reupload
│  🔗 Clone from GitHub/GitLab    │  ← Opens modal, then POST /reanalyze with { repoUrl, branch }
└─────────────────────────────────┘
```

### 7.2 Option 1: Re-analyze (current source)

```typescript
apiService.reanalyzeProject(projectId)
// POST /api/projects/{id}/reanalyze with {}
```
For Git projects: re-clones the repo (picks up new commits). For ZIP projects: re-scans existing files.

### 7.3 Option 2: Upload new ZIP

```typescript
apiService.reuploadProject(projectId, file)
// POST /api/projects/{id}/reupload with multipart/form-data
```
Opens a modal with the `<app-file-upload>` component. Shows upload progress bar. On completion, triggers analysis polling. The project's source type switches to `UPLOAD`.

### 7.4 Option 3: Clone from GitHub/GitLab

```typescript
apiService.reanalyzeProject(projectId, { repoUrl, branch })
// POST /api/projects/{id}/reanalyze with { repoUrl, branch }
```
Opens a modal with repo URL + branch fields. Pre-fills from the project's current `sourceUrl`/`branch` for Git projects. The project's source type switches to `GITHUB`/`GITLAB`.

### 7.5 Source Type Display

The `SourceType` union includes both legacy (`ZIP_UPLOAD`, `GIT_CLONE`) and new (`UPLOAD`, `GITHUB`, `GITLAB`) values. Helper methods determine the display:
- `isGitProject()` checks for `GITHUB`, `GITLAB`, or `GIT_CLONE`
- `isUploadProject()` checks for `UPLOAD` or `ZIP_UPLOAD`
- Source type badges handle all variants with appropriate colors

### 7.6 New TypeScript Interfaces & API Methods

```typescript
// New request type (optional body for reanalyze)
interface ReanalyzeRequest {
  repoUrl?: string;   // if provided, switches to this Git repo
  branch?: string;    // optional branch (defaults to remote HEAD)
}

// Updated API method
reanalyzeProject(projectId: number, request?: ReanalyzeRequest): Observable<UploadResponse>
// POST /api/projects/{id}/reanalyze (optional body for source switching)

// New API method
reuploadProject(projectId: number, file: File): Observable<HttpEvent<UploadResponse>>
// POST /api/projects/{id}/reupload (multipart/form-data, with upload progress)
```

---

## 8. Error Handling

### Consistent Error Shape

All backend errors return:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable message",
  "timestamp": "2026-03-20T12:00:00"
}
```

The frontend uses `error.error?.message` in:
- Login/Register pages
- Upload page (upload failures, clone failures)
- Results page (re-analysis failures, re-upload failures)
- History page (re-analysis failures, re-upload failures)

### Global 401 Handling

The `errorInterceptor` catches `401` responses (except `/auth/*`), clears tokens, and redirects to `/login`.

---

## 9. How It Works

### Token Injection
The `authInterceptor` handles token injection automatically for all `HttpClient` calls — no per-method changes needed.

### Backward Compatibility
- `SourceType` includes both old (`ZIP_UPLOAD`, `GIT_CLONE`) and new (`UPLOAD`, `GITHUB`, `GITLAB`) values.
- `healthScoreBreakdown` is nullable — absent means no breakdown rendered.
- `diff` is nullable — absent means first analysis (info note shown).
- `branch` on `Project` is nullable — only set for Git projects.
- `ReanalyzeRequest` is optional — omitting it keeps the current source.

### Route Protection

```
/login          → Public
/register       → Public
/upload         → Protected (authGuard)
/results/:jobId → Protected (authGuard)
/history        → Protected (authGuard)
/               → Redirects to /upload
```

---

## 10. Backend Contract Alignment

All frontend interfaces and API calls verified against `BACKEND_CHANGES_README.md`. **No additional backend changes are required.**

### §1–§4: Authentication & Health Score Breakdown

| Concern | Status |
|---------|:------:|
| `AuthResponse` shape (`token`, `tokenType`, `expiresIn`, `user`) | ✅ Matches |
| `UserResponse` shape (`id`, `name`, `email`) | ✅ Matches |
| `HealthScoreBreakdown` shape (`overallScore`, `grade`, `categories[]`) | ✅ Matches |
| `ScoreCategory`/`ScoreDeduction` shapes | ✅ Matches |
| Auth interceptor + 401 handling | ✅ Correct |
| Error shape `{ error, message, timestamp }` | ✅ Correct |

### §6: Re-Analysis Diff

| Concern | Status |
|---------|:------:|
| `POST /api/projects/{id}/reanalyze` → `{ projectId, jobId }` | ✅ Matches |
| `GET /api/projects/{id}/history` → `AnalysisResult[]` with `diff` | ✅ Matches |
| `GET /api/jobs/{id}/diff` → `AnalysisDiffResponse` | ✅ Matches |
| `AnalysisDiffResponse` full shape (25+ fields) | ✅ Matches §6.3 |
| `MetricDelta` / `DoubleDelta` / `AntiPatternChange` / `CategoryDelta` | ✅ All match |
| `analysisNumber` on `AnalysisJob` | ✅ Matches |
| `analysisCount` / `latestJobId` on `Project` | ✅ Matches |

### §8: Re-Upload & Source-Type Switching

| Concern | Status |
|---------|:------:|
| `POST /api/projects/{id}/reupload` (multipart) → `{ projectId, jobId }` | ✅ Matches |
| `POST /api/projects/{id}/reanalyze` with optional `{ repoUrl, branch }` | ✅ Matches §8.2 |
| `ReanalyzeRequest` shape (`repoUrl?`, `branch?`) | ✅ Matches §8.5 |
| `SourceType` includes `UPLOAD`, `GITHUB`, `GITLAB` | ✅ Matches §8.3 |
| `Project.branch` field (nullable) | ✅ Matches `ProjectResponse` |
| Re-upload with progress tracking (`reportProgress: true`) | ✅ Same as initial upload |
| Git URL validation regex | ✅ Matches §8.2 validation |
| Dropdown menu with 3 options on results + history pages | ✅ Per §8.4 suggestion |
| Pre-fill Git URL/branch from project for Git projects | ✅ Per §8.4 conditional display |
| Source type badge display handles all 5 variants | ✅ Backward + forward compatible |
