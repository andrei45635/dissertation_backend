# MSA Detector Dissertation Presentation

Total target time: **15 minutes**
- Main presentation: ~13 minutes
- Live demo: ~2 minutes

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
  1. Detect 8 anti-patterns across 4 dimensions.
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
- **Communication**: Chatty Service, Cyclic Dependency, Hardcoded Endpoints
- **Data management**: Shared Database
- **Deployment and coupling**: Distributed Monolith, API Versioning Absence

**Visual**
- Left: `thesis/figures/images/chapter2/cyclic-dependency.drawio.png`
- Right: `thesis/figures/images/chapter2/shared_db.drawio.png`

**Presenter notes**
- I target anti-patterns that are both common and impactful in microservice systems.
- Each detector has configurable thresholds and generates source code evidence for remediation.

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
- Coupling metrics for distributed monolith signals
- Health score formula:
  - `H = max(0, 100 - 15*n_crit - 10*n_high - 5*n_med - 2*n_low)`

**Visual**
- `thesis/figures/images/chapter3/dependency_graph_example.png`

**Presenter notes**
- The graph is central to detecting structural anti-patterns such as cycles and high coupling.
- The health score gives one trackable indicator while still preserving detailed findings by severity and category.

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
- Dataset: 4 open-source projects, 60 microservices, ~465k LOC
- Health scores:
  - MicroservicesSocial: 77 (C)
  - microservices-design-patterns: 60 (D)
  - Activiti: 45 (F)
  - Karate: 87 (B)
- Frequent detections: Nano Service, God Service, Hardcoded Endpoints

**Visual**
- Bar chart from `thesis/presentation/results_chart_data.csv`

**Presenter notes**
- The scoring differentiated projects with lower and higher architectural issue concentration.
- The largest project, Activiti, scored lowest, while Karate scored highest.
- Shared Database appears in MicroservicesSocial, consistent with its architecture.

---

## Slide 10 - Conclusions and Future Work (1:00)

**On-slide content**
- Contributions:
  - Multi-level static analysis approach
  - 8 anti-pattern detectors with evidence
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

## Slide 11 - Live Demo (2:00)

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

