// MSA Detector Master's Dissertation Defense — 15-minute presentation
// Redesigned deck: typography-driven, single accent colour, strict layout grid.
//
// Layout system (16:9, 10" x 5.625"):
//   - left/right margin 0.6", content width 8.8"
//   - slide title at y 0.28, hairline rule under the header at y 0.92
//   - body content starts at y >= 1.05
//   - page number bottom-right at y 5.32; nothing else in the footer
//   - colour: navy + teal accent; red/amber/green reserved for severity & grades
//   - thin rules and whitespace instead of filled cards, banners, and icon chips
const pptxgen = require("pptxgenjs");

const C = {
    navy:     "0F2A44",   // headings, dark slides
    deepBlue: "1C4E80",   // category colour (Architecture) only
    teal:     "0D9488",   // single accent
    ice:      "DBEAFE",   // light text on dark slides
    bg:       "FFFFFF",
    text:     "1E293B",
    muted:    "64748B",
    faint:    "C2CCD6",   // oversized list numerals
    hairline: "D8DEE6",   // rules, bar tracks
    good:     "059669",
    warn:     "D97706",
    bad:      "DC2626",
};

const FONT = "Calibri";

(async () => {
    const pres = new pptxgen();
    pres.layout = "LAYOUT_16x9"; // 10" x 5.625"
    pres.author = "Iacob Andrei";
    pres.title  = "MSA Detector — Master's Dissertation Defense";

    const ML = 0.6;   // left margin
    const CW = 8.8;   // content width

    // ------------------------------------------------------------
    // Layout helpers
    // ------------------------------------------------------------
    function rule(slide, x, y, w, color, h = 0.03) {
        slide.addShape(pres.shapes.RECTANGLE, {
            x, y, w, h, fill: { color }, line: { color },
        });
    }

    function addHeader(slide, title) {
        slide.addText(title, {
            x: ML, y: 0.28, w: CW, h: 0.55,
            fontSize: 25, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });
        rule(slide, ML, 0.92, CW, C.hairline, 0.015);
    }

    function pageNum(slide, n, light = false) {
        slide.addText(`${n} / 12`, {
            x: 8.7, y: 5.32, w: 0.7, h: 0.25,
            fontSize: 9, fontFace: FONT, color: light ? C.ice : C.muted,
            align: "right", margin: 0,
        });
    }

    // ============================================================
    // SLIDE 1 — TITLE
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.navy };

        rule(s, ML, 1.28, 0.7, C.teal, 0.045);

        s.addText([
            { text: "Detecting Architectural", options: { breakLine: true } },
            { text: "Anti-Patterns in Microservices" },
        ], {
            x: ML, y: 1.5, w: 8.6, h: 1.55,
            fontSize: 32, fontFace: FONT, color: "FFFFFF", bold: true,
            paraSpaceAfter: 4, margin: 0,
        });

        s.addText("A multi-level static analysis tool for Java/Spring Boot systems", {
            x: ML, y: 3.1, w: 8.6, h: 0.4,
            fontSize: 15, fontFace: FONT, color: C.ice, italic: true, margin: 0,
        });

        s.addText([
            { text: "Iacob Andrei", options: { fontSize: 15, bold: true, color: "FFFFFF", breakLine: true } },
            { text: "Master's Dissertation Defense", options: { fontSize: 11.5, color: C.ice, breakLine: true } },
            { text: "Supervisor: Prof. Dr. Simona Motogna", options: { fontSize: 11.5, color: C.ice, breakLine: true } },
            { text: "Faculty of Mathematics and Computer Science, Babeş-Bolyai University", options: { fontSize: 11, color: C.ice, italic: true } },
        ], { x: ML, y: 4.15, w: 8.6, h: 1.15, fontFace: FONT, margin: 0, paraSpaceAfter: 2 });

        s.addNotes(
            "Open: 'Good morning. My name is Andrei Iacob, and today I'll be defending my dissertation: MSA Detector — a tool for detecting architectural anti-patterns in Java-based microservice systems. My supervisor is Professor Simona Motogna.'\n\n" +
            "You can add: 'The core idea is straightforward — as organisations adopt microservices, they often unknowingly introduce structural problems that erode the very benefits they were after. This tool helps catch those problems early, from a source code checkout, before they become production incidents.'\n\n" +
            "Keep this short — about 30 seconds. Don't introduce content yet; that's the next slide.\n\n" +
            "Breathe, slow down. The committee already knows your name from the schedule, so this is just orientation."
        );
    }

    // ============================================================
    // SLIDE 2 — MOTIVATION & PROBLEM
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addHeader(s, "Why this matters");

        s.addText("Microservices adoption has outpaced tooling", {
            x: ML, y: 1.08, w: CW, h: 0.4,
            fontSize: 15, fontFace: FONT, color: C.muted, italic: true, margin: 0,
        });

        const items = [
            {
                title: "Architectural debt accumulates silently",
                body: "Shared databases, cyclic dependencies, and ill-sized services erode the benefits microservices promise.",
            },
            {
                title: "Related tools vary in scope",
                body: "Prior tools such as Arcan, MicroART, MSANose, and MARS address important slices of the problem, but differ in scope and output.",
            },
            {
                title: "Findings without evidence don't help",
                body: "Warnings without source snippets, affected services, or remediation guidance get ignored.",
            },
        ];
        items.forEach((it, i) => {
            const y = 1.68 + i * 0.92;
            s.addText(String(i + 1), {
                x: ML, y: y - 0.06, w: 0.5, h: 0.55,
                fontSize: 26, fontFace: FONT, color: C.faint, bold: true, margin: 0,
            });
            s.addText(it.title, {
                x: 1.3, y: y, w: 8.1, h: 0.32,
                fontSize: 14, fontFace: FONT, color: C.navy, bold: true, margin: 0,
            });
            s.addText(it.body, {
                x: 1.3, y: y + 0.33, w: 8.1, h: 0.5,
                fontSize: 11.5, fontFace: FONT, color: C.text, margin: 0,
            });
        });

        rule(s, ML, 4.6, 0.5, C.teal, 0.04);
        s.addText("Goal: actionable, evidence-based detection that bridges code and architecture levels", {
            x: ML, y: 4.72, w: CW, h: 0.4,
            fontSize: 13, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });

        pageNum(s, 2);

        s.addNotes(
            "Spend ~90 seconds here. This frames why the tool exists.\n\n" +
            "Talking points:\n" +
            "• Microservices have become the default for new large systems. But tooling for architectural quality hasn't kept up.\n" +
            "• Teams ship anti-patterns — shared DBs, cycles, services that are either too small or too big — and they only find out when deployment friction or cascading failures appear in production.\n" +
            "• Existing academic tools address important parts of the problem: Arcan detects architectural smells, MicroART visualises recovered architectures, MSANose detects microservice smells, and MARS covers a broader anti-pattern catalogue. They differ in the anti-patterns they cover and in how much developer-facing evidence, scoring, and workflow support they provide.\n" +
            "• Even when something IS detected, the output is usually an abstract warning. Developers don't act on warnings without code evidence and remediation guidance.\n\n" +
            "You can mention a concrete example: 'For instance, a shared database between two services looks harmless at first — both teams just point at the same Postgres instance. But over time, schema changes in one service silently break the other, and you've lost independent deployability — arguably the single biggest reason to use microservices in the first place.'\n\n" +
            "Pivot to next slide: 'So the goal of this work was a tool that bridges both levels and outputs actionable findings.'"
        );
    }

    // ============================================================
    // SLIDE 3 — OBJECTIVES & CONTRIBUTIONS
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addHeader(s, "Objectives & contributions");

        const rows = [
            { title: "Multi-level analysis",
              text: "Combines intra-service code analysis (DesigniteJava smells + Spoon structural metrics) with inter-service dependency analysis (graph algorithms)" },
            { title: "Ten anti-patterns detected",
              text: "Across four dimensions: service design, communication, data management, deployment & coupling" },
            { title: "Automated boundary detection",
              text: "Three-signal deployability gate (framework entry point, Dockerfile, main method) with confidence levels" },
            { title: "Composite health score",
              text: "0–100 with letter grading; decomposes into four interpretable categories; tracks change over time" },
            { title: "Evidence-based reporting",
              text: "Every finding includes source snippets, affected services, and remediation guidance" },
        ];
        rows.forEach((r, i) => {
            const y = 1.12 + i * 0.76;
            s.addText(`${i + 1}`, {
                x: ML, y: y - 0.02, w: 0.45, h: 0.4,
                fontSize: 17, fontFace: FONT, color: C.teal, bold: true, margin: 0,
            });
            s.addText(r.title, {
                x: 1.2, y: y, w: 8.2, h: 0.32,
                fontSize: 14, fontFace: FONT, color: C.navy, bold: true, margin: 0,
            });
            s.addText(r.text, {
                x: 1.2, y: y + 0.32, w: 8.2, h: 0.42,
                fontSize: 11, fontFace: FONT, color: C.text, margin: 0,
            });
        });

        pageNum(s, 3);

        s.addNotes(
            "Spend ~75 seconds here. Don't read the slide — talk to it.\n\n" +
            "What to actually say:\n" +
            "• 'The work has five contributions. The first is the multi-level analysis itself — using code-level structural metrics like class cohesion to flag architectural problems like God Service. That bridge between abstraction levels is the central novelty.'\n" +
            "• 'Second, ten selected anti-patterns across four dimensions — not the largest catalogue, since MARS covers more, but integrated with code-level smell density, evidence snippets, remediation guidance, and a health score.'\n" +
            "• 'Third, automated microservice boundary detection via a three-signal deployability gate — framework entry points, Dockerfiles, and main methods — with confidence levels. Most existing tools require manual configuration.'\n" +
            "• 'Fourth, a composite health score on 0–100, decomposed into four categories so you can see which dimension is dragging the score down.'\n" +
            "• 'Fifth, evidence-based reporting — every finding includes the actual code that triggered it.'\n\n" +
            "Bridge to next slide: 'To give you a clearer picture of how these pieces fit together, let me walk you through the analysis pipeline — from the moment a project is uploaded to the final report.'\n\n" +
            "Don't dwell on each one. They'll see the details later. This slide sets expectations for the rest of the talk."
        );
    }

    // ============================================================
    // SLIDE 4 — PIPELINE OVERVIEW
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addHeader(s, "The analysis pipeline");

        const stages = [
            { title: "Ingest",          sub: "ZIP upload / Git clone" },
            { title: "Detect services", sub: "Deployability gate (3 signals)" },
            { title: "Intra + inter",   sub: "DesigniteJava + Spoon" },
            { title: "Detect patterns", sub: "10 detectors (Strategy)" },
            { title: "Score & report",  sub: "Health score + evidence" },
        ];
        const boxW = 1.6, boxH = 1.45, gap = 0.2, yBox = 1.15;
        stages.forEach((st, i) => {
            const x = ML + i * (boxW + gap);
            s.addShape(pres.shapes.RECTANGLE, {
                x, y: yBox, w: boxW, h: boxH,
                fill: { color: C.bg }, line: { color: C.hairline, width: 1 },
            });
            rule(s, x, yBox, boxW, C.teal, 0.045);
            s.addText(`${i + 1}`, {
                x: x + 0.12, y: yBox + 0.12, w: 0.5, h: 0.25,
                fontSize: 11, fontFace: FONT, color: C.teal, bold: true, margin: 0,
            });
            s.addText(st.title, {
                x: x + 0.12, y: yBox + 0.38, w: boxW - 0.24, h: 0.5,
                fontSize: 12.5, fontFace: FONT, color: C.navy, bold: true, margin: 0,
            });
            s.addText(st.sub, {
                x: x + 0.12, y: yBox + 0.88, w: boxW - 0.24, h: 0.5,
                fontSize: 9.5, fontFace: FONT, color: C.muted, margin: 0,
            });
            if (i < stages.length - 1) {
                s.addText("→", {
                    x: x + boxW, y: yBox + 0.5, w: gap, h: 0.4,
                    fontSize: 14, fontFace: FONT, color: C.teal, bold: true,
                    align: "center", valign: "middle", margin: 0,
                });
            }
        });

        rule(s, ML, 3.0, 0.5, C.teal, 0.04);
        s.addText("Key design choices", {
            x: ML, y: 3.12, w: CW, h: 0.32,
            fontSize: 13, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });
        s.addText([
            { text: "Async pipeline: long analyses don't block the API; Spring @Async with TransactionSynchronization.afterCommit() ensures consistency",
                options: { bullet: true, breakLine: true } },
            { text: "Each detector is a Spring @Component implementing AntiPatternDetector; adding a new detector means no orchestrator changes",
                options: { bullet: true, breakLine: true } },
            { text: "Snippets are extracted and persisted as JSON during analysis, so results survive workspace cleanup",
                options: { bullet: true } },
        ], {
            x: 0.8, y: 3.5, w: 8.6, h: 1.4,
            fontSize: 11, fontFace: FONT, color: C.text, margin: 0, paraSpaceAfter: 5,
        });

        pageNum(s, 4);

        s.addNotes(
            "Spend ~2 minutes here. This is the spine of the talk — get the pipeline right and everything else follows.\n\n" +
            "Walk left to right:\n" +
            "• Ingest: user uploads a ZIP or pastes a Git URL. The system extracts/clones and creates an analysis job.\n" +
            "• Service detection: scan for build files — pom.xml, build.gradle, build.gradle.kts. Filter out non-service modules using exclusion keywords and aggregator checks. Then apply a three-signal deployability gate: framework entry point (HIGH), Dockerfile (MEDIUM), main() method (LOW). Only candidates passing at least one signal are kept.\n" +
            "• Intra-service analysis runs DesigniteJava as a subprocess for code smells. Inter-service uses Spoon AST to find @FeignClient, RestTemplate, WebClient calls and build the dependency graph.\n" +
            "• Then ten detectors run over that graph + the smells. Each is a Spring component implementing a common interface — Strategy pattern.\n" +
            "• Finally results are assembled, the health score is computed, the dependency graph is serialised to JSON, and everything is persisted.\n\n" +
            "If asked about why async: 'Analyses on large projects like Activiti take several minutes. Blocking the HTTP request would time out. The frontend polls for status.'\n\n" +
            "Worth mentioning: 'The dependency graph construction is the most technically interesting part of this stage. Spoon parses the AST of every Java file and looks for inter-service communication annotations — @FeignClient declarations, RestTemplate.exchange calls, WebClient invocations — then resolves each target to a known service. The result is a directed weighted graph where edge weight represents the number of distinct call sites.'\n\n" +
            "Don't read the design-choices panel verbatim — mention one or two if you have time."
        );
    }

    // ============================================================
    // SLIDE 5 — GOD SERVICE DETECTOR (DEEP DIVE)
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addHeader(s, "Spotlight: God Service detection");

        // Left column: the multi-level bridge
        const LX = ML, LW = 4.0;
        s.addText("Multi-level bridge in action", {
            x: LX, y: 1.08, w: LW, h: 0.32,
            fontSize: 13, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });

        // Code-level box
        s.addShape(pres.shapes.RECTANGLE, {
            x: LX, y: 1.5, w: LW, h: 1.0,
            fill: { color: C.bg }, line: { color: C.hairline, width: 1 },
        });
        s.addShape(pres.shapes.RECTANGLE, {
            x: LX, y: 1.5, w: 0.045, h: 1.0,
            fill: { color: C.navy }, line: { color: C.navy },
        });
        s.addText("CODE LEVEL", {
            x: LX + 0.18, y: 1.6, w: LW - 0.3, h: 0.24,
            fontSize: 9, fontFace: FONT, color: C.muted, bold: true, charSpacing: 2, margin: 0,
        });
        s.addText("Structural class metrics (per class)", {
            x: LX + 0.18, y: 1.85, w: LW - 0.3, h: 0.3,
            fontSize: 12.5, fontFace: FONT, color: C.text, bold: true, margin: 0,
        });
        s.addText("Computed from the Spoon AST: fields, methods, LOC, cohesion", {
            x: LX + 0.18, y: 2.15, w: LW - 0.3, h: 0.3,
            fontSize: 10, fontFace: FONT, color: C.muted, italic: true, margin: 0,
        });

        // Down arrow
        s.addShape(pres.shapes.LINE, {
            x: LX + LW / 2, y: 2.58, w: 0, h: 0.32,
            line: { color: C.teal, width: 2.25, endArrowType: "triangle" },
        });

        // Architectural-level box
        s.addShape(pres.shapes.RECTANGLE, {
            x: LX, y: 2.98, w: LW, h: 1.0,
            fill: { color: C.bg }, line: { color: C.hairline, width: 1 },
        });
        s.addShape(pres.shapes.RECTANGLE, {
            x: LX, y: 2.98, w: 0.045, h: 1.0,
            fill: { color: C.teal }, line: { color: C.teal },
        });
        s.addText("ARCHITECTURAL LEVEL", {
            x: LX + 0.18, y: 3.08, w: LW - 0.3, h: 0.24,
            fontSize: 9, fontFace: FONT, color: C.muted, bold: true, charSpacing: 2, margin: 0,
        });
        s.addText("God Service (per microservice)", {
            x: LX + 0.18, y: 3.33, w: LW - 0.3, h: 0.3,
            fontSize: 12.5, fontFace: FONT, color: C.text, bold: true, margin: 0,
        });
        s.addText("Flag service if it contains at least one God Class", {
            x: LX + 0.18, y: 3.63, w: LW - 0.3, h: 0.3,
            fontSize: 10, fontFace: FONT, color: C.muted, italic: true, margin: 0,
        });

        s.addText("Code-level evidence → architectural-level finding", {
            x: LX, y: 4.25, w: LW, h: 0.35,
            fontSize: 10.5, fontFace: FONT, color: C.muted, italic: true, margin: 0,
        });

        // Right column: the six metrics
        const RX = 5.2, RW = 4.2;
        s.addText("Six structural metrics", {
            x: RX, y: 1.08, w: RW, h: 0.32,
            fontSize: 13, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });
        s.addText("Each non-data class is evaluated against six thresholds computed from its AST:", {
            x: RX, y: 1.44, w: RW, h: 0.52,
            fontSize: 11, fontFace: FONT, color: C.text, margin: 0,
        });
        s.addText([
            { text: "≥ 25 fields", options: { bullet: true, breakLine: true } },
            { text: "≥ 30 public methods", options: { bullet: true, breakLine: true } },
            { text: "≥ 1000 lines of code", options: { bullet: true, breakLine: true } },
            { text: "≥ 20 distinct import domains", options: { bullet: true, breakLine: true } },
            { text: "≥ 12 constructor parameters", options: { bullet: true, breakLine: true } },
            { text: "TCC < 0.5  (Bieman & Kang, 1995)", options: { bullet: true } },
        ], {
            x: RX + 0.1, y: 2.0, w: RW - 0.1, h: 1.95,
            fontSize: 11, fontFace: FONT, color: C.text, margin: 0, paraSpaceAfter: 3,
        });

        rule(s, RX, 4.12, 0.5, C.teal, 0.04);
        s.addText("Flag class as God Class if ≥ 3 of 6 thresholds are exceeded", {
            x: RX, y: 4.25, w: RW, h: 0.5,
            fontSize: 11.5, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });

        pageNum(s, 5);

        s.addNotes(
            "Spend ~90–120 seconds. This is the slide that demonstrates the multi-level claim.\n\n" +
            "Talking points:\n" +
            "• 'I picked God Service as the example because it's the cleanest illustration of how code-level signals feed an architectural-level finding.'\n" +
            "• Walk through left side: 'For each service, a Spoon-based analysis parses every class and computes structural metrics — field count, public method count, lines of code, import domains, constructor parameters, and class cohesion. The God Service detector flags any microservice containing at least one class identified as a God Class.'\n" +
            "• Right side: 'A class is flagged as a God Class when it exceeds at least three of the six metrics. Pure data holders such as entities and DTOs are excluded first, since they naturally have many fields and low cohesion without being an anti-pattern.'\n" +
            "• On TCC: 'Tight Class Cohesion measures the fraction of method pairs that share an instance field access. Low TCC means the methods operate on disjoint state — a sign of unrelated responsibilities packed into one class.'\n\n" +
            "EXPECT THIS QUESTION: 'Why does one God Class flag a service?'\n" +
            "Answer: 'The default is intentionally sensitive. False positives are cheap to dismiss. False negatives — architectural debt growing undetected — are expensive. The threshold is configurable; teams in production would calibrate it. Even one class concentrating that many responsibilities is worth a look.'\n\n" +
            "If asked where DesigniteJava fits: 'DesigniteJava runs per service for code smells, but its catalogue is abstraction and modularization smells rather than a literal God Class. So God Service detection is driven by the Spoon structural metrics, and DesigniteJava smell density feeds the code-quality category of the health score instead.'"
        );
    }

    // ============================================================
    // SLIDE 6 — DETECTOR CATALOGUE
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addHeader(s, "Ten detectors across four dimensions");

        // Severity drives the colour: tinted pill + dot, legend ties colours to score penalties
        const SEV = {
            critical: { dot: C.bad,      tint: "FEE2E2" },
            high:     { dot: C.warn,     tint: "FEF3C7" },
            medium:   { dot: C.deepBlue, tint: C.ice },
        };

        s.addText([
            { text: "● ",            options: { color: C.bad } },
            { text: "critical (−8 pts)      ", options: { color: C.muted } },
            { text: "● ",            options: { color: C.warn } },
            { text: "high (−5 pts)      ",     options: { color: C.muted } },
            { text: "● ",            options: { color: C.deepBlue } },
            { text: "medium (−3 pts)",         options: { color: C.muted } },
        ], { x: ML, y: 1.06, w: CW, h: 0.3, fontSize: 10, fontFace: FONT, margin: 0 });

        function pill(x, y, w, name, sev) {
            const sv = SEV[sev];
            s.addShape(pres.shapes.ROUNDED_RECTANGLE, {
                x, y, w, h: 0.44, rectRadius: 0.1,
                fill: { color: sv.tint }, line: { color: sv.tint },
            });
            s.addShape(pres.shapes.OVAL, {
                x: x + 0.15, y: y + 0.175, w: 0.09, h: 0.09,
                fill: { color: sv.dot }, line: { color: sv.dot },
            });
            s.addText(name, {
                x: x + 0.3, y, w: w - 0.32, h: 0.44,
                fontSize: 10.5, fontFace: FONT, color: C.text, valign: "middle", margin: 0,
            });
        }

        function band(label, x, y, items) {
            s.addText(label.toUpperCase(), {
                x, y, w: 4.4, h: 0.28,
                fontSize: 10.5, fontFace: FONT, color: C.navy, bold: true, charSpacing: 2, margin: 0,
            });
            let px = x;
            items.forEach(([name, sev, w]) => {
                pill(px, y + 0.33, w, name, sev);
                px += w + 0.15;
            });
        }

        // Two small dimensions share the first band; the wider ones get full rows
        band("Service Design", ML, 1.5, [
            ["Nano Service", "medium", 1.45],
            ["God Service", "high", 1.4],
        ]);
        band("Data Management", 5.2, 1.5, [
            ["Shared Database", "high", 1.7],
        ]);
        band("Communication", ML, 2.7, [
            ["Chatty Service", "high", 1.6],
            ["Cyclic Dependency", "critical", 1.85],
            ["Hardcoded Endpoints", "medium", 2.0],
            ["ESB Misuse", "high", 1.3],
        ]);
        band("Deployment & Coupling", ML, 3.9, [
            ["Distributed Monolith", "critical", 2.0],
            ["API Versioning Absence", "medium", 2.25],
            ["Wrong Cuts", "high", 1.3],
        ]);

        rule(s, ML, 4.85, 0.5, C.teal, 0.04);
        s.addText("All detectors are pluggable Strategy components with configurable thresholds", {
            x: ML, y: 4.95, w: CW, h: 0.32,
            fontSize: 11.5, fontFace: FONT, color: C.navy, margin: 0,
        });

        pageNum(s, 6);

        s.addNotes(
            "Quick slide — about 45 seconds. Don't read every detector aloud.\n\n" +
            "What to say:\n" +
            "• 'These are the ten detectors, organised by architectural dimension. Each is a Spring component implementing a common interface, so adding a new detector means writing one class — no orchestrator changes.'\n" +
            "• 'Severities feed directly into the health score: critical issues cost 8 points, high cost 5, medium cost 3.'\n" +
            "• 'I won't walk through each detector — they're in Chapter 3 of the thesis. I'll focus on the scoring mechanism next.'\n\n" +
            "If a committee member asks about a specific detector:\n" +
            "• Shared DB — group services by spring.datasource.url, flag groups of size > 1\n" +
            "• Cyclic Dependency — Tarjan's SCC on the dep graph, any SCC > 1 vertex is a cycle\n" +
            "• Chatty Service — call count per edge ≥ 5 OR a Feign interface with ≥ 5 methods\n" +
            "• Hardcoded Endpoints — regex scan of .java for http://, localhost:, IP literals (with comment/test exclusions)\n" +
            "• Distributed Monolith — composite rule on coupling coefficient, connected ratio, shared DB count\n" +
            "• API Versioning — regex /v\\d+[/.] on endpoint paths\n" +
            "• ESB Misuse — caller/callee ratios + volume-based mediator ratio\n" +
            "• Wrong Cuts — bidirectional edges between a service pair\n\n" +
            "Worth saying aloud: 'The severity levels aren't arbitrary — they reflect how much damage the anti-pattern can cause. Cyclic Dependency and Distributed Monolith are critical because they undermine independent deployability entirely. Hardcoded Endpoints and API Versioning are medium because they're easier to fix and their impact is more localised.'\n\n" +
            "Don't get defensive about limitations. Acknowledging them is more impressive than glossing over them."
        );
    }

    // ============================================================
    // SLIDE 7 — HEALTH SCORE STRUCTURE
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addHeader(s, "Composite health score");

        const cats = [
            { label: "Anti-Patterns",  budget: "40", color: C.bad,
              desc: "−8/−5/−3/−1 per issue (crit/high/med/low)" },
            { label: "Code Quality",   budget: "20", color: C.warn,
              desc: "Density-based penalty (smells / KLOC)" },
            { label: "Architecture",   budget: "25", color: C.deepBlue,
              desc: "Coupling coefficient + cycle count" },
            { label: "Service Sizing", budget: "15", color: C.teal,
              desc: "Nano (cap 8) + god (cap 10) penalties" },
        ];
        const colW = 2.05, colGap = 0.2;
        cats.forEach((c, i) => {
            const x = ML + i * (colW + colGap);
            rule(s, x, 1.15, colW, c.color, 0.045);
            s.addText(c.budget, {
                x, y: 1.28, w: colW, h: 0.55,
                fontSize: 30, fontFace: FONT, color: C.navy, bold: true, margin: 0,
            });
            s.addText(c.label, {
                x, y: 1.86, w: colW, h: 0.3,
                fontSize: 12.5, fontFace: FONT, color: C.navy, bold: true, margin: 0,
            });
            s.addText(c.desc, {
                x, y: 2.18, w: colW, h: 0.6,
                fontSize: 9.5, fontFace: FONT, color: C.muted, margin: 0,
            });
        });

        // Grade scale: one segmented horizontal bar
        s.addText("Letter grade scale", {
            x: ML, y: 2.98, w: CW, h: 0.3,
            fontSize: 12.5, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });
        const grades = [
            { l: "A", lo: "≥ 90", color: C.good },
            { l: "B", lo: "≥ 80", color: C.teal },
            { l: "C", lo: "≥ 65", color: C.deepBlue },
            { l: "D", lo: "≥ 50", color: C.warn },
            { l: "F", lo: "< 50",     color: C.bad },
        ];
        const segW = CW / grades.length;
        grades.forEach((g, i) => {
            const x = ML + i * segW;
            s.addShape(pres.shapes.RECTANGLE, {
                x, y: 3.32, w: segW - 0.04, h: 0.5,
                fill: { color: g.color }, line: { color: g.color },
            });
            s.addText([
                { text: g.l + "  ", options: { bold: true, fontSize: 14 } },
                { text: g.lo, options: { fontSize: 11 } },
            ], {
                x: x + 0.15, y: 3.32, w: segW - 0.2, h: 0.5,
                fontFace: FONT, color: "FFFFFF", valign: "middle", margin: 0,
            });
        });

        s.addText("Why decompose?", {
            x: ML, y: 4.18, w: CW, h: 0.3,
            fontSize: 12.5, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });
        s.addText("A single number tells teams whether quality is improving; the per-category breakdown tells them which dimension needs attention. Both views are exposed via the analysis-diff feature for tracking over time.", {
            x: ML, y: 4.5, w: CW, h: 0.6,
            fontSize: 11, fontFace: FONT, color: C.text, margin: 0,
        });

        pageNum(s, 7);

        s.addNotes(
            "Spend ~75 seconds.\n\n" +
            "Talking points:\n" +
            "• 'The composite score is a sum of four independent categories. Each has its own budget — Anti-Patterns is 40 points, Code Quality 20, Architecture 25, Service Sizing 15. Total: 100.'\n" +
            "• 'Each category clamps at zero, so penalties can't bleed across categories. That's intentional: a code-quality-heavy project shouldn't have its architectural score dragged down.'\n" +
            "• 'Importantly, the breakdown is preserved in the UI. A developer doesn't just see 45/100 — they see WHICH category is dragging the score down, with itemised deductions.'\n" +
            "• 'The diff feature compares successive analyses, so a team that fixes a shared DB anti-pattern can see exactly how many points that bought them.'\n\n" +
            "EXPECT THIS QUESTION: 'Doesn't clamping at zero mean you lose discrimination between moderately bad and very bad projects?'\n" +
            "Answer: 'Yes — that's a known limitation of the category-cap approach, which I note in the threats to validity. A team with 13 nano services and one with 50 would both see Anti-Patterns at zero. The Service Sizing breakdown still preserves the per-issue counts, so the full picture is still visible to the user. A scaled, density-style penalty in the AP category — similar to the Code Quality category — would address this in future work.'\n\n" +
            "You can also add: 'The weights were informed by how other composite metrics work in the literature — SonarQube's maintainability rating, SQALE, and the ISO 25010 quality model. Anti-patterns get the largest budget because they represent architectural-level issues that are expensive to fix after deployment.'"
        );
    }

    // ============================================================
    // SLIDE 8 — WORKED EXAMPLE: microservice-recruit
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addHeader(s, "Worked example: microservice-recruit");

        // Left: the findings and the math
        const LX = ML, LW = 4.3;
        s.addText("Anti-pattern findings", {
            x: LX, y: 1.08, w: LW, h: 0.32,
            fontSize: 13, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });
        const findings = [
            ["6×", "API Versioning Absence", "−3 each = −18"],
            ["2×", "ESB Misuse",             "−5 each = −10"],
            ["2×", "Wrong Cuts",             "−5 each = −10"],
            ["1×", "Cyclic Dependency",      "−8"],
            ["1×", "Hardcoded Endpoints",    "−3"],
        ];
        findings.forEach((row, i) => {
            const y = 1.48 + i * 0.33;
            s.addText(row[0], {
                x: LX, y, w: 0.45, h: 0.3,
                fontSize: 11, fontFace: FONT, color: C.bad, bold: true, margin: 0,
            });
            s.addText(row[1], {
                x: LX + 0.5, y, w: 2.45, h: 0.3,
                fontSize: 11, fontFace: FONT, color: C.text, margin: 0,
            });
            s.addText(row[2], {
                x: LX + 2.95, y, w: 1.35, h: 0.3,
                fontSize: 10, fontFace: FONT, color: C.muted, align: "right", margin: 0,
            });
            rule(s, LX, y + 0.29, LW, C.hairline, 0.012);
        });
        s.addText("Total penalty 49  →  category clamps at 0 / 40", {
            x: LX, y: 3.22, w: LW, h: 0.32,
            fontSize: 11.5, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });

        s.addText("Deductions spread across all four categories: the anti-pattern load clamps AP to 0; Architecture loses 9 to dependency-graph coupling; Code Quality 8 to smell density; Service Sizing 3 to one nano service.", {
            x: LX, y: 3.75, w: LW, h: 1.2,
            fontSize: 10, fontFace: FONT, color: C.muted, italic: true, margin: 0,
        });

        // Right: category bars and the final score
        const RX = 5.4, RW = 4.0;
        s.addText("Category contributions", {
            x: RX, y: 1.08, w: RW, h: 0.32,
            fontSize: 13, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });
        const bars = [
            { label: "Anti-Patterns",  max: 40, got: 0,  color: C.bad },
            { label: "Code Quality",   max: 20, got: 12, color: C.warn },
            { label: "Architecture",   max: 25, got: 16, color: C.deepBlue },
            { label: "Service Sizing", max: 15, got: 12, color: C.teal },
        ];
        const barX = 6.85, barW = 1.85, barH = 0.24;
        bars.forEach((b, i) => {
            const y = 1.52 + i * 0.5;
            s.addText(b.label, {
                x: RX, y: y - 0.04, w: 1.4, h: 0.32,
                fontSize: 10.5, fontFace: FONT, color: C.text, valign: "middle", margin: 0,
            });
            s.addShape(pres.shapes.RECTANGLE, {
                x: barX, y, w: barW, h: barH,
                fill: { color: C.hairline }, line: { color: C.hairline },
            });
            if (b.got > 0) {
                s.addShape(pres.shapes.RECTANGLE, {
                    x: barX, y, w: barW * (b.got / b.max), h: barH,
                    fill: { color: b.color }, line: { color: b.color },
                });
            }
            s.addText(`${b.got} / ${b.max}`, {
                x: barX + barW + 0.08, y: y - 0.04, w: 0.62, h: 0.32,
                fontSize: 10, fontFace: FONT, color: C.muted, valign: "middle", margin: 0,
            });
        });

        s.addText([
            { text: "40", options: { fontSize: 40, color: C.bad, bold: true } },
            { text: "  / 100  ·  Grade F", options: { fontSize: 14, color: C.navy, bold: true } },
        ], {
            x: RX, y: 3.75, w: RW, h: 0.85, fontFace: FONT, margin: 0,
        });
        s.addText("0 + 12 + 16 + 12", {
            x: RX, y: 4.6, w: RW, h: 0.3,
            fontSize: 11, fontFace: FONT, color: C.muted, italic: true, margin: 0,
        });

        pageNum(s, 8);

        s.addNotes(
            "Spend ~75 seconds. This slide makes the scoring reproducible.\n\n" +
            "Walk through it:\n" +
            "• 'Take microservice-recruit — a Spring Cloud recruitment platform. In the anti-pattern category it has 6 API versioning absences, 2 ESB misuse and 2 wrong-cuts findings, 1 cyclic dependency, and 1 hardcoded endpoint — total penalty 49, but the category caps at 40, so it clamps to zero.'\n" +
            "• 'Its single nano service is scored under Service Sizing instead of the anti-pattern category, to avoid double counting — costing 3 points there, leaving 12 of 15.'\n" +
            "• 'Architecture loses 9 points for the structural coupling of its dependency graph — leaving 16 of 25.'\n" +
            "• 'Code Quality is scored by smell density: 260 smells over 8.3K LOC is 31.4 per KLOC, an 8-point deduction, leaving 12 of 20.'\n" +
            "• 'The final composite is 0 + 12 + 16 + 12 = 40 — grade F. Unlike a project whose problems sit in one place, this one loses points in every dimension, which is exactly what the multi-level scoring is meant to surface.'\n\n" +
            "This demonstrates that the scoring is transparent and that the breakdown shows exactly what's wrong.\n\n" +
            "EXPECT QUESTION: 'Only 8 points lost on Code Quality with 260 smells — why so little?'\n" +
            "Answer: 'Code Quality is scored on smell density per 1000 LOC, not raw count, so a codebase isn't punished just for its size. The category is deliberately bounded so that architectural anti-patterns remain the dominant signal in the overall score.'\n\n" +
            "If time allows, mention: 'What makes this example interesting is that the problems are spread across all four categories — it's not a project with one catastrophic issue, it's a project with consistent low-level neglect across every dimension. That's exactly the kind of systemic debt the multi-category decomposition is designed to surface.'"
        );
    }

    // ============================================================
    // SLIDE 9 — EVALUATION RESULTS
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addHeader(s, "Evaluation: eleven open-source projects");

        s.addChart(pres.charts.BAR, [{
            name: "Health score",
            labels: ["MicroSocial", "ftgo", "PiggyMetrics", "RuoYi", "Site-Where", "design-pat.", "NetworkDisk", "piomin", "Train-Ticket", "mall-swarm", "recruit"],
            values: [83, 75, 71, 71, 69, 60, 59, 55, 54, 53, 40],
        }], {
            x: 0.55, y: 1.05, w: 5.5, h: 2.95,
            barDir: "col",
            chartColors: [C.teal],
            chartArea: { fill: { color: C.bg } },
            catAxisLabelColor: C.muted,
            valAxisLabelColor: C.muted,
            catAxisLabelFontSize: 7,
            valAxisLabelFontSize: 9,
            valAxisMinVal: 0, valAxisMaxVal: 100,
            valGridLine: { color: C.hairline, size: 0.5 },
            catGridLine: { style: "none" },
            showValue: true, dataLabelPosition: "outEnd",
            dataLabelColor: C.text, dataLabelFontSize: 9,
            showLegend: false,
            showTitle: true, title: "Health scores by project (0–100)",
            titleColor: C.navy, titleFontSize: 12, titleFontFace: FONT,
        });

        // Dataset table — plain text rows, hairline under the header, total set off by a rule
        const TX = 6.5, TW = 2.9;
        s.addText("Dataset (services · LOC)", {
            x: TX, y: 1.05, w: TW, h: 0.3,
            fontSize: 12, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });
        rule(s, TX, 1.38, TW, C.hairline, 0.015);
        const rows = [
            ["MicroSocial",  "4",  "2k"],
            ["design-pat.",  "16", "19k"],
            ["Site-Where",   "15", "74k"],
            ["Train-Ticket", "41", "36k"],
            ["NetworkDisk",  "10", "7k"],
            ["recruit",      "8",  "8k"],
            ["ftgo",         "7",  "11k"],
            ["mall-swarm",   "7",  "86k"],
            ["RuoYi",        "8",  "27k"],
            ["PiggyMetrics", "8",  "4.5k"],
            ["piomin",       "6",  "1.3k"],
        ];
        rows.forEach((r, i) => {
            const y = 1.46 + i * 0.215;
            s.addText(r[0], {
                x: TX, y, w: 1.55, h: 0.21,
                fontSize: 9, fontFace: FONT, color: C.text, valign: "middle", margin: 0,
            });
            s.addText(r[1], {
                x: TX + 1.55, y, w: 0.5, h: 0.21,
                fontSize: 9, fontFace: FONT, color: C.text, align: "center", valign: "middle", margin: 0,
            });
            s.addText(r[2], {
                x: TX + 2.05, y, w: 0.85, h: 0.21,
                fontSize: 9, fontFace: FONT, color: C.text, align: "right", valign: "middle", margin: 0,
            });
        });
        rule(s, TX, 3.86, TW, C.navy, 0.02);
        s.addText("Total", {
            x: TX, y: 3.92, w: 1.55, h: 0.22,
            fontSize: 9, fontFace: FONT, color: C.navy, bold: true, valign: "middle", margin: 0,
        });
        s.addText("130", {
            x: TX + 1.55, y: 3.92, w: 0.5, h: 0.22,
            fontSize: 9, fontFace: FONT, color: C.navy, bold: true, align: "center", valign: "middle", margin: 0,
        });
        s.addText("274k", {
            x: TX + 2.05, y: 3.92, w: 0.85, h: 0.22,
            fontSize: 9, fontFace: FONT, color: C.navy, bold: true, align: "right", valign: "middle", margin: 0,
        });

        // Findings
        rule(s, ML, 4.32, 0.5, C.teal, 0.04);
        s.addText([
            { text: "98 anti-pattern instances across 9 types; API Versioning Absence (37) and Nano Service (23) dominate",
                options: { bullet: true, breakLine: true } },
            { text: "PiggyMetrics and piomin broadened ESB Misuse and Nano Service coverage; Cyclic Dependency and Wrong Cuts detected in microservice-recruit",
                options: { bullet: true } },
        ], {
            x: 0.8, y: 4.44, w: 8.6, h: 0.7,
            fontSize: 10.5, fontFace: FONT, color: C.text, margin: 0, paraSpaceAfter: 2,
        });

        pageNum(s, 9);

        s.addNotes(
            "Spend ~90 seconds.\n\n" +
            "Talking points:\n" +
            "• 'I evaluated on eleven open-source Spring Boot projects, spanning from piomin at 1.3k LOC to mall-swarm at 86k LOC. 130 microservices, about 274k LOC total.'\n" +
            "• 'Health scores ranged from 40 to 83 — the scoring mechanism produces meaningful spread.'\n" +
            "• 'The dominant findings are configuration and sizing issues: 37 API versioning absences, 23 nano services, 15 hardcoded endpoints. Cyclic Dependency and Wrong Cuts were detected in microservice-recruit. Adding PiggyMetrics and piomin broadened ESB Misuse and Nano Service coverage.'\n" +
            "• '98 total anti-pattern instances across 9 distinct types. Only Distributed Monolith was not detected — plausible given these projects' topologies.'\n\n" +
            "You can add: 'The score range — 40 to 83 — shows that the metric has useful discriminating power. Projects known to be well-structured like MicroSocial score highest; projects known to have issues like Train-Ticket and microservice-recruit score lowest. If every project scored 70, the metric wouldn't be telling us much.'\n\n" +
            "EXPECT: 'Only eleven projects, is that enough?'\n" +
            "Answer: 'Not for a statistical claim. This evaluation is a feasibility demonstration across heterogeneous codebases. A full precision/recall study would need a labelled corpus, which doesn't exist for microservice anti-patterns — that's an open problem in the field. I note this in Threats to Validity.'"
        );
    }

    // ============================================================
    // SLIDE 10 — LIVE DEMO
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.navy };

        rule(s, ML, 0.95, 0.7, C.teal, 0.045);
        s.addText("Live demo", {
            x: ML, y: 1.15, w: 8.6, h: 0.85,
            fontSize: 44, fontFace: FONT, color: "FFFFFF", bold: true, margin: 0,
        });
        s.addText("Cloning and analysing a microservice project in real time", {
            x: ML, y: 2.05, w: 8.6, h: 0.4,
            fontSize: 16, fontFace: FONT, color: C.ice, italic: true, margin: 0,
        });

        const steps = [
            { title: "Clone a small repo",
              sub: "Paste a public Git URL, submit, watch the pipeline progress" },
            { title: "Open the results dashboard",
              sub: "Health score gauge, four-category breakdown, summary metrics" },
            { title: "Drill into a finding",
              sub: "Expand an anti-pattern card to see source-code evidence and remediation" },
            { title: "Explore the dependency graph",
              sub: "Interactive Cytoscape view of inter-service edges" },
        ];
        steps.forEach((st, i) => {
            const y = 2.8 + i * 0.6;
            s.addText(String(i + 1), {
                x: ML, y, w: 0.4, h: 0.32,
                fontSize: 16, fontFace: FONT, color: C.teal, bold: true, margin: 0,
            });
            s.addText(st.title, {
                x: 1.15, y: y + 0.01, w: 8.2, h: 0.3,
                fontSize: 14, fontFace: FONT, color: "FFFFFF", bold: true, margin: 0,
            });
            s.addText(st.sub, {
                x: 1.15, y: y + 0.29, w: 8.2, h: 0.26,
                fontSize: 11, fontFace: FONT, color: C.ice, margin: 0,
            });
        });

        pageNum(s, 10, true);

        s.addNotes(
            "This is the demo slide — switch to the browser/tool after this title comes up. Aim for ~2 minutes total.\n\n" +
            "Before you start:\n" +
            "• Have the app already running on localhost. Don't start the server live.\n" +
            "• Be logged in already. Don't waste demo time on the login form.\n" +
            "• Have a small known-working repo URL ready in your clipboard. MicroservicesSocial is the safest choice — small, fast, and produces a varied set of findings (Shared DB, Nano Service, Hardcoded Endpoint, API Versioning).\n" +
            "• Have a second browser tab open with a PREVIOUSLY COMPLETED analysis loaded, as a fallback. If the live clone fails or runs slow, switch to that tab and say 'here's a previous run on the same project.'\n\n" +
            "Demo script (~2 min):\n" +
            "1. CLONE (~30 s): 'I'll paste a GitHub URL of a small microservice project and submit it.' Paste, click submit. While it runs: 'the pipeline is now detecting microservices, running DesigniteJava per service, building the dependency graph, and running the ten detectors.'\n" +
            "2. DASHBOARD (~30 s): When the results page loads, point at the health score gauge. 'Score 83, grade B. The four-category breakdown shows where the deductions came from — mostly Anti-Patterns category.'\n" +
            "3. DRILL-DOWN (~45 s): Click on the Shared Database finding to expand it. 'You can see the affected services, the actual datasource URL that's being shared, the code snippet from the application.yml file, and remediation guidance.' This is the slide's strongest moment — the committee sees the evidence-based reporting in action.\n" +
            "4. DEPENDENCY GRAPH (~15 s): Scroll down or switch to the dep-graph view. 'Interactive force-directed graph. Each node is a service, edges show inter-service calls, node size scales with LOC.'\n\n" +
            "Then say: 'I'll switch back to the slides.' Click back to slide 11.\n\n" +
            "If the demo BREAKS at any point:\n" +
            "• Don't panic. Say 'looks like the network is being slow — let me switch to a pre-computed run.'\n" +
            "• Switch to your backup tab.\n" +
            "• If even the backup fails: 'the deployment is on my laptop and I'd be happy to demo it after the defense — meanwhile, the screenshots in Chapter 4 of the thesis show the same flow.'\n" +
            "• Move on quickly. Don't burn 90 seconds debugging in front of the committee. The thesis stands on its own."
        );
    }

    // ============================================================
    // SLIDE 11 — LIMITATIONS & FUTURE WORK
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addHeader(s, "Limitations & future work");

        // Left: limitations
        s.addText("Limitations", {
            x: ML, y: 1.1, w: 4.2, h: 0.38,
            fontSize: 15, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });
        rule(s, ML, 1.52, 0.5, C.warn, 0.04);
        s.addText([
            { text: "Java / Spring Boot only; polyglot systems not supported",
                options: { bullet: true, breakLine: true } },
            { text: "Static analysis only; no runtime call frequency data",
                options: { bullet: true, breakLine: true } },
            { text: "Service detection deployability gate may miss services without framework annotations, Dockerfile, or main method",
                options: { bullet: true, breakLine: true } },
            { text: "Dynamic URLs (e.g. Spring Cloud Config) are not resolved",
                options: { bullet: true, breakLine: true } },
            { text: "Health score weights chosen by engineering judgement, not empirically calibrated",
                options: { bullet: true, breakLine: true } },
            { text: "Evaluation on 11 open-source projects; not a statistical claim",
                options: { bullet: true } },
        ], {
            x: ML, y: 1.72, w: 4.2, h: 3.3,
            fontSize: 11, fontFace: FONT, color: C.text, margin: 0, paraSpaceAfter: 6,
        });

        // Right: future work
        s.addText("Future work", {
            x: 5.2, y: 1.1, w: 4.2, h: 0.38,
            fontSize: 15, fontFace: FONT, color: C.navy, bold: true, margin: 0,
        });
        rule(s, 5.2, 1.52, 0.5, C.teal, 0.04);
        s.addText([
            { text: "Multi-language support (Python, Go, .NET, Node.js) via language-agnostic dependency graphs from Docker Compose / Kubernetes manifests",
                options: { bullet: true, breakLine: true } },
            { text: "Extend anti-pattern catalogue (timeout misconfiguration, missing API gateway)",
                options: { bullet: true, breakLine: true } },
            { text: "ML-based detection: classifiers on labelled datasets or graph neural networks on the dependency graph",
                options: { bullet: true, breakLine: true } },
            { text: "Empirical threshold calibration across an industrial corpus",
                options: { bullet: true } },
        ], {
            x: 5.2, y: 1.72, w: 4.2, h: 3.3,
            fontSize: 11, fontFace: FONT, color: C.text, margin: 0, paraSpaceAfter: 6,
        });

        pageNum(s, 11);

        s.addNotes(
            "Spend ~60 seconds. This is your honesty slide — committee will respect frankness here.\n\n" +
            "Talking points:\n" +
            "• 'The tool has real limitations. The biggest is the Java/Spring Boot restriction — real-world systems are polyglot, and the current tool can't analyse non-Java services.'\n" +
            "• 'Static analysis trades precision for applicability. A distributed tracing approach would give exact runtime call counts but requires the system to be deployed and instrumented — which prevents the tool from running on a fresh checkout.'\n" +
            "• 'The health score weights aren't empirically validated. They're engineering judgement informed by the literature. Different teams might want different weightings.'\n" +
            "• 'Future work in priority order: multi-language support, then ML-based detection, then empirical calibration of thresholds.'\n\n" +
            "You can add: 'The multi-language extension is actually partially designed already — Chapter 5 describes a roadmap where Docker Compose and Kubernetes manifests provide a language-agnostic dependency graph, and only the intra-service analysis needs per-language adapters. So the architecture is ready for it, even though the implementation is Java-only today.'\n\n" +
            "Don't get defensive about limitations. Acknowledging them is more impressive than glossing over them."
        );
    }

    // ============================================================
    // SLIDE 12 — CLOSING
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.navy };

        rule(s, ML, 1.0, 0.7, C.teal, 0.045);
        s.addText("Thank you", {
            x: ML, y: 1.2, w: 8.6, h: 0.8,
            fontSize: 44, fontFace: FONT, color: "FFFFFF", bold: true, margin: 0,
        });
        s.addText("Questions & discussion", {
            x: ML, y: 2.05, w: 8.6, h: 0.45,
            fontSize: 18, fontFace: FONT, color: C.ice, italic: true, margin: 0,
        });

        s.addText("RECAP", {
            x: ML, y: 2.95, w: 8.6, h: 0.3,
            fontSize: 11, fontFace: FONT, color: C.teal, bold: true, charSpacing: 3, margin: 0,
        });
        s.addText([
            { text: "Multi-level static analysis combining code smells and architectural metrics",
                options: { bullet: true, breakLine: true } },
            { text: "Ten anti-patterns, configurable thresholds, evidence-based reporting",
                options: { bullet: true, breakLine: true } },
            { text: "Composite health score with four-category decomposition and analysis diff",
                options: { bullet: true, breakLine: true } },
            { text: "Open-source web application, ready for CI/CD integration",
                options: { bullet: true } },
        ], {
            x: 0.8, y: 3.3, w: 8.4, h: 1.45,
            fontSize: 12.5, fontFace: FONT, color: "FFFFFF", margin: 0, paraSpaceAfter: 4,
        });

        s.addText("Iacob Andrei  ·  Supervisor: Prof. Dr. Simona Motogna  ·  UBB FMI", {
            x: ML, y: 5.05, w: 8.6, h: 0.3,
            fontSize: 10.5, fontFace: FONT, color: C.ice, margin: 0,
        });

        s.addNotes(
            "Quick close — about 20 seconds.\n\n" +
            "Suggested wording:\n" +
            "'To recap: the MSA Detector is a multi-level static analysis tool for Java/Spring Boot microservices, with ten anti-pattern detectors, a composite health score, and evidence-based reporting. The full implementation, evaluation data, and dissertation are available. Thank you — I'm happy to take questions.'\n\n" +
            "You can also say before 'thank you': 'If there's one thing I'd like you to take away, it's that the bridge between code-level evidence and architectural-level findings is what makes this tool different — it doesn't just say something is wrong, it shows you exactly where in the code the problem lives.'\n\n" +
            "Then SHUT UP. Don't fill the silence. Let the committee speak.\n\n" +
            "When the first question comes:\n" +
            "1. Listen to the whole question — don't start composing the answer mid-question.\n" +
            "2. If unclear, ask for clarification: 'Just to make sure I understand — are you asking about X or about Y?'\n" +
            "3. Pause for 2 seconds before answering. Looks thoughtful, not nervous.\n" +
            "4. Answer concisely. If you don't know, say 'I didn't investigate that — my best guess is X but I'd want to verify.'\n\n" +
            "Common questions to prepare for:\n" +
            "• 'Why God Service threshold = 1?' → false positives cheap, false negatives expensive; configurable.\n" +
            "• 'Why only eleven projects?' → feasibility demo not statistical claim; no labelled corpus exists.\n" +
            "• 'Score compression at the bottom?' → known limitation, a scaled AP penalty would fix.\n" +
            "• 'Why static instead of dynamic?' → applicability over precision; no deployed system required.\n" +
            "• 'Why Spring Boot only?' → biggest Java microservice ecosystem; other frameworks future work."
        );
    }

    // Save
    const outPath = __dirname + "/MSA_Detector_Defense_v2.pptx";
    await pres.writeFile({ fileName: outPath });
    console.log("Wrote:", outPath);
})();
