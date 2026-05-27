# Thesis Review Findings (Round 2)

Cross-reference of the thesis (LaTeX source in `thesis/chapters/`) against the backend codebase (`src/main/java/com/msadetector/`). Updated after code fixes (health score, job flags, betweenness centrality) and thesis edits (new datasets, number corrections, typo fixes).

Items marked **FIXED** were resolved since the first review.

---

## FIXED Since Round 1

- **Betweenness centrality not implemented** — FIXED. `EsbMisuseDetector` now implements Brandes' algorithm as a third detection signal.
- **ESB Misuse severity mismatch (Medium vs High)** — FIXED in Table 2.1 (now High).
- **Two conflicting health score formulas** — FIXED. Legacy `calculateHealthScore()` removed from `AnalysisResult`. `HealthScoreCalculator` is now used to persist the correct score.
- **Per-job detection flags ignored** — FIXED. `AntiPatternDetectorService.isDetectorEnabled()` now checks job flags before running each detector.
- **`/api/**` vs `/api/auth/**` typo in Ch.4** — FIXED (line 199).
- **gRPC and message queue claims in Ch.2** — FIXED (commented out in the itemize list).
- **Stale evaluation numbers (4 projects, 60 services, 37 anti-patterns)** — FIXED across abstract, Ch.5, and Ch.6. Now says 6 projects, 113 services, 84 anti-patterns.
- **Apollo-Config grade D → F** — FIXED (score 11 < 50).
- **Missing Train-Ticket and Apollo-Config description paragraphs** — FIXED.
- **Anti-pattern distribution table missing Wrong Cuts / ESB Misuse rows** — FIXED.
- **Ch.4 typos (currrently, containg, messsage, etc.)** — FIXED.
- **Ch.3 nano service equation missing underscore** — FIXED.

---

## OPEN — Thesis–Code Mismatches

### 1. HIGH: Ch.3 ESB section doesn't describe betweenness centrality despite the code now implementing it

The code (`EsbMisuseDetector.java`) now uses three detection signals: connection-based ratios, volume-based mediator ratio, and betweenness centrality. However, the formal ESB detection section in **Chapter 3, lines 441–476** only describes two signals (Equations 3.10, 3.11, 3.12). Betweenness centrality is mentioned in passing at Ch.3 line 95 ("centrality analysis computes betweenness centrality") but never formalized with an equation or algorithm in the ESB subsection.

A reviewer would notice the disconnect: the overview promises betweenness centrality, the detailed section omits it, and the code implements it.

**Fix**: Add to Ch.3 Section 3.2.9 (ESB Misuse Detection):
- A paragraph introducing betweenness centrality as a third signal
- The BC formula: `BC(v) = Σ_{s≠v≠t} σ_st(v) / σ_st`
- Normalization: `BC_norm(v) = BC(v) / ((n-1)(n-2))`
- A note that Brandes' algorithm computes it in O(V×E) time
- Update Equation 3.12 to include `∨ BC_norm(s) ≥ θ_m` as a third disjunct
- Update the text "two ratios" (line 447) to "three signals"

---

### 2. HIGH: Ch.3 line 80 still mentions "messaging" as a dependency type

> "Edges are annotated with metadata including the dependency type (REST synchronous, Feign client, messaging, etc.)"

The code only detects `FEIGN_CLIENT`, `REST_TEMPLATE`, and `WEB_CLIENT`. The `MESSAGE_ASYNC` and `GRPC` enum values in `DependencyType.java` are never assigned. While Ch.2's itemize list was fixed (gRPC/messaging commented out), this Ch.3 prose still claims messaging support.

**Fix**: Change "REST synchronous, Feign client, messaging, etc." to "Feign client, RestTemplate, WebClient" or similar.

---

### 3. MEDIUM: MapStruct still listed in tech stack but never used

Chapter 4, Table 4.1 (line 60): *"MapStruct 1.6.3 — DTO mapping and boilerplate reduction"*

No `@Mapper` interface exists anywhere in the codebase. All DTO mapping is manual. `pom.xml` includes the dependency and annotation processor but they produce no generated code.

**Fix**: Either remove MapStruct from Table 4.1 and `pom.xml`, or implement at least one mapper.

---

### 4. MEDIUM: Four of ten anti-patterns unvalidated in evaluation

Cyclic Dependency, Distributed Monolith, Wrong Cuts, and ESB Misuse have zero detections across all six projects. Chatty Service was validated (4 instances in Train-Ticket), which is an improvement over Round 1.

The thesis acknowledges this (Ch.5 distribution paragraph) but doesn't explicitly discuss it as a threat to validity or explain why these detectors couldn't be triggered.

**Fix**: Add a paragraph in Ch.5 Threats to Validity (Internal or External) explicitly noting that four detectors remain untested against the evaluation corpus and discussing implications (e.g., the evaluated projects have simple topologies; enterprise systems are more likely to exhibit these patterns).

---

### 5. MEDIUM: `DependencyType` enum contains unused values

`DependencyType.java` defines `GRPC`, `MESSAGE_ASYNC`, `REST_SYNC`, and `DATABASE` — none of which are ever assigned in code. Only `FEIGN_CLIENT`, `REST_TEMPLATE`, and `WEB_CLIENT` are used. Dead enum values could confuse a reviewer reading the code.

**Fix**: Either remove unused enum values or add a comment marking them as future-work placeholders.

---

## OPEN — Thesis-Internal Issues

### 6. MEDIUM: TODO comments remain in LaTeX source

Won't render in PDF but visible if source is requested:

| File | Line | Content |
|---|---|---|
| `chapter4.tex` | 4 | `% TODO - !!!!!!!!!!!!!!!!!!!!!! FIX THE STUPID DIAGRAMS AND TABLES !!!!!!!!!!!!!!!!!!!!!!` |
| `chapter5.tex` | 1 | `% TODO - here I think this is where I should go in depth more about...` |
| `chapter5.tex` | 176 | `% TODO - for each antipattern type write a paragraph assessing accuracy` |
| `chapter5.tex` | 178 | `% TODO: Fill in the precision/recall table with actual values.` |
| `chapter5.tex` | 211 | `% TODO - write 2-3 paragraphs about threshold sensitivity` |
| `chapter5.tex` | 224 | `% TODO: Add a paragraph about what types of teams would benefit most` |
| `chapter5.tex` | 264 | `% TODO: Add a sentence about the number of projects` |
| `chapter6_conclusions.tex` | 18 | `% TODO - rework this` |

**Fix**: Remove all TODO comments before submission. Especially the chapter 4 one.

---

### 7. LOW: Commented-out precision/recall table in Ch.5

Lines 170–208 contain a commented-out precision/recall table with `--` placeholders and commented-out threshold sensitivity section. The thesis provides no quantitative accuracy metrics — only prose-based manual validation. A reviewer may ask for precision/recall numbers.

**Fix**: Either fill in the table, or remove it entirely and frame the evaluation as purely qualitative.

---

### 8. LOW: Ch.4 `textcolor{red}` comment still in Table 4.1 caption

Line 78: `% \textcolor{red}{tabelul depasea marginea. Am prescurtat API Doc, recompileaza sa vedem ca e ok}`

Reviewer-facing comment from your supervisor left in the source. Won't render, but visible if source is requested.

---

## OPEN — Backend Code Issues

### 9. MEDIUM: CORS config reflects any origin with credentials

`CorsFilterConfig.java:23` sets `Access-Control-Allow-Origin` to `request.getHeader("Origin")` with credentials enabled. This allows any origin — an OWASP concern. Meanwhile, `SecurityConfig.java:39` disables CORS at Spring Security level, creating two contradictory configurations.

### 10. MEDIUM: Hardcoded JWT secret in default config

`application.yml:39` default: `msa-detector-jwt-secret-key-change-this-in-production-please`. The production docker-compose requires it as an env var, but the default is still a well-known string.

### 11. LOW: Job cancellation is cosmetic

`AnalysisWorker.processJob()` has no cancellation check. Setting status to CANCELLED via the API doesn't stop the running analysis thread.

### 12. LOW: Flyway disabled in YAML but manually forced on

`spring.flyway.enabled: false` in `application.yml`, but `FlywayConfig.java` runs Flyway via `@PostConstruct`. Works but contradictory.

### 13. LOW: `@OneToOne` inverse side cannot be truly lazy

`AnalysisJob.result` is the inverse side of a `@OneToOne` — Hibernate cannot make it truly lazy without bytecode enhancement, causing eager fetches and potential N+1 in list queries.

---

## Priority Fix List

1. **Add betweenness centrality formalization to Ch.3 ESB section** — equation, Brandes' reference, update decision rule
2. **Fix Ch.3 line 80** — remove "messaging" from dependency type list
3. **Remove MapStruct** from tech stack table (or implement a mapper)
4. **Address unvalidated detectors** in Threats to Validity
5. **Remove all TODO comments** from LaTeX source before submission
6. **Clean up `DependencyType` enum** — remove or annotate unused values
