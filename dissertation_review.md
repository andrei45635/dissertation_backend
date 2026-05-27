## Still broken

### 1. God service threshold — STILL contradictory across three chapters

This is the same problem as before, not fixed. The same threshold has three different defaults:

- Ch. 2 Eq. 2.2 (Shared Database subsection's neighbor — God Service): default **3**
- Ch. 3 §3.3.4 line for Eq. 3.6: $\theta_{\text{god}}$ defaults to **1**
- Ch. 4 Table 4.5: `GOD_MIN_DOMAINS` default **3**

Pick whichever matches your actual code and propagate. If the real default is 1, change Ch. 2 and Ch. 4. If it's 3, change Ch. 3.

### 2. Health score arithmetic in Ch. 5 still doesn't match the formula

This is the most important remaining issue and a reviewer will catch it immediately. Applying Eq. 3.3 ($H = \max(0, 100 - 15n_{\text{crit}} - 10n_{\text{high}} - 5n_{\text{med}} - 2n_{\text{low}})$) to Table 5.2 counts, using the severities from Table 3.2:

| Project | Crit | High | Med | Computed H | Table 5.1 H |
|---|---|---|---|---|---|
| MicroservicesSocial | 0 | 1 (SharedDB) | 4 (2 Nano, 1 Hardcoded, 1 APIv) | 100 − 10 − 20 = **70** | 77 ✗ |
| microservices-design-patterns | 0 | 0 (God=0 in table, but listed in row?) | 11 (2 Nano + 6 Hardcoded + 3 APIv) | 100 − 55 = **45** | 60 ✗ |
| Activiti | 0 | 2 (God) | 17 (13 Nano + 4 Hardcoded) | 100 − 20 − 85 = **0** | 45 ✗ |
| Karate | 0 | 1 (God) | 1 (Hardcoded) | 100 − 10 − 5 = **85** | 87 ✗ |

None of the four scores in Table 5.1 are reproducible from Table 5.2 + Eq. 3.3. The Karate one is close (85 vs 87) but the others diverge wildly, especially Activiti (0 vs 45). This means one of three things is true and you need to decide which:

- **(a)** The real `HealthScoreCalculator` does NOT use Eq. 3.3 — it uses the four-category weighted decomposition you mention in §3.2.5 ("a category-based breakdown") and in Ch. 4 §4.3.5 and Ch. 6. In that case **Eq. 3.3 is misleading and should be replaced** with the actual formula your code implements.
- **(b)** Eq. 3.3 is correct, but Table 5.1 numbers were taken from a different/older configuration.
- **(c)** Some deduction caps exist (e.g., "max 40 points deducted per category") that the formula doesn't show.

Whatever the truth is, the Ch. 3 formula and Ch. 5 numbers must agree, or your evaluation isn't reproducible.

### 3. Internal contradictions in Ch. 5 Table 5.1 itself

The "Anti-Patterns" column for **microservices-design-patterns** lists "God Service", but Table 5.2 shows **0 God Service** detections for that project. One of them is wrong. Either the project did detect God Service (then Table 5.2 needs the count fixed) or it didn't (then remove "God Service" from the Table 5.1 row).

### 4. Manual Validation text contradicts the Nano Service threshold

Ch. 5 §5.4 line ~153 still says "all 17 instances satisfied the configured thresholds (fewer than 500 lines of code and **fewer than 5 endpoints**)." But your configured threshold (Ch. 2 Eq. 2.1, Ch. 3 Eq. 3.5, Ch. 4 Table 4.5) is **≤ 2 endpoints**. This was a previously flagged issue not yet fixed.

### 5. Angular version — STILL contradictory

Ch. 4 Table 4.1 (line 62) now says "Angular 19", but the prose in §4.4.1 also says "Angular 19" ✓ — those agree. But the **abstract** (your new addition) doesn't mention Angular version, so that's fine. Wait — let me recheck the table. Yes, table says Angular 19 now. ✓ This is actually fixed. Good.

But verify: does your actual `package.json` say Angular 19? Angular 19 was released Nov 2024; Angular 20 came May 2025; Angular 21 came Nov 2025. If you're building this in 2025-2026 with `ng new` you'd get 20 or 21 by default. Just confirm the version against your real codebase before defending.

### 6. Ch. 4 §4.1.1 "DesigniteJava" subsubsection is still oddly placed

Same issue as before: only DesigniteJava gets a description paragraph as a "subsubsection" before Table 4.1, while every other tech-stack item only appears in the table. Inconsistent treatment. Either give every backend dependency a paragraph, or fold the DesigniteJava paragraph into a single "selected tools" paragraph after the table, or drop the subsubsection heading entirely and let it be a paragraph.

### 7. Ch. 2 §2.4.2 still ends mid-sentence

Line 260: "...as summarized in Table " — missing `\ref{tab:antipattern-dimensions}` after the word "Table". Unchanged from before.

### 8. Ch. 4 Figure 4.2 (ER diagram) still has the wrong caption

The ER diagram caption duplicates the architecture-overview caption. It should say something like "Entity-relationship diagram of the data model showing the nine JPA entities and their associations." Unchanged from before.

### 9. TCC formula still has the unordered/ordered pair mismatch

Ch. 3 Eq. 3.7 numerator iterates $(m_i, m_j)$ with no $i<j$ constraint (so counts ordered pairs), denominator is $\binom{|M|}{2}$ (unordered). Either constrain the numerator with $i < j$ or change the denominator to $|M|(|M|-1)$. Otherwise the value can exceed 1 and the threshold "TCC < 0.5" is mathematically suspect.

## New issues introduced

### A. Abstract — health score range vs Table 5.1

Abstract says: "Health scores ranged from **45 to 87**". This is internally consistent with your updated Ch. 5, ✓. But once you fix the health score arithmetic above, this number will change too. Flagging so you remember to keep them in sync.

### B. Abstract typos

- "It **suppors** the detection" → "supports"
- "An automated microservice boundary **detefction** mechanism" → "detection"
- "**broared** anti-pattern coverage" → "broader"
- "**differentiaties**" → "differentiates"
- "open-source **microservices** projects" → "microservice" (singular adjective)
- "a programmatic integration" → drop the "a" ("interactive use ... and programmatic integration")

### C. Abstract — model self-reference

You wrote "the author used **Claude Opus 4.6**". The current model is Claude Opus 4.7, so 4.6 may be outdated by submission. More importantly: do you know your university's exact AI-disclosure policy? Some require naming the model + provider + date range of use; some require a more general statement; some prohibit AI-assisted *language editing* altogether. Worth checking BBU/FMI's specific requirements before finalizing — what you have is a reasonable template but the exact wording may matter.

### D. Ch. 6 §6.1 dimension list still wrong

You wrote: "ten anti-patterns organized across four architectural dimensions: service design (Nano Service, God Service), communication (Chatty Service, Cyclic Dependency, Hardcoded Endpoints, **ESB misuse, Wrong Cuts**), data management (Shared Database) and deployment and coupling (Distributed Monolith, API Versioning Absence)."

That's 2+5+1+2 = 10 ✓ but **this contradicts Ch. 2 Table 2.2 (the taxonomy mapping table)**, where Wrong Cuts is placed under "Deployment & Coupling" not "Communication". Pick one mapping and use it consistently in Ch. 1, Ch. 2, Ch. 6.

Same issue in Ch. 2 §2.4.5 line: "inter-service communication (Chatty Service, Cyclic Dependency, Hardcoded Endpoints, ESB misuse, Wrong Cuts), data management (Shared Database), and system-level coupling (Distributed Monolith, API Versioning Absence)" — Wrong Cuts under Communication here too, contradicting Table 2.2 in the same chapter.

Decide: is Wrong Cuts a Communication anti-pattern or a Deployment/Coupling one? Taibi 2020's taxonomy places it under "design/boundaries" — but pick whichever fits your story and use it everywhere.

### E. Carryover typos still present

Many of the typos I flagged last time are still there. Most notable ones a reviewer will see at a glance:

- Ch. 1 line 6: "practictions" → "practitioners"
- Ch. 1 line 18: "by integration DesigniteJava" → "by integrating"; "develope" → "develop"; "intepretable" → "interpretable"
- Ch. 2 line 254: "acorss" → "across"
- Ch. 2 §2.4.2 line ~260: "misue" → "misuse"
- Ch. 2 §2.6: "ussed" → "used"; "code-levle" → "code-level" (in §2.4.4)
- Ch. 3 line 4: "description algorithm" → "detection algorithm" (looks like a word swap)
- Ch. 3 various: "maintaing", "propery", "countine", "currrently", "fie" → "five"
- Ch. 4 line 27: "persistes" → "persisted"
- Ch. 4 line 142: "concers" → "concerns"
- Ch. 4 §4.3.2 line: "bandwith" → "bandwidth"
- Ch. 4 §4.3.4: "DesginiteJava" (in two places) → "DesigniteJava"
- Ch. 4 §4.3.4: "JobProgressUpdated" → "JobProgressUpdater" (the class name)
- Ch. 4 §4.4.1: "navigation linkes" → "links"; "used" instead of "user" in "load a previously stored used"
- Ch. 4 §4.4.3: "diplaying" → "displaying"; "reloades" → "reloads"; "containsthe" → "contains the"
- Ch. 4 §4.4.3: filename `analysis-results0\{jobId\}.json` — the literal `0` before `\{jobId\}` is still suspicious
- Ch. 4 §4.5: "DOcker" (twice in line 533) → "Docker"
- Ch. 4 §4.5.5: "sumbit", "dpeloyed", "revese", "forwared"
- Ch. 5 §5.3.3.1: "thwo" → "two"; "MicroserviceSocial" should be "MicroservicesSocial" (consistent with Table 5.1)
- Ch. 6: "bases on" → "based on"; "detecting pipeline" → "detection pipeline"; "moduels" → "modules"

### F. Ch. 5 §5.5.4 references non-existent section

Line ~256: "as discussed in Section~\ref{subsec:threshold-sensitivity}". The threshold sensitivity subsection is **commented out** (line 192-195 area). This will produce a broken `??` reference when compiled. Either uncomment and write that subsection, or remove this reference.

### G. SCC algorithm description, Ch. 3 line ~193

"The outer loop (lines 4--8) ensures all vertices are visited even in disconnected graphs." The algorithm shows the outer loop at lines 4-8 of the listing — verify the line numbers actually match (this is the kind of thing that drifts when algorithms are edited).

### H. Ch. 4 line 220 — citation `\cite{RFC4364}` for HMAC-SHA

RFC 4364 is "BGP/MPLS IP Virtual Private Networks". HMAC-SHA is RFC 2104 (HMAC) + FIPS 180 (SHA). The right citations would be `\cite{RFC2104}` (HMAC) and FIPS 180-4 for SHA. RFC 4364 is wrong. Please replace.

## Quick prioritization

If you only have an hour, fix in this order:

1. **God service threshold** (Ch. 2 vs Ch. 3 vs Ch. 4) — 5 min, prevents an embarrassing inconsistency.
2. **Wrong RFC citation for HMAC** (Ch. 4 §4.3.2) — 2 min, factually wrong.
3. **Wrong Cuts dimension** (Ch. 1, Ch. 2 §2.4.5, Ch. 6 vs Ch. 2 Table 2.2) — 5 min.
4. **Ch. 5 Table 5.1 row inconsistency** (microservices-design-patterns: God Service listed but count is 0) — 2 min.
5. **Broken reference to commented-out section** (Ch. 5 §5.5.4) — 2 min.
6. **Health-score arithmetic mismatch** (Ch. 5 Table 5.1 vs Eq. 3.3) — biggest item, will take longer because you may need to clarify what formula your code actually uses.
7. **Nano-service endpoint threshold typo in Ch. 5** (5 → 2) — 1 min.
8. **TCC formula numerator/denominator** — 5 min math fix.
9. **Ch. 2 mid-sentence break, Ch. 4 ER caption duplication** — 2 min each.
10. **Typo sweep** — run aspell or LanguageTool.
11. **Abstract polish** — 10 min.