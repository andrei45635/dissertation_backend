# MSA Detector Dissertation Presentation

Total target time: **15 minutes**
- Main presentation: ~12 minutes
- Case studies: ~1.5 minutes
- Live demo: ~1.5 minutes

---

## Slide 1 - Title (0:30)

**On-slide content**
- MSA Detector: Automated Detection of Architectural Anti-Patterns in Java Microservices
- Author name
- Supervisor name
- University / Department
- Date

**Visual**
- University logo (top-right)

**Presenter notes**
- Good [morning/afternoon], my name is [Name], and today I present my dissertation on MSA Detector.
- This work focuses on automatically detecting architectural anti-patterns in Java microservice systems.

---

## Slide 2 - Agenda (0:20)

**On-slide content**
- Motivation and problem
- Objectives and anti-pattern scope
- Detection methodology
- System architecture and implementation
- Evaluation results
- Conclusions and future work
- Live demo

**Visual**
- Optional simple timeline icon row

**Presenter notes**
- I will first explain why this problem matters, then show methodology and implementation.
- I will then present evaluation results and finish with a short live demo.

---

## Slide 3 - Context and Motivation (1:30)

**On-slide content**
- Microservices improve scalability and deployment independence.
- Architectural anti-patterns still emerge in practice.
- Manual architecture review is hard to scale.
- Need: automated, early, evidence-based detection.

**Visual**
- `thesis/figures/images/chapter2/monolith_vs_microservices.drawio.png`

**Presenter notes**
- Microservices promise independent deployability, fault isolation, and team autonomy.
- In real projects, anti-patterns such as shared databases and cyclic dependencies silently accumulate as architectural debt.
- The cost of fixing these issues increases over time, which motivates automated detection early in development.

---

## Slide 4 - Problem and Objectives (1:30)

**On-slide content**
- Problem:
  - Existing detection support is fragmented across abstraction levels.
  - Outputs are often hard to act on.
- Objectives:
  1. Detect 10 anti-patterns across 4 dimensions.
  2. Combine code-level and architecture-level analysis.
  3. Provide a composite health score.
  4. Deliver as web app + REST API.
  5. Evaluate on open-source microservice datasets.

**Visual**
- Simple "Problem -> Objectives" diagram

**Presenter notes**
- The key gap is that many approaches focus either on code smells or on architecture dependencies, but rarely both.
- This dissertation addresses that by combining both levels into one analysis pipeline and presenting actionable evidence.

---

## Slide 5 - Anti-Patterns in Scope (1:30)

**On-slide content**
- **Service design**: Nano Service, God Service
- **Communication**: Chatty Service, Cyclic Dependency, Hardcoded Endpoints, ESB Misuse
- **Data management**: Shared Database
- **Deployment and coupling**: Distributed Monolith, API Versioning Absence, Wrong Cuts

**Visual**
- Left: `thesis/figures/images/chapter2/cyclic-dependency.drawio.png`
- Right: `thesis/figures/images/chapter2/shared_db.drawio.png`

**Presenter notes**
- I target ten anti-patterns that are both common and impactful in microservice systems.
- Each detector has configurable thresholds and generates source code evidence for remediation.
- ESB Misuse detection uses betweenness centrality via Brandes' algorithm alongside ratio-based signals.
- Wrong Cuts uses Feature Envy smells and bidirectional dependency detection.

---

## Slide 6 - Detection Pipeline (2:00)

**On-slide content**
1. Project ingestion (ZIP or Git)
2. Automatic microservice boundary detection
3. Intra-service analysis (DesigniteJava)
4. Inter-service AST analysis (Spoon)
5. Dependency graph construction
6. Anti-pattern detection + evidence extraction
7. Composite health score and report

**Visual**
- `thesis/figures/images/chapter3/analysis_pipeline.drawio.png`

**Presenter notes**
- The pipeline starts by identifying microservices from build files.
- DesigniteJava contributes code-smell signals (for example, God Class frequency).
- Spoon extracts dependencies from annotations and client calls.
- Results are merged into a dependency graph used for graph-based detection and final reporting.

---

## Slide 7 - Graph Analysis and Health Score (1:30)

**On-slide content**
- Dependency graph: nodes = services, edges = inter-service dependencies
- Cycle detection via Tarjan SCC
- Betweenness centrality for ESB Misuse (Brandes' algorithm)
- Coupling coefficient for distributed monolith signals
- Health score: `H = S_ap(40) + S_cq(20) + S_arch(25) + S_sz(15)`
  - 4 categories, each with independent cap and itemized deductions

**Visual**
- `thesis/figures/images/chapter3/dependency_graph_example.png`

**Presenter notes**
- The graph is central to detecting structural anti-patterns such as cycles, high coupling, and ESB misuse.
- The health score is decomposed into four categories: Anti-Patterns (40 pts), Code Quality (20), Architecture (25), Service Sizing (15).
- Each category has an independent cap and itemized deductions, enabling teams to identify which quality dimension is degraded.

---

## Slide 8 - Implementation Architecture (1:00)

**On-slide content**
- Backend: Spring Boot
- Frontend: Angular
- Data store: PostgreSQL
- Deployment: Docker
- Detector design: pluggable Strategy components
- Interfaces: browser dashboard + REST API

**Visual**
- `thesis/figures/images/chapter4/high-level-diagram.drawio.png`

**Presenter notes**
- The implementation is designed for practical use in both manual analysis and CI/CD integration.
- Pluggable detector components make future anti-pattern extensions straightforward.

---

## Slide 9 - Evaluation Results (2:00)

**On-slide content**
- Dataset: 6 open-source projects, 99 microservices, ~335k LOC
- Health scores:
  - MicroservicesSocial: 77 (C)
  - Site-Where: 69 (C)
  - microservices-design-patterns: 60 (D)
  - Apollo-Config: 59 (D)
  - Train-Ticket: 52 (D)
  - Genie: 45 (F)
- Most frequent: Hardcoded Endpoints, Nano Service, API Versioning Absence
- ESB Misuse detected in Site-Where; Chatty Service in Train-Ticket

**Visual**
- Bar chart from `thesis/presentation/results_chart_data.csv`

**Presenter notes**
- Health scores ranged from 45 to 77, showing the scoring differentiates meaningfully.
- Train-Ticket triggered Chatty Service and Shared Database detections.
- Site-Where triggered ESB Misuse detection — validating the betweenness centrality implementation.
- Genie scored lowest due to God Service, Nano Service and Hardcoded Endpoints.

---

## Slide 10 - Case Study: Site-Where (0:45)

**On-slide content**
- Site-Where: IoT platform, 15 microservices, 74k LOC
- Health score: 69 (C)
- Detected: God Service, ESB Misuse, Hardcoded Endpoints
- First project to trigger ESB Misuse detector
- ESB Misuse detected via betweenness centrality — a service acting as central mediator

**Visual**
- Optional: screenshot of Site-Where results page from the app

**Presenter notes**
- Site-Where is significant because it triggered the ESB Misuse detector, validating the betweenness centrality implementation.
- The centralized orchestration layer in the IoT platform naturally creates a hub service that mediates most inter-service communication.
- This demonstrates that the detector works on real-world architectural patterns, not just synthetic examples.

---

## Slide 11 - Case Study: Train-Ticket (0:45)

**On-slide content**
- Train-Ticket: academic benchmark, 42 services, 38k LOC
- Health score: 52 (D)
- Detected: 4 Chatty Services, 27 Hardcoded Endpoints, 4 Nano Services, 1 Shared Database
- 36 total anti-pattern instances — highest count across all datasets
- First project in evaluation to trigger Chatty Service detector
- Realistic microservice topology with inter-service REST calls

**Visual**
- Optional: screenshot of Train-Ticket results page from the app

**Presenter notes**
- Train-Ticket is specifically designed as a microservice research benchmark by Fudan University.
- The 42 services communicate via RestTemplate, producing a rich dependency graph.
- The 4 Chatty Service detections validated a detector that no other project triggered.
- 27 Hardcoded Endpoints reflect the project's use of literal localhost URLs rather than service discovery.
- The Shared Database detection found services sharing a single data store, consistent with the project's known architecture.

---

## Slide 12 - Conclusions and Future Work (1:00)

**On-slide content**
- Contributions:
  - Multi-level static analysis approach
  - 10 anti-pattern detectors with evidence
  - Composite health score and trend tracking
  - Automated microservice boundary detection
- Limitations:
  - Java + Spring Boot scope
  - Static-analysis-only constraints
- Future work:
  - Multi-language support
  - Additional anti-patterns
  - Deeper CI/CD and IDE integration

**Visual**
- Optional roadmap graphic or minimal text-only slide

**Presenter notes**
- The core contribution is actionable architectural quality feedback without requiring a running system.
- The methodology is extensible even though the current implementation is Java/Spring-focused.

---

## Slide 13 - Live Demo (2:00)

**On-slide content**
- Live demo:
  1. Ingest project (Git clone)
  2. Run analysis
  3. Inspect anti-pattern findings + snippets
  4. Show health score and history/diff

**Visual**
- Optional small thumbnails:
  - `thesis/figures/images/chapter4/clone_page.png`
  - `thesis/figures/images/chapter4/analysis_page.png`
  - `thesis/figures/images/chapter4/history_page.png`

**Presenter notes**
- I will run one full analysis flow live and show the final findings.
- If runtime is tight, I will use a pre-analyzed project and focus on findings, score, and analysis history.
- Transition to Q&A after the demo.

