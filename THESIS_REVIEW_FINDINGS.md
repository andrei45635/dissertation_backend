# Thesis Review Findings (Round 4)

Cross-reference of the thesis against the backend codebase. Updated after adding NetworkDisk and microservice-recruit, formalizing betweenness centrality, adding the deployability gate, and all related prose updates.

Items marked **FIXED** were resolved in prior rounds. Items marked **OPEN** remain.

---

## FIXED Since Round 1/2/3

- Betweenness centrality not implemented → **FIXED** in `EsbMisuseDetector` (Brandes' algorithm)
- ESB Misuse severity mismatch (Medium vs High in Table 2.1) → **FIXED**
- Two conflicting health score formulas → **FIXED** (legacy removed, `HealthScoreCalculator` used)
- Per-job detection flags ignored → **FIXED** (`isDetectorEnabled()` added)
- `/api/**` vs `/api/auth/**` typo → **FIXED**
- gRPC/message queue claims in Ch.2 → **FIXED** (commented out)
- Ch.3 line 80 "messaging" in dependency types → **FIXED** (now "REST synchronous, Feign client, etc.")
- Stale evaluation numbers (4 projects, 60 services, etc.) → **FIXED** (now 8 projects, 116 services, ~350k LOC, 106 anti-patterns)
- Activiti/Karate replaced with Site-Where/Genie → **FIXED** throughout Ch.5, Ch.6, abstract, slides
- Apollo-Config grade D → F → **FIXED** (score 59 = D, correct)
- Missing Train-Ticket/Apollo-Config description paragraphs → **FIXED**
- Anti-pattern distribution table missing Wrong Cuts/ESB Misuse rows → **FIXED**
- Duplicate rows in datasets/results tables → **FIXED**
- Ch.4 typos (currrently, containg, messsage, etc.) → **FIXED**
- Ch.3 nano service equation missing underscore → **FIXED**
- Slide deck (build_deck.js) updated for 8 projects, new numbers, deployability gate → **FIXED**
- NetworkDisk and microservice-recruit added → **FIXED** (Ch.5 tables, prose, Ch.6, abstract)
- Ch.3 ESB section missing betweenness centrality formalization → **FIXED** (3 signals, BC formula, normalization, Brandes 2001 citation, updated decision rule equation)
- `DependencyType` enum unused values → **FIXED** (commented as future-work placeholders)
- MapStruct not actually used → **FIXED** (footnote added to Ch.4 Table 4.1 clarifying status)
- Cyclic Dependency and Wrong Cuts unvalidated → **FIXED** (both now detected in microservice-recruit)
- Ch.3 microservice detection heuristic not described → **FIXED** (new §3.1.7 deployability gate subsection)
- Ch.4 Phase 1 description stale (build-file only) → **FIXED** (rewritten for deployability gate)
- Ch.4 Microservice entity missing new fields → **FIXED** (`detectionConfidence`, `detectionSignal` added to entity description)
- Ch.5 §5.1.2 project descriptions verbose/bolded → **FIXED** (rewritten as flowing prose grouped by scale, URLs in footnotes)
- Zhao et al. cross-service clone citation added → **FIXED** (Ch.5, references.bib)
- Brandes 2001 reference missing → **FIXED** (added to references.bib)

---


## OPEN — Backend Code Issues

### 5. MEDIUM: CORS config reflects any origin with credentials

`CorsFilterConfig.java:23` — `Access-Control-Allow-Origin` set to `request.getHeader("Origin")` with credentials. OWASP concern.

### 6. LOW: Hardcoded JWT secret in default config

`application.yml:39` — well-known default secret. Production docker-compose requires env var, but default is still there.

### 7. LOW: Job cancellation is cosmetic

`AnalysisWorker.processJob()` has no cancellation check. Setting CANCELLED via API doesn't stop the running thread.

### 8. LOW: Flyway disabled in YAML but manually forced on

`spring.flyway.enabled: false` in config, but `FlywayConfig.java` runs it via `@PostConstruct`. Works but contradictory.

---

## OPEN — Evaluation Coverage Gap

### 9. MEDIUM: Distributed Monolith detector unvalidated

Distributed Monolith is the only anti-pattern type with zero detections across all eight evaluated projects. The absence is plausible given the projects' communication topologies, but leaves the detector unvalidated against the evaluation corpus.


---

## Manual Inspection Notes (Borderline Cases)

### Apollo-Config

- **Nano Service (apollo-assembly)**: The flagged module is `apollo-assembly`, which appears to be an orchestration/aggregation module rather than an independently deployable business service. Its role is to package and assemble the other Apollo modules. This is arguably a false positive — the deployability gate may have picked it up via a `main()` method or Dockerfile, but it is not a true microservice boundary. Worth noting in the limitations section.

- **Hardcoded Endpoint**: The detected instance was `String homePageUrl = "http://" + instance.getHost() + ":" + instance.getPort() + "/";` — this is dynamically constructed from a service instance object (likely Eureka/Consul metadata), not a literal hardcoded URL. This is a **false positive**: the URL is resolved at runtime from the service registry, not hardcoded. The detector's string-literal heuristic cannot distinguish dynamic concatenation from a fixed string. Worth noting as a known limitation of the regex-based approach.

### microservice-recruit

- **Wrong Cuts (pf-recruit ↔ pf-resume)**: The detected bidirectional dependency is:
  - `pf-recruit` calls `pf-resume` via `ResumeClient.getRate()` (`POST /resume/rate/batch`)
  - `pf-resume` calls `pf-recruit` via `RecruitClient.getRecruit()` (`GET /recruit/one`)
  
  This is a genuine bidirectional dependency and is consistent with the Wrong Cuts definition (tightly coupled services with mutual calls suggesting a misplaced boundary). Whether it represents a true architectural problem or an intentional design decision is debatable — recruitment logically needs resume data and vice versa — but the detection itself is technically correct. Could be classified as a **true positive with nuance** (the boundary may be correct, but the bidirectional coupling warrants review).

### NetworkDisk

- **Hardcoded Endpoint**: The detected instance was `return "http://"+trackerServer.getInetSocketAddress().getHostString()+":"+ClientGlobal.getG_tracker_http_port()+"/";` — similar to the Apollo case, this is dynamically constructed from a socket address object and a global config variable, not a literal fixed URL. This is another **false positive** from the regex-based detector: the host and port are resolved at runtime, not hardcoded. Reinforces the limitation of string-literal pattern matching for this anti-pattern.

---

## Priority Fix List

1. **CORS config** (`CorsFilterConfig.java:23`) — low-effort security improvement

---

## Detector Code Review

### HIGH

**1. `HardcodedEndpointDetector`: duplicate evidence per line**

`scanForHardcodedUrls()` iterates over all URL patterns (`http://`, `https://`, `localhost:`, `127.0.0.1`) for each line. A line like `"http://localhost:8080/api"` matches both `http://` and `localhost:`, creating **duplicate evidence entries** for the same line. This inflates the hardcoded endpoint count and produces duplicate snippets in the UI.

*Fix*: break after the first pattern match per line, or deduplicate by `(file, lineNumber)` before building the result.

**2. `ChattyServiceDetector`: dormant `LazyInitializationException`**

`findChattyDependencies()` does not use `JOIN FETCH` for `sourceService`/`targetService`. With `open-in-view: false`, calling `dep.getSourceService().getName()` (line 89) on the detached proxy will throw `LazyInitializationException`. This has never triggered because no test project has a dependency with `callCount >= 10` (the default threshold), so the for-loop at line 88 never executes. If a project ever does produce such a dependency, the detector will crash.

*Fix*: add `JOIN FETCH sd.sourceService JOIN FETCH sd.targetService` to the `findChattyDependencies` query.

### MEDIUM

**3. `GodServiceDetector`: immutable list mutation risk (line 125 + 135)**

When DesigniteJava finds god classes, `snippets` is assigned from `.stream()...toList()` (unmodifiable). The Spoon branch at line 135 calls `snippets.add()`. Currently unreachable (Spoon only runs when DesigniteJava finds nothing), but fragile if the guard at line 94 is ever changed.

*Fix*: always initialize `snippets` as `new ArrayList<>()`.

**4. `CyclicDependencyDetector`: misleading cycle description**

Tarjan's SCC returns nodes in reverse finishing order, not in actual cycle traversal order. The displayed cycle string `A -> B -> C -> A` may not correspond to any real edge chain — the SCC guarantees mutual reachability, not that those specific directed edges exist consecutively.

*Fix*: after finding the SCC, reconstruct an actual cycle path by following edges, or label the description as "services involved in cycle" rather than implying a specific path.

**5. `HardcodedEndpointDetector`: dynamic URL concatenation false positives**

The regex pattern matches `"http://"` inside string literals that are part of dynamic concatenations (e.g. `"http://" + instance.getHost()`). These are runtime-resolved URLs, not hardcoded endpoints. Already documented in Manual Inspection Notes above (Apollo, NetworkDisk).

*Known limitation*: would require AST-level analysis (checking if the string literal is the sole initializer of a field/variable vs. part of a concatenation expression) to fix properly.

**6. `AntiPatternDetectorService.buildGraphJson()`: N+1 query performance**

`findByProject()` (line 158) lacks `JOIN FETCH` for `sourceService`/`targetService`. Each `dep.getSourceService().getId()` works via Hibernate's proxy ID optimization (no initialization needed), so this doesn't crash, but `.getDependencyType()` and `.getCallCount()` may trigger proxy initialization, causing N+1 queries.

*Fix*: use `findByProjectWithServices()` instead.

### LOW

**7. `BaseDetector.readSnippet()`: reads entire file into memory**

`Files.readAllLines()` loads the whole file even when only ~7 lines are needed. Could use `Files.lines().skip(start).limit(count)` to stream only the necessary lines. Not a correctness issue — only relevant for very large generated files.

**8. `NanoServiceDetector.findMainClass()`: reads all Java files into memory**

`Files.walk().toList()` materializes all paths, then `Files.readString()` reads each file fully. Could use lazy streaming with `Files.lines()` to scan for the `@SpringBootApplication` pattern line-by-line without loading entire files, and short-circuit on first match without materializing the full path list.

**9. `EsbMisuseDetector`: gateway exclusion is name-based only**

A service not named "gateway" but acting as one (e.g. "edge-router", "reverse-proxy") could be falsely flagged as ESB misuse. Conversely, a non-gateway service coincidentally containing "gateway" in its name would be silently skipped. Could be improved by also checking for gateway-related annotations (`@EnableZuulProxy`, `@EnableGateway`) or Spring Cloud Gateway dependencies in `pom.xml`/`build.gradle`.

---

## Citation Gap Analysis — Applied

**2 citations added to `references.bib` and `chapter4.tex`:**
- Docker → `\cite{Merkel2014}` at system overview (line 18), tech stack table, and chapter summary
- BCrypt → `\cite{Provos1999}` at §4.3.2 "Passwords are hashed using BCrypt" and chapter summary

**7 footnotes added to `chapter4.tex`:**
- Angular → footnote at system overview (line 18) and tech stack table
- PostgreSQL → footnote at system overview (line 18) and tech stack table
- Flyway → footnote at §4.2 first mention (line 88); existing footnote in Docker Compose section removed to avoid duplication
- JGit → footnote in tech stack table
- Lombok → footnote in tech stack table
- Cytoscape.js → footnote at §4.4.3 Results page and §4.4.4 Reusable Components
- OpenAPI → footnote in tech stack table

---

## Citation Gap Analysis (original analysis)

The dissertation currently has 28 references. Below are opportunities to add citations, organized by strength of recommendation.

### Strongly Recommended (academic credibility gaps)

**1. Docker / containerization — Ch.4 §4.5 (Deployment)**
Docker is mentioned extensively (Dockerfile, Docker Compose, multi-stage builds, container-aware JVM flags) but never cited. Add:
- Merkel, D. (2014). "Docker: Lightweight Linux Containers for Consistent Development and Deployment." *Linux Journal*, 2014(239), Article 2.
- *Where*: Ch.4 line 18 ("containerized using Docker") or line 495 ("containerized using Docker and orchestrated with Docker Compose")

**2. Angular — Ch.4 §4.4 (Frontend)**
Angular is used as the entire frontend framework but has no citation. Add:
- The official Angular documentation or a canonical reference:
  - Google LLC. "Angular — One Framework. Mobile & Desktop." https://angular.dev/ (accessed 2026-XX-XX).
- *Where*: Ch.4 line 407 ("built with Angular 19")

**3. Flyway — Ch.4 §4.2 (Data Model)**
Flyway is mentioned for schema migrations (line 88, line 59 in tech table) but only has a footnote in Docker Compose section. Should have a proper citation or at least a consistent footnote. Currently there's a footnote at line 529 but not at line 88 where it's first mentioned.
- *Where*: Ch.4 line 88 ("versioned using Flyway migrations")

### Nice to Have (strengthen existing mentions)

**5. Cytoscape.js — Ch.4 §4.4.3/§4.4.4**
The dependency graph visualization uses Cytoscape.js (mentioned in §4.4.3 line 476 and §4.4.4 line 490) but has no citation. Add:
- Franz, M. et al. (2016). "Cytoscape.js: a graph theory library for visualisation and analysis." *Bioinformatics*, 32(2), 309–311. doi:10.1093/bioinformatics/btv557
- *Where*: Ch.4 line 476 ("interactive dependency graph rendered with Cytoscape.js")

**6. JGit — Ch.4 §4.3.1**  
JGit is used for Git cloning (line 255) and listed in the tech stack table but has no citation or footnote. Add:
- Eclipse Foundation. "JGit." https://www.eclipse.org/jgit/ (accessed 2026-XX-XX).
- *Where*: Ch.4 line 57 in tech stack table, or line 255.

**7. Lombok — Ch.4 Table 4.1**
Lombok is in the tech stack table (line 61) with no citation. Add:
- Project Lombok. https://projectlombok.org/ (accessed 2026-XX-XX).
- *Where*: footnote in tech stack table.

**8. PostgreSQL — Ch.4**
PostgreSQL is a core component but never cited. Add:
- The PostgreSQL Global Development Group. "PostgreSQL: The World's Most Advanced Open Source Relational Database." https://www.postgresql.org/ (accessed 2026-XX-XX).
- *Where*: Ch.4 line 18 or line 67 in tech stack table.

**9. BCrypt — Ch.4 §4.3.2**
BCrypt is mentioned for password hashing (line 246) but not cited. Add:
- Provos, N. and Mazières, D. (1999). "A Future-Adaptable Password Scheme." *Proceedings of the USENIX Annual Technical Conference*, 81–92.
- *Where*: Ch.4 line 246 ("Passwords are hashed using BCrypt")

**10. OpenAPI / Swagger — Ch.4 Table 4.1**
SpringDoc OpenAPI is in the tech stack (line 73) but has no citation. A footnote to the OpenAPI specification would suffice:
- OpenAPI Initiative. "OpenAPI Specification." https://spec.openapis.org/oas/latest.html
- *Where*: footnote in tech stack table.

### Not Needed (already well-covered or too minor)

- Spring Boot, Spring Security, Spring Data JPA — industry-standard frameworks; citation not expected in a thesis unless discussing their design principles specifically.
- Nginx — only mentioned once in Docker Compose; footnote at most.
- Maven — standard build tool, no citation needed.

### Summary

**4 strongly recommended** citations (Docker, Angular, Flyway consistency, Twelve-Factor App) would address the most visible gaps. **6 nice-to-have** citations (Cytoscape.js, JGit, Lombok, PostgreSQL, BCrypt, OpenAPI) would bring the reference list to ~34–38 entries, which is solid for a master's thesis. The most impactful additions are **Docker** and **Twelve-Factor App**, as they back core architectural decisions discussed at length in the deployment section.
