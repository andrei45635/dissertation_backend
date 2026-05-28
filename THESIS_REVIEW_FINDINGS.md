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
