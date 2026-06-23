# Thesis Defense — Q&A Cheat Sheet

**Title:** Diagnosing the Distributed: A Static Analysis Approach to Microservice Anti-Pattern Detection
**Tool:** MSA Detector — Spring Boot + Angular + PostgreSQL, Dockerized.
**Format:** ~5 min Q&A. Usually 1 theoretical + 1 practical + 1 source-code question.
**Where questions come from:** mostly from what YOU present — the committee reacts to the slides and demo; only the official reviewer will have read the thesis closely. So every slide you show is a question you invite: scoring slide → weights/clamping; pipeline slide → boundary detection; demo → diff & evidence. Rehearse the answers attached to your own slides first.

---

## BEFORE YOU SPEAK (read this first if nervous)

- Take **one breath** before answering. Silence for 2 seconds is fine and looks thoughtful.
- **Lead with the term or the number, then say why.** That's all a good answer is.
- Keep each answer **30–60 seconds**. Don't over-explain.
- If you don't recall an exact value: *"I'd have to check the exact number, but the mechanism is…"* — the mechanism matters more.
- Have the repo open to **`SharedDatabaseDetector`**, **`GodServiceDetector`**, **`DependencyGraphBuilder`**, and **`HealthScoreCalculator`** before walking in.

**One-line summary of the whole thesis (say if asked "what did you do?"):**
*"I built a static-analysis tool that detects ten microservice anti-patterns in Java/Spring projects by combining code-level smell detection with inter-service dependency analysis, and summarizes architectural quality as a 0–100 health score. I evaluated it on eleven open-source projects."*

**Key numbers to know:** 10 anti-patterns · 4 architectural dimensions · 11 projects · 130 services · ~274,000 LOC · 98 anti-pattern instances · 9 distinct types detected.

---

## 1. THEORETICAL QUESTION

**Q: What is an anti-pattern, and which ones do you detect?**
> An anti-pattern is a common solution to a recurring problem that does more harm than good. I detect ten, across four dimensions: **service design** (Nano Service, God Service), **communication** (Chatty Service, Cyclic Dependency, Hardcoded Endpoints, ESB Misuse), **data management** (Shared Database), and **deployment & coupling** (Distributed Monolith, API Versioning Absence, Wrong Cuts).

**Q: Why static analysis instead of dynamic/runtime analysis?**
> Static analysis needs no running system, so it works at any stage of development and integrates into CI/CD or code review. The tradeoff is I lose real runtime call frequencies — I acknowledge this in my Threats to Validity.

**Q: What makes your approach "multi-level"? (this is my main contribution)**
> Existing tools work at one level — either code smells OR architecture. I bridge both: code-level structural metrics computed with Spoon — class cohesion, field and method counts, size — become signals for architectural-level anti-patterns like God Service, and DesigniteJava's code-smell density feeds the code-quality dimension of the health score. For example, a class that trips several structural thresholds flags the whole service as a God Service.

**Q: Explain Tarjan's algorithm / how you detect cyclic dependencies.**
> I model services as a directed graph and run Tarjan's algorithm to find strongly connected components. Any component with more than one service is a cycle. It's a DFS tracking an index and a lowlink per node; runs in O(V+E), which is plenty for service-sized graphs.

**Q: What is the coupling coefficient?**
> It's the ratio of actual dependency edges to the maximum possible: `C = |E| / (|V| × (|V|−1))`. A high value means most services depend on most others — a sign of a distributed monolith.

**Q: How does ESB Misuse detection work?**
> I look for a service that mediates a disproportionate share of communication, using three signals: high caller+callee ratios, a volume-based mediator ratio, and normalized **betweenness centrality** (Brandes' algorithm). Threshold 0.4; gateways are excluded since they're meant to be central.

**Q: Why did you choose these ten anti-patterns?**
> I chose anti-patterns that are common in the microservice literature and observable from source code or configuration without runtime tracing. They also cover the main dimensions of microservice quality: service size and responsibility, communication coupling, data ownership, and independent deployment. I deliberately preferred a smaller catalogue with evidence and scoring over a larger catalogue with weak signals.

**Q: Which microservice principles do these anti-patterns violate?**
> Shared Database violates decentralized data ownership. Cyclic Dependency, Wrong Cuts and Distributed Monolith violate independent deployability. Chatty Service and ESB Misuse violate loose coupling and lightweight communication. God Service and Nano Service point to poor service boundary design.

**Q: How is your tool different from MSANose or MARS?**
> MSANose is the closest prior static Java microservice smell detector, but it does not integrate general code-smell density, a composite health score, or God Service and Chatty Service as implemented here. MARS covers more anti-patterns and reports strong precision/recall, but my work is positioned differently: a smaller catalogue integrated with evidence snippets, remediation, health scoring, re-analysis diff, and a web/API workflow.

**Q: What is Tight Class Cohesion (TCC), and why does it matter?**
> TCC measures the fraction of public-method pairs in a class that access at least one common instance field. If TCC is low, the methods operate on mostly disjoint state, which suggests unrelated responsibilities are packed into the same class. In my God Service detector, TCC is one of six structural metrics; it is not enough alone, but combined with size, field count, method count and dependency breadth it strengthens the God Class signal.

---

## 2. PRACTICAL QUESTION

**Q: Walk me through what happens when a user uploads a project.** (rehearse this!)
> The project is ingested by ZIP upload or Git clone. Then the pipeline runs asynchronously in five phases: (1) **detect microservices**, (2) run **DesigniteJava** per service for code smells, (3) build the **inter-service dependency graph** with Spoon, (4) run all **anti-pattern detectors**, (5) **assemble the result** and compute the health score. The frontend polls job status the whole time.

**Q: How is the health score computed? Give an example.**
> 100 points split across four capped categories: **Anti-Patterns (40), Code Quality (20), Architecture (25), Service Sizing (15)**. Each detected issue subtracts points; the score is clamped to 0–100 and mapped to a letter grade.
> **Example — microservice-recruit scored 40 (F):** anti-pattern penalties exceeded the 40-point budget → 0; Code Quality 12/20 (31 smells/KLOC); Architecture 16/25; Service Sizing 12/15. Total = 0 + 12 + 16 + 12 = **40**. It loses points in every category, which shows the multi-level scoring.

**Q: How do you decide what is a microservice vs. just a module?**
> A "deployability gate." I scan for build files (pom.xml, build.gradle), filter out non-service folders (examples, tests, aggregators) by keyword, then require one of three signals: a framework entry point like `@SpringBootApplication` (HIGH confidence), a Dockerfile (MEDIUM), or a `main()` method (LOW). This cuts false positives in monorepos.

**Q: What does the re-analysis diff feature do?**
> When a project is re-analyzed, I compare the new result to the previous one: which anti-patterns were **resolved, new, or unchanged**, plus per-metric and per-category deltas and the health-score change. It's inspired by SonarQube's "Clean as You Code" — teams can measure the impact of a refactor.

**Q: How do you collect evidence for a finding?**
> Each detector extracts the relevant source snippet — for a cycle, the actual `@FeignClient` or `RestTemplate` call — with the line highlighted, plus affected services and remediation advice. It's stored as JSON and shown in the UI, so the developer isn't just told "cyclic dependency," they're shown exactly where.

---

## 3. SOURCE-CODE QUESTION

**Q: Show me / explain how a detector is implemented.** (open SharedDatabaseDetector)
> Every detector implements the `AntiPatternDetector` interface with one method: `detect(Project, List<Microservice>)`. It's the **Strategy pattern**. Each is a Spring `@Component`, and Spring auto-collects them all by injecting `List<AntiPatternDetector>` into the orchestrator. A `BaseDetector` superclass provides shared helpers (JSON serialization, snippet extraction).

**Q: How would you add an eleventh detector?**
> Implement the interface, annotate the class `@Component`, done. Spring discovers and registers it automatically — no changes to the orchestrator. That extensibility was a deliberate design goal.

**Q: Explain the God Service detection.**
> A **Spoon structural analysis** evaluates every class in the service against six metrics (field count, public methods, LOC, import domains, constructor params, and TCC — Tight Class Cohesion). A class is a God Class if it exceeds at least 3 of the 6, and a single God Class flags the service. Pure data-holder classes (entities, DTOs) are excluded first so they don't get falsely flagged. DesigniteJava also runs per service, but its smell catalogue is abstraction and modularization smells rather than a literal God Class, so it feeds the code-quality score rather than this detector.

**Q (depth flex — drop one of these unprompted to prove ownership):**
- **Per-method Feign counting:** each method in a `@FeignClient` interface counts as a separate call, so a 50-method client produces a call count of 50 — that feeds Chatty Service detection accurately.
- **Async correctness:** I start the analysis worker via Spring's `afterCommit()` callback so the job row is committed before the background thread reads it, and progress updates run in `REQUIRES_NEW` transactions so polling clients see them immediately.

---

## DETECTOR-BY-DETECTOR Q&A (one per anti-pattern)

> Pattern for any of these: **input → logic → threshold → evidence.** If they name a detector, hit those four beats.

**1. Shared Database — HIGH.** *How do you detect it?*
> I parse each service's `application.yml`/`.properties` for `spring.datasource.url`, resolving placeholders to their defaults. Then I group services by resolved URL — any URL used by more than one service is a shared database. Evidence is the datasource line from each sharing service's config.

**2. Cyclic Dependency — CRITICAL.** *How do you detect it?*
> On the directed dependency graph I run Tarjan's algorithm for strongly connected components; any component with more than one service is a cycle. O(V+E). I produce a readable chain like "Order → Payment → Order" and attach the `@FeignClient`/`RestTemplate` calls that form it.

**3. Nano Service — MEDIUM.** *How do you detect it?*
> A service is flagged when it's below **both** thresholds: fewer than **500 LOC** *and* at most **2 endpoints** (both configurable). LOC is counted by walking the service's `.java` files; endpoints come from Spoon's annotation scan. Both conditions must hold, which avoids flagging a small but endpoint-rich gateway. Evidence is the first ~15 lines of the main application class.

**4. God Service — HIGH.** *How do you detect it?*
> A **Spoon** structural analysis over six per-class metrics (≥25 fields, ≥30 public methods, ≥1000 LOC, ≥20 import domains, ≥12 constructor params, TCC < 0.5); a class is a God Class if it exceeds at least **3 of the 6**, and a single God Class flags the service. Pure data holders (entities, DTOs, getter/setter classes) are excluded first.

**5. Chatty Service — HIGH.** *How do you detect it?*
> Two complementary approaches. From the dependency graph, any edge with a call count ≥ **5** (configurable), where each `@FeignClient` method counts as one call. Plus a Spoon source scan for chatty HTTP-client types the graph misses: Feign interfaces with ≥5 methods, non-Feign "client/api/http"-named interfaces with ≥5 HTTP-mapping methods, and classes with ≥5 calls on known client types (`RestTemplate`, `WebClient`, `HttpURLConnection`…). Server-side controllers are excluded to avoid false positives.

**6. Hardcoded Endpoints — MEDIUM.** *How do you detect it?*
> A line-by-line scan of each service's `.java` files for URL patterns (`http://`, `https://`, `localhost:`, `127.0.0.1`) that appear inside string literals, extracting the full URL with a regex. I skip comments, imports, package declarations and test files, and cap evidence at five snippets per service. These indicate calls bypassing service discovery.

**7. Distributed Monolith — CRITICAL.** *How do you detect it?*
> A system-level check using three metrics: the **coupling coefficient** C, the **connected ratio** R (fraction of services in at least one dependency), and the **shared-database count** D. I flag the system if `C > 0.5`, **or** `R > 0.8 and D > 0`, **or** `R > 0.8 and C > 0.3`. Only runs with ≥3 services. It's intentionally sensitive because an undetected distributed monolith is costly — thresholds are configurable if a team finds it too aggressive.

**8. API Versioning Absence — MEDIUM.** *How do you detect it?*
> During endpoint extraction I set a `hasVersioning` flag when any recognized strategy appears: URL path versioning like `/v1/`, version headers such as `X-API-Version`, vendor media types such as `application/vnd.example.v1+json`, or query parameters such as `version=`. A service is flagged when **none** of its endpoints carry any recognized version indicator. Evidence is the `@RestController`/`@RequestMapping` of an unversioned controller.

**9. Wrong Cuts — HIGH.** *How do you detect it?*
> **Bidirectional dependencies**: a pair of services calling each other in both directions, which signals they're too tightly coupled to be separate. Each pair is reported once, with the call declarations from both directions as evidence. In microservice-recruit these were the pf-recruit/pf-resume and pf-recruit/pf-user pairs.

**10. ESB Misuse — HIGH.** *How do you detect it?*
> I look for a central mediator using three signals against a 0.4 threshold: high **caller and callee ratios** together, a volume-based **mediator ratio**, or normalized **betweenness centrality** (Brandes' algorithm). Any one triggers a flag — so it favors recall, meaning the results are "candidate hubs to inspect." Gateway-named services (`gateway`, `zuul`, `proxy`…) are excluded since they're designed to be central. Only runs with ≥3 services.

**Quick reference — defaults & severities:**

| Detector | Severity | Key threshold / rule |
|---|---|---|
| Shared Database | HIGH | same datasource URL across >1 service |
| Cyclic Dependency | CRITICAL | Tarjan SCC, component size > 1 |
| Nano Service | MEDIUM | < 500 LOC **and** ≤ 2 endpoints |
| God Service | HIGH | ≥1 God Class (Spoon: ≥3 of 6 structural metrics) |
| Chatty Service | HIGH | call count ≥ 5 (per-method Feign) |
| Hardcoded Endpoints | MEDIUM | URL literal in `.java` (non-test) |
| Distributed Monolith | CRITICAL | C>0.5, or R>0.8 & D>0, or R>0.8 & C>0.3 |
| API Versioning Absence | MEDIUM | no endpoint has recognized path/header/media/query versioning |
| Wrong Cuts | HIGH | Bidirectional dependencies between a service pair |
| ESB Misuse | HIGH | any signal ≥ 0.4 (gateways excluded) |

---

## LIKELY "SNEAK-IN" QUESTIONS

**Q: What's the biggest limitation?**
> It's Java/Spring-only; it can't resolve dynamically-configured URLs (e.g. Spring Cloud Config); and the boundary heuristic can misclassify a shared library as a nano service. I document all of these in the thesis.

**Q: Why was no Distributed Monolith detected?**
> It's consistent with the evaluated projects' topologies — they show moderate coupling, not the pervasive coupling a distributed monolith requires. None crossed the thresholds (C > 0.5, or R > 0.8 combined with shared databases or moderately high coupling).

**Q: How did you validate the results?**
> Manual inspection of the 98 instances — checking datasource URLs for Shared Database, controller mappings for API versioning, the call graph for cycles, etc. I note in Conclusion Validity that a single reviewer (me) is a bias.

---

## ADDITIONAL COMMISSION-STYLE QUESTIONS

> Pattern they usually use: **1 theoretical + 1 practical + 1 source-code question.** These are extra questions that go beyond the obvious detector walkthroughs.

### Extra theoretical questions

**Q: What is the difference between a code smell and an architectural anti-pattern?**
> A code smell is usually local to a class or method — for example high complexity, low cohesion, or too many fields. An architectural anti-pattern appears at the service or system-boundary level — for example Shared Database, Cyclic Dependency, or Distributed Monolith. My work connects both levels: code-level metrics can become evidence for service-level design problems, especially for God Service and the health score.

**Q: Why are thresholds acceptable if architecture quality is context-dependent?**
> They are heuristics, not universal truth. The thresholds operationalize anti-pattern definitions from the literature into measurable signals. The tool should be read as an evidence-backed detector of candidates, not as an absolute judge. In a real organization, the defaults would be calibrated to the project type and team conventions.

**Q: Why did you not use machine learning?**
> The main reason is explainability and data availability. Publicly labeled datasets of microservice anti-patterns are limited, and architectural smells are context-sensitive. A rule-based static analysis tool can show exactly why a service was flagged: the threshold, the metric, the affected service, and the source evidence.

**Q: What is the strongest threat to validity in your work?**
> Construct validity: whether the static signals fully capture the real architectural problem. For example, Chatty Service is approximated from static call declarations, not real runtime traffic volume. I mitigate this by treating results as candidate findings and attaching concrete evidence for manual inspection.

**Q: Why is API Versioning Absence considered an anti-pattern?**
> Because microservices evolve independently. Without explicit versioning, a provider change can break consumers and force coordinated releases. It is less severe in a small prototype, but in long-lived independently deployed services it increases coupling between teams and release cycles.

**Q: How did you choose the health-score category weights (40/25/20/15)?**
> Engineering judgement informed by how composite metrics work in the literature — SonarQube's maintainability rating, SQALE, and the ISO 25010 quality model. Anti-Patterns gets the largest budget because architectural issues are the focus of the work and the most expensive to fix after deployment. I state explicitly in the limitations that the weights are not empirically calibrated — that's listed as future work.

**Q: Doesn't clamping a category at zero lose information?**
> Yes — a project with penalty 49 and one with penalty 90 both show Anti-Patterns at 0. That's a known limitation of the category-cap design, noted in Threats to Validity. The per-issue counts are still preserved in the breakdown, so nothing is hidden from the user. A density-style scaled penalty, like the Code Quality category already uses, would fix it and is future work.

**Q: Why does a single God Class flag the whole service?**
> The default is intentionally sensitive: a false positive costs a developer a minute to dismiss; a false negative is architectural debt growing undetected. Even one class concentrating that many responsibilities is worth a look. The threshold is configurable, and in production a team would calibrate it.

### Extra practical questions

**Q: If a company used your tool, what should they fix first?**
> I would prioritize issues that block independent deployment: Cyclic Dependency, Shared Database, Wrong Cuts, and Distributed Monolith indicators. Those affect service autonomy. Lower-risk issues like missing API versioning or nano services can be handled after the structural coupling is understood.

**Q: How would this integrate into CI/CD?**
> The backend already supports asynchronous analyses and stores historical results. In CI/CD, the tool could run after a merge request or nightly build and report new critical findings. I would avoid failing builds on all legacy issues; a better gate is "no new critical issues" or "health score must not regress."

**Q: What result surprised you most?**
> The frequency of API Versioning Absence and Nano Service. Many projects show microservice decomposition and service discovery, but not always mature API lifecycle practices or balanced granularity. That is why the results are useful: they reveal architectural debt even in projects that look structurally microservice-based.

**Q: Why did no project receive an A grade?**
> Because the score combines multiple dimensions. A project can avoid severe cyclic dependencies and still lose points for API versioning, hardcoded endpoints, code-smell density, or service sizing. The goal is not to label projects as bad, but to show where architectural debt accumulates.

**Q: Is eleven projects enough for evaluation?**
> Enough for a feasibility evaluation, not for a statistical claim. The corpus is intentionally heterogeneous — small demos, reference applications, benchmark systems, and larger enterprise-style projects — so it exercises different detectors. A full precision/recall study would require a labelled microservice anti-pattern dataset, which is not currently available in a mature form.

**Q: Why did respected reference projects like FTGO or PiggyMetrics only get C grades?**
> The score measures conformance to this tool's catalogue and conventions, not overall software excellence. FTGO mainly loses points for API versioning absence; that does not mean it is badly designed, only that it does not expose a versioning strategy recognized by the detector. This is why the category breakdown and evidence matter more than the letter grade alone.

**Q: What if API versioning is handled at a gateway or through contracts?**
> Then the detector may report a candidate finding even though the team has a valid external versioning strategy. The implementation recognizes several source-level strategies — path, header, media-type, and query-parameter versioning — but it cannot prove gateway-level or consumer-contract versioning from service code alone. So this finding should be interpreted as "no recognized service-level versioning evidence."

**Q: What would you change before deploying this in an enterprise environment?**
> I would add organization-specific threshold profiles, better support for Spring Cloud Config and Kubernetes manifests, and eventually runtime telemetry. Static analysis is good for early detection, but traces and metrics would improve confidence for Chatty Service and real coupling.

**Q: What is the most important future improvement?**
> Multi-language support. Real microservice systems are often polyglot, so the next step would be a language-agnostic dependency graph from Docker Compose, Kubernetes manifests or gateway configuration, plus language-specific analyzers for Python, Go, .NET or Node.js.

**Q: How does a developer know whether a finding is a false positive?**
> Each finding includes affected services, severity, explanation, remediation advice, and evidence snippets. The tool does not ask developers to trust a black box; it gives them the concrete source or graph evidence so they can accept, reject, or tune the finding.

**Q: How exactly does each health-score category compute its penalty?**
> **Code Quality (20):** smell density per KLOC, scaled — `min(20, round(20 × density / 80))`, where 80 smells/KLOC is the configurable full-penalty point. microservice-recruit: 31.4 density → 8-point penalty.
> **Architecture (25):** coupling coefficient above 0.1 costs `min(15, round(coupling × 15))`, plus `min(10, 5 per cycle)` for dependency cycles.
> **Service Sizing (15):** `min(8, 3 per nano service)` plus `min(10, 5 per god service)`.
> **Anti-Patterns (40):** flat −8/−5/−3/−1 by severity for everything else.

**Q: What do the HIGH/MEDIUM/LOW confidence levels actually do?**
> Each detected service records which signal admitted it — framework entry point is HIGH, Dockerfile MEDIUM, `main()` LOW — and that's surfaced with the result, so the user can judge how trustworthy the boundary detection was. There are also graceful fallbacks: if the gate rejects every candidate, or the project is single-module, the tool falls back to a LOW-confidence service rather than failing the analysis with zero services.

**Q: What does the diff show if a service was renamed between analyses?**
> Findings are matched by a stable key — anti-pattern type plus affected service plus a type-specific signature — so a renamed service won't match its old findings: they show up as resolved + new rather than unchanged. That's honest behaviour (the tool doesn't guess identity), but I'd document it as a known limitation of the matching.

**Q: What does the frontend add beyond showing raw JSON?**
> It turns the analysis result into an actionable workflow: upload or clone a project, monitor asynchronous progress, inspect the health score and category breakdown, expand anti-pattern cards with evidence snippets, view the dependency graph, export JSON, and compare re-analyses through the history/diff views.

**Q: How is the application deployed?**
> With Docker Compose: Spring Boot backend, Angular/Nginx frontend, and PostgreSQL. The development setup exposes services locally; the production setup exposes only the frontend, reverse-proxies API calls internally, keeps PostgreSQL private, and externalizes secrets like the database password and JWT signing key.

### Extra source-code questions

**Q: What are the main REST API groups?**
> Three groups: `/api/auth` for registration, login and current-user info; `/api/projects` for upload, clone, listing, history, re-analysis and deletion; and `/api/jobs` for job status, results, diff, recent jobs and cancellation.

**Q: How is the application secured?**
> The backend uses stateless JWT authentication with Spring Security. Public endpoints are limited to login/register, Swagger and actuator health/info. Protected requests pass through a JWT filter, passwords are stored with BCrypt, and controller methods operate on the authenticated user.

**Q: Why does each analysis job own its own microservice snapshot?**
> To preserve history. Re-analysis should not overwrite previous service, endpoint, dependency, smell, or anti-pattern records. Each `AnalysisJob` gets its own snapshot, so the diff feature can compare two completed runs accurately and old results remain reproducible.

**Q: Why do you start the analysis worker after transaction commit?**
> Uploading or cloning creates the project and job rows in a transaction. The worker is started through an `afterCommit()` callback, so the async thread cannot read a job that has not been committed yet. That avoids a race condition between the request thread and the background analysis.

**Q: How is progress visible while analysis is running?**
> The worker updates the job status between phases: detecting services, analyzing services, building the graph, detecting patterns, then completed or failed. The frontend polls these job fields, so the user sees progress instead of waiting for a single blocking request.

**Q: How did you protect ZIP upload extraction?**
> The extractor normalizes every ZIP entry path to prevent Zip Slip, rejects duplicate entries, and streams each file while counting decompressed bytes. It also enforces limits on total uncompressed size, number of entries, and nesting depth, then deletes the partial workspace if extraction fails.

**Q: How does the dependency graph resolve a call target to a service?**
> It builds aliases from service directory names, `spring.application.name`, and configured ports. Then it resolves Feign clients, `RestTemplate` calls, and `WebClient` calls using exact name matching, port matching, and fallback fuzzy matching. Each resolved call becomes a directed edge in the service dependency graph.

**Q: Where is API versioning detected in the code?**
> The versioning signal is created in `DependencyGraphBuilder` during endpoint extraction. It checks the full endpoint path, mapping headers, media types, and query-parameter conditions, then stores `hasVersioning` and `apiVersion` on each `Endpoint`. `ApiVersioningDetector` is intentionally simple after that: it loads endpoints per service and flags the service only if `versionedCount == 0`.

**Q: Why use Spoon instead of regular expressions everywhere?**
> Spoon understands Java structure: annotations, methods, invocations, types, and class relationships. That is more reliable for endpoint extraction, Feign clients, TCC metrics, and God Service detection. Regex is only used where the signal is naturally textual, such as hardcoded URL literals.

**Q: How do you avoid double-counting Nano Service and God Service in the health score?**
> They are detected as anti-patterns, but they are excluded from the general Anti-Patterns category and counted only in Service Sizing. That way service-granularity problems affect the score once, in the category where they belong.

**Q: What happens if one detector fails?**
> The current orchestrator logs that detector failure and continues with the remaining detectors. The benefit is resilience: one detector does not kill the whole analysis. The tradeoff is that a production version should expose partial-analysis warnings clearly in the UI.

**Q: Where is the system extensible in code?**
> The main extension point is the `AntiPatternDetector` interface. A new detector implements `detect(Project, List<Microservice>)`, is annotated as a Spring component, and is automatically included because the orchestrator receives a `List<AntiPatternDetector>`. That keeps the pipeline independent of individual detector classes.

**Q: How is the re-analysis diff implemented?** (open `AnalysisDiffService`)
> `buildDiff()` indexes the previous analysis's findings into a map keyed by an **issue key**: anti-pattern type + primary affected service + a type-specific signature (for God Service, the canonical list of god-class names from the details JSON). Each current finding looks up that key — match found means **unchanged** (and is consumed from a `Deque`, so duplicates are matched one-to-one, not all-to-one), no match means **new**, and whatever remains unmatched on the previous side is **resolved**. Per-category score deltas are recomputed through the same `HealthScoreCalculator`, so the diff and the score can't disagree.

**Q: Where does the deployability gate live in code?** (open `MicroserviceDetector`)
> `detectServicesWithConfidence()` returns `DetectedService` records — path, confidence enum, and the **name of the signal that admitted it** (e.g. framework entry point vs Dockerfile vs main method). The pipeline is: scan for build files → drop excluded folders by keyword → drop aggregator modules → apply the three-signal gate. Three explicit fallbacks (`single-module-fallback`, `no-candidates-fallback`, `all-gated-fallback`) ensure a usable LOW-confidence result instead of an empty analysis.

**Q: Where are the score weights and thresholds defined in code?**
> Category budgets are constants in `HealthScoreCalculator`; tunable values are injected via `@Value` from `application.yml` — for example `app.thresholds.code-smell-density-threshold` (default 80) controls the Code Quality scaling. Detector thresholds (nano LOC, chatty call count, coupling cutoffs…) live in the same externalized configuration, which is what makes the "configurable thresholds" claim concrete.

**Q: How did you test the tool?**
> Each detector has dedicated tests for positive, negative and threshold-boundary cases. Shared helper logic is tested through a concrete `BaseDetector` subclass. Controller integration tests use `@WebMvcTest`, `MockMvc`, and mocked service beans to verify validation, security and JSON responses.

---

## FINAL CHALLENGE QUESTIONS (cover the remaining angles)

**Q: What is the exact research gap?**
> Existing tools either focus on code-level smells or architecture-level dependency structure. My gap is combining both into one workflow: code smells, structural metrics, dependency graph analysis, evidence snippets, a health score, and re-analysis diff.

**Q: What is the main novelty of your dissertation?**
> Not a single algorithm. The novelty is the integration: multi-level static analysis plus evidence-based reporting and a composite score that can be tracked over time.

**Q: Why should developers trust the health score?**
> They should not treat it as absolute truth. It is a summary indicator. The real value is the transparent breakdown: each deduction is traceable to a detector, severity, affected services, and source evidence.

**Q: Why not report precision and recall?**
> There is no mature labelled benchmark dataset for these microservice anti-patterns. I performed manual validation instead. Precision/recall would require multiple reviewers and labelled ground truth, which is future work.

**Q: What is your strongest technical decision?**
> Separating detectors behind the `AntiPatternDetector` interface. It keeps the pipeline extensible: the orchestrator does not know detector internals, and new detectors can be added as Spring components.

**Q: What is your weakest assumption?**
> That static source-level signals are good proxies for runtime architectural behaviour. For example, static Feign method count approximates chatty communication, but real traffic volume requires runtime telemetry.

**Q: Why use source code instead of bytecode?**
> Source code gives access to configuration files, annotations, endpoint mappings, snippets, and remediation evidence. Bytecode analysis is useful for dependency structure, but less convenient for developer-facing explanations.

**Q: What happens with asynchronous messaging, Kafka, RabbitMQ?**
> The current dependency graph mainly targets REST-style communication: Feign, RestTemplate and WebClient. Messaging-based dependencies are a limitation and a natural future detector extension.

**Q: What if services use Kubernetes or Docker Compose names instead of Spring config?**
> The current tool can miss those dependencies. Future work would build a language-agnostic dependency graph from Docker Compose, Kubernetes manifests, ingress/gateway config and service discovery metadata.

**Q: Why PostgreSQL?**
> The data is structured and relational: users, projects, jobs, services, endpoints, dependencies, smells, results and anti-patterns. PostgreSQL fits that model well and gives persistence for historical analysis and diffs.

**Q: Why persist snippets instead of reading files later?**
> Because uploaded or cloned workspaces may be cleaned up or changed. Persisting snippets during analysis makes the report reproducible even if the source directory is later removed.

**Q: What is the risk of false positives?**
> The tool favors recall in several detectors. That is acceptable because findings are candidates for inspection, not automatic refactoring commands. Evidence snippets let developers accept or reject findings quickly.

**Q: What is the risk of false negatives?**
> Dynamic URLs, external config servers, runtime-only traffic, messaging systems and non-Java services can hide dependencies from static analysis.

**Q: Which detector is most context-sensitive?**
> Nano Service and API Versioning Absence. A small service may be valid if it owns a critical capability, and API versioning may be handled outside the service. Those findings require human interpretation.

**Q: Which detector is most objective?**
> Shared Database and Cyclic Dependency. Shared Database is based on matching datasource URLs, and Cyclic Dependency is graph-based through strongly connected components.

**Q: Why does the presentation focus on God Service?**
> Because it clearly demonstrates the thesis contribution: code-level metrics, especially cohesion and class structure, become evidence for an architectural-level finding.

**Q: Why does the presentation use microservice-recruit as the worked example?**
> It is the most anti-pattern-dense evaluated project and triggers several important detectors: Cyclic Dependency, Wrong Cuts, ESB Misuse, API Versioning Absence, Hardcoded Endpoint and Nano Service. It shows the scoring system better than a mostly clean project.

**Q: What would you do differently if you started again?**
> I would design threshold calibration and reviewer validation earlier, so the evaluation could include stronger empirical claims. The implementation works, but the validation could be expanded with labelled findings and multiple reviewers.

**Q: What is the enterprise risk before using this as a quality gate?**
> Thresholds must be calibrated first. I would not fail builds on all findings immediately. I would start with "no new critical findings" and "health score must not regress."

**Q: What if a committee member says this is just a wrapper around existing tools?**
> DesigniteJava is only one input. The contribution is the full pipeline: service boundary detection, Spoon-based endpoint/dependency extraction, graph-based detectors, custom detectors, evidence extraction, scoring, persistence, diffing and UI workflow.

**Q: What if they say the health score is subjective?**
> It is subjective in the same way most composite quality scores are. The value is not that 75 is universally "correct," but that the scoring is transparent, decomposed, repeatable and useful for comparing runs over time.

**Q: What if they ask why not machine learning?**
> Lack of labelled data and need for explainability. A rule-based detector can explain exactly which threshold fired and show the source evidence. ML would be interesting later, after collecting enough labelled findings.

## ADDITIONAL EDGE-CASE QUESTIONS

### Additional theoretical questions

**Q: How sensitive are your evaluation results to the default threshold values?**
> They are sensitive, especially for Nano Service, Chatty Service, God Service and ESB Misuse. That is why I present the thresholds as configurable heuristics, not universal truths. The evaluation shows the behaviour of one documented default profile; enterprise use would require calibration against local architecture conventions.

**Q: Can the same structural problem be penalized twice, for example as both Wrong Cuts and Cyclic Dependency?**
> Yes, a bidirectional dependency can be both a two-node cycle and evidence of a wrong service boundary. I prevent double counting only for Nano Service and God Service because they are moved into Service Sizing. For cycles and Wrong Cuts I leave both findings visible because they describe different consequences of the same coupling, but I acknowledge that the penalty can be read as an upper bound.

**Q: Why is the same 0.4 threshold reasonable for ESB Misuse signals that measure different things?**
> It is a pragmatic default for normalized signals, not a mathematically universal cutoff. Caller ratio, callee ratio, mediator ratio and normalized betweenness all map to a 0-1 range, so 0.4 means a service participates in a large share of communication. The tradeoff is that small graphs can produce candidate mediators more easily, so the finding should be inspected rather than accepted blindly.

**Q: Could a God Service escape detection if responsibility is spread across many medium-sized classes instead of one God Class?**
> Yes. The current detector is designed to catch responsibility concentration visible through at least one God Class. A service with many cohesive but unrelated classes could still be over-broad without crossing those class-level thresholds. Detecting that would require service-level domain cohesion or package/module analysis, which is future work.

**Q: Does the coupling coefficient lose important information by ignoring dependency type, direction meaning, or criticality?**
> Yes. It compresses the graph into a density measure, so it does not know whether an edge is synchronous, asynchronous, business-critical, or rarely used. That is why it is only one signal in the score and in Distributed Monolith detection. The dependency graph and evidence remain available for interpretation.

**Q: Does DesigniteJava overestimate code-smell density because each service is analyzed separately?**
> It can. For example, abstractions used across service boundaries may look unused when a service is analyzed in isolation. I treat DesigniteJava smell density as a code-quality proxy and possible upper bound, not exact ground truth.

**Q: What claims can your evaluation support, and what claims can it not support?**
> It supports feasibility: the tool can analyze real open-source Java microservice projects and produce differentiated, evidence-backed findings. It does not support a statistical claim about precision, recall, or industry-wide anti-pattern prevalence, because that would need a labelled corpus and multiple reviewers.

**Q: Why include a polyglot repository if the detector only analyzes Java services?**
> Because real microservice repositories are often mixed-language. The current tool analyzes the Java services it can understand and ignores the rest. That demonstrates a realistic limitation: the reported result is for the Java-visible part of the system, not the full polyglot architecture.

**Q: Does excluding gateway-named services from ESB Misuse risk hiding a real central bottleneck?**
> Yes, it can hide a gateway that is badly implemented as a mediator. I exclude gateways because centrality is expected for API gateways, and otherwise the detector would flag many valid architectures. If a team wants to audit gateway bottlenecks, that exclusion should be configurable or handled by a separate gateway-specific check.

**Q: How do you reconcile the presentation's broad-coverage claim with MARS detecting more anti-pattern types?**
> Broad coverage in my work means coverage across four dimensions with a working web/API workflow, evidence, scoring and diffing. MARS covers more anti-pattern types and reports precision/recall, so it is broader as a catalogue. My contribution is the integrated developer-facing pipeline rather than having the largest catalogue.

### Additional practical questions

**Q: Were the evaluated repository versions pinned to commits, or could results change if the default branch changes?**
> If the exact commit is not pinned, results can change as repositories evolve. For strict reproducibility, I would record the commit SHA for each clone and include it in the evaluation table. The current results should be read as a snapshot of the repositories at analysis time.

**Q: What exact criteria did you use to select the eleven projects?**
> Public GitHub/GitLab availability, Java or JVM microservice code, Maven or Gradle build files, and at least three services so graph-based detectors have meaningful input. I also chose a mix of small demos, reference systems, benchmark projects and larger enterprise-style systems to exercise different detectors.

**Q: How would results differ on enterprise systems versus demo/reference projects?**
> Enterprise systems would likely introduce more scale, more historical debt, more diverse communication mechanisms, and more external configuration. That could reveal additional coupling, but it could also create more false negatives because the current tool does not fully handle messaging, runtime discovery, or non-Java services.

**Q: What happens if a project uses Spring profiles, environment variables, or external config for datasource URLs?**
> The detector resolves simple placeholders and defaults, but it cannot fully resolve runtime profile activation or Spring Cloud Config values. In those cases Shared Database detection may miss a real shared database or compare incomplete configuration values. Runtime or deployment configuration analysis would improve this.

**Q: How does the tool behave when DesigniteJava is missing, times out, or exits with an error?**
> The Designite step is resilient: if the JAR is missing it is skipped, if it times out the process is stopped, and if it exits with a non-zero code the service still tries to parse any output produced. The analysis can continue, but the Code Quality category may have fewer or no smell records.

**Q: Is job cancellation immediate, or only checked between analysis phases?**
> It is cooperative cancellation. The worker checks the job status before major phases and between per-service analysis steps. It does not forcibly interrupt every low-level Spoon or Designite operation instantly, so cancellation may take effect at the next checkpoint.

**Q: How do you prevent one authenticated user from accessing another user's project or job by ID?**
> Service methods query by both resource ID and owner, or validate the job through its owning project. The controller receives the authenticated user from Spring Security, so an ID alone is not enough to access another user's resources.

**Q: Why are only public GitHub/GitLab HTTPS repositories supported?**
> It keeps ingestion simple and safer for a thesis prototype. Supporting private repositories would require token handling, secret storage, revocation rules and stronger audit controls. Public HTTPS URLs are enough for the evaluation and demo.

**Q: What are the security tradeoffs of storing the JWT in browser storage?**
> It is simple and works well for an SPA, but local storage is exposed to XSS if the frontend has a script injection vulnerability. A production system could use short-lived access tokens, refresh-token rotation, stronger CSP, or secure HttpOnly cookies depending on deployment constraints.

**Q: Why are CSRF and CORS disabled in the backend configuration?**
> CSRF is disabled because the API uses stateless bearer-token authentication rather than cookie-based sessions. CORS is disabled in this backend setup because production traffic is expected to pass through the frontend/reverse proxy; if the API were exposed to separate browser origins, CORS should be explicitly configured.

**Q: If detector thresholds are changed between two analyses, is the historical diff still comparable?**
> It is comparable as "what changed under the configured analysis runs," but not as a pure source-code delta. For strict comparisons, threshold profiles should be versioned and kept constant between runs, or displayed in the diff.

**Q: Would you fail a CI build on the total health score, or only on new critical findings?**
> I would start with "no new critical findings" and "score must not regress" rather than failing on all legacy issues. That matches the Clean as You Code idea: avoid blocking teams on existing debt while preventing new high-risk debt.

### Additional source-code questions

**Q: Show where user ownership is enforced when loading projects, jobs, and results.**
> I would open the service layer, especially `ProjectService` and `JobService`. Project queries are scoped through the authenticated user's ID or owner entity, and job/result access is checked through the job's project ownership before returning data.

**Q: Show how cooperative cancellation is implemented in `AnalysisWorker`.**
> `AnalysisWorker` calls `isCancelled(jobId)` before starting and between major phases: service detection, per-service analysis, graph building, detection and completion. `JobService.cancelJob()` marks the job as `CANCELLED`; the worker observes that status and stops before continuing.

**Q: What happens if Spoon fails to parse one service or one class?**
> The relevant scanner logs the failure and skips that model or class rather than killing the whole analysis. This favours resilience, but it can produce false negatives for that service. A production version should surface parse warnings in the UI.

**Q: How does `DependencyGraphBuilder` avoid false matches from fuzzy service-name resolution?**
> It tries safer strategies first: service aliases from directory names and `spring.application.name`, then port-based matching, and only then fallback fuzzy matching. The fuzzy step improves recall, but it is the least certain part and should be interpreted with the stored evidence.

**Q: Why are evidence, affected services, and details stored as JSON text instead of fully normalized tables?**
> The evidence schema differs by detector: cycles, hardcoded URLs, god classes and shared databases all need different fields. JSON keeps detector-specific payloads flexible while the common relational fields remain queryable. The tradeoff is weaker database-level structure for evidence details.

**Q: How are disabled detectors represented in the final score and result interpretation?**
> Disabled detectors simply do not produce findings, so they do not contribute penalties. That means the score reflects the configured detector set, not an absolute full-catalogue assessment. In production I would display the active detector profile next to the score.

**Q: What happens under load if many users start analyses at the same time?**
> Analyses run through a configured async executor with bounded pool and queue settings. Jobs beyond active capacity wait in the queue. For production, I would add stronger resource isolation, per-user rate limits and external worker scaling.

**Q: Why does the async executor use configured pool and queue sizes?**
> Static analysis is CPU- and I/O-heavy. A bounded executor prevents every upload from immediately starting a heavy analysis and exhausting the server. The values are configurable so deployment can match available hardware.

**Q: Could the diff issue key misclassify two different findings as unchanged?**
> It can in edge cases, because the key is a heuristic based on type, affected services and type-specific signatures. It handles duplicates with a deque so identical findings match one-to-one, but renamed services or weak signatures can still appear as resolved/new or unchanged incorrectly.

**Q: Is the persisted health score authoritative, or is the score recalculated from current scoring logic?**
> The result stores the health score, but detailed breakdowns and diffs use `HealthScoreCalculator`. If scoring logic changes later, recomputed breakdowns may differ from older stored scores unless the scoring profile is versioned. That is another reason to version scoring rules in a production system.

**Q: Why is source LOC counted from `.java` files only, and what does that exclude?**
> The detector focuses on Java/Spring source analysis, so Java LOC is the relevant denominator for service sizing and code-smell density. It excludes YAML, SQL, frontend code, generated files and non-Java services, which is acceptable for this scope but incomplete for polyglot systems.

**Q: How would you test a new detector beyond positive and negative cases?**
> I would add threshold-boundary tests, false-positive fixtures, malformed input cases, evidence-snippet checks, disabled-detector behaviour and integration with the health score if the detector affects scoring. For graph detectors, I would also test small, disconnected and duplicate-edge graphs.

**Answer structure if the wording is unexpected:**
> Use **input → method → tradeoff → limitation**. For example: "The input is the dependency graph; the method is Tarjan SCC; the tradeoff is static calls rather than runtime traffic; the limitation is that dynamic or message-based calls can be missed."

---

## IF I BLANK (recovery scripts — these are allowed)

- *"Could you repeat the question?"* — buys time, totally normal.
- *"Let me start from the high level: …"* — then give the one-line summary and narrow down.
- *"The exact value I'd need to check, but the idea is…"* — pivot to mechanism.
- Worst case: *"That's in Chapter X — the short version is…"* and give one sentence.

**Final reminder:** They already know the work is solid. They just want to hear me explain my own decisions. Breathe. Lead with a specific. One sentence of why. Done.
