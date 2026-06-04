# MSA Detector Dissertation Presentation

Total target time: **~15 minutes** (12 slides + live demo).

This outline mirrors the generated deck in `build_deck.js`. Slide numbers, titles and on-slide content match the `.pptx`; the presenter notes are condensed versions of the speaker notes embedded in the deck.

---

## Slide 1 - Title (0:30)

**On-slide content**
- Detecting Architectural Anti-Patterns in Microservices
- A multi-level static analysis tool for Java/Spring Boot systems
- Iacob Andrei
- Supervisor: Prof. Dr. Simona Motogna
- Faculty of Mathematics and Computer Science, UBB

**Presenter notes**
- Good [morning/afternoon], my name is Andrei Iacob, and today I defend my dissertation on the MSA Detector, a tool for detecting architectural anti-patterns in Java-based microservice systems.
- The core idea: as teams adopt microservices, they often introduce structural problems that erode the very benefits they were after. This tool catches those problems early, from a source checkout.

---

## Slide 2 - Why this matters (1:30)

**On-slide content**
- Microservices adoption has outpaced tooling.
- Architectural debt accumulates silently (shared databases, cyclic dependencies, ill-sized services).
- Existing tools are single-level: Arcan, MicroART, MSANose each cover a slice, rarely both code and architecture.
- Findings without evidence (snippets, affected services, remediation) get ignored.
- Goal: actionable, evidence-based detection that bridges code and architecture levels.

**Visual**
- `thesis/figures/images/chapter2/monolith_vs_microservices.drawio.png`

**Presenter notes**
- Microservices are now the default for new large systems, but architectural quality tooling has not kept up.
- Existing academic tools each handle a slice: Arcan does code-level cycles, MicroART recovers architecture, MSANose detects some microservice smells. None combine code-level analysis with architectural-level analysis.
- Even when something is detected, the output is usually an abstract warning. Developers do not act without code evidence and remediation guidance.

---

## Slide 3 - Objectives and contributions (1:15)

**On-slide content**
- Multi-level analysis: intra-service code analysis (DesigniteJava smells + Spoon structural metrics) combined with inter-service dependency analysis (graph algorithms).
- Ten anti-patterns across four dimensions.
- Automated boundary detection: three-signal deployability gate with confidence levels.
- Composite health score: 0-100 with letter grading, four interpretable categories, change over time.
- Evidence-based reporting: source snippets, affected services, remediation per finding.

**Presenter notes**
- The first contribution is the multi-level analysis itself: using code-level structural metrics like class cohesion to flag architectural problems like God Service. That bridge between abstraction levels is the central novelty.
- Ten anti-patterns across four dimensions is broader coverage than any single existing tool.
- Automated boundary detection via a three-signal deployability gate (framework entry points, Dockerfiles, main methods) removes the manual configuration most tools require.
- The composite score is decomposed into four categories so you can see which dimension drags the score down, and every finding ships with the code that triggered it.

---

## Slide 4 - The analysis pipeline (2:00)

**On-slide content**
1. Ingest (ZIP upload / Git clone)
2. Detect services (deployability gate, three signals)
3. Intra + inter analysis (DesigniteJava + Spoon)
4. Detect patterns (ten detectors, Strategy pattern)
5. Score and report (health score + evidence)

**Visual**
- `thesis/figures/images/chapter3/analysis_pipeline.drawio.png`

**Presenter notes**
- Ingest a ZIP or a Git URL, then detect microservices by scanning for build files and applying a three-signal deployability gate: framework entry point (HIGH), Dockerfile (MEDIUM), main method (LOW).
- Intra-service analysis runs DesigniteJava per service for code smells, whose density feeds the health score. Inter-service analysis uses Spoon to find @FeignClient, RestTemplate and WebClient calls and to build the dependency graph.
- Ten detectors then run over the graph and the per-class structural metrics. Each detector is a Spring component implementing a common interface, so adding one means writing a single class.
- Results are assembled, the health score is computed, and the dependency graph and evidence snippets are persisted as JSON.

---

## Slide 5 - Spotlight: God Service detection (2:00)

**On-slide content**
- Multi-level bridge: code-level structural class metrics (computed from the Spoon AST) feed an architectural-level finding.
- Code level: structural class metrics per class (fields, methods, LOC, cohesion).
- Architectural level: flag a service if it contains at least one God Class.
- Six structural metrics per class: >= 25 fields, >= 30 public methods, >= 1000 LOC, >= 20 import domains, >= 12 constructor parameters, TCC < 0.5 (Bieman and Kang, 1995).
- A class is flagged as a God Class if it exceeds at least 3 of the 6 thresholds.

**Presenter notes**
- I picked God Service because it is the cleanest illustration of how code-level metrics feed an architectural-level finding.
- For each service, a Spoon-based analysis parses every class and computes the six structural metrics. The detector flags any microservice that contains at least one class identified as a God Class. A class is a God Class when it exceeds at least three of the six metrics.
- Pure data holders such as entities and DTOs are excluded first, since they naturally have many fields and low cohesion without being an anti-pattern.
- TCC, Tight Class Cohesion, is the fraction of method pairs that share an instance field access. Low TCC means methods operate on disjoint state, a sign of unrelated responsibilities packed into one class.
- If asked where DesigniteJava fits: it runs per service for code smells, but its catalogue is abstraction and modularization smells rather than a literal God Class, so its smell density feeds the code-quality category of the health score while God Service detection is driven by the Spoon structural metrics.

---

## Slide 6 - Ten detectors across four dimensions (0:45)

**On-slide content**
- Service Design: Nano Service (med), God Service (high)
- Communication: Chatty Service (high), Cyclic Dependency (critical), Hardcoded Endpoints (med), ESB Misuse (high)
- Data Management: Shared Database (high)
- Deployment and Coupling: Distributed Monolith (critical), API Versioning Absence (med), Wrong Cuts (high)
- All detectors are pluggable Strategy components with configurable thresholds.

**Visual**
- Left: `thesis/figures/images/chapter2/cyclic-dependency.drawio.png`
- Right: `thesis/figures/images/chapter2/shared_db.drawio.png`

**Presenter notes**
- Ten detectors organised by architectural dimension. Each is a Spring component implementing a common interface, so adding a detector means one class and no orchestrator changes.
- Severities feed the health score directly: critical costs 8 points, high 5, medium 3.
- If asked about a specific detector: Shared DB groups services by datasource URL; Cyclic Dependency uses Tarjan SCC; Chatty Service uses per-edge call count >= 10 or a Feign interface with >= 10 methods; Hardcoded Endpoints is a regex scan of .java files; Distributed Monolith is a composite coupling rule; API Versioning is a regex on endpoint paths; ESB Misuse uses caller/callee ratios, a volume ratio and betweenness centrality; Wrong Cuts uses bidirectional edges between a service pair.

---

## Slide 7 - Composite health score: four-category decomposition (1:15)

**On-slide content**
- `H = S_ap(40) + S_cq(20) + S_arch(25) + S_sz(15)`, each category capped independently.
- Anti-Patterns (40): -8/-5/-3/-1 per issue (critical/high/medium/low).
- Code Quality (20): density-based penalty (smells per KLOC).
- Architecture (25): coupling coefficient + cycle count.
- Service Sizing (15): nano (cap 8) + god (cap 10) penalties.
- Letter grades: A >= 90, B >= 80, C >= 65, D >= 50, F < 50.

**Visual**
- `thesis/figures/images/chapter3/dependency_graph_example.png`

**Presenter notes**
- The composite is a sum of four independent categories, each clamped at zero, so penalties cannot bleed across categories.
- The breakdown is preserved in the UI: a developer does not just see a number, they see which category is dragging it down, with itemised deductions.
- The analysis-diff feature compares successive runs, so a team that fixes a shared database can see exactly how many points that bought them.

---

## Slide 8 - Worked example: microservice-recruit (40 / F) (1:15)

**On-slide content**
- Anti-pattern findings: 6 API Versioning Absence (-18), 2 ESB Misuse (-10), 2 Wrong Cuts (-10), 1 Cyclic Dependency (-8), 1 Hardcoded Endpoint (-3). Total penalty 49, so Anti-Patterns clamps to 0.
- Code Quality: 260 smells over 8,280 LOC = 31.4 per KLOC, penalty 8, leaving 12/20.
- Architecture: 16/25 (dependency-graph coupling).
- Service Sizing: one nano service, penalty 3, leaving 12/15.
- Composite: 0 + 12 + 16 + 12 = 40, grade F.

**Presenter notes**
- microservice-recruit is a Spring Cloud recruitment platform. Its anti-pattern load (penalty 49) exceeds the 40-point cap, so Anti-Patterns clamps to zero. The two Wrong Cuts are bidirectional dependencies between pf-recruit/pf-resume and pf-recruit/pf-user.
- The single nano service is scored under Service Sizing, not Anti-Patterns, to avoid double counting.
- Code Quality is scored by smell density per 1000 LOC, not raw count, so a codebase is not punished just for its size.
- This project loses points in every dimension, which is exactly what the multi-category decomposition is meant to surface.

---

## Slide 9 - Evaluation: eleven open-source projects (2:00)

**On-slide content**
- 11 projects, 130 detected microservices, ~274k LOC.
- Health scores (0-100): MicroSocial 83, ftgo 75, PiggyMetrics 71, Site-Where 69, RuoYi 68, design-patterns 60, mall-swarm 53, NetworkDisk 52, piomin 51, Train-Ticket 40, recruit 40.
- 126 anti-pattern instances across 9 distinct types.
- Hardcoded Endpoints (43) and API Versioning Absence (37) dominate.
- Cyclic Dependency and Wrong Cuts detected in microservice-recruit; Distributed Monolith was not detected.

**Visual**
- Bar chart from `thesis/presentation/results_chart_data.csv`

**Presenter notes**
- Eleven open-source Spring Boot projects, from piomin at 1.3k LOC to mall-swarm at 86k LOC, 130 microservices and about 274k LOC.
- Health scores ranged from 40 to 83, so the scoring has useful discriminating power.
- The dominant findings are configuration and sizing issues. Only Distributed Monolith was not detected, plausible given these projects' topologies.
- If asked whether eleven projects is enough: this is a feasibility demonstration across heterogeneous codebases, not a statistical precision/recall claim, which would need a labelled corpus that does not yet exist for microservice anti-patterns. I note this in Threats to Validity.

---

## Slide 10 - Live demo (2:00)

**On-slide content**
1. Clone a small public repo and watch the pipeline progress.
2. Open the results dashboard (health score, four-category breakdown).
3. Drill into a finding to see source evidence and remediation.
4. Explore the interactive dependency graph.

**Visual**
- `thesis/figures/images/chapter4/clone_page.png`, `analysis_page.png`, `history_page.png`

**Presenter notes**
- Have the app already running and be logged in; do not start the server live.
- Keep a small known-working repo URL ready (MicroservicesSocial is a safe choice) and a second tab with a previously completed analysis as a fallback.
- The drill-down into a finding is the strongest moment: the committee sees evidence-based reporting in action.
- If the demo breaks, switch to the backup tab or the Chapter 4 screenshots and move on quickly.

---

## Slide 11 - Limitations and future work (1:00)

**On-slide content**
- Limitations: Java/Spring Boot scope; static analysis only (no runtime call frequencies); deployability gate may miss unconventional services; dynamic URLs (Spring Cloud Config) not resolved; health-score weights and thresholds chosen by literature and engineering judgement rather than empirical calibration; eleven-project evaluation is not a statistical claim.
- Future work: multi-language support via language-agnostic dependency graphs (Docker Compose / Kubernetes manifests); extended anti-pattern catalogue; ML-based detection; empirical threshold calibration.

**Presenter notes**
- The biggest limitation is the Java/Spring Boot restriction; real-world systems are polyglot.
- Static analysis trades precision for applicability: it runs on a fresh checkout without a deployed, instrumented system.
- The health-score weights and detection thresholds are engineering judgement informed by the literature rather than systematically calibrated.
- Future work in priority order: multi-language support, then ML-based detection, then empirical threshold calibration.

---

## Slide 12 - Closing (0:30)

**On-slide content**
- Multi-level static analysis combining code-level structural metrics and architectural-level dependency analysis.
- Ten anti-patterns, configurable thresholds, evidence-based reporting.
- Composite health score with four-category decomposition and analysis diff.
- Open-source web application, ready for CI/CD integration.

**Presenter notes**
- Recap the four points above, then thank the committee and invite questions.
- The takeaway: the bridge between code-level evidence and architectural-level findings is what makes this tool different. It does not just say something is wrong, it shows where in the code the problem lives.
- Then stop talking and let the committee ask. Common questions: why one God Class flags a service (sensitivity, configurable), why only eleven projects (feasibility demo, no labelled corpus), score compression at the bottom (known limitation), static versus dynamic (applicability), Spring Boot only (largest Java microservice ecosystem).
