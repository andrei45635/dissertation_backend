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

## Chapter Length Analysis

### Raw sizes (source characters / lines)

| Chapter | Lines | Chars | Est. PDF pages |
|---|---|---|---|
| Ch.1 Introduction | 28 | 6.7k | ~2 |
| Ch.2 Background & Related Work | 403 | 41.1k | ~15 |
| Ch.3 Detection Methodology | 526 | 46.6k | ~18 |
| Ch.4 Application Design & Impl. | 654 | 69.7k | ~30+ |
| Ch.5 Results & Evaluation | 299 | 36.3k | ~13 |
| Ch.6 Conclusions | 40 | 7.7k | ~3 |

### Chapter 4 — Shrink Candidates

Ch.4 is nearly 70k chars — roughly 50% larger than the next biggest chapter and likely 30+ PDF pages. The following subsections are the primary candidates for trimming:

**1. §4.4 Frontend Implementation (L399–L530, ~130 lines, ~14k chars)**
This is the single largest section in Ch.4. It describes every Angular component in exhaustive detail: routing config, auth interceptor implementation, file upload drag-and-drop events, SVG gauge math (`2πr ≈ 283 units`), Cytoscape.js force-directed layout parameters. Most of this is implementation-level detail that belongs in source code documentation, not a thesis.
- **Recommendation**: **Cut by ~60%.** Keep the application structure overview, the routing table, and one paragraph per page component summarising its purpose. Move the component-level implementation details (§4.4.4 Reusable Components) to an appendix or remove entirely. The SVG math, drag-and-drop DOM events, and Cytoscape configuration parameters add no academic value.

**2. §4.5 Deployment (L532–L650, ~120 lines, ~13k chars)**
Five subsubsections under "Production Deployment Considerations" (hosting, data access, TLS, backup, scaling) read more like a deployment guide than a thesis chapter.
- **Recommendation**: **Cut by ~50%.** Merge the five production subsubsections into a single paragraph noting key considerations. The Docker Compose dev/prod comparison is valuable; the detailed TLS/Caddy/Cloudflare discussion and `pg_dump` backup guidance is not thesis-relevant.

**3. §4.3.2 Authentication and Security (L202–L247, ~45 lines)**
Very detailed description of JWT flow, BCrypt, SecurityContext. Standard Spring Security boilerplate.
- **Recommendation**: **Cut by ~30%.** Keep the architecture overview; remove the step-by-step request lifecycle description.

**4. Code listings (scattered)**
The JWT generation listing (L221–L243) and the Dockerfile runtime listing (L542–L563) are ~20 lines each. These are fine to keep — they add concrete evidence. The AnalysisWorker listing (L264–L307) is critical and should stay.

### Estimated savings from Ch.4 trimming
- Frontend: ~8k chars saved
- Deployment: ~6k chars saved
- Auth: ~2k chars saved
- **Total: ~16k chars → roughly 6–8 fewer PDF pages → Ch.4 drops to ~22–24 pages**

### Other Chapters — Assessment

**Ch.2 (41k chars, ~15 pages)**: Appropriate length for a background + related work chapter. The related work section (§2.3, L237–L397) is thorough and well-structured. No cuts recommended.

**Ch.3 (47k chars, ~18 pages)**: Contains 10 anti-pattern detector algorithms plus the health score, deployability gate, and dependency graph sections. Length is justified by the number of detectors. The pseudocode algorithms (Shared DB, Tarjan's SCC) are appropriately concise. No cuts recommended.

**Ch.5 (36k chars, ~13 pages)**: Good length. The MicroservicesSocial case study section (§5.3.1) could theoretically be shortened, but it provides useful worked-example value. No cuts recommended.

**Ch.1 & Ch.6**: Both short and appropriate. Ch.1 could arguably be expanded slightly but is fine as-is.

### Summary

The only chapter that warrants shrinking is **Chapter 4**, and the primary targets are the **frontend component descriptions** and the **production deployment guide**. These two sections together account for ~27k chars (~40% of the chapter) and could be cut roughly in half without losing any academically relevant content.

---

## Consistency Check Summary

| Item | Abstract | Ch.5 | Ch.6 | Slides |
|---|---|---|---|---|
| Project count | 8 ✓ | 8 ✓ | 8 ✓ | 8 ✓ |
| Service count | 116 ✓ | 116 ✓ | 116 ✓ | 116 ✓ |
| LOC | ~350k ✓ | ~350k ✓ | ~350k ✓ | ~350k ✓ |
| Anti-pattern instances | 106 ✓ | 106 ✓ | 106 ✓ | 106 ✓ |
| Anti-pattern types detected | 9 ✓ | 9 ✓ | 9 ✓ | 9 ✓ |
| Score range | 45–77 ✓ | 45–77 ✓ | — | 45–77 ✓ |
| Activiti/Karate refs | None ✓ | None ✓ | None ✓ | None ✓ |
| ESB Misuse severity | High ✓ | High ✓ | — | High ✓ |
| Health score formula | Category-based ✓ | Category-based ✓ | — | Category-based ✓ |
| ESB betweenness centrality | Ch.3 ✓ | Validated ✓ | — | — |
| Deployability gate | Ch.3 ✓ | — | — | Slide 4 ✓ |
| MapStruct disclaimer | — | — | Ch.4 footnote ✓ | — |

---

## Chapter 4 Cuts Applied (chapter4_slimmer.tex)

The following cuts were applied to `thesis/chapters/chapter4_slimmer.tex`, reducing it from 654 → 587 source lines (~67 lines, ~10% reduction, estimated 6–8 fewer PDF pages).

| Section | What was cut | Before | After |
|---|---|---|---|
| §4.4.2 Auth Integration | BehaviorSubject details, RxJS catchError internals, CanActivateFn implementation, per-interceptor paragraphs | 4 paragraphs | 1 paragraph |
| §4.4.3 Pages | Per-component implementation details (ngModel binding, HttpEvent.UploadProgress, setInterval polling internals, modal dialog flow, metric card layout, Jobs tab) | ~60 lines | ~20 lines (kept all 4 figures) |
| §4.4.4 Reusable Components | All 7 detailed component paragraphs (SVG math, stroke-dashoffset, Cytoscape CoSE params, drag-drop DOM events, CodeSnippet pre-rendering, ProgressTracker step classification) | 7 paragraphs (~70 lines) | 1 summary paragraph listing all 7 components |
| §4.5.5 Production Deployment | 5 subsubsections (Hosting Infrastructure, Data Access, TLS Termination, Backup, Resource Sizing) collapsed | 5 subsubsections (~30 lines) | 1 paragraph retaining the key architectural insight (DB-only statefulness) |
| §4.3.2 Backend Auth | OncePerRequestFilter lifecycle, CustomUserDetailsService, UserPrincipal fields | verbose paragraph | trimmed to essentials |
| §4.6 Summary | Updated to mention frontend components and merged deployment note | — | reflects shortened content |

### What was preserved
- All 4 page screenshots (Figures: login, upload, results, history)
- Routing table (Table 4.5)
- All code listings (JWT generation, AnalysisWorker, Detector interface, Dockerfile runtime)
- Application structure & directory layout paragraph
- All backend sections unchanged (REST API, Pipeline, Detector Architecture, Diff, Exception Handling)
- Docker Compose dev/prod subsections
- Configuration management table

---

## Further Cuts Analysis (All Chapters)

After reviewing all chapters for additional trimming opportunities, here are the findings:

### Candidates for cutting

**1. Ch.5 §5.2.3 MicroservicesSocial detailed walkthrough (~30 lines)**
The step-by-step narrative of how the tool analyzed MicroservicesSocial (clone → scan → results) is a useful worked example but could be condensed from ~30 lines to ~15 lines by removing intermediate observations and keeping only the key findings and the "what the tool detected vs. what was expected" comparison.
- **Savings**: ~15 lines (~1 page)

**2. Ch.2 §2.3.4 Dynamic and Hybrid Approaches (~8 lines, L308–315)**
Describes runtime tracing, chaos engineering, and hybrid approaches that the tool does NOT use. Could be removed entirely or reduced to a single sentence noting these exist but are out of scope.
- **Savings**: ~6 lines (~0.3 pages)

### Not recommended for cutting

- **Ch.1 (28 lines)**: Already minimal.
- **Ch.2 anti-pattern definitions (§2.1)**: Core background, all needed.
- **Ch.2 related work (§2.3)**: Well-structured comparison, justifies the tool's position.
- **Ch.3 detection algorithms**: All 10 detectors + health score + deployability gate are essential.
- **Ch.5 evaluation tables & per-project results**: Core contribution, cannot be cut.
- **Ch.6 (40 lines)**: Already minimal.

### Source cleanup (no page count impact)

- Ch.5 has ~40 lines of commented-out LaTeX (old validation paragraphs, precision/recall table skeleton, TODO comments) that take no PDF space but clutter the source. Can be removed for cleanliness.
- Ch.2 has ~40 lines of commented-out tools comparison section. Same — no PDF impact, but cleaner without them.

### Verdict

**Very little remains to cut.** The Chapter 4 cuts already removed the main excess. The remaining chapters are appropriately sized. At most, ~1.5 pages could be saved from Ch.5 §5.2.3 and Ch.2 §2.3.4, but the quality trade-off is marginal. The dissertation at 70 pages is well within the normal 60–100 page range for a master's thesis.

