# Chapter Cuts — What Changed in the `_after_cuts` Versions

This file documents the differences between the original chapters and their trimmed
counterparts:

| Original | Trimmed version |
|----------|-----------------|
| `chapter2.tex` | `chapter2_after_cuts.tex` |
| `chapter3.tex` | `chapter3_after_cuts.tex` |
| `chapter4.tex` | `chapter4_after_cuts.tex` |

The `_after_cuts` files are the canonical, current versions. Cuts were made to remove
**redundancy, commented-out/dead content, and illustrative code listings** — not substance.
Every equation, table, threshold, severity, and architectural claim from the originals is
preserved (verbatim, relocated, or described in prose).

## Word-count impact

| Chapter | Original | After cuts | Δ words | Δ % |
|---------|---------:|-----------:|--------:|----:|
| Ch. 2 — State of the Art            | 5008 | 4253 | −755  | ≈ −15% |
| Ch. 3 — Detection Methodology        | 5485 | 5317 | −168  | ≈ −3%  |
| Ch. 4 — Application Design & Impl.    | 8004 | 5576 | −2428 | ≈ −30% |
| **Total**                            | **18497** | **15146** | **−3351** | **≈ −18%** |

> Note: word reduction does **not** map 1:1 to pages. Most of the removed text was code
> listings, pseudocode, and dead/commented blocks; sentence-level trims mostly reflow.
> Estimated rendered reduction is roughly **1–2 pages**.

## Cross-reference integrity

No dangling `\ref` was introduced. Every removed label was checked and is referenced
nowhere in any chapter:
`lst:jwt-generate`, `lst:detector-interface`, `lst:dockerfile-runtime`,
`alg:shared-database`, `alg:tarjan`, `eq:nano-service`, `eq:god-service`,
`eq:chatty-service`, `eq:distributed-monolith`, and the `subsubsec:*` deployment labels.

---

## Chapter 2 — State of the Art

**Removed (dead/commented content):**
- Commented-out author note (Romanian `\textcolor{red}{…}`) about figure attribution.
- The entire commented-out **"Tools and Technologies"** section — it was already disabled
  with `%` in the original, so it never appeared in the PDF. It contained subsections on
  Spring Boot, PostgreSQL, Spoon, JGit, DesigniteJava, and Docker, plus an author note.

**Relocated to Chapter 3 (not lost):**
- Four inline equations and their threshold explanations, which now live in the
  per-anti-pattern methodology:
  - Nano Service `eq:nano-service` → `eq:nano-service-detection`
  - God Service `eq:god-service` → `eq:god-service-detection`
  - Chatty Service `eq:chatty-service` → `eq:chatty-service-detection`
  - Distributed Monolith `eq:distributed-monolith` → `eq:coupling-coeff-dm` + `eq:dm-decision-rule`
  - Chapter 2 now references Chapter 3 for the formal criteria instead of duplicating them.

**Relocated to Chapter 4 (not lost):**
- The **DesigniteJava** description moved into the Ch. 4 Technology Stack
  (`subsubsection{DesigniteJava}`, label `subsec:designite-java`).

**Condensed (content preserved, shorter form):**
- **"Positioning and Contributions of This Work"** was rewritten from seven enumerated
  contribution paragraphs (*First… Sixth… Finally*) into two paragraphs. All seven
  contributions are retained: multi-level analysis, ten anti-patterns across four
  dimensions, deployability-gate auto-detection, configurable thresholds, the composite
  health score, evidence-based code-snippet reporting, and the web app + REST/CI-CD delivery.

**Fixed:** the chapter Summary previously read *"The tools and technologies used in the
implementation were surveyed as well."* — inaccurate after the tools section moved to
Chapter 4. That sentence was removed, and the opening was trimmed from "theoretical and
technological foundation" to "theoretical foundation."

---

## Chapter 3 — Detection Methodology

**Removed (pseudocode listings; prose descriptions retained):**
- **Algorithm "Shared Database Detection"** (`alg:shared-database`) — the detector's
  two-phase logic remains described in prose in §"Shared Database Detection".
- **Algorithm "Tarjan's SCC Algorithm for Cycle Detection"** (`alg:tarjan`) — Tarjan's
  `index`/`lowlink`, the SCC-root condition, `O(|V|+|E|)`, and disconnected-graph handling
  all remain in prose in §"Cyclic Dependency Detection".

**Removed (dead/commented content):**
- A commented-out `verbatim` block showing the `/v\d+[/.]` regex. The regex itself still
  appears inline in the prose of §"API Versioning Absence Detection".

**Reformatted (no content change):**
- The three-signal deployability-gate `enumerate` list (Framework entry point / Dockerfile /
  `main()` method) was rewritten into three prose paragraphs. All three signals, their
  confidence levels, and the fallback strategy are fully preserved.

**Received relocated content:** the four equations moved out of Chapter 2 (see above).

---

## Chapter 4 — Application Design & Implementation

This chapter had the largest reduction (≈ −30%), almost entirely from code listings and
verbose frontend/deployment detail.

**Removed (author scaffolding):**
- The top-of-file `% TODO …` planning/comment block.
- A commented-out `\textcolor{red}{…}` note about a table overflowing the margin.

**Removed (code listings; behaviour retained in prose):**
- **JWT `generateToken()`** Java listing (`lst:jwt-generate`) — both the commented
  `lstlisting` variant and the active `minted` version. The token-generation logic
  (subject = user ID, email claim, HMAC-SHA signing, 24 h expiry) remains in prose.
- **`AntiPatternDetector` interface** listing (`lst:detector-interface`) — the single
  `detect(Project, List<Microservice>)` signature and Spring auto-collection remain in prose.
- **Dockerfile runtime-stage** listing (`lst:dockerfile-runtime`) — the non-root user,
  container-aware JVM flags, Git install, and health check remain in prose.
- **Kept:** the `AnalysisWorker.processJob()` five-phase listing (`lst:analysis-worker`).

**Condensed (content trimmed, key facts kept):**
- **Frontend → Application Structure and Routing:** the `bootstrapApplication`/providers-array
  walkthrough and the context-sensitive-header description were shortened.
- **Frontend → Authentication Integration:** collapsed from five detailed paragraphs into
  one. Dropped low-level detail (the `auth_token`/`auth_user` `localStorage` keys, the
  `BehaviorSubject` internals, per-method walkthroughs); kept the roles of `AuthService`,
  the two interceptors, and the guard.
- **Frontend → Pages:** per-page descriptions trimmed (e.g. the six-character password rule,
  upload-mode internals, results-page layout specifics, history-tab specifics).
- **Frontend → Reusable Components:** collapsed from seven detailed per-component paragraphs
  into one summarizing paragraph that still names all seven components. Dropped implementation
  minutiae: the SVG gauge math (circumference ≈ 283), the `DiffBanner` eleven-card layout,
  Cytoscape.js **v3.10** + CoSE layout parameters (edge length 150, repulsion 8000) and the
  string-ID coercion note, the `FileUpload` DOM-event handling, `CodeSnippetViewer` language
  detection, and the `ProgressTracker` six-step model.
- **Deployment → Production Deployment Considerations:** five subsubsections collapsed into
  two paragraphs:
  - `Hosting Infrastructure` (`subsubsec:hosting`)
  - `Data Access and Code Snippet Storage` (`subsubsec:data-access`)
  - `TLS Termination and Domain Configuration` (`subsubsec:tls`)
  - `Backup and Data Persistence` (`subsubsec:backup`)
  - `Resource Sizing and Scaling` (`subsubsec:scaling`)

  Dropped specifics (named providers: DigitalOcean, Hetzner, AWS EC2/RDS, Cloudflare; and
  some operational detail). Retained the key points: portability, no horizontal scaling
  needed (sequential jobs), JSON evidence persisted in `code_snippets`/`dependency_graph_json`
  so PostgreSQL is the sole stateful component, TLS via reverse proxy + Let's Encrypt or a
  cloud LB, `pg_dump` → object storage, and Spoon-driven heap sizing.
