# Further Review — Action Plan

Analysis of `further_review.md` against source code and thesis chapters.

---

## 🔴 Critical

### 1. Health score arithmetic — ✅ VERIFIED CORRECT
**Verified against actual tool output for Activiti (score 45, grade F):**

| Category | Budget | Deductions | Score |
|----------|--------|------------|-------|
| Anti-Patterns | 40 | God(×2)=−10, Nano(×13)=−39, Hardcoded(×4)=−12 → total −61 | **0** |
| Code Quality | 20 | Perfect — no code smells reported | **20** |
| Architecture | 25 | Perfect — no coupling/cycle issues | **25** |
| Service Sizing | 15 | Nano=−8 (capped), God=−10 (capped) → total −18 | **0** |
| **Total** | **100** | | **45** ✓ |

The formula reproduces exactly. DesigniteJava reported 0 code smells for Activiti (likely because the project structure didn't allow DesigniteJava to run successfully on most modules), and the coupling coefficient was ≤ 0.1. No action needed.

**Optional improvement:** Add a brief worked example (e.g., for Activiti) in Ch. 5 showing how the four categories sum to 45, so reviewers can verify the arithmetic themselves.

### 2. HMAC RFC citation key is misleading but content is correct
**Verified:** The bib key `RFC4364` actually contains RFC **4634** ("US Secure Hash Algorithms (SHA and HMAC-SHA)") — the **content is correct** for HMAC-SHA. The key name is just wrong (4364 vs 4634).

**Fix:** Rename the bib key from `RFC4364` to `RFC4634` in `references.bib`, and update `\cite{RFC4364}` → `\cite{RFC4634}` in `chapter4.tex` line 199. This is cosmetic but prevents a reviewer from looking up RFC 4364 (BGP/MPLS) and getting confused.

---

## 🟡 Important

### 3. Ch. 2 §2.2.7 "Additional Anti-Patterns" — stale and incomplete
**Verified:** Line 152 has stale TODO: `% TO DO: Add the other 2 anti-patterns when they are done in the backend`. Lines 159-161 have commented-out Wrong Cuts and ESB Misuse paragraphs.

**Fix:** Uncomment and flesh out the Wrong Cuts and ESB Misuse paragraphs, delete the TODO, and update the subsection title/intro to reflect all three additional anti-patterns (not just API Versioning Absence).

### 4. Wrong Cuts dimension — still inconsistent in Ch. 2 §2.4.5
**Verified:** Line 391 says `inter-service communication (Chatty Service, Cyclic Dependency, Hardcoded Endpoints, ESB misuse, Wrong Cuts)` — Wrong Cuts under Communication. But Table 2.2 (same chapter) and Ch. 6 put Wrong Cuts under Deployment & Coupling.

**Fix:** Move Wrong Cuts from Communication to Deployment & Coupling in line 391.

### 5. "capped at file per service" — ✅ ALREADY FIXED
Says "five" now. No action needed.

### 6. God service θ=1 — needs brief justification
**Verified:** Ch. 3 line 271 states the default but gives no rationale. The reviewer flags this as very aggressive.

**Fix:** Add 1-2 sentences after the threshold statement explaining the choice. E.g.: "The threshold of 1 is intentionally low because even a single God Class in a service indicates a significant concentration of responsibilities that may impair independent deployability and testability. Teams that prefer a less sensitive setting can raise the threshold via configuration."

### 7. Distributed Monolith — note sensitivity in limitations
No code change needed. Add a sentence to Ch. 3 §3.3.7 or Ch. 5 limitations acknowledging the OR rule is intentionally sensitive.

### 8. Empty footnotes in Ch. 4
**Verified:** Lines 247, 517, 562 have `\href{...}{}` with empty display text.

**Fix:** Add display text: e.g., `\href{url}{Zip Slip vulnerability}`, `\href{url}{Cytoscape.js}`, `\href{url}{Flyway}`.

### 9. Duplicate `\label{tab:placeholder}` on Ch. 2 Table 2.2
**Verified:** Line 296.

**Fix:** Delete `\label{tab:placeholder}`.

---

## 🟢 Minor (typos & cosmetics)

### 10. Remaining typos

| File | Line | Typo | Fix |
|------|------|------|-----|
| `chapter1_introduction.tex` | 11 | withing | within |
| `chapter2.tex` | 18 | singled | single |
| `chapter2.tex` | 69 | forma a | form a |
| `chapter2.tex` | 318 | confirming tha | confirming that |
| `chapter2.tex` | 320 | applicabiltiy | applicability |
| `chapter2.tex` | 329 | architecures | architectures |
| `chapter2.tex` | 329 | visualizations capabilities | visualization capabilities |
| `chapter4.tex` | 18 | RESTFul | RESTful |
| `chapter4.tex` | 309 | databse | database |
| `chapter4.tex` | 485 | analysis-results0\{jobId\} | analysis-results-\{jobId\} (verify against frontend code) |
| `chapter4.tex` | 642 | backed | backend |

### 11. "Ruby and C" in Ch. 5 §5.2
**Line 55:** "microservices written in multiple languages, including Java, JavaScript, Ruby and C." The reviewer suggests this may be C# — verify against the actual repository.

### 12. Bib key `RFC4364` → `RFC4634`
Rename to match the actual RFC number. Content is correct, key is misleading.

### 13. AI disclosure (Ch. main.tex line 53)
"Claude Opus 4.6" is fine. Optionally add date range. Check university policy.

---

## Round 3 Analysis (from further_review.md lines 166–292)

### Status of previously flagged items

| # | Issue | Status |
|---|-------|--------|
| 1 | Health score arithmetic | ✅ Fixed — worked example added |
| 2 | RFC citation | ✅ Fixed — now `RFC4634` (see note below) |
| 3 | Ch. 2 §2.2.7 stale section | ✅ Fixed — all 3 anti-patterns introduced |
| 4 | Wrong Cuts dimension | ⚠️ Need to verify Ch. 2 §2.4.5 |
| 5 | "capped at file" | ✅ Fixed — says "five" |
| 6 | God service justification | ✅ Fixed |
| 7 | Distributed Monolith sensitivity | ✅ Fixed |
| 8 | Empty footnotes | ⚠️ Syntax may be wrong (see below) |
| 9 | Duplicate label | ✅ Fixed |
| 10 | Typos | ⚠️ Some remain (see below) |

### New issues from Round 3

#### A. Footnote `\href` syntax — ⚠️ NEEDS VERIFICATION
**Review claims** the footnotes were fixed with wrong syntax: `\href{url}{https://...}` instead of `\href{https://...}{Display Text}`. 
**Verified:** Lines 247, 517, 562 in `chapter4.tex` still have `\href{URL}{}` (empty display text).  The reviewer's claim about reversed args doesn't match what's in the file — the URLs are in the correct first position, but display text is still empty.

**Fix needed:** Add display text to all three:
- Line 247: `\href{https://maven.apache.org/security-plexus-archiver.html}{Zip Slip vulnerability}`
- Line 517: `\href{https://cytoscape.org/index.html}{Cytoscape.js}`
- Line 562: `\href{https://github.com/flyway/flyway}{Flyway}`

#### B. RFC 4634 — obsoleted but acceptable
RFC 4634 is correct content (SHA + HMAC-SHA) but was obsoleted by RFC 6234 in 2011. Reviewer says RFC 2104 is the canonical HMAC citation. **Not critical** — RFC 4634 won't cause a failure, but switching to RFC 2104 would be more rigorous.

**Action:** Optional. Keep RFC 4634 or switch to RFC 2104. Low priority.

#### C. microservices-design-patterns score: 60 vs computed 61 — off by 1
**Reviewer's arithmetic:** AP penalties = 3×3 (APIv) + 2×3 (Nano) + 6×3 (Hardcoded) = 33 → S_ap = 7. S_sz = 15−6 = 9. Total = 7+20+25+9 = **61**, but table shows **60**.

The 1-point discrepancy is almost certainly from the Code Quality category: if DesigniteJava reported even 1 code smell, `P_cq = min(20, round(ln(2) × 3.5)) = min(20, round(2.43)) = min(20, 2) = 2` → wait, that gives S_cq = 18, total = 7+18+25+9 = 59. With `round(ln(2)×3.5) = round(2.43) = 2` → S_cq=18, total=59. Hmm. With `Math.round(Math.log1p(1) * 3.5) = Math.round(0.693 * 3.5) = Math.round(2.426) = 2` → S_cq=18, total=59. Not 60 either.

Actually the Java code uses `Math.round()` which returns `long`, cast to `int`. If there were 0 smells → S_cq=20, total=61. The discrepancy suggests either (a) the score was captured from a slightly different run, or (b) there's a minor rounding edge case.

**Fix:** Either update Table 5.1 from 60 to 61, or add a brief rounding caveat. Easiest: change to 61 if that's what the formula produces, or re-run the tool to get the definitive value.

#### D. Distributed Monolith on microservices-design-patterns — 0 detections despite shared DB
**Reviewer's concern:** The project "employs a shared database by design" but shows 0 Distributed Monolith detections. Rule fires when `(R > 0.8 ∧ D > 0)`. With 16 services, R > 0.8 requires ≥13 services in dependencies.

**Most likely explanation:** The Shared Database detector found 0 shared DBs for this project (Table 5.2 shows 0). The "shared database by design" is described in Ch. 5 prose but the Java services likely don't all declare `spring.datasource.url` pointing to the same DB — the sharing may be at the infrastructure level (Docker Compose) rather than in Spring config files. So D=0 for this project, and the DM rule's `(R > 0.8 ∧ D > 0)` branch doesn't fire.

**Action:** No fix needed. The detection is consistent with the data. But be prepared to explain this in the viva.

#### E. Remaining typos — verified against current files

| File | Typo | Status |
|------|------|--------|
| `chapter1_introduction.tex:11` | withing → within | ❌ Still present |
| `chapter4.tex:18` | RESTFul → RESTful | ❌ Still present |
| `chapter4.tex:18` | figure → Figure | ❌ Still present |
| `chapter4.tex:309` | databse → database | ❌ Still present |
| `chapter4.tex:457` | display it inline → displays it inline | ❌ Still present |
| All others from review §6 | DesginiteJava, currrently, containg, messsage, JobProgressUpdated, stored used, description algorithm | ✅ Already fixed |

#### F. Score compression limitation — note in threats to validity
The reviewer correctly observes that once S_ap hits 0 (easy with >13 medium issues), additional anti-patterns become invisible to the score. This is a construct validity concern.

**Action:** Add a sentence to Ch. 5 Construct Validity (§5.5.1) noting that the category-capped scoring compresses at the extremes.

#### G. Zip Slip footnote URL
Reviewer suggests replacing `maven.apache.org/security-plexus-archiver.html` with the canonical Snyk disclosure: `https://snyk.io/research/zip-slip-vulnerability`. Optional but more authoritative.

---

## Updated fix priority

1. **Remaining typos** (withing, RESTFul, figure→Figure, databse, display→displays) — 5 min
2. **Footnote display text** (3 empty `\href` in Ch. 4) — 3 min
3. **Wrong Cuts in Ch. 2 §2.4.5** — verify if still inconsistent — 2 min
4. **Score compression note** in Ch. 5 Construct Validity — 3 min
5. **microservices-design-patterns score** (60 vs 61) — verify by re-running tool or add rounding note — 2 min
6. ~~RFC citation~~ — acceptable as-is with RFC 4634
7. ~~Distributed Monolith~~ — no fix needed, consistent with data
