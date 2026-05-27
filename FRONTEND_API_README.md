# MSA Detector — Frontend API Reference

> Complete API contract for the MSA Detector backend.
> Use this document to build the Angular frontend.

---

## Table of Contents

- [Base URL](#base-url)
- [API Endpoints](#api-endpoints)
  - [Projects](#projects)
  - [Jobs](#jobs)
- [Data Types & Enums](#data-types--enums)
- [Response Schemas](#response-schemas)
  - [UploadResponse](#uploadresponse)
  - [ProjectResponse](#projectresponse)
  - [MicroserviceResponse](#microserviceresponse)
  - [AnalysisJobResponse](#analysisjobresponse)
  - [AnalysisResultResponse](#analysisresultresponse)
  - [AntiPatternResponse](#antipatternresponse)
  - [CodeSnippet](#codesnippet)
  - [DependencyGraphResponse](#dependencygraphresponse)
- [Anti-Pattern Types](#anti-pattern-types)
- [Displaying Code Snippets](#displaying-code-snippets)
  - [Data Structure](#data-structure)
  - [What Each Detector Provides](#what-each-detector-provides)
  - [UI Component Guidelines](#ui-component-guidelines)
  - [Example Angular Component](#example-angular-component)
  - [Syntax Highlighting Libraries](#syntax-highlighting-libraries)

---

## Base URL

```
http://localhost:8080
```

All endpoints are prefixed with `/api`.

---

## API Endpoints

### Projects

| Method   | Path                     | Description                          | Request                                         | Response              |
|----------|--------------------------|--------------------------------------|--------------------------------------------------|-----------------------|
| `POST`   | `/api/projects/upload`   | Upload a ZIP and start analysis      | `multipart/form-data` — `file` (ZIP), `name` (string) | `UploadResponse`      |
| `POST`   | `/api/projects/clone`    | Clone a GitHub/GitLab repo and start analysis | `application/json` — `GitCloneRequest`    | `UploadResponse`      |
| `GET`    | `/api/projects`          | List all projects                    | —                                                | `ProjectResponse[]`   |
| `GET`    | `/api/projects/{id}`     | Get a single project                 | —                                                | `ProjectResponse`     |
| `DELETE` | `/api/projects/{id}`     | Delete a project                     | —                                                | `204 No Content`      |

### Jobs

| Method   | Path                     | Description                          | Response                  |
|----------|--------------------------|--------------------------------------|---------------------------|
| `GET`    | `/api/jobs/{id}`         | Poll job status / progress           | `AnalysisJobResponse`     |
| `GET`    | `/api/jobs/{id}/results` | Get completed analysis results       | `AnalysisResultResponse`  |
| `GET`    | `/api/jobs/recent`       | List 20 most recent jobs             | `AnalysisJobResponse[]`   |
| `POST`   | `/api/jobs/{id}/cancel`  | Cancel a running job                 | `200 OK`                  |

---

## Data Types & Enums

### JobStatus

```typescript
export type JobStatus =
  | 'PENDING'
  | 'CLONING'
  | 'DETECTING_SERVICES'
  | 'ANALYZING_SERVICES'
  | 'BUILDING_GRAPH'
  | 'DETECTING_PATTERNS'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';
```

### Severity

```typescript
export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
```

### AntiPatternType

```typescript
export type AntiPatternType =
  | 'CYCLIC_DEPENDENCY'
  | 'SHARED_DATABASE'
  | 'NANO_SERVICE'
  | 'GOD_SERVICE'
  | 'CHATTY_SERVICE'
  | 'HARDCODED_ENDPOINTS'
  | 'DISTRIBUTED_MONOLITH'
  | 'API_VERSIONING_ABSENCE'
  | 'WRONG_CUTS'
  | 'ESB_MISUSE';
```

### SourceType

```typescript
export type SourceType = 'ZIP_UPLOAD' | 'GIT_CLONE';
```

---

## Response Schemas

### UploadResponse

Returned by both `/upload` and `/clone` endpoints.

```typescript
export interface UploadResponse {
  projectId: number;
  jobId: number;
}
```

### GitCloneRequest

Used with `POST /api/projects/clone` to analyse a public GitHub or GitLab repository.

```typescript
export interface GitCloneRequest {
  repoUrl: string;    // required — HTTPS URL, e.g. "https://github.com/owner/repo"
  name: string;       // required — display name for the project
  branch?: string;    // optional — defaults to "main"
}
```

> **Supported URL formats:**
> - `https://github.com/owner/repo`
> - `https://github.com/owner/repo.git`
> - `https://gitlab.com/owner/repo`
> - `https://gitlab.com/owner/repo.git`
>
> Only **public** repositories are supported. Private repos will return an error.

### ProjectResponse

```typescript
export interface ProjectResponse {
  id: number;
  name: string;
  sourceType: SourceType;
  sourceUrl: string | null;
  createdAt: string; // ISO 8601 datetime
  microservices: MicroserviceResponse[];
}
```

### MicroserviceResponse

```typescript
export interface MicroserviceResponse {
  id: number;
  name: string;
  relativePath: string;
  linesOfCode: number;
  numberOfEndpoints: number;
}
```

### AnalysisJobResponse

```typescript
export interface AnalysisJobResponse {
  id: number;
  projectId: number;
  status: JobStatus;
  currentPhase: string | null;
  currentService: string | null;
  servicesCompleted: number | null;
  totalServices: number | null;
  progressPercentage: number | null;
  startedAt: string | null;   // ISO 8601
  completedAt: string | null;  // ISO 8601
  errorMessage: string | null;
}
```

### AnalysisResultResponse

```typescript
export interface AnalysisResultResponse {
  id: number;
  jobId: number;
  healthScore: number;         // 0–100
  servicesAnalyzed: number;
  totalAntiPatterns: number;
  totalCodeSmells: number;
  criticalIssues: number;
  highIssues: number;
  mediumIssues: number;
  lowIssues: number;
  totalLinesOfCode: number;
  averageServiceSize: number;
  cycleCount: number;
  antiPatterns: AntiPatternResponse[];
  dependencyGraph: DependencyGraphResponse;
}
```

### AntiPatternResponse

```typescript
export interface AntiPatternResponse {
  id: number;
  patternType: AntiPatternType;
  severity: Severity;
  description: string;
  affectedServices: string[];
  remediation: string;
  codeSnippets: CodeSnippet[];  // ← NEW: source code evidence
}
```

### CodeSnippet

Each anti-pattern can include zero or more code snippets that show **exactly where** in the source code the issue was detected.

```typescript
export interface CodeSnippet {
  file: string;          // relative or absolute file path (always uses '/' separators)
  startLine: number;     // first line of the snippet (1-based)
  endLine: number;       // last line of the snippet (1-based)
  highlightLine: number; // the specific line where the issue was detected (1-based)
  snippet: string;       // the actual source code text (multi-line, '\n' separated)
}
```

### DependencyGraphResponse

```typescript
export interface DependencyGraphResponse {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

export interface GraphNode {
  id: string;
  name: string;
  linesOfCode: number;
}

export interface GraphEdge {
  source: string; // node id
  target: string; // node id
  type: string;   // 'REST_SYNC' | 'FEIGN_CLIENT' | 'REST_TEMPLATE' | 'WEB_CLIENT' | ...
  weight: number; // call count
}
```

---

## Anti-Pattern Types

| Key                      | Display Name              | Severity  | Description                                                      |
|--------------------------|---------------------------|-----------|------------------------------------------------------------------|
| `CYCLIC_DEPENDENCY`      | Cyclic Dependency         | CRITICAL  | Circular dependencies between services                           |
| `SHARED_DATABASE`        | Shared Database           | HIGH      | Multiple services accessing the same database                    |
| `NANO_SERVICE`           | Nano Service              | MEDIUM    | Service too small to justify operational overhead                |
| `GOD_SERVICE`            | God Service               | HIGH      | Service handling too many responsibilities                       |
| `CHATTY_SERVICE`         | Chatty Service            | HIGH      | Excessive fine-grained communication between services            |
| `HARDCODED_ENDPOINTS`    | Hardcoded Endpoints       | MEDIUM    | Service URLs hardcoded instead of using service discovery        |
| `DISTRIBUTED_MONOLITH`   | Distributed Monolith      | CRITICAL  | Tightly coupled services that must be deployed together          |
| `API_VERSIONING_ABSENCE` | API Versioning Absence    | MEDIUM    | No API versioning strategy detected                              |
| `WRONG_CUTS`             | Wrong Cuts                | HIGH      | Misplaced service boundaries causing high coupling               |
| `ESB_MISUSE`             | ESB Misuse                | HIGH      | Single service mediating most inter-service communication        |

---

## Displaying Code Snippets

### Data Structure

The `codeSnippets` array on each `AntiPatternResponse` contains source code evidence. Each snippet includes:

- **`file`** — the path to the source file (e.g. `order-service/src/main/java/com/example/OrderController.java`)
- **`startLine` / `endLine`** — the line range of the snippet (1-based, inclusive)
- **`highlightLine`** — the specific line that triggered the detection (should be visually highlighted)
- **`snippet`** — the raw source code text, with lines separated by `\n`

### What Each Detector Provides

| Anti-Pattern              | Snippet Content                                                                 |
|---------------------------|---------------------------------------------------------------------------------|
| `GOD_SERVICE`             | The class declaration where DesigniteJava flagged a "God Class" smell           |
| `CHATTY_SERVICE`          | The inter-service call site (RestTemplate / FeignClient / WebClient invocation) |
| `CYCLIC_DEPENDENCY`       | The inter-service call evidence for each edge in the dependency cycle           |
| `SHARED_DATABASE`         | The `application.yml` / `application.properties` datasource configuration      |
| `NANO_SERVICE`            | The service's `@SpringBootApplication` main class (or first Java file)          |
| `HARDCODED_ENDPOINTS`     | The source line containing a hardcoded URL (up to 5 occurrences)               |
| `API_VERSIONING_ABSENCE`  | The controller class declaration with `@RequestMapping` / `@RestController`     |
| `DISTRIBUTED_MONOLITH`    | Inter-service call evidence from the most coupled dependencies                 |
| `WRONG_CUTS`              | Feature Envy smell locations, or both sides of bidirectional dependencies       |
| `ESB_MISUSE`              | Incoming/outgoing call evidence through the mediator service                   |

> **Note:** `codeSnippets` may be an **empty array** (`[]`) when no source-level evidence could be read (e.g. files deleted after analysis, or the detection was purely metric-based).

### UI Component Guidelines

1. **Split the snippet into lines** using `snippet.split('\n')`.
2. **Number each line** starting from `startLine`.
3. **Highlight the `highlightLine`** — use a distinct background color (e.g. a soft red/yellow) to draw attention to the offending line.
4. **Show the file path** above the snippet as a header, optionally extracting just the filename for compact views.
5. **Apply syntax highlighting** based on the file extension (`.java`, `.yml`, `.properties`, `.xml`).
6. **Cap the display** — if an anti-pattern has many snippets, consider showing 2–3 by default with a "Show more" toggle.
7. **Collapse by default** on list views; expand on detail views or on click.

### Example JSON Response

```json
{
  "id": 42,
  "patternType": "HARDCODED_ENDPOINTS",
  "severity": "MEDIUM",
  "description": "Service 'order-service' has 3 hardcoded endpoint URL(s).",
  "affectedServices": ["order-service"],
  "remediation": "Use service discovery (e.g., Eureka, Consul) or externalize URLs to configuration",
  "codeSnippets": [
    {
      "file": "order-service/src/main/java/com/example/OrderService.java",
      "startLine": 22,
      "endLine": 28,
      "highlightLine": 25,
      "snippet": "    @Autowired\n    private RestTemplate restTemplate;\n\n    public User getUser(Long id) {\n        return restTemplate.getForObject(\"http://localhost:8081/users/\" + id, User.class);\n    }\n}"
    },
    {
      "file": "order-service/src/main/java/com/example/PaymentClient.java",
      "startLine": 10,
      "endLine": 16,
      "highlightLine": 13,
      "snippet": "@Service\npublic class PaymentClient {\n\n    private static final String PAYMENT_URL = \"http://payment-service:8080/pay\";\n\n    public PaymentResult pay(Order order) {\n        // ..."
    }
  ]
}
```

### Example Angular Component

Below is a standalone Angular component for rendering code snippets with line numbers and line highlighting.

#### `code-snippet-viewer.component.ts`

```typescript
import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CodeSnippet } from '../models/analysis.models';

interface SnippetLine {
  lineNumber: number;
  code: string;
  isHighlighted: boolean;
}

@Component({
  selector: 'app-code-snippet-viewer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './code-snippet-viewer.component.html',
  styleUrls: ['./code-snippet-viewer.component.scss'],
})
export class CodeSnippetViewerComponent {
  /** The list of code snippets to display. */
  @Input() snippets: CodeSnippet[] = [];

  /** Maximum number of snippets shown before "Show more" appears. */
  @Input() maxVisible = 3;

  showAll = false;

  get visibleSnippets(): CodeSnippet[] {
    return this.showAll ? this.snippets : this.snippets.slice(0, this.maxVisible);
  }

  get hiddenCount(): number {
    return this.snippets.length - this.maxVisible;
  }

  getLines(snippet: CodeSnippet): SnippetLine[] {
    return snippet.snippet.split('\n').map((code, i) => {
      const lineNumber = snippet.startLine + i;
      return {
        lineNumber,
        code,
        isHighlighted: lineNumber === snippet.highlightLine,
      };
    });
  }

  getFileName(filePath: string): string {
    return filePath.split('/').pop() || filePath;
  }

  getFileDir(filePath: string): string {
    const lastSlash = filePath.lastIndexOf('/');
    return lastSlash > 0 ? filePath.substring(0, lastSlash) : '';
  }

  getLanguage(filePath: string): string {
    if (filePath.endsWith('.java')) return 'java';
    if (filePath.endsWith('.yml') || filePath.endsWith('.yaml')) return 'yaml';
    if (filePath.endsWith('.properties')) return 'properties';
    if (filePath.endsWith('.xml')) return 'xml';
    if (filePath.endsWith('.json')) return 'json';
    return 'text';
  }
}
```

#### `code-snippet-viewer.component.html`

```html
@if (snippets.length > 0) {
  <div class="snippets-container">
    <h4 class="snippets-heading">Source Evidence</h4>

    @for (snippet of visibleSnippets; track snippet.file + snippet.startLine) {
      <div class="snippet-block">
        <!-- File header -->
        <div class="snippet-header">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14"
               viewBox="0 0 24 24" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="16 18 22 12 16 6" />
            <polyline points="8 6 2 12 8 18" />
          </svg>
          <span class="file-dir">{{ getFileDir(snippet.file) }}/</span>
          <span class="file-name">{{ getFileName(snippet.file) }}</span>
        </div>

        <!-- Code block -->
        <pre class="snippet-code">@for (line of getLines(snippet); track line.lineNumber) {
<div class="code-line" [class.highlighted]="line.isHighlighted"><span class="line-number">{{ line.lineNumber }}</span><code>{{ line.code }}</code></div>}</pre>
      </div>
    }

    @if (!showAll && snippets.length > maxVisible) {
      <button class="show-more-btn" (click)="showAll = true">
        Show {{ hiddenCount }} more snippet(s)
      </button>
    }
  </div>
}
```

#### `code-snippet-viewer.component.scss`

```scss
.snippets-container {
  margin-top: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.snippets-heading {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
  margin: 0;
}

.snippet-block {
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  overflow: hidden;
  background: #f9fafb;
}

.snippet-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.375rem 0.75rem;
  background: #f3f4f6;
  border-bottom: 1px solid #e5e7eb;
  font-family: 'Fira Code', 'Consolas', monospace;
  font-size: 0.7rem;
  color: #6b7280;

  svg {
    flex-shrink: 0;
  }

  .file-dir {
    color: #9ca3af;
  }

  .file-name {
    font-weight: 600;
    color: #374151;
  }
}

.snippet-code {
  margin: 0;
  padding: 0;
  font-size: 0.8rem;
  line-height: 1.6;
  overflow-x: auto;
}

.code-line {
  display: flex;
  border-left: 3px solid transparent;
  padding-right: 1rem;

  &.highlighted {
    background: #fef2f2;
    border-left-color: #f87171;

    .line-number {
      color: #ef4444;
      font-weight: 700;
    }
  }
}

.line-number {
  display: inline-block;
  width: 3rem;
  text-align: right;
  padding-right: 0.75rem;
  color: #9ca3af;
  user-select: none;
  font-size: 0.7rem;
  line-height: 1.6;
  flex-shrink: 0;
}

code {
  flex: 1;
  padding-left: 0.5rem;
  font-family: 'Fira Code', 'Consolas', monospace;
  white-space: pre;
}

.show-more-btn {
  background: none;
  border: none;
  color: #2563eb;
  font-size: 0.8rem;
  cursor: pointer;
  padding: 0;
  text-align: left;

  &:hover {
    text-decoration: underline;
  }
}
```

### Integration in the Anti-Pattern Detail Card

Use the component inside any template that has an `AntiPatternResponse` object:

```html
<div class="anti-pattern-card">
  <!-- Severity badge + title -->
  <div class="card-header">
    <app-severity-badge [severity]="pattern.severity" />
    <h3>{{ getDisplayName(pattern.patternType) }}</h3>
  </div>

  <!-- Description -->
  <p class="description">{{ pattern.description }}</p>

  <!-- Affected services chips -->
  <div class="service-chips">
    @for (svc of pattern.affectedServices; track svc) {
      <span class="chip">{{ svc }}</span>
    }
  </div>

  <!-- ✨ Code snippets -->
  <app-code-snippet-viewer [snippets]="pattern.codeSnippets" />

  <!-- Remediation -->
  <div class="remediation-box">
    <strong>Remediation:</strong> {{ pattern.remediation }}
  </div>
</div>
```

### Syntax Highlighting Libraries

For production-quality syntax highlighting in Angular, consider:

| Library | Notes |
|---------|-------|
| [ngx-highlightjs](https://www.npmjs.com/package/ngx-highlightjs) | Angular wrapper for highlight.js — wide language support, easy setup |
| [Prism.js](https://prismjs.com/) (manual integration) | Lightweight; call `Prism.highlightElement()` in `ngAfterViewInit` |
| [ngx-prism](https://www.npmjs.com/package/@ngx-prism/core) | Angular directive wrapper for Prism.js |
| [CodeMirror 6](https://codemirror.net/) + `ngx-codemirror` | Full editor component; use `readOnly` mode for display |

#### Quick setup with `ngx-highlightjs`

```bash
npm install ngx-highlightjs highlight.js
```

```typescript
// app.config.ts (standalone)
import { provideHighlightOptions } from 'ngx-highlightjs';

export const appConfig = {
  providers: [
    provideHighlightOptions({
      coreLibraryLoader: () => import('highlight.js/lib/core'),
      languages: {
        java: () => import('highlight.js/lib/languages/java'),
        yaml: () => import('highlight.js/lib/languages/yaml'),
        properties: () => import('highlight.js/lib/languages/properties'),
        xml: () => import('highlight.js/lib/languages/xml'),
      },
    }),
  ],
};
```

Then in your snippet template, replace the plain `<code>` with:

```html
<code [highlight]="line.code" [language]="getLanguage(snippet.file)"></code>
```

---

## Angular Service Example

A minimal Angular `HttpClient` service for communicating with the backend:

```typescript
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  UploadResponse,
  ProjectResponse,
  AnalysisJobResponse,
  AnalysisResultResponse,
  GitCloneRequest,
} from '../models/analysis.models';

@Injectable({ providedIn: 'root' })
export class MsaDetectorService {
  private readonly baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  // ── Projects ──────────────────────────────────────────

  uploadProject(file: File, name: string): Observable<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('name', name);
    return this.http.post<UploadResponse>(`${this.baseUrl}/projects/upload`, formData);
  }

  cloneProject(request: GitCloneRequest): Observable<UploadResponse> {
    return this.http.post<UploadResponse>(`${this.baseUrl}/projects/clone`, request);
  }

  getProjects(): Observable<ProjectResponse[]> {
    return this.http.get<ProjectResponse[]>(`${this.baseUrl}/projects`);
  }

  getProject(id: number): Observable<ProjectResponse> {
    return this.http.get<ProjectResponse>(`${this.baseUrl}/projects/${id}`);
  }

  deleteProject(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/projects/${id}`);
  }

  // ── Jobs ──────────────────────────────────────────────

  getJobStatus(jobId: number): Observable<AnalysisJobResponse> {
    return this.http.get<AnalysisJobResponse>(`${this.baseUrl}/jobs/${jobId}`);
  }

  getJobResults(jobId: number): Observable<AnalysisResultResponse> {
    return this.http.get<AnalysisResultResponse>(`${this.baseUrl}/jobs/${jobId}/results`);
  }

  getRecentJobs(): Observable<AnalysisJobResponse[]> {
    return this.http.get<AnalysisJobResponse[]>(`${this.baseUrl}/jobs/recent`);
  }

  cancelJob(jobId: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/jobs/${jobId}/cancel`, null);
  }
}
```

### Polling job status with RxJS

```typescript
import { interval, switchMap, takeWhile, tap } from 'rxjs';

pollJobStatus(jobId: number): void {
  interval(2000).pipe(
    switchMap(() => this.msaService.getJobStatus(jobId)),
    tap(job => this.jobStatus = job),
    takeWhile(job =>
      job.status !== 'COMPLETED' &&
      job.status !== 'FAILED' &&
      job.status !== 'CANCELLED'
    ),
  ).subscribe({
    complete: () => {
      if (this.jobStatus?.status === 'COMPLETED') {
        this.msaService.getJobResults(jobId).subscribe(results => {
          this.analysisResult = results;
        });
      }
    },
  });
}
```

---

## Typical Frontend Workflow

```
1a. POST /api/projects/upload  →  { projectId, jobId }   (ZIP upload)
1b. POST /api/projects/clone   →  { projectId, jobId }   (GitHub/GitLab URL)
2.  Poll GET /api/jobs/{jobId}  →  check status & progressPercentage
3.  When status === "COMPLETED":
      GET /api/jobs/{jobId}/results  →  full AnalysisResultResponse
4.  Render:
      • Health score gauge (0–100)
      • Summary cards (critical / high / medium / low counts)
      • Anti-pattern list with expandable code snippets
      • Dependency graph visualization (nodes + edges)
```

---

## CORS

The backend allows all origins in development. The frontend can run on any port (e.g. `http://localhost:4200`).

