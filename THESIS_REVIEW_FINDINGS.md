# Thesis Review Findings (Round 3)

Cross-reference of the thesis against the backend codebase. Updated after replacing Activiti/Karate with Site-Where/Genie, finalizing anti-pattern distribution table, and updating all numbers.

Items marked **FIXED** were resolved in prior rounds. Items marked **OPEN** remain.

---

## FIXED Since Round 1/2

- Betweenness centrality not implemented → **FIXED** in `EsbMisuseDetector` (Brandes' algorithm)
- ESB Misuse severity mismatch (Medium vs High in Table 2.1) → **FIXED**
- Two conflicting health score formulas → **FIXED** (legacy removed, `HealthScoreCalculator` used)
- Per-job detection flags ignored → **FIXED** (`isDetectorEnabled()` added)
- `/api/**` vs `/api/auth/**` typo → **FIXED**
- gRPC/message queue claims in Ch.2 → **FIXED** (commented out)
- Ch.3 line 80 "messaging" in dependency types → **FIXED** (now "REST synchronous, Feign client, etc.")
- Stale evaluation numbers (4 projects, 60 services, etc.) → **FIXED** (now 6 projects, 99 services, 335k LOC, 81 anti-patterns)
- Activiti/Karate replaced with Site-Where/Genie → **FIXED** throughout Ch.5, Ch.6, abstract, slides
- Apollo-Config grade D → F → **FIXED** initially, then score changed to 59 (D) which is correct
- Missing Train-Ticket/Apollo-Config description paragraphs → **FIXED**
- Anti-pattern distribution table missing Wrong Cuts/ESB Misuse rows → **FIXED**
- Duplicate rows in datasets/results tables → **FIXED**
- Ch.4 typos (currrently, containg, messsage, etc.) → **FIXED**
- Ch.3 nano service equation missing underscore → **FIXED**
- Slide deck (build_deck.js, slides.md, CSV) updated → **FIXED**

---

## OPEN — Thesis-Code Mismatches

### 1. HIGH: Ch.3 ESB section doesn't formalize betweenness centrality

The code (`EsbMisuseDetector.java`) now uses three detection signals: connection-based ratios, volume-based mediator ratio, and betweenness centrality (Brandes' algorithm). However, the formal ESB detection section in **Chapter 3, Section 3.2.9 (lines 441-476)** only describes two signals (Equations 3.10, 3.11, 3.12).

Ch.3 line 95 correctly mentions "centrality analysis computes betweenness centrality" in the overview, but the detailed ESB subsection never defines the formula, the algorithm, or how it integrates into the decision rule.

**Fix**: Add to Ch.3 Section 3.2.9:
- A paragraph introducing betweenness centrality as a third signal
- The formula: `BC(v) = Σ_{s≠v≠t} σ_st(v) / σ_st`
- Normalization: `BC_norm(v) = BC(v) / ((n-1)(n-2))`
- Reference to Brandes (2001) for O(V×E) algorithm
- Update Equation 3.12 to add `∨ BC_norm(s) ≥ θ_m` as a third disjunct
- Update line 447: "two ratios" → "three signals"

---

### 2. MEDIUM: MapStruct listed in tech stack but never used

Chapter 4, Table 4.1 (line 60): *"MapStruct 1.6.3, Lombok 1.18.38 — DTO mapping and boilerplate reduction"*

No `@Mapper` interface exists in the codebase. All DTO mapping is manual. MapStruct is in `pom.xml` with its annotation processor but produces no generated code.

**Fix**: Either remove MapStruct from Table 4.1 and `pom.xml`, or split the row so Lombok stands alone (Lombok IS used).

---

### 3. MEDIUM: Three of ten anti-patterns unvalidated

Cyclic Dependency, Distributed Monolith, and Wrong Cuts have zero detections across all six projects. Seven of ten detectors are now validated (up from five in Round 1), which is a significant improvement — ESB Misuse and Chatty Service are now covered.

The thesis acknowledges the absence in the distribution paragraph (Ch.5 line 146) but doesn't explicitly discuss it in Threats to Validity.

**Fix**: Add a sentence in External Validity (Ch.5 Section 5.4.3) noting that three detectors remain untested against the evaluation corpus and that their absence is plausible given the projects' topologies.

---

### 4. MEDIUM: `DependencyType` enum contains unused values

`DependencyType.java` defines `GRPC`, `MESSAGE_ASYNC`, `REST_SYNC`, and `DATABASE` — none are ever assigned in code. Only `FEIGN_CLIENT`, `REST_TEMPLATE`, and `WEB_CLIENT` are used.

**Fix**: Remove unused enum values or add a comment marking them as future-work placeholders.

---

## OPEN — Thesis-Internal Issues

### 5. LOW: TODO comments remain in LaTeX source

Won't render in PDF but visible if source is requested:

| File | Line | Content |
|---|---|---|
| `chapter4.tex` | 4 | `% TODO - !!!! FIX THE STUPID DIAGRAMS AND TABLES !!!!` |
| `chapter5.tex` | 1 | `% TODO - here I think this is where I should go in depth more about...` |
| `chapter5.tex` | 185 | `% TODO - for each antipattern type write a paragraph assessing accuracy` |
| `chapter5.tex` | 187 | `% TODO: Fill in the precision/recall table with actual values.` |
| `chapter5.tex` | 220 | `% TODO - write 2-3 paragraphs about threshold sensitivity` |
| `chapter5.tex` | 233 | `% TODO: Add a paragraph about what types of teams would benefit most` |
| `chapter5.tex` | 273 | `% TODO: Add a sentence about the number of projects` |
| `chapter6.tex` | 18 | `% TODO - rework this` |

**Fix**: Remove all TODO comments before submission.

---

### 6. LOW: Commented-out precision/recall table with placeholder values

Ch.5 lines 189-215 contain a commented-out precision/recall table. The thesis provides no quantitative accuracy metrics — only prose-based manual validation.

**Fix**: Either fill in the table or remove it entirely.

---

### 7. LOW: Supervisor comment in Ch.4 Table 4.1 caption source

Line 78: `% \textcolor{red}{tabelul depasea marginea...}`

**Fix**: Remove before submission.

---

### 8. LOW: Stale commented-out paragraphs in Ch.5

Lines 174-178 contain old commented-out validation text referencing "37 detected anti-pattern instances" and "Activiti". Won't render but adds noise.

**Fix**: Remove the old commented-out paragraphs.

---

## OPEN — Backend Code Issues

### 9. MEDIUM: CORS config reflects any origin with credentials

`CorsFilterConfig.java:23` — `Access-Control-Allow-Origin` set to `request.getHeader("Origin")` with credentials. OWASP concern.

### 10. LOW: Hardcoded JWT secret in default config

`application.yml:39` — well-known default secret. Production docker-compose requires env var, but default is still there.

### 11. LOW: Job cancellation is cosmetic

`AnalysisWorker.processJob()` has no cancellation check. Setting CANCELLED via API doesn't stop the running thread.

### 12. LOW: Flyway disabled in YAML but manually forced on

`spring.flyway.enabled: false` in config, but `FlywayConfig.java` runs it via `@PostConstruct`. Works but contradictory.

---

## Priority Fix List

1. **Add betweenness centrality formalization to Ch.3 ESB section** — the single most important open item; the code implements it but the thesis doesn't describe it
2. **Fix MapStruct in tech stack** — remove or split from Lombok
3. **Add a sentence about unvalidated detectors** in Threats to Validity
4. **Remove all TODO comments** from LaTeX source
5. **Clean up commented-out old paragraphs** in Ch.5

---

## Consistency Check Summary

| Item | Abstract | Ch.5 | Ch.6 | Slides |
|---|---|---|---|---|
| Project count | 6 ✓ | 6 ✓ | 6 ✓ | 6 ✓ |
| Service count | 99 ✓ | 99 ✓ | 99 ✓ | 99 ✓ |
| LOC | ~335k ✓ | ~335k ✓ | ~335k ✓ | ~335k ✓ |
| Anti-pattern instances | 81 ✓ | 81 ✓ | 81 ✓ | 81 ✓ |
| Anti-pattern types | 7 ✓ | 7 ✓ | — | 7 ✓ |
| Score range | 45–77 ✓ | 45–77 ✓ | — | 45–77 ✓ |
| Activiti/Karate refs | None ✓ | None ✓ | None ✓ | None ✓ |
| ESB Misuse severity | High ✓ | High ✓ | — | High ✓ |
| Health score formula | Category-based ✓ | Category-based ✓ | — | Category-based ✓ |
