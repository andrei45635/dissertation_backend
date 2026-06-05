# Thesis Defense — Q&A Cheat Sheet

**Title:** Diagnosing the Distributed: A Static Analysis Approach to Microservice Anti-Pattern Detection
**Tool:** MSA Detector — Spring Boot + Angular + PostgreSQL, Dockerized.
**Format:** ~5 min Q&A. Usually 1 theoretical + 1 practical + 1 source-code question.

---

## BEFORE YOU SPEAK (read this first if nervous)

- Take **one breath** before answering. Silence for 2 seconds is fine and looks thoughtful.
- **Lead with the term or the number, then say why.** That's all a good answer is.
- Keep each answer **30–60 seconds**. Don't over-explain.
- If you don't recall an exact value: *"I'd have to check the exact number, but the mechanism is…"* — the mechanism matters more.
- Have the repo open to **`SharedDatabaseDetector`** and **`GodServiceDetector`** before walking in.

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
> Two complementary approaches. From the dependency graph, any edge with a call count ≥ **10** (configurable), where each `@FeignClient` method counts as one call. Plus a Spoon source scan for chatty HTTP-client types the graph misses: Feign interfaces with ≥10 methods, non-Feign "client/api/http"-named interfaces with ≥10 HTTP-mapping methods, and classes with ≥10 calls on known client types (`RestTemplate`, `WebClient`, `HttpURLConnection`…). Server-side controllers are excluded to avoid false positives.

**6. Hardcoded Endpoints — MEDIUM.** *How do you detect it?*
> A line-by-line scan of each service's `.java` files for URL patterns (`http://`, `https://`, `localhost:`, `127.0.0.1`) that appear inside string literals, extracting the full URL with a regex. I skip comments, imports, package declarations and test files, and cap evidence at five snippets per service. These indicate calls bypassing service discovery.

**7. Distributed Monolith — CRITICAL.** *How do you detect it?*
> A system-level check using three metrics: the **coupling coefficient** C, the **connected ratio** R (fraction of services in at least one dependency), and the **shared-database count** D. I flag the system if `C > 0.5`, **or** `R > 0.8 and D > 0`, **or** `R > 0.8 and C > 0.3`. Only runs with ≥3 services. It's intentionally sensitive because an undetected distributed monolith is costly — thresholds are configurable if a team finds it too aggressive.

**8. API Versioning Absence — MEDIUM.** *How do you detect it?*
> During endpoint extraction I test each path against the regex `/v\d+[/.]` (matches `/v1/`, `/v2/`, etc.) and set a `hasVersioning` flag. A service is flagged when **none** of its endpoints carry a version indicator. Evidence is the `@RestController`/`@RequestMapping` of an unversioned controller, showing where a `/v1/` prefix could go.

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
| Chatty Service | HIGH | call count ≥ 10 (per-method Feign) |
| Hardcoded Endpoints | MEDIUM | URL literal in `.java` (non-test) |
| Distributed Monolith | CRITICAL | C>0.5, or R>0.8 & D>0, or R>0.8 & C>0.3 |
| API Versioning Absence | MEDIUM | no endpoint matches `/v\d+[/.]` |
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

### Extra practical questions

**Q: If a company used your tool, what should they fix first?**
> I would prioritize issues that block independent deployment: Cyclic Dependency, Shared Database, Wrong Cuts, and Distributed Monolith indicators. Those affect service autonomy. Lower-risk issues like missing API versioning or nano services can be handled after the structural coupling is understood.

**Q: How would this integrate into CI/CD?**
> The backend already supports asynchronous analyses and stores historical results. In CI/CD, the tool could run after a merge request or nightly build and report new critical findings. I would avoid failing builds on all legacy issues; a better gate is "no new critical issues" or "health score must not regress."

**Q: What result surprised you most?**
> The frequency of API Versioning Absence and Nano Service. Many projects show microservice decomposition and service discovery, but not always mature API lifecycle practices or balanced granularity. That is why the results are useful: they reveal architectural debt even in projects that look structurally microservice-based.

**Q: Why did no project receive an A grade?**
> Because the score combines multiple dimensions. A project can avoid severe cyclic dependencies and still lose points for API versioning, hardcoded endpoints, code-smell density, or service sizing. The goal is not to label projects as bad, but to show where architectural debt accumulates.

**Q: What would you change before deploying this in an enterprise environment?**
> I would add organization-specific threshold profiles, better support for Spring Cloud Config and Kubernetes manifests, and eventually runtime telemetry. Static analysis is good for early detection, but traces and metrics would improve confidence for Chatty Service and real coupling.

**Q: How does a developer know whether a finding is a false positive?**
> Each finding includes affected services, severity, explanation, remediation advice, and evidence snippets. The tool does not ask developers to trust a black box; it gives them the concrete source or graph evidence so they can accept, reject, or tune the finding.

### Extra source-code questions

**Q: Why do you start the analysis worker after transaction commit?**
> Uploading or cloning creates the project and job rows in a transaction. The worker is started through an `afterCommit()` callback, so the async thread cannot read a job that has not been committed yet. That avoids a race condition between the request thread and the background analysis.

**Q: How is progress visible while analysis is running?**
> The worker updates the job status between phases: detecting services, analyzing services, building the graph, detecting patterns, then completed or failed. The frontend polls these job fields, so the user sees progress instead of waiting for a single blocking request.

**Q: How did you protect ZIP upload extraction?**
> The extractor normalizes every ZIP entry path to prevent Zip Slip, rejects duplicate entries, and streams each file while counting decompressed bytes. It also enforces limits on total uncompressed size, number of entries, and nesting depth, then deletes the partial workspace if extraction fails.

**Q: How does the dependency graph resolve a call target to a service?**
> It builds aliases from service directory names, `spring.application.name`, and configured ports. Then it resolves Feign clients, `RestTemplate` calls, and `WebClient` calls using exact name matching, port matching, and fallback fuzzy matching. Each resolved call becomes a directed edge in the service dependency graph.

**Q: Why use Spoon instead of regular expressions everywhere?**
> Spoon understands Java structure: annotations, methods, invocations, types, and class relationships. That is more reliable for endpoint extraction, Feign clients, TCC metrics, and God Service detection. Regex is only used where the signal is naturally textual, such as hardcoded URL literals.

**Q: How do you avoid double-counting Nano Service and God Service in the health score?**
> They are detected as anti-patterns, but they are excluded from the general Anti-Patterns category and counted only in Service Sizing. That way service-granularity problems affect the score once, in the category where they belong.

**Q: What happens if one detector fails?**
> The current orchestrator logs that detector failure and continues with the remaining detectors. The benefit is resilience: one detector does not kill the whole analysis. The tradeoff is that a production version should expose partial-analysis warnings clearly in the UI.

**Q: Where is the system extensible in code?**
> The main extension point is the `AntiPatternDetector` interface. A new detector implements `detect(Project, List<Microservice>)`, is annotated as a Spring component, and is automatically included because the orchestrator receives a `List<AntiPatternDetector>`. That keeps the pipeline independent of individual detector classes.

---

## IF I BLANK (recovery scripts — these are allowed)

- *"Could you repeat the question?"* — buys time, totally normal.
- *"Let me start from the high level: …"* — then give the one-line summary and narrow down.
- *"The exact value I'd need to check, but the idea is…"* — pivot to mechanism.
- Worst case: *"That's in Chapter X — the short version is…"* and give one sentence.

**Final reminder:** They already know the work is solid. They just want to hear me explain my own decisions. Breathe. Lead with a specific. One sentence of why. Done.
