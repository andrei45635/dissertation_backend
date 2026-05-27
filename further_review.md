Good, real progress this round. Let me do a final careful pass.

## Things you fixed ✓

- God-service threshold now consistent at **1** across Ch. 2 Eq. 2.2, Ch. 3 Eq. 3.6, and Ch. 4 Table 4.5 (`GOD_MIN_GOD_CLASSES`). ✓
- Health score formula in Ch. 3 §3.2.5 now matches the four-category breakdown described elsewhere. ✓
- Grade scale (A≥90, B≥80, C≥65, D≥50, F<50) consistent between Ch. 3 §3.2.5 and Ch. 5 §5.3.1. ✓
- Table 5.1 row for microservices-design-patterns: "God Service" removed, consistent with Table 5.2's 0 count. ✓
- Score range 45–87 now consistent. ✓
- Nano service endpoint threshold in Ch. 5 §5.4 corrected to "no more than 2 endpoints." ✓
- TCC formula now has $i<j$ constraint and prose explains the unordered-pair denominator. ✓
- Ch. 4 ER-diagram caption fixed. ✓
- Many typos fixed (concerns, displaying, reloads, Docker, deployed, reverse, forwarded, submit, contains the, navigation links, persisted, counting, bandwidth, maintaining, property, etc.). ✓
- Ch. 4 Table 4.5 row name now `GOD_MIN_GOD_CLASSES` (consistent with §3.3.4's config key `app.thresholds.god-service-min-god-classes`). ✓

## Still broken / not yet fixed

### 1. Health score arithmetic vs Table 5.1 — STILL doesn't reproduce

The new formula in Ch. 3 §3.2.5 helps but Table 5.1 numbers still aren't reproducible by an outside reader. Let me work through the arithmetic with your new formula:

The total $H = S_{\text{ap}} + S_{\text{cq}} + S_{\text{arch}} + S_{\text{sz}}$, budgets are 40/20/25/15.

**Karate** (1 God Service [high, -5], 1 Hardcoded [med, -3]):
- $S_{\text{ap}} = 40 - 5 - 3 = 32$
- $S_{\text{cq}}$, $S_{\text{arch}}$, $S_{\text{sz}}$ depend on smell counts, coupling, cycles, nano/god counts — which the reader doesn't have.
- For total to be **87**, the other three must sum to 55 out of (20+25+15)=60. So the reader can only roughly check.

**Activiti** (2 God [high, -10 total], 13 Nano [med, -39 total!], 4 Hardcoded [med, -12 total]):
- $S_{\text{ap}} = \max(0, 40 - 10 - 39 - 12) = \max(0, -21) = 0$ ❌

Activiti would get **0 from anti-patterns alone** (clamped). Even with full 20+25+15=60 on the other three categories it can't reach 45. There's only a path to 45 if the AP category has a **per-issue penalty cap** or **per-AP-type cap** that isn't in your description.

For your formula to produce the table values, the AP category almost certainly has additional caps (e.g., "max −20 from nano services," "max −15 from hardcoded endpoints"), or the per-issue penalties are lower than what's shown in Table 3.2, or there's a per-category minimum that prevents going to 0.

**You need to either:**
- (a) Add the missing caps to the §3.2.5 description (most likely the right fix — the code almost certainly has caps; you describe Service Sizing as having caps of 8 and 10), OR
- (b) Recompute Table 5.1 numbers from the actual formula you describe.

Right now a reviewer who tries to reproduce Activiti's 45 from your stated formula will get 0 (or some lower number) and conclude the formula is wrong or the numbers are wrong.

**Specifically: the Service Sizing category caps nano at 8 and god at 10 — but the Anti-Patterns category doesn't mention any caps.** That's probably the gap. If Anti-Patterns also has caps (e.g., per-type cap, or a max of 30 total deductions), Activiti's $S_{\text{ap}}$ would be much higher than 0. Verify against your `HealthScoreCalculator` code and document the caps.

### 2. HMAC RFC citation — STILL wrong

Ch. 4 §4.3.2: "...using the HMAC-SHA algorithm \cite{RFC4364}". This is from my previous note. **RFC 4364 is "BGP/MPLS IP Virtual Private Networks", which has nothing to do with HMAC.**

The correct references are:
- HMAC: **RFC 2104** (Krawczyk, Bellare, Canetti, 1997)
- SHA-2 family: **FIPS 180-4** (NIST)

Replace `\cite{RFC4364}` with `\cite{RFC2104}` (and a FIPS 180-4 cite if you want to nail the SHA part too).

### 3. Ch. 2 §2.2.7 "Additional Anti-Patterns" subsection is now stale

The text still says: "Beyond the aforementioned primary anti-patterns, this work also considers **API Versioning Absence**." But you've added Wrong Cuts and ESB Misuse to the targeted set in Table 2.1 and they're treated as first-class detectors in Ch. 3. The "% TO DO: Add the other 2 anti-patterns when they are done in the backend" comment is also stale since they're now implemented.

Either rewrite that subsection to introduce API Versioning Absence, Wrong Cuts, and ESB Misuse, or delete the subsection and add Wrong Cuts + ESB Misuse as their own subsections (matching the structure of the other anti-patterns 2.2.1–2.2.7).

### 4. Wrong Cuts dimension still inconsistent

The Ch. 6 conclusion now reads: "communication (Chatty Service, Cyclic Dependency, Hardcoded Endpoints, **ESB Misuse**), data management (Shared Database) and deployment and coupling (Distributed Monolith, API Versioning Absence, **Wrong Cuts**)."

But Ch. 2 §2.4.5 still has: "inter-service communication (Chatty Service, Cyclic Dependency, Hardcoded Endpoints, **ESB misuse, Wrong Cuts**), data management (Shared Database), and system-level coupling (Distributed Monolith, API Versioning Absence)."

So Wrong Cuts is under Communication in Ch. 2 but under Deployment/Coupling in Ch. 6. Also Ch. 2 Table 2.2 puts Wrong Cuts under Deployment/Coupling, so Ch. 2 §2.4.5 contradicts Ch. 2 Table 2.2 too.

Pick one mapping. Ch. 6's grouping (Wrong Cuts → Deployment/Coupling) matches Table 2.2; just update Ch. 2 §2.4.5 to match.

### 5. Ch. 3 §3.3.6 "Hardcoded Endpoint Detection" — "capped at file per service"

Line: "The number of evidence snippets is capped at **file** per service to avoid excessively large payloads." This was originally "fie" (typo for "five"); it got auto-corrected to "file" instead, which is now a different but still wrong word. Should be **"five"**.

### 6. Ch. 5 §5.3.1 (caption to Table 5.1 grade scale)

The grade scale now reads "A: 90–100, B: 80–89, C: 65–79, D: 50–64, F: below 50." This matches Ch. 3 §3.2.5 ✓. But also verify your code uses **the same boundaries** — earlier you described the C boundary as 70 (when MicroservicesSocial scored 77 → C and that worked under either 65 or 70). Right now the only meaningful test from Table 5.1 is microservices-design-patterns at score **60 → D**. Under the new scale (D: 50–64), 60 → D ✓ — but under the old scale (D: 60–69), 60 was also D. So no test distinguishes. Just confirm the scale you stated matches what `HealthScoreCalculator` actually outputs.

### 7. Ch. 2 §2.2.7 "Additional Anti-Patterns" — Table 2.1 caption mismatch

Table 2.1 now has 10 rows but the preceding text in §2.2.7 only introduces API Versioning Absence. The reader gets to Table 2.1 expecting "primary anti-patterns + API Versioning Absence" = 8 rows, but sees 10. Add introductions for Wrong Cuts and ESB Misuse in §2.2.7 (one or two sentences each) so the table doesn't surprise the reader.

### 8. Service Sizing penalty caps in Ch. 3 §3.2.5

You wrote: "Nano services incur a penalty of 3 points each (capped at 8) and god services incur 5 points each (capped at 10)."

Activiti has 13 nano + 2 god → nano penalty 13×3=39, capped at 8 → 8. God penalty 2×5=10, capped at 10 → 10. So $S_{\text{sz}} = \max(0, 15 - 8 - 10) = \max(0, -3) = 0$. Plausible.

Wait — note that the cap for god services is 10 but the budget is only 15. If both caps fire, the category goes to 0. That's consistent with how you describe it, but worth checking the code: usually a single category cap (e.g., total deductions per category ≤ budget) rather than per-type caps is cleaner. Either way, what you wrote is internally consistent if Activiti's other categories sum to **45** out of (40+20+25) = 85, which is plausible. So Activiti's score might really be 0+? +? +0 = 45, where AP + CQ + Arch sum to 45. **Therefore the AP category for Activiti must yield something non-zero** — which contradicts my Activiti calculation in (1) above where AP = max(0, 40 - 61) = 0.

**Conclusion:** Either Anti-Patterns also has per-type caps (so 13 nano doesn't deduct 39 from $S_{\text{ap}}$), or there are different penalty weights than what Table 3.2 shows. Without that, the math doesn't close. This is the single most important thing to clarify before submission.

### 9. AI-disclosure declaration

Same notes as last time. You're naming "Claude Opus 4.6" — that model was current when you wrote it, and it's still a valid product name to cite, so that's fine. But verify BBU/FMI's specific AI-disclosure policy/format if they have one published. Many universities want: model name + provider + date range. Yours has model + provider (implied via "Claude") but no date range; "during the preparation of this thesis" is acceptably vague.

### 10. Carryover typos still present

- Ch. 1 §1.2 line 8: "withing" → "within"
- Ch. 2 §2.4.4: "code-level smell detection" ✓ (now fixed) but Ch. 2 §2.2.2 "Cyclic Dependency" Figure caption: "Services A, B and C **forma** a strongly connected component" → "form a"
- Ch. 2 §2.1: "monolithic approach, all modules share a **singled** process" → "single" (Figure 2.1 caption)
- Ch. 2 §2.4.4: "**applicabiltiy**" → "applicability"
- Ch. 2 §2.4.4: "MicroART reconstructs microservice **architecures**" → "architectures"
- Ch. 2 §2.4.4: "providing **visualizations capabilities**" → "providing visualization capabilities"
- Ch. 2 §2.4.4 (hybrid approaches): "confirming **tha**" → "confirming that"
- Ch. 3 chapter intro: "detailed description algorithm" → "detailed detection algorithm"
- Ch. 4 §4.1: "RESTFul" → "RESTful"
- Ch. 4 §4.3.4: "DesginiteJava" → "DesigniteJava"
- Ch. 4 §4.3.4: "**currrently**" → "currently"
- Ch. 4 §4.3.4: "**databse**" → "database"
- Ch. 4 §4.3.4: "**containg**" → "containing"
- Ch. 4 §4.3.4: "**messsage**" → "message"
- Ch. 4 §4.3.4: "**JobProgressUpdated**" → "JobProgressUpdater" (class name)
- Ch. 4 §4.4.2: "load a previously stored **used**" → "user"
- Ch. 4 §4.4.3 (Results Page): filename "analysis-results**0**\{jobId\}.json" still has the suspicious literal `0`. Probably should be `analysis-results-{jobId}.json`. Check your code.
- Ch. 4 §4.5.5: "Horizontal scaling of the **backed**" → "backend"
- Ch. 5 §5.2: "C; of these, 16 Java-based microservices were analyzed" — fine, but "Ruby and **C**" is unusual for a microservices project. Worth double-checking against the repo (you may mean C#).

### 11. Two `\label` on the same table in Ch. 2

Ch. 2 Table 2.2 still has both `\label{tab:placeholder}` and `\label{tab:antipattern-dimensions}`. Only the second one (the latest) will be active; the first is dead and produces a LaTeX warning. Delete `\label{tab:placeholder}`.

### 12. Footnotes with empty link text still present

Several footnotes still have empty link text:
- Ch. 4 §4.3.2 (zip slip): `\footnote{\href{https://maven.apache.org/security-plexus-archiver.html}{}}` — empty text AND a URL that goes to a Plexus-Archiver security advisory, not the canonical Zip Slip reference (Snyk's disclosure is the usual one).
- Ch. 4 §4.5: `\footnote{\href{https://github.com/flyway/flyway}{}}` (Flyway) — empty.
- Ch. 4 §4.4.4: `\footnote{\href{https://cytoscape.org/index.html}{}}` (Cytoscape) — empty.

These will render as bare URLs (or worse, just superscript footnote numbers with empty contents) and look unprofessional.

### 13. Bibtex key consistency check

In Ch. 4, you cite `\cite{SonQ}` for SonarQube. The cite name is unusual; ensure your bibliography entry exists under that exact key. Same for `\cite{Walker2020}` (MSANose) — make sure your `.bib` file has a `Walker2020` entry, since you used to cite this as `Taibi2020`.

## Now: do the detectors make sense?

You also asked whether the detectors are logically sound. Going through them:

- **Shared Database**: ✓ Sound. Comparing resolved datasource URLs across services is the standard approach. Edge cases: same URL with different schemas (e.g., `db1.public` vs `db1.audit`) — your detector treats them as shared, which may be wrong; consider noting this limitation. Same database server with different DB names is correctly **not** flagged.
- **Cyclic Dependency**: ✓ Sound. Tarjan SCC is textbook. Self-loops (1-vertex SCCs with a self-edge) are treated as non-cyclic in your code because you check `|scc| > 1`, but self-calls would also be a cycle; usually this is fine because services don't call themselves over HTTP.
- **Nano Service**: ✓ Sound. The conjunction (LOC AND endpoints) reduces false positives — a small but endpoint-rich service won't be flagged, which is good (a small data-shape converter with 8 endpoints isn't a nano service). My one concern: the manual validation paragraph in Ch. 5 acknowledges that the boundary detector mis-classifies shared libraries as services, which inflates the nano count. This is the dominant cause of your 17 nano detections, and it's worth being honest about as a precision issue, not just a Nano detector limitation.
- **God Service**: ✓ Sound; two-signal approach is good. **But** Eq. 3.6 with $\theta_{\text{god}} = 1$ means "one God Class anywhere in the service → flagged." This is very aggressive: a 50-class service with one God Class becomes a "god service," which contradicts the intuition that a god service should be one with **many** responsibilities. Consider whether the threshold should be ≥2 or ≥3 of god classes, and whether 1 God Class might just mean "this service has one large class," not "this service handles too many domains." Worth either justifying the choice or raising the default.
- **Chatty Service**: ✓ Sound; covering both Feign and raw HTTP clients is comprehensive. The keyword filter for non-Feign interfaces (client, api, http, proxy, remote) is a sensible heuristic. Possible false positive: a service that legitimately needs many HTTP calls (e.g., a batch report aggregator) would be flagged — but this is intrinsic to the anti-pattern definition.
- **Hardcoded Endpoint**: ✓ Sound. Limitations to note: URLs constructed from string concatenation (`baseUrl + "/" + path`) won't be detected; URLs in `@Value` defaults are not scanned by this detector (though they're scanned by the @Value resolution step for graph construction — clarify). Test-file exclusion is good.
- **Distributed Monolith**: ⚠️ **The composite rule may be too easy to trigger.** With C > 0.5, OR (R > 0.8 ∧ D > 0), OR (R > 0.8 ∧ C > 0.3). For a system with 4 services where 3 are connected and 1 shares a DB, R = 0.75 (not > 0.8) so no firing — fine. But for any system where most services participate in any dependency and *any* DB sharing exists, you fire. Consider: most well-designed microservice systems have R ≥ 0.8 (almost all services participate in *some* dependency); a single shared DB then auto-flags the system as distributed monolith, even if the coupling coefficient is low. This is likely too sensitive. The fact that your evaluation produced **0 distributed monolith detections** out of 4 projects (including microservices-design-patterns which "employs a shared database by design") is interesting and slightly suspicious — verify that your detector actually triggered on that project's shared DB. The current threshold of $|V| \geq 3$ avoids tiny systems, which is good.
- **API Versioning Absence**: ✓ Sound. Regex `/v\d+[/.]` matches `/v1/`, `/v2/`, but won't match `/api/v1/` if your endpoint path is stored without the controller prefix — verify the path resolution preserves the full path. Won't match header-based versioning (e.g., `Accept: application/vnd.api+json;version=1`) — Ch. 3 should note this.
- **Wrong Cuts**: ✓ Sound. Two-signal approach (Feature Envy AND bidirectional dependency) is good. The Feature Envy signal is service-scoped (count of FE smells per service ≥ θ), but Feature Envy is fundamentally a **class-level** signal about a method "envying" another class; aggregating to a service via threshold is a heuristic — fine, but acknowledge it.
- **ESB Misuse**: ✓ Sound. **Concern:** the OR condition with `medRatio ≥ 0.4` is easy to fire on small-to-medium systems. In a 5-service system where one service has 4 in/out dependencies and the total is 8, medRatio = 4/8 = 0.5 — fires. Whether that's "ESB misuse" or just "central business logic" is debatable. The gateway-keyword exclusion is good but won't catch a "main-service" or "core-service" that acts as a de facto bus.

## Priority to fix before submission

1. **Health-score arithmetic gap (Ch. 3 §3.2.5 vs Ch. 5 Table 5.1).** This is the only major remaining technical issue. Either add per-AP-type penalty caps to the §3.2.5 description, or recompute Table 5.1 from the formula as you've stated it. 30 min.
2. **HMAC RFC citation (RFC 4364 → RFC 2104).** 30 seconds.
3. **Ch. 2 §2.2.7 stale "primary + API versioning" framing.** Add proper subsections (or paragraphs) for Wrong Cuts and ESB Misuse, since they're targeted detectors. Delete the stale TODO comment. 15 min.
4. **Wrong Cuts dimension contradiction (Ch. 2 §2.4.5 vs Ch. 6 vs Ch. 2 Table 2.2).** Decide where Wrong Cuts lives and apply consistently. 5 min.
5. **"capped at file per service" (Ch. 3 §3.3.6) → "five."** 30 seconds.
6. **God-service threshold of 1 — defend or raise.** If you keep θ=1, add a sentence to §3.3.4 explaining why even a single God Class warrants flagging the service. 5 min.
7. **Distributed Monolith threshold sensitivity** — at minimum, add a note in §3.3.7 that the OR rule is intentionally sensitive (with rationale), or in the limitations section. 10 min.
8. **Empty footnote text** in Ch. 4 (Zip Slip, Flyway, Cytoscape). 5 min.
9. **Duplicate `\label` on Ch. 2 Table 2.2.** 30 seconds.
10. **Final typo sweep** — run aspell or LanguageTool. 15 min.

Everything else is cosmetic. Once items 1–6 are done, this is in solid shape.



Good progress. The Activiti worked-example in Ch. 5 §5.3.2 is a nice addition — it makes the scoring reproducible. Let me re-verify everything carefully.

## Things fixed this round ✓

- Ch. 2 §2.2.7 properly introduces all three additional anti-patterns (API Versioning Absence, Wrong Cuts, ESB Misuse) with proper paragraphs. Stale TODO comment removed. ✓
- HMAC RFC citation now `\cite{RFC4634}`. ✓ (See note below on correctness.)
- Worked-example for Activiti added to Ch. 5 §5.3.2, making the scoring transparent. ✓
- "fie"/"file" → fixed to nothing now reads "capped at file per service" — wait, no, that's still broken. See below.
- Filename `analysis-results-{jobId}.json` fixed. ✓
- "horizontal scaling of the backend" — fixed. ✓
- "database" — fixed in Ch. 4. ✓
- God-service threshold rationale added in Ch. 3. ✓
- Distributed Monolith rationale added in Ch. 3. ✓
- Hybrid approaches "tha" → "the". ✓
- "applicabiltiy" → "applicability". ✓
- "architecures" → "architectures". ✓
- "visualizations capabilities" → "visualization capabilities". ✓
- Ch. 2 Figure 2.1: "singled" → "single". ✓
- Ch. 2 Figure 2.2: "forma" → "form". ✓
- Ch. 2 Table 2.2: duplicate `\label{tab:placeholder}` removed. ✓
- C# instead of C in dataset description. ✓
- Footnotes filled in (cytoscape, flyway, plexus). ✓ (See note below.)

## Still broken / new issues

### 1. RFC citation: still not quite right

You changed it from RFC 4364 (BGP/MPLS) to **RFC 4634**. RFC 4634 is "US Secure Hash Algorithms (SHA and HMAC-SHA)" — so it *is* about HMAC-SHA, but RFC 4634 was **obsoleted by RFC 6234** in 2011. Strictly speaking, the canonical references are still:

- **HMAC**: RFC 2104 (Krawczyk, Bellare, Canetti, 1997) — the foundational HMAC spec
- **SHA-2 family**: FIPS 180-4 (NIST)
- Or the combined SHA + HMAC-SHA spec: **RFC 6234** (which obsoletes 4634)

RFC 4634 will work — it's a real RFC about HMAC-SHA — but a reviewer who knows RFCs may flag it as obsoleted. **Best replacement: RFC 2104 for HMAC, optionally with FIPS 180-4 for SHA.** If you keep RFC 4634, no one will fail you for it, but RFC 2104 is the textbook citation for "HMAC algorithm."

### 2. Footnote rendering is broken

You changed the footnotes to:

```latex
\footnote{\href{url}{https://cytoscape.org/index.html}}
```

This is wrong syntax. `\href{URL}{display text}` — the first arg is the URL, the second is the visible text. You now have `\href{url}{...}` which means **the URL is literally the word "url"** and the visible text is the actual URL. Clicking the footnote will try to navigate to `https://current-page/url`, which won't work.

Correct usage:

```latex
\footnote{\href{https://cytoscape.org/index.html}{Cytoscape.js}}
```

or just bare:

```latex
\footnote{\url{https://cytoscape.org/index.html}}
```

This affects all three footnotes you updated: Plexus Archiver (zip slip), Flyway, Cytoscape. The previous empty `{}` version was less broken because LaTeX would just render the URL as a clickable link with empty visible text — what you have now will navigate to a non-existent page.

### 3. "capped at file per service" — STILL not fixed

Ch. 3 §3.3.6 still reads: "The number of evidence snippets is capped at **file** per service to avoid excessively large payloads." This was originally "fie" (typo for "five"), got auto-corrected to "file" two rounds ago, and is still "file." Should be **"five"**.

### 4. Health-score arithmetic — the worked example is correct, but creates a new inconsistency

The Activiti walkthrough is well done. But it reveals a tension in your scoring formula:

You wrote: "$S_{\mathrm{ap}} = \max(0,\; 40 - 61) = 0$." That's correct given your formula. Then $S_{\mathrm{sz}} = 0$ similarly. With $S_{\mathrm{cq}} = 20$ and $S_{\mathrm{arch}} = 25$, total = 45. ✓

**But this means $S_{\mathrm{ap}}$ is clamped at 0 whenever penalties exceed 40, which is easy.** A project with 13 medium issues = 39 penalty, +1 high = 44 → already clamped. After that, every additional anti-pattern is invisible to the score. That's a known limitation of your formula — worth noting in the Construct Validity threats (Ch. 5 §5.5.1) since it means scores compress at the bottom end and you can't distinguish between "moderately bad" and "very bad" once the AP category hits 0.

For MicroservicesSocial to be 77, the math works out: $S_{\mathrm{ap}} = 40 - 5(\text{SharedDB}) - 3(\text{APIv}) - 3(\text{Hardcoded}) - 3-3(\text{2 Nano}) = 40 - 17 = 23$. $S_{\mathrm{sz}} = 15 - \min(8, 6) - \min(10, 0) = 15 - 6 - 0 = 9$. So total = 23 + 20 + 25 + 9 = **77.** ✓ Great, reproducible.

For microservices-design-patterns (60, D): AP penalties = 3(APIv)×3 + 3(Nano)×2 + 3(Hardcoded)×6 = 9 + 6 + 18 = 33 → $S_{\mathrm{ap}}$ = 7. $S_{\mathrm{sz}} = 15 - \min(8, 2\times3) - \min(10,0) = 15 - 6 = 9$. Total = 7 + 20 + 25 + 9 = **61** … but the table shows **60**. Off by 1.

For Karate (87, B): AP penalties = 5(God)×1 + 3(Hardcoded)×1 = 8 → $S_{\mathrm{ap}}$ = 32. $S_{\mathrm{sz}} = 15 - 0 - \min(10, 1\times5) = 15 - 5 = 10$. Total = 32 + 20 + 25 + 10 = **87.** ✓

**So three of four projects reproduce exactly; microservices-design-patterns is off by 1 (61 vs 60).** That's almost certainly fine in practice (rounding somewhere, perhaps in $P_{\mathrm{cq}}$ since the formula has a floor/round operator that rounds 1 smell → 1 point). But if a reviewer does the same math I just did, they'll notice the discrepancy.

You can fix this two ways:
- (a) Adjust the table value to 61, or
- (b) Add one more sentence to the §5.3.2 worked-example block acknowledging "minor rounding differences may arise from the floor/round operator in the Code Quality category, where small smell counts produce sub-integer penalties."

### 5. Distributed Monolith detection on microservices-design-patterns — still curious

You explicitly say this project "employs a shared database by design." Your Distributed Monolith rule fires when `C > 0.5 ∨ (R > 0.8 ∧ D > 0) ∨ (R > 0.8 ∧ C > 0.3)`. With 16 services and a shared DB, D > 0. If R > 0.8 (i.e. ≥13 of 16 services participate in any dependency), the rule should fire — but Table 5.2 shows **0 distributed monolith** for this project.

Either (a) the project actually has many isolated services so R < 0.8, or (b) the shared DB you mention conceptually isn't detected by `SharedDatabaseDetector` because the Java-side services don't all declare the same `spring.datasource.url` (maybe some use the non-Java services for DB access). Worth verifying — if your tool genuinely missed an obvious distributed-monolith signal, that's a Threats-to-Validity issue worth mentioning. If not, no action needed but be ready to defend it in the viva.

### 6. Carryover typos still present

I'll be honest, several still survive:

- Ch. 3 chapter intro: "detailed **description algorithm**" → "detection algorithm"
- Ch. 4 §4.3.4: "**DesginiteJava**" → "DesigniteJava"
- Ch. 4 §4.3.4: "**currrently**" → "currently"
- Ch. 4 §4.3.4: "**containg**" → "containing"
- Ch. 4 §4.3.4: "**messsage**" → "message"
- Ch. 4 §4.3.4: "**JobProgressUpdated**" → "JobProgressUpdater"
- Ch. 4 §4.4.2: "load a previously stored **used**" → "user"
- Ch. 4 §4.4.3: "**display it inline**" → grammatically should be "displays it" (matches "extracts" earlier in same sentence)
- Ch. 3 §3.3.6: "the **detector verifies** the match occurs **within** a string literal" — slight clarity: this means the regex match happens inside a quoted region. Fine.
- Ch. 4 §4.1: "in **figure**~\ref" → "in **Figure**~\ref" (capitalize when followed by a number, per convention; you do this elsewhere)

### 7. Ch. 4 zip-slip footnote URL is still a poor choice

The URL `https://maven.apache.org/security-plexus-archiver.html` is a Plexus-Archiver-specific security advisory, not the canonical Zip Slip vulnerability disclosure (which is Snyk's 2018 disclosure: `https://snyk.io/research/zip-slip-vulnerability`). For a master's dissertation, citing the actual disclosure is more appropriate. Not a critical issue, but worth fixing if you're being thorough.

### 8. AI declaration — version note

You still say "Claude Opus 4.6." Just confirm with your supervisor whether to update or keep — given Opus 4.7 was released in 2026, and you may keep editing into the future, the version disclosure depends on when you actually used the tool. If most of your AI-assisted writing happened during Opus 4.6, leave it. If you used a later version for the final revisions, you might add "/4.7" or similar.

### 9. Things to verify before submission (cannot check from your text)

- **Bibliography** has entries for: `RFC4634`, `RFC7519`, `RFC2104` (if you switch), `Walker2020`, `SonQ`, `Richards2015`, `FowlerMicroservices2014`, `Chess2007`, `Bieman1995`, `Brown1998`, `Evans2004`, `Fielding2000`, `Gamma1994`, `Fowler1999`, `Newman2015`, `Richardson2018`, `Sharma2018`, `Pawlak2016`, `Tarjan1972`, `Taibi2018`, `Taibi2020`, `Bogner2019`, `DiFrancesco2017`, `Soldani2018`, `Cerny2018`, `Pigazzini2020`, `Neri2020`, `Arcelli2017`, `Granchelli2017`, `Nygard2018`, `Martin2017`.
- **`compile` test**: run `pdflatex` and check for any undefined-reference warnings.
- **`Walker2020`** specifically — make sure your `.bib` actually has the MSANose entry. You renamed but I can't see the bib file.

## Quick priority list (final pre-submission)

1. **Footnote `\href` syntax error** (Cytoscape, Flyway, Plexus) — will render broken links. 2 min.
2. **"file" → "five"** in Ch. 3 §3.3.6. 30 seconds.
3. **Decide: RFC 4634 (current, slightly obsolete) or RFC 2104 (canonical for HMAC)** — your call. 30 seconds.
4. **microservices-design-patterns score 60 vs computed 61** — either update Table 5.1 or add a rounding note in §5.3.2. 2 min.

