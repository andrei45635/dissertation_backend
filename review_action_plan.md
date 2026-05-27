# Dissertation Review — Action Plan

Derived from `dissertation_review.md`. Grouped by priority.

---

## 🔴 Critical (fix first)

### 1. God Service threshold inconsistency
- **Ch. 2** (Eq. 2.2 area): default **3**
- **Ch. 3** (Eq. 3.6, ~line 263): default **1**
- **Ch. 4** (Table 4.5, ~line 608): `GOD_MIN_DOMAINS` default **3**
- **Fix:** Check your actual code (`GOD_MIN_DOMAINS`). If it's 3 → change Ch. 3 from 1 to 3. If it's 1 → change Ch. 2 and Ch. 4.

### 2. Health score formula vs results mismatch
- Eq. 3.3 (`H = max(0, 100 − 15·crit − 10·high − 5·med − 2·low)`) does **not** reproduce Table 5.1 scores for any of the 4 projects.
- **Fix:** Either (a) replace Eq. 3.3 with the actual category-weighted formula your code uses, or (b) re-run analysis and update Table 5.1 numbers. The abstract health score range (45–87) must also be updated if scores change.

### 3. Wrong RFC for HMAC-SHA (Ch. 4, ~line 204)
- `\cite{RFC4364}` is BGP/MPLS, not HMAC.
- **Fix:** Replace with `\cite{RFC2104}` (HMAC) and/or FIPS 180-4 (SHA).

### 4. TCC formula mismatch (Ch. 3, ~line 299)
- Numerator counts ordered pairs, denominator uses `\binom{|M|}{2}` (unordered).
- **Fix:** Add `i < j` constraint to numerator, or change denominator to `|M|(|M|-1)`.

---

## 🟡 Important (contradictions / broken refs)

### 5. Wrong Cuts dimension placement
- Ch. 2 §2.4.5 and Ch. 6 §6.1: placed under **Communication**
- Ch. 2 Table 2.2: placed under **Deployment & Coupling**
- **Fix:** Pick one and propagate to Ch. 1, Ch. 2 (prose + table), Ch. 6.

### 6. Ch. 5 Table 5.1 vs 5.2: God Service for microservices-design-patterns
- Table 5.1 lists "God Service" as detected, Table 5.2 shows count **0**.
- **Fix:** Remove from Table 5.1 or add the count to Table 5.2.

### 7. Nano Service threshold in Ch. 5 §5.4 (~line 153)
- Says "fewer than **5** endpoints" but configured threshold is **≤ 2**.
- **Fix:** Change 5 → 2.

### 8. Broken `\ref{subsec:threshold-sensitivity}` (Ch. 5, ~line 257)
- References a commented-out subsection → renders as `??`.
- **Fix:** Either uncomment and write the subsection, or remove the reference.

### 9. Ch. 2 ~line 260: incomplete table ref
- "as summarized in Table " — missing `\ref{...}`.
- **Fix:** Add `\ref{tab:antipattern-dimensions}` or the correct label.

### 10. Ch. 4 ER diagram caption (~line 89)
- Caption duplicates the architecture overview caption.
- **Fix:** Change to e.g. "Entity-relationship diagram of the MSA Detector data model."

---

## 🟢 Minor (typos & polish)

### 11. Typo sweep
Key typos by chapter (not exhaustive):

| Chapter | Typo | Fix |
|---------|------|-----|
| Ch. 1 | practictions | practitioners |
| Ch. 1 | by integration | by integrating |
| Ch. 1 | develope | develop |
| Ch. 1 | intepretable | interpretable |
| Ch. 2 | acorss | across |
| Ch. 2 | misue | misuse |
| Ch. 2 | ussed | used |
| Ch. 2 | code-levle | code-level |
| Ch. 3 | description algorithm | detection algorithm |
| Ch. 3 | maintaing, propery, countine, currrently, fie | fix each |
| Ch. 4 | persistes | persisted |
| Ch. 4 | concers | concerns |
| Ch. 4 | bandwith | bandwidth |
| Ch. 4 | DesginiteJava (×2) | DesigniteJava |
| Ch. 4 | navigation linkes | links |
| Ch. 4 | diplaying, reloades, containsthe | fix each |
| Ch. 4 | DOcker (×2) | Docker |
| Ch. 4 | sumbit, dpeloyed, revese, forwared | fix each |
| Ch. 5 | thwo | two |
| Ch. 5 | MicroserviceSocial | MicroservicesSocial |
| Ch. 6 | bases on | based on |
| Ch. 6 | detecting pipeline | detection pipeline |
| Ch. 6 | moduels | modules |
| Abstract | suppors, detefction, broared, differentiaties | fix each |

### 12. Abstract polish
- Fix typos listed above.
- Update health score range after fixing issue #2.
- Verify AI disclaimer model version (Claude Opus 4.6 vs current).

### 13. Ch. 4 §4.1.1 DesigniteJava subsection
- Inconsistent treatment — only this tool gets a dedicated subsubsection before the tech table.
- **Fix:** Fold into a paragraph after the table, or drop the subsubsection heading.

### 14. Angular version
- Currently consistent (Angular 19) but verify against your actual `package.json`.

---

## Suggested fix order (1 hour budget)
1. God Service threshold (#1) — 5 min
2. Wrong RFC (#3) — 2 min
3. Wrong Cuts dimension (#5) — 5 min
4. Table 5.1/5.2 God Service inconsistency (#6) — 2 min
5. Broken ref (#8) — 2 min
6. Nano threshold text (#7) — 1 min
7. TCC formula (#4) — 5 min
8. Incomplete table ref (#9) + ER caption (#10) — 4 min
9. Health score formula (#2) — 20+ min (needs code check)
10. Typo sweep (#11) — remaining time

