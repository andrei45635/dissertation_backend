# MSA Detector

**Find architectural anti-patterns in Java microservice systems — automatically, with no running system and no manual setup.**

MSA Detector is a static-analysis tool that scans a Java/Spring Boot microservice codebase, reconstructs the inter-service dependency graph, and reports architectural anti-patterns (cyclic dependencies, shared databases, chatty services, and more) — each with the exact source code that triggered it, a remediation hint, and a deduction from a single **0–100 architectural health score**.

Point it at a Git URL or upload a ZIP. It figures out where the services are on its own.

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen)
![Angular](https://img.shields.io/badge/Angular-19-red)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)
![License](https://img.shields.io/badge/License-MIT-yellow)

> Frontend lives in a separate repo: **[dissertation_frontend](https://github.com/andrei45635/dissertation_frontend)**

---

## Why

Microservices are easy to get wrong in ways that don't show up until much later: two services quietly sharing a database, a cycle of calls that makes independent deployment impossible, a "service" that's really just a utility module. These are well-known anti-patterns, but most tooling either needs a *running* system to find them, works below the service-boundary level, or makes you list your services by hand.

MSA Detector works on the source repository alone, identifies the service boundaries itself, and gives you a report you can act on directly in code review or a CI step.

## Features

- 🔍 **Detects 10 architectural anti-patterns** — across service design, communication, data management, and coupling (see the table below).
- 🧩 **Automatic microservice boundary detection** — a three-signal *deployability gate* (framework entry point, Dockerfile, or `main()` method) finds the real services in a multi-module Maven/Gradle repo and filters out libraries, BOMs, and test modules.
- 📊 **Composite health score (0–100 + letter grade)** — decomposed into four weighted categories so you can see *which* dimension is dragging the score down, not just a single opaque number.
- 🧠 **Multi-level analysis** — combines code-level smell density (via [DesigniteJava](https://www.designite-tools.com/)) and structural metrics (via [Spoon](https://github.com/INRIA/spoon)) with graph-level dependency analysis.
- 📎 **Evidence-based findings** — every detection ships with the offending `@FeignClient` / `RestTemplate` snippet, the affected services, and a remediation suggestion.
- 🕒 **Analysis history & diffs** — re-analyze after a refactor and see exactly which findings were resolved or introduced, and how the score moved.
- 🌐 **Web dashboard + REST API** — interactive use through an Angular UI, or script it into a CI pipeline as a quality gate.
- 📈 **Interactive dependency graph** — explore service topology visually (Cytoscape.js).

## Detected anti-patterns

| Anti-pattern | Dimension | Severity | What it flags |
|---|---|---|---|
| **Cyclic Dependency** | Communication | Critical | Circular call chains between services (Tarjan SCC) |
| **Distributed Monolith** | Coupling | Critical | Services so coupled they can't deploy independently |
| **Shared Database** | Data | High | Multiple services pointing at the same datasource |
| **God Service** | Service design | High | A service concentrating too many responsibilities |
| **Chatty Service** | Communication | High | Excessive fine-grained remote calls |
| **ESB Misuse** | Communication | High | One service mediating most traffic (high betweenness centrality) |
| **Wrong Cuts** | Coupling | High | Bidirectional dependencies signalling misplaced boundaries |
| **Nano Service** | Service design | Medium | Services too small to justify their overhead |
| **Hardcoded Endpoints** | Communication | Medium | Service URLs hardcoded instead of discovered |
| **API Versioning Absence** | Coupling | Medium | REST endpoints exposed with no versioning strategy |

## How the health score works

The score starts at 100 and deducts across four independently-capped categories:

| Category | Budget | Driven by |
|---|---|---|
| Anti-Patterns | 40 | Severity-weighted penalties (Critical −8, High −5, Medium −3) |
| Code Quality | 20 | Code-smell density (smells per 1,000 LOC) |
| Architecture | 25 | Dependency-graph coupling coefficient + cycle count |
| Service Sizing | 15 | Nano- and god-service penalties |

Grades: **A** ≥ 90 · **B** ≥ 80 · **C** ≥ 65 · **D** ≥ 50 · **F** < 50.

## Quick start (Docker Compose)

**Prerequisites**

- Docker + Docker Compose
- [DesigniteJava](https://www.designite-tools.com/) JAR (free for academic use) — used for code-smell detection
- The frontend repo cloned **next to** this one (Compose builds it from `../dissertation_frontend`)

```bash
# 1. Clone both repos as siblings
git clone https://github.com/andrei45635/dissertation_backend.git
git clone https://github.com/andrei45635/dissertation_frontend.git
cd dissertation_backend

# 2. Drop the DesigniteJava JAR here (mounted read-only into the backend)
mkdir -p lib
cp /path/to/DesigniteJava.jar lib/DesigniteJava.jar

# 3. Configure environment
cp .env.example .env        # then edit DB_PASSWORD and JWT_SECRET

# 4. Launch the stack
docker compose up --build
```

Then open:

| Service | URL |
|---|---|
| Web dashboard | http://localhost:4200 |
| Backend API | http://localhost:8080/api |
| API docs (Swagger UI) | http://localhost:8080/swagger-ui.html |
| pgAdmin (optional, `--profile tools`) | http://localhost:5050 |

Register an account in the UI, then submit a project by Git URL or ZIP upload.

## Tech stack

**Backend** — Java 25 · Spring Boot 4.0 · Spring Data JPA / Hibernate · Spring Security + JWT · [Spoon](https://github.com/INRIA/spoon) (AST analysis) · [DesigniteJava](https://www.designite-tools.com/) (code smells) · JGit · Flyway · PostgreSQL 16
**Frontend** — Angular 19 · TypeScript · Cytoscape.js
**Infra** — Docker · Docker Compose · Eclipse Temurin 25 (Alpine)
**Testing** — JUnit 5 · Mockito · Testcontainers (185 unit tests)

## REST API

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/auth/register`, `/api/auth/login` | POST | Authentication (JWT) |
| `/api/projects/upload` | POST | Upload a ZIP and start analysis |
| `/api/projects/clone` | POST | Clone a Git repo and start analysis |
| `/api/projects/{id}/history` | GET | Analysis history with diffs |
| `/api/jobs/{id}` | GET | Job status & progress |
| `/api/jobs/{id}/results` | GET | Results with health-score breakdown |
| `/api/jobs/{id}/diff` | GET | Diff against the previous run |

All endpoints except auth, Swagger UI, and actuator health require a JWT bearer token.

**CI integration:** because submission and result retrieval are plain REST calls, a pipeline step can submit a repo, poll the job, read the health score, and fail the build below a threshold — the same pattern as a SonarQube quality gate.

## Local development

```bash
# Backend (needs JDK 25, Maven, and a running PostgreSQL)
./mvnw spring-boot:run          # or: mvn spring-boot:run

# Run the test suite
mvn test
```

## Configuration

Detection thresholds and operational settings are environment variables (see [`.env.example`](.env.example)) — no recompile needed:

| Variable | Default | Description |
|---|---|---|
| `NANO_MAX_LOC` | 500 | LOC ceiling for nano-service detection |
| `NANO_MAX_ENDPOINTS` | 2 | Endpoint ceiling for nano-service detection |
| `CHATTY_MIN_CALLS` | 5 | Call-count threshold for chatty services |
| `CODE_SMELL_DENSITY_THRESHOLD` | 80 | Smells/KLOC at which Code Quality hits full penalty |
| `CLONE_TIMEOUT` | 300 | Git clone timeout (seconds) |
| `ANALYSIS_TIMEOUT` | 1800 | DesigniteJava timeout (seconds) |

## Scope & limitations

- Targets **JVM microservices**, optimised for **Spring Boot** conventions (Feign, `RestTemplate`, `WebClient`, Spring config). Non-Java services in a polyglot system aren't analyzed.
- **Static analysis only** — anti-patterns that manifest purely at runtime won't be caught.
- Detection thresholds favour recall; calibrate them to your team's conventions before using the score as a hard CI gate.

## Academic context

MSA Detector was built as a master's dissertation on automated architectural quality assurance for microservice systems. If you use it in academic work, please cite it:

```bibtex
@mastersthesis{iacob_msadetector,
  author = {Iacob, Andrei},
  title  = {Diagnosing the Distributed: A Static Analysis Approach to
            Microservice Anti-Pattern Detection},
  school = {Babe\c{s}-Bolyai University},
  year   = {2026}
}
```

## License

Released under the MIT License.

## Acknowledgments

Built on the shoulders of [Spoon](https://github.com/INRIA/spoon) (INRIA) for AST analysis and [DesigniteJava](https://www.designite-tools.com/) (Tushar Sharma) for code-smell detection.
