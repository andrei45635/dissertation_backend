# Thesis Updates Required After Detector Refactoring

This document lists every section of the thesis that needs updating to reflect the code changes made to the God Service, Chatty Service, and ESB Misuse detectors, as well as the Dependency Graph Builder.

---

## 1. Chapter 3, Section 3.5 — God Service Detection (lines 251–269)

**Status:** ❌ OUTDATED — Must be rewritten

**What the thesis currently says:**
- Detection relies solely on DesigniteJava "God Class" smell count
- Threshold is `≥ 3` God Class smells (`god-service-min-domains`)
- No mention of Spoon-based analysis, structural metrics, or TCC

**What the code actually does now (two-signal approach):**

1. **Signal 1 — DesigniteJava:** Any `≥ 1` God Class smell from DesigniteJava triggers detection (threshold lowered from 3 to 1, config key renamed to `god-service-min-god-classes`)
2. **Signal 2 — Spoon multi-metric analysis (fallback):** If no DesigniteJava God Classes are found, each class is evaluated against 6 structural metrics. A class is flagged when **≥ 3** metrics exceed their thresholds simultaneously:

| Metric | Threshold | What it measures |
|---|---|---|
| Field count | ≥ 25 | Number of instance + static fields |
| Public method count | ≥ 30 | Number of public methods |
| Lines of code (LOC) | ≥ 1000 | Source lines (end line − start line + 1) |
| Import domain count | ≥ 20 | Distinct top-2-level package families referenced (excluding `java.lang`) |
| Constructor parameter count | ≥ 12 | Largest constructor's parameter count |
| Tight Class Cohesion (TCC) | < 0.5 | Fraction of method pairs sharing ≥ 1 instance field access |

A service is flagged as a God Service if it contains ≥ 1 God Class from **either** signal.

**What to update in the thesis:**
- Rewrite the detection description to reflect the two-signal approach
- Add the Spoon-based multi-metric analysis with the 6 metrics table
- Add the TCC formula: `TCC = connectedPairs / totalPairs`
- Update the formula from `θ_god = 3` to `θ_god = 1` (DesigniteJava signal)
- Replace `god-service-min-domains` with `god-service-min-god-classes`
- Update line: *"Rather than defining responsibility count heuristics directly, this detector uses the output of DesigniteJava's..."* → now it uses **both** DesigniteJava and Spoon
- Update sentence about only counting God Class smells → now also scans source with Spoon

---

## 2. Chapter 3, Section 3.6 — Chatty Service Detection (lines 271–285)

**Status:** ❌ OUTDATED — Must be significantly expanded

**What the thesis currently says:**
- Detection operates only on dependency graph edges (callCount attribute)
- Only considers `@FeignClient`, `RestTemplate`, `WebClient` calls resolved to known microservices
- Threshold is `≥ 5` calls
- No mention of source-based scanning

**What the code actually does now (two-approach detection):**

1. **Approach 1 — Dependency-based (unchanged concept, updated threshold):**
   - Queries `ServiceDependency` records where `callCount ≥ 10` (threshold raised from 5 to 10)
   - Note: `DependencyGraphBuilder.detectFeignClients()` now counts each individual non-default method in a `@FeignClient` interface as a separate call (previously counted 1 per `@FeignClient` annotation)

2. **Approach 2 — Source-based (new):**
   Scans each microservice's source code with Spoon for types exhibiting chatty HTTP client patterns:
   
   - **Check 1 — Interfaces:**
     - `@FeignClient` interfaces: flagged if they have ≥ 10 non-default methods
     - Non-`@FeignClient` interfaces: flagged only if ALL of:
       - Not annotated with `@Controller`/`@RestController`/`@RequestMapping` (excludes server-side controllers)
       - Name contains an HTTP-client keyword (`client`, `api`, `feign`, `rest`, `http`, `proxy`, `remote`, `gateway`)
       - Has ≥ 10 methods annotated with HTTP-mapping annotations (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, `@RequestMapping`, `@RequestLine`)
   
   - **Check 2 — Classes:**
     - Excluded if annotated as a controller
     - Counts invocations where both:
       - Method name is in `{openConnection, getForObject, getForEntity, postForObject, postForEntity, exchange, patchForObject, bodyToMono, bodyToFlux}`
       - Declaring type is a known HTTP client class (`RestTemplate`, `WebClient`, `HttpURLConnection`, `RestClient`, `OkHttpClient`, `CloseableHttpClient`, `HttpClient`, `AsyncHttpClient`)
     - Flagged if ≥ 10 verified HTTP call sites

**What to update in the thesis:**
- Update threshold from 5 to 10
- Add Approach 2 (source-based scanning) with the two checks described above
- Mention that Approach 2 catches chatty patterns invisible to the dependency graph (e.g., `HttpURLConnection`-based clients, interfaces without `@FeignClient`)
- Note the false-positive prevention mechanisms: controller exclusion, name-keyword filtering, declaring-type verification
- Update the formula to note it now applies to both approaches

---

## 3. Chapter 3, Summary (lines 435–443)

**Status:** ⚠️ NEEDS MINOR UPDATE

**What to update:**
- Line 440: *"God Service detection leverages God Class code smell frequency via DesigniteJava"* → should mention the Spoon multi-metric fallback
- Line 440: *"Chatty Service detection analyzes dependency edge call counts"* → should mention source-based scanning as a second approach

---

## 4. Chapter 3, Commented-out Section 3.10 — ESB Misuse Detection (lines 397–433)

**Status:** ⚠️ COMMENTED OUT but contains outdated formulas

If this section is ever uncommented:
- **Mediator ratio formula** (Equation in old line 417): Was `(inDeps + outDeps) / (2 × totalDeps)` → now `(inDeps + outDeps) / totalDeps` (the `2×` divisor made the threshold nearly unreachable)
- **Threshold** changed from `0.5` to `0.4`
- **Gateway exclusion** is now implemented (services whose names contain gateway-related keywords are skipped)
- Config key `esb-mediator-threshold` default changed from `0.5` to `0.4`

---

## 5. Chapter 4, Table 4.6 — Configuration Parameters (line 601)

**Status:** ❌ OUTDATED — Two rows need updating

| Current (wrong) | Correct | 
|---|---|
| `GOD_MIN_DOMAINS` / default `3` / *"God service God Class threshold"* | `GOD_MIN_GOD_CLASSES` / default `1` / *"God service minimum God Class count (DesigniteJava signal)"* |
| `CHATTY_MIN_CALLS` / default `5` / *"Chatty service call count threshold"* | `CHATTY_MIN_CALLS` / default `10` / *"Chatty service minimum call/method count threshold"* |

Also consider adding a new row:
| `ESB_MEDIATOR_THRESHOLD` | `0.4` | *"ESB misuse mediator ratio threshold"* |

---

## 6. Chapter 4, Section 4.3.4 — Anti-Pattern Detector Architecture, Table 4.5 (lines 343–373)

**Status:** ⚠️ CHECK if Wrong Cuts and ESB Misuse are still commented out

The commented-out rows for `WrongCutsDetector` and `EsbMisuseDetector` should be uncommented if those detectors are now active, or a note should be added explaining they exist but are disabled.

---

## 7. Chapter 3, Section 3.3 — Spoon Analysis (lines 23–30)

**Status:** ⚠️ NEEDS MINOR EXPANSION

**What the thesis currently says:**
- Spoon is used for three purposes: REST endpoint detection, inter-service communication detection, service metadata extraction

**What should be added:**
- Spoon is now also used for **intra-service structural analysis**: computing God Class metrics (field count, method count, TCC) and scanning for chatty HTTP client patterns (method counting, invocation analysis with declaring-type verification)
- This represents a 4th and 5th use of Spoon beyond what's currently documented

---

## 8. Chapter 3, Section 3.4 — Dependency Graph Construction (lines 75–93)

**Status:** ⚠️ NEEDS MINOR UPDATE

**What to add:**
- The `@FeignClient` detection now counts each individual abstract method in the interface as a separate call evidence entry (previously, one `@FeignClient` annotation = one dependency edge with `callCount = 1`). Default and static methods are skipped. This is important because it feeds accurate call counts to both the Chatty Service detector and the dependency graph visualization.

---

## Summary of Changes by Priority

| Priority | Section | Change Type |
|---|---|---|
| 🔴 HIGH | Ch3 §3.5 God Service | ✅ DONE — two-signal approach, 6 metrics, TCC |
| 🔴 HIGH | Ch3 §3.6 Chatty Service | ✅ DONE — add source-based approach |
| 🔴 HIGH | Ch4 Table 4.6 Config Params | ✅ DONE — `GOD_MIN_DOMAINS`→`GOD_MIN_GOD_CLASSES`, defaults, added ESB |
| 🟡 MEDIUM | Ch3 §3.3 Spoon Analysis | ✅ DONE — added 2 new Spoon use cases |
| 🟡 MEDIUM | Ch3 §3.4 Dependency Graph | ✅ DONE — added Feign per-method counting note |
| 🟡 MEDIUM | Ch3 Summary | ✅ DONE — updated 2 sentences |
| 🟢 LOW | Ch3 §3.10 ESB Misuse | ✅ DONE — fixed formula, threshold, added gateway exclusion |
| 🟢 LOW | Ch4 Table 4.5 Detectors | ✅ DONE — uncommented ESB/WrongCuts rows, count 8→10 |

