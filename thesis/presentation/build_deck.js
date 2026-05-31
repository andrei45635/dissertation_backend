// MSA Detector Master's Dissertation Defense — 15-minute presentation
const pptxgen = require("pptxgenjs");
const React = require("react");
const ReactDOMServer = require("react-dom/server");
const sharp = require("sharp");
const {
    FaDatabase, FaProjectDiagram, FaCogs, FaChartLine,
    FaShieldAlt, FaCode, FaBalanceScale, FaExclamationTriangle,
    FaCheckCircle, FaSearchLocation, FaServer, FaSitemap,
    FaFileCode, FaArrowRight, FaLightbulb, FaWrench
} = require("react-icons/fa");

// ============================================================
// COLOR PALETTE — "Ocean Gradient" with a teal accent
// ============================================================
const C = {
    navy:      "0F2A44",   // primary dark
    deepBlue:  "1C4E80",   // primary mid
    teal:      "0D9488",   // accent
    ice:       "DBEAFE",   // light supporting
    bg:        "FFFFFF",   // page background
    text:      "1E293B",   // body text
    muted:     "64748B",   // muted/captions
    good:      "059669",   // good (green)
    warn:      "D97706",   // warning (amber)
    bad:       "DC2626",   // bad (red)
    card:      "F8FAFC",   // card background
    cardBorder:"E2E8F0",   // card border
};

const HEADER_FONT = "Calibri";
const BODY_FONT   = "Calibri";

// ============================================================
// Icon helpers
// ============================================================
function renderIconSvg(IconComponent, color = "#FFFFFF", size = 256) {
    return ReactDOMServer.renderToStaticMarkup(
        React.createElement(IconComponent, { color, size: String(size) })
    );
}
async function iconPng(IconComponent, color = "#FFFFFF", size = 256) {
    const svg = renderIconSvg(IconComponent, color, size);
    const buf = await sharp(Buffer.from(svg)).png().toBuffer();
    return "image/png;base64," + buf.toString("base64");
}

// ============================================================
// Build deck
// ============================================================
(async () => {
    const pres = new pptxgen();
    pres.layout = "LAYOUT_16x9"; // 10" x 5.625"
    pres.author = "Iacob Andrei";
    pres.title  = "MSA Detector — Master's Dissertation Defense";

    // Pre-rasterise icons
    const icDb       = await iconPng(FaDatabase,         "#FFFFFF");
    const icGraph    = await iconPng(FaProjectDiagram,   "#FFFFFF");
    const icCogs     = await iconPng(FaCogs,             "#FFFFFF");
    const icChart    = await iconPng(FaChartLine,        "#FFFFFF");
    const icShield   = await iconPng(FaShieldAlt,        "#FFFFFF");
    const icCode     = await iconPng(FaCode,             "#FFFFFF");
    const icBalance  = await iconPng(FaBalanceScale,     "#FFFFFF");
    const icWarn     = await iconPng(FaExclamationTriangle,"#FFFFFF");
    const icCheck    = await iconPng(FaCheckCircle,      "#FFFFFF");
    const icSearch   = await iconPng(FaSearchLocation,   "#FFFFFF");
    const icServer   = await iconPng(FaServer,           "#FFFFFF");
    const icSitemap  = await iconPng(FaSitemap,          "#FFFFFF");
    const icFile     = await iconPng(FaFileCode,         "#FFFFFF");
    const icArrow    = await iconPng(FaArrowRight,       "#" + C.teal);
    const icBulb     = await iconPng(FaLightbulb,        "#FFFFFF");
    const icWrench   = await iconPng(FaWrench,           "#FFFFFF");

    // ============================================================
    // Helpers for repeated layout primitives
    // ============================================================
    function addFooter(slide, n, total) {
        slide.addText("MSA Detector  •  Iacob Andrei  •  UBB FMI", {
            x: 0.4, y: 5.30, w: 6, h: 0.25,
            fontSize: 9, fontFace: BODY_FONT, color: C.muted,
        });
        slide.addText(`${n} / ${total}`, {
            x: 8.8, y: 5.30, w: 0.8, h: 0.25,
            fontSize: 9, fontFace: BODY_FONT, color: C.muted, align: "right",
        });
    }

    function addTitle(slide, title) {
        // simple title — no decorative accent line (per skill guidance)
        slide.addText(title, {
            x: 0.5, y: 0.28, w: 9, h: 0.6,
            fontSize: 28, fontFace: HEADER_FONT, color: C.navy, bold: true,
            margin: 0,
        });
    }

    // Coloured icon "chip" — fresh shadow object each call
    function iconChip(slide, x, y, size, fillHex, iconData) {
        slide.addShape(pres.shapes.ROUNDED_RECTANGLE, {
            x, y, w: size, h: size,
            fill: { color: fillHex }, line: { color: fillHex },
            rectRadius: 0.08,
            shadow: { type: "outer", color: "000000", blur: 6, offset: 2, angle: 135, opacity: 0.12 },
        });
        const iconPad = size * 0.22;
        slide.addImage({
            data: iconData,
            x: x + iconPad, y: y + iconPad,
            w: size - 2 * iconPad, h: size - 2 * iconPad,
        });
    }

    // ============================================================
    // SLIDE 1 — TITLE
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.navy };

        // accent block on the right
        s.addShape(pres.shapes.RECTANGLE, {
            x: 7.0, y: 0, w: 3.0, h: 5.625,
            fill: { color: C.deepBlue }, line: { color: C.deepBlue },
        });
        // thin teal vertical bar
        s.addShape(pres.shapes.RECTANGLE, {
            x: 6.95, y: 0, w: 0.05, h: 5.625,
            fill: { color: C.teal }, line: { color: C.teal },
        });

        // Big icon on the right side
        s.addImage({
            data: icSitemap,
            x: 7.7, y: 1.7, w: 1.6, h: 1.6,
        });
        s.addText("MSA Detector", {
            x: 7.0, y: 3.4, w: 3.0, h: 0.4,
            fontSize: 14, fontFace: BODY_FONT, color: C.ice, align: "center", bold: true,
        });

        // Title — single text block with two lines, generous heights to avoid overlap
        s.addText([
            { text: "Detecting Architectural",          options: { breakLine: true } },
            { text: "Anti-Patterns in Microservices" },
        ], {
            x: 0.6, y: 1.4, w: 6.3, h: 1.7,
            fontSize: 34, fontFace: HEADER_FONT, color: "FFFFFF", bold: true,
            paraSpaceAfter: 6, margin: 0,
        });

        // Subtitle
        s.addText("A multi-level static analysis tool for Java/Spring Boot systems", {
            x: 0.6, y: 3.15, w: 6.3, h: 0.4,
            fontSize: 16, fontFace: BODY_FONT, color: C.ice, italic: true, margin: 0,
        });

        // Author block
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.6, y: 4.0, w: 0.04, h: 1.1,
            fill: { color: C.teal }, line: { color: C.teal },
        });
        s.addText([
            { text: "Iacob Andrei",                 options: { fontSize: 16, bold: true, color: "FFFFFF", breakLine: true } },
            { text: "Master's Dissertation Defense", options: { fontSize: 12, color: C.ice, breakLine: true } },
            { text: "Supervisor: Prof. Dr. Simona Motogna", options: { fontSize: 12, color: C.ice, breakLine: true } },
            { text: "Faculty of Mathematics and Computer Science, UBB", options: { fontSize: 11, color: C.ice, italic: true } },
        ], { x: 0.8, y: 4.0, w: 6.0, h: 1.1, fontFace: BODY_FONT, margin: 0 });

        s.addNotes(
            "Open: 'Good morning. My name is Andrei Iacob, and today I'll be defending my dissertation: MSA Detector — a tool for detecting architectural anti-patterns in Java-based microservice systems. My supervisor is Professor Simona Motogna.'\n\n" +
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
        addTitle(s, "Why this matters");

        // Big stat-style opening
        s.addText("Microservices adoption has outpaced tooling", {
            x: 0.5, y: 1.0, w: 9, h: 0.5,
            fontSize: 20, fontFace: BODY_FONT, color: C.text, italic: true, margin: 0,
        });

        // Three problem cards
        const cards = [
            {
                x: 0.5,
                title: "Architectural debt accumulates silently",
                body: "Shared databases, cyclic dependencies, and ill-sized services erode the benefits microservices promise",
                icon: icWarn, color: C.bad,
            },
            {
                x: 3.7,
                title: "Existing tools are single-level",
                body: "Arcan, MicroART, MSANose each cover a slice: code-level OR architectural-level, rarely both",
                icon: icSearch, color: C.deepBlue,
            },
            {
                x: 6.9,
                title: "Findings without evidence don't help",
                body: "Warnings without source snippets, affected services, or remediation guidance get ignored",
                icon: icCode, color: C.warn,
            },
        ];
        cards.forEach(c => {
            // Card background
            s.addShape(pres.shapes.RECTANGLE, {
                x: c.x, y: 1.75, w: 2.6, h: 2.9,
                fill: { color: C.card }, line: { color: C.cardBorder, width: 0.75 },
            });
            // Left accent stripe
            s.addShape(pres.shapes.RECTANGLE, {
                x: c.x, y: 1.75, w: 0.06, h: 2.9,
                fill: { color: c.color }, line: { color: c.color },
            });
            // Icon
            iconChip(s, c.x + 0.25, 1.95, 0.55, c.color, c.icon);
            // Title
            s.addText(c.title, {
                x: c.x + 0.25, y: 2.62, w: 2.25, h: 0.7,
                fontSize: 14, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
            });
            // Body
            s.addText(c.body, {
                x: c.x + 0.25, y: 3.35, w: 2.25, h: 1.2,
                fontSize: 11, fontFace: BODY_FONT, color: C.text, margin: 0,
            });
        });

        // Bottom takeaway
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 4.85, w: 9, h: 0.35,
            fill: { color: C.navy }, line: { color: C.navy },
        });
        s.addText("Goal: actionable, evidence-based detection that bridges code and architecture levels", {
            x: 0.5, y: 4.85, w: 9, h: 0.35,
            fontSize: 12, fontFace: BODY_FONT, color: "FFFFFF", bold: true, align: "center", valign: "middle", margin: 0,
        });

        addFooter(s, 2, 12);

        s.addNotes(
            "Spend ~90 seconds here. This frames why the tool exists.\n\n" +
            "Talking points:\n" +
            "• Microservices have become the default for new large systems. But tooling for architectural quality hasn't kept up.\n" +
            "• Teams ship anti-patterns — shared DBs, cycles, services that are either too small or too big — and they only find out when deployment friction or cascading failures appear in production.\n" +
            "• Existing academic tools each handle a slice: Arcan does code-level cycles, MicroART visualises architecture, MSANose detects some microservice smells. None combine code-level smells with architectural-level analysis.\n" +
            "• Even when something IS detected, the output is usually an abstract warning. Developers don't act on warnings without code evidence and remediation guidance.\n\n" +
            "Pivot to next slide: 'So the goal of this work was a tool that bridges both levels and outputs actionable findings.'"
        );
    }

    // ============================================================
    // SLIDE 3 — OBJECTIVES & CONTRIBUTIONS
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addTitle(s, "Objectives & contributions");

        // Five contributions as icon rows
        const rows = [
            { icon: icCogs,    color: C.deepBlue, title: "Multi-level analysis",        text: "Combines intra-service code smells (DesigniteJava) with inter-service dependency analysis (Spoon + graphs)" },
            { icon: icSitemap, color: C.teal,     title: "Ten anti-patterns detected",  text: "Across four dimensions: service design, communication, data management, deployment & coupling" },
            { icon: icSearch,  color: C.navy,     title: "Automated boundary detection",text: "Three-signal deployability gate (framework entry point, Dockerfile, main method) with confidence levels" },
            { icon: icChart,   color: C.good,     title: "Composite health score",      text: "0–100 with letter grading; decomposes into four interpretable categories; tracks change over time" },
            { icon: icFile,    color: C.warn,     title: "Evidence-based reporting",    text: "Every finding includes source snippets, affected services, and remediation guidance" },
        ];
        const startY = 1.05;
        const rowH   = 0.78;
        rows.forEach((r, i) => {
            const y = startY + i * rowH;
            iconChip(s, 0.5, y + 0.06, 0.55, r.color, r.icon);
            s.addText(r.title, {
                x: 1.25, y: y + 0.04, w: 8.3, h: 0.32,
                fontSize: 15, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
            });
            s.addText(r.text, {
                x: 1.25, y: y + 0.36, w: 8.3, h: 0.42,
                fontSize: 11.5, fontFace: BODY_FONT, color: C.text, margin: 0,
            });
        });

        addFooter(s, 3, 12);

        s.addNotes(
            "Spend ~75 seconds here. Don't read the slide — talk to it.\n\n" +
            "What to actually say:\n" +
            "• 'The work has five contributions. The first is the multi-level analysis itself — using code-level signals like God Class frequency to flag architectural problems like God Service. That bridge between abstraction levels is the central novelty.'\n" +
            "• 'Second, ten anti-patterns across four dimensions — broader coverage than any single existing tool.'\n" +
            "• 'Third, automated microservice boundary detection via a three-signal deployability gate — framework entry points, Dockerfiles, and main methods — with confidence levels. Most existing tools require manual configuration.'\n" +
            "• 'Fourth, a composite health score on 0–100, decomposed into four categories so you can see which dimension is dragging the score down.'\n" +
            "• 'Fifth, evidence-based reporting — every finding includes the actual code that triggered it.'\n\n" +
            "Don't dwell on each one. They'll see the details later. This slide sets expectations for the rest of the talk."
        );
    }

    // ============================================================
    // SLIDE 4 — PIPELINE OVERVIEW
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addTitle(s, "The analysis pipeline");

        // Five-stage flow with icons
        const stages = [
            { x: 0.45, title: "1. Ingest",            sub: "ZIP upload / Git clone",     icon: icServer,  color: C.navy },
            { x: 2.30, title: "2. Detect services",   sub: "Deployability gate (3 signals)", icon: icSearch,  color: C.deepBlue },
            { x: 4.15, title: "3. Intra + Inter",     sub: "DesigniteJava + Spoon",      icon: icCogs,    color: C.teal },
            { x: 6.00, title: "4. Detect patterns",   sub: "10 detectors (Strategy)",    icon: icShield,  color: C.warn },
            { x: 7.85, title: "5. Score & report",    sub: "Health score + evidence",    icon: icChart,   color: C.good },
        ];
        const boxW = 1.7, boxH = 1.6, yBox = 1.2;
        stages.forEach((st, i) => {
            s.addShape(pres.shapes.RECTANGLE, {
                x: st.x, y: yBox, w: boxW, h: boxH,
                fill: { color: C.card }, line: { color: C.cardBorder, width: 0.75 },
            });
            // Top stripe
            s.addShape(pres.shapes.RECTANGLE, {
                x: st.x, y: yBox, w: boxW, h: 0.08,
                fill: { color: st.color }, line: { color: st.color },
            });
            iconChip(s, st.x + (boxW - 0.55) / 2, yBox + 0.25, 0.55, st.color, st.icon);
            s.addText(st.title, {
                x: st.x, y: yBox + 0.95, w: boxW, h: 0.3,
                fontSize: 12, fontFace: HEADER_FONT, color: C.navy, bold: true, align: "center", margin: 0,
            });
            s.addText(st.sub, {
                x: st.x, y: yBox + 1.25, w: boxW, h: 0.3,
                fontSize: 9.5, fontFace: BODY_FONT, color: C.muted, align: "center", margin: 0,
            });

            // Arrows between stages
            if (i < stages.length - 1) {
                s.addImage({
                    data: icArrow,
                    x: st.x + boxW + 0.02, y: yBox + boxH / 2 - 0.13, w: 0.11, h: 0.26,
                });
            }
        });

        // Below: detail panel
        const panelY = 3.2;
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: panelY, w: 9, h: 1.85,
            fill: { color: C.card }, line: { color: C.cardBorder, width: 0.75 },
        });
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: panelY, w: 0.06, h: 1.85,
            fill: { color: C.teal }, line: { color: C.teal },
        });
        s.addText("Key design choices", {
            x: 0.8, y: panelY + 0.1, w: 8.5, h: 0.35,
            fontSize: 14, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });
        s.addText([
            { text: "Async pipeline: long analyses don't block the API; Spring @Async with TransactionSynchronization.afterCommit() ensures consistency",
                options: { bullet: true, fontSize: 11, color: C.text, breakLine: true } },
            { text: "Each detector is a Spring @Component implementing AntiPatternDetector; adding a new detector means no orchestrator changes",
                options: { bullet: true, fontSize: 11, color: C.text, breakLine: true } },
            { text: "Snippets are extracted and persisted as JSON during analysis, so results survive workspace cleanup",
                options: { bullet: true, fontSize: 11, color: C.text } },
        ], { x: 0.9, y: panelY + 0.48, w: 8.4, h: 1.3, fontFace: BODY_FONT, margin: 0, paraSpaceAfter: 2 });

        addFooter(s, 4, 12);

        s.addNotes(
            "Spend ~2 minutes here. This is the spine of the talk — get the pipeline right and everything else follows.\n\n" +
            "Walk left to right:\n" +
            "• Ingest: user uploads a ZIP or pastes a Git URL. The system extracts/clones and creates an analysis job.\n" +
            "• Service detection: scan for build files — pom.xml, build.gradle, build.gradle.kts. Filter out non-service modules using exclusion keywords and aggregator checks. Then apply a three-signal deployability gate: framework entry point (HIGH), Dockerfile (MEDIUM), main() method (LOW). Only candidates passing at least one signal are kept.\n" +
            "• Intra-service analysis runs DesigniteJava as a subprocess for code smells. Inter-service uses Spoon AST to find @FeignClient, RestTemplate, WebClient calls and build the dependency graph.\n" +
            "• Then ten detectors run over that graph + the smells. Each is a Spring component implementing a common interface — Strategy pattern.\n" +
            "• Finally results are assembled, the health score is computed, the dependency graph is serialised to JSON, and everything is persisted.\n\n" +
            "If asked about why async: 'Analyses on large projects like Activiti take several minutes. Blocking the HTTP request would time out. The frontend polls for status.'\n\n" +
            "Don't read the design-choices panel verbatim — mention one or two if you have time."
        );
    }

    // ============================================================
    // SLIDE 5 — GOD SERVICE DETECTOR (DEEP DIVE)
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addTitle(s, "Spotlight: God Service detection");

        // Left: bridging diagram
        s.addText("Multi-level bridge in action", {
            x: 0.5, y: 0.95, w: 4.3, h: 0.35,
            fontSize: 14, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });

        // Code level box
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 1.4, w: 4.3, h: 1.0,
            fill: { color: C.card }, line: { color: C.cardBorder, width: 0.75 },
        });
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 1.4, w: 0.06, h: 1.0,
            fill: { color: C.deepBlue }, line: { color: C.deepBlue },
        });
        s.addText("Code level", {
            x: 0.7, y: 1.45, w: 4, h: 0.3,
            fontSize: 11, fontFace: HEADER_FONT, color: C.muted, bold: true, margin: 0,
        });
        s.addText("God Class smell (per class)", {
            x: 0.7, y: 1.72, w: 4, h: 0.32,
            fontSize: 13, fontFace: BODY_FONT, color: C.text, bold: true, margin: 0,
        });
        s.addText("Detected by DesigniteJava, persisted as CodeSmell entities", {
            x: 0.7, y: 2.02, w: 4, h: 0.35,
            fontSize: 10, fontFace: BODY_FONT, color: C.muted, italic: true, margin: 0,
        });

        // Down arrow
        s.addShape(pres.shapes.LINE, {
            x: 2.65, y: 2.5, w: 0, h: 0.35,
            line: { color: C.teal, width: 2.5, endArrowType: "triangle" },
        });

        // Architectural level box
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 2.95, w: 4.3, h: 1.05,
            fill: { color: C.card }, line: { color: C.cardBorder, width: 0.75 },
        });
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 2.95, w: 0.06, h: 1.05,
            fill: { color: C.teal }, line: { color: C.teal },
        });
        s.addText("Architectural level", {
            x: 0.7, y: 3.0, w: 4, h: 0.3,
            fontSize: 11, fontFace: HEADER_FONT, color: C.muted, bold: true, margin: 0,
        });
        s.addText("God Service (per microservice)", {
            x: 0.7, y: 3.27, w: 4, h: 0.32,
            fontSize: 13, fontFace: BODY_FONT, color: C.text, bold: true, margin: 0,
        });
        s.addText("Flag service if ≥ θ_god God Classes detected (default = 1)", {
            x: 0.7, y: 3.57, w: 4, h: 0.4,
            fontSize: 10, fontFace: BODY_FONT, color: C.muted, italic: true, margin: 0,
        });

        // Right side: fallback panel
        s.addText("Spoon-based fallback", {
            x: 5.1, y: 0.95, w: 4.4, h: 0.35,
            fontSize: 14, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });
        s.addText("If DesigniteJava reports zero God Classes, a Spoon-based structural analysis evaluates each class against six metrics:", {
            x: 5.1, y: 1.32, w: 4.4, h: 0.65,
            fontSize: 11, fontFace: BODY_FONT, color: C.text, margin: 0,
        });
        s.addText([
            { text: "≥ 25 fields",                  options: { bullet: true, fontSize: 11, color: C.text, breakLine: true } },
            { text: "≥ 30 public methods",         options: { bullet: true, fontSize: 11, color: C.text, breakLine: true } },
            { text: "≥ 1000 lines of code",        options: { bullet: true, fontSize: 11, color: C.text, breakLine: true } },
            { text: "≥ 20 distinct import domains",options: { bullet: true, fontSize: 11, color: C.text, breakLine: true } },
            { text: "≥ 12 constructor parameters", options: { bullet: true, fontSize: 11, color: C.text, breakLine: true } },
            { text: "TCC < 0.5  (Bieman & Kang, 1995)", options: { bullet: true, fontSize: 11, color: C.text } },
        ], { x: 5.3, y: 1.95, w: 4.2, h: 2.1, fontFace: BODY_FONT, margin: 0, paraSpaceAfter: 1 });

        s.addShape(pres.shapes.RECTANGLE, {
            x: 5.1, y: 4.15, w: 4.4, h: 0.55,
            fill: { color: C.navy }, line: { color: C.navy },
        });
        s.addText("Flag class as God Class if ≥ 3 of 6 thresholds exceeded", {
            x: 5.1, y: 4.15, w: 4.4, h: 0.55,
            fontSize: 11, fontFace: BODY_FONT, color: "FFFFFF", align: "center", valign: "middle", italic: true, margin: 0,
        });

        // Caption under the diagram
        s.addText("Code-level evidence → architectural-level finding", {
            x: 0.5, y: 4.15, w: 4.3, h: 0.55,
            fontSize: 11, fontFace: BODY_FONT, color: C.muted, italic: true, align: "center", valign: "middle", margin: 0,
        });

        addFooter(s, 5, 12);

        s.addNotes(
            "Spend ~90–120 seconds. This is the slide that demonstrates the multi-level claim.\n\n" +
            "Talking points:\n" +
            "• 'I picked God Service as the example because it's the cleanest illustration of how code-level signals feed an architectural-level finding.'\n" +
            "• Walk through left side: 'DesigniteJava detects God Class smells per class. Those are persisted as CodeSmell entities in the database. The God Service detector then queries that table and flags any microservice containing one or more God Classes.'\n" +
            "• Right side: 'But what if DesigniteJava fails or is disabled? The detector falls back to its own Spoon-based analysis using six structural metrics. A class is flagged if it exceeds at least three.'\n" +
            "• On TCC: 'Tight Class Cohesion measures the fraction of method pairs that share an instance field access. Low TCC means the methods operate on disjoint state — a sign of unrelated responsibilities packed into one class.'\n\n" +
            "EXPECT THIS QUESTION: 'Why threshold = 1? One God Class shouldn't flag a service.'\n" +
            "Answer: 'The default is intentionally sensitive. False positives are cheap to dismiss. False negatives — architectural debt growing undetected — are expensive. The threshold is configurable; teams in production would calibrate it. Even one God Class concentrates responsibilities enough to warrant a look.'"
        );
    }

    // ============================================================
    // SLIDE 6 — DETECTOR CATALOGUE
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addTitle(s, "Ten detectors across four dimensions");

        // Four columns by dimension
        const cols = [
            { x: 0.5,  label: "Service Design",     color: C.deepBlue,
                items: ["Nano Service  •  med", "God Service  •  high"] },
            { x: 2.85, label: "Communication",      color: C.teal,
                items: ["Chatty Service  •  high", "Cyclic Dependency  •  critical", "Hardcoded Endpoints  •  med", "ESB Misuse  •  high"] },
            { x: 5.20, label: "Data Management",    color: C.warn,
                items: ["Shared Database  •  high"] },
            { x: 7.55, label: "Deployment & Coupling", color: C.bad,
                items: ["Distributed Monolith  •  critical", "API Versioning Absence  •  med", "Wrong Cuts  •  high"] },
        ];

        cols.forEach(col => {
            // Column card
            s.addShape(pres.shapes.RECTANGLE, {
                x: col.x, y: 1.1, w: 2.2, h: 3.7,
                fill: { color: C.card }, line: { color: C.cardBorder, width: 0.75 },
            });
            // Top stripe
            s.addShape(pres.shapes.RECTANGLE, {
                x: col.x, y: 1.1, w: 2.2, h: 0.45,
                fill: { color: col.color }, line: { color: col.color },
            });
            s.addText(col.label, {
                x: col.x + 0.1, y: 1.1, w: 2.0, h: 0.45,
                fontSize: 12, fontFace: HEADER_FONT, color: "FFFFFF", bold: true,
                align: "center", valign: "middle", margin: 0,
            });

            // Items
            let y = 1.7;
            col.items.forEach(it => {
                const parts = it.split("  •  ");
                s.addText(parts[0], {
                    x: col.x + 0.15, y: y, w: 2.0, h: 0.28,
                    fontSize: 11, fontFace: BODY_FONT, color: C.text, bold: true, margin: 0,
                });
                s.addText(parts[1], {
                    x: col.x + 0.15, y: y + 0.27, w: 2.0, h: 0.22,
                    fontSize: 9, fontFace: BODY_FONT, color: C.muted, italic: true, margin: 0,
                });
                y += 0.62;
            });
        });

        // Bottom callout
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 4.95, w: 9, h: 0.35,
            fill: { color: C.navy }, line: { color: C.navy },
        });
        s.addText("All detectors are pluggable Strategy components with configurable thresholds", {
            x: 0.5, y: 4.95, w: 9, h: 0.35,
            fontSize: 11, fontFace: BODY_FONT, color: "FFFFFF", italic: true,
            align: "center", valign: "middle", margin: 0,
        });

        addFooter(s, 6, 12);

        s.addNotes(
            "Quick slide — about 45 seconds. Don't read every detector aloud.\n\n" +
            "What to say:\n" +
            "• 'These are the ten detectors, organised by architectural dimension. Each is a Spring component implementing a common interface, so adding a new detector means writing one class — no orchestrator changes.'\n" +
            "• 'Severities feed directly into the health score: critical issues cost 8 points, high cost 5, medium cost 3.'\n" +
            "• 'I won't walk through each detector — they're in Chapter 3 of the thesis. I'll focus on the scoring mechanism next.'\n\n" +
            "If a committee member asks about a specific detector:\n" +
            "• Shared DB — group services by spring.datasource.url, flag groups of size > 1\n" +
            "• Cyclic Dependency — Tarjan's SCC on the dep graph, any SCC > 1 vertex is a cycle\n" +
            "• Chatty Service — call count per edge ≥ 10 OR a Feign interface with ≥ 10 methods\n" +
            "• Hardcoded Endpoints — regex scan of .java for http://, localhost:, IP literals (with comment/test exclusions)\n" +
            "• Distributed Monolith — composite rule on coupling coefficient, connected ratio, shared DB count\n" +
            "• API Versioning — regex /v\\d+[/.] on endpoint paths\n" +
            "• ESB Misuse — caller/callee ratios + volume-based mediator ratio\n" +
            "• Wrong Cuts — Feature Envy count OR bidirectional edges"
        );
    }

    // ============================================================
    // SLIDE 7 — HEALTH SCORE STRUCTURE
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addTitle(s, "Composite health score: four-category decomposition");

        // 4 category cards across the top
        const cats = [
            { x: 0.5,  label: "Anti-Patterns",   budget: "40", color: C.bad,
                desc: "−8/−5/−3/−1 per issue (crit/high/med/low)" },
            { x: 2.85, label: "Code Quality",    budget: "20", color: C.warn,
                desc: "Density-based penalty (smells / KLOC)" },
            { x: 5.20, label: "Architecture",    budget: "25", color: C.deepBlue,
                desc: "Coupling coefficient + cycle count" },
            { x: 7.55, label: "Service Sizing",  budget: "15", color: C.teal,
                desc: "Nano (cap 8) + god (cap 10) penalties" },
        ];
        cats.forEach(c => {
            s.addShape(pres.shapes.RECTANGLE, {
                x: c.x, y: 1.05, w: 2.2, h: 1.55,
                fill: { color: C.card }, line: { color: C.cardBorder, width: 0.75 },
            });
            s.addShape(pres.shapes.RECTANGLE, {
                x: c.x, y: 1.05, w: 2.2, h: 0.08,
                fill: { color: c.color }, line: { color: c.color },
            });
            s.addText(c.budget, {
                x: c.x, y: 1.18, w: 2.2, h: 0.6,
                fontSize: 36, fontFace: HEADER_FONT, color: c.color, bold: true,
                align: "center", margin: 0,
            });
            s.addText(c.label, {
                x: c.x, y: 1.78, w: 2.2, h: 0.32,
                fontSize: 13, fontFace: HEADER_FONT, color: C.navy, bold: true,
                align: "center", margin: 0,
            });
            s.addText(c.desc, {
                x: c.x + 0.1, y: 2.12, w: 2.0, h: 0.5,
                fontSize: 9.5, fontFace: BODY_FONT, color: C.muted, italic: true,
                align: "center", margin: 0,
            });
        });

        // Grade scale below
        s.addText("Letter grade scale", {
            x: 0.5, y: 2.85, w: 9, h: 0.3,
            fontSize: 12, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });
        const grades = [
            { x: 0.5,  l: "A", lo: "≥ 90", color: C.good },
            { x: 2.4,  l: "B", lo: "≥ 80", color: C.teal },
            { x: 4.3,  l: "C", lo: "≥ 65", color: C.deepBlue },
            { x: 6.2,  l: "D", lo: "≥ 50", color: C.warn },
            { x: 8.1,  l: "F", lo: "< 50", color: C.bad },
        ];
        grades.forEach(g => {
            s.addShape(pres.shapes.RECTANGLE, {
                x: g.x, y: 3.2, w: 1.5, h: 0.85,
                fill: { color: g.color }, line: { color: g.color },
            });
            s.addText(g.l, {
                x: g.x, y: 3.22, w: 1.5, h: 0.5,
                fontSize: 26, fontFace: HEADER_FONT, color: "FFFFFF", bold: true,
                align: "center", margin: 0,
            });
            s.addText(g.lo, {
                x: g.x, y: 3.72, w: 1.5, h: 0.28,
                fontSize: 11, fontFace: BODY_FONT, color: "FFFFFF",
                align: "center", margin: 0,
            });
        });

        // Bottom rationale
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 4.3, w: 9, h: 0.85,
            fill: { color: C.card }, line: { color: C.cardBorder, width: 0.75 },
        });
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 4.3, w: 0.06, h: 0.85,
            fill: { color: C.teal }, line: { color: C.teal },
        });
        s.addText("Why decompose?", {
            x: 0.75, y: 4.35, w: 8.5, h: 0.3,
            fontSize: 12, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });
        s.addText("A single number tells teams whether quality is improving; per-category breakdown tells them WHICH dimension needs attention; both views are exposed via the analysis-diff feature for tracking over time", {
            x: 0.75, y: 4.62, w: 8.5, h: 0.5,
            fontSize: 10.5, fontFace: BODY_FONT, color: C.text, margin: 0,
        });

        addFooter(s, 7, 12);

        s.addNotes(
            "Spend ~75 seconds.\n\n" +
            "Talking points:\n" +
            "• 'The composite score is a sum of four independent categories. Each has its own budget — Anti-Patterns is 40 points, Code Quality 20, Architecture 25, Service Sizing 15. Total: 100.'\n" +
            "• 'Each category clamps at zero, so penalties can't bleed across categories. That's intentional: a code-quality-heavy project shouldn't have its architectural score dragged down.'\n" +
            "• 'Importantly, the breakdown is preserved in the UI. A developer doesn't just see 45/100 — they see WHICH category is dragging the score down, with itemised deductions.'\n" +
            "• 'The diff feature compares successive analyses, so a team that fixes a shared DB anti-pattern can see exactly how many points that bought them.'\n\n" +
            "EXPECT THIS QUESTION: 'Doesn't clamping at zero mean you lose discrimination between moderately bad and very bad projects?'\n" +
            "Answer: 'Yes — that's a known limitation of the category-cap approach, which I note in the threats to validity. A team with 13 nano services and one with 50 would both see Anti-Patterns at zero. The Service Sizing breakdown still preserves the per-issue counts, so the full picture is still visible to the user. A scaled, density-style penalty in the AP category — similar to the Code Quality category — would address this in future work.'"
        );
    }

    // ============================================================
    // SLIDE 8 — WORKED EXAMPLE: microservice-recruit
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addTitle(s, "Worked example: microservice-recruit (40 / F)");

        // Left: the math
        s.addText("Anti-Pattern findings", {
            x: 0.5, y: 0.95, w: 4.5, h: 0.32,
            fontSize: 13, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });
        const findings = [
            ["6×", "API Versioning Absence", "−3 each = −18"],
            ["2×", "ESB Misuse",             "−5 each = −10"],
            ["2×", "Wrong Cuts",             "−5 each = −10"],
            ["1×", "Cyclic Dependency",      "−8 = −8"],
            ["1×", "Hardcoded Endpoints",    "−3 = −3"],
        ];
        findings.forEach((row, i) => {
            const y = 1.18 + i * 0.345;
            s.addShape(pres.shapes.RECTANGLE, {
                x: 0.5, y: y, w: 4.5, h: 0.31,
                fill: { color: C.card }, line: { color: C.cardBorder, width: 0.5 },
            });
            s.addText(row[0], {
                x: 0.55, y: y, w: 0.6, h: 0.31,
                fontSize: 12, fontFace: HEADER_FONT, color: C.bad, bold: true,
                align: "center", valign: "middle", margin: 0,
            });
            s.addText(row[1], {
                x: 1.2, y: y, w: 2.3, h: 0.31,
                fontSize: 11, fontFace: BODY_FONT, color: C.text, valign: "middle", margin: 0,
            });
            s.addText(row[2], {
                x: 3.5, y: y, w: 1.45, h: 0.31,
                fontSize: 10, fontFace: BODY_FONT, color: C.muted, italic: true,
                align: "right", valign: "middle", margin: 0,
            });
        });
        // Total
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 2.95, w: 4.5, h: 0.45,
            fill: { color: C.navy }, line: { color: C.navy },
        });
        s.addText("Total penalty: 49   →   AP clamped at 0", {
            x: 0.5, y: 2.95, w: 4.5, h: 0.45,
            fontSize: 12, fontFace: BODY_FONT, color: "FFFFFF", bold: true,
            align: "center", valign: "middle", margin: 0,
        });

        // Right: category bars
        s.addText("Category contributions", {
            x: 5.3, y: 0.95, w: 4.2, h: 0.32,
            fontSize: 13, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });

        const bars = [
            { label: "Anti-Patterns",   max: 40, got: 0,  color: C.bad },
            { label: "Code Quality",    max: 20, got: 12, color: C.warn },
            { label: "Architecture",    max: 25, got: 16, color: C.deepBlue },
            { label: "Service Sizing",  max: 15, got: 12, color: C.teal },
        ];
        const barXLabel = 5.3, barX = 6.7, barW = 2.6, barH = 0.28;
        bars.forEach((b, i) => {
            const y = 1.35 + i * 0.55;
            s.addText(b.label, {
                x: barXLabel, y: y - 0.05, w: 1.4, h: 0.3,
                fontSize: 10.5, fontFace: BODY_FONT, color: C.text, valign: "middle", margin: 0,
            });
            // Track
            s.addShape(pres.shapes.RECTANGLE, {
                x: barX, y: y, w: barW, h: barH,
                fill: { color: C.cardBorder }, line: { color: C.cardBorder },
            });
            // Fill
            if (b.got > 0) {
                s.addShape(pres.shapes.RECTANGLE, {
                    x: barX, y: y, w: barW * (b.got / b.max), h: barH,
                    fill: { color: b.color }, line: { color: b.color },
                });
            }
            // Score text
            s.addText(`${b.got} / ${b.max}`, {
                x: barX + barW + 0.1, y: y - 0.04, w: 0.6, h: 0.35,
                fontSize: 10, fontFace: BODY_FONT, color: C.muted, bold: true, valign: "middle", margin: 0,
            });
        });

        // Final score circle
        s.addShape(pres.shapes.OVAL, {
            x: 6.4, y: 3.75, w: 1.4, h: 1.4,
            fill: { color: C.bad }, line: { color: C.bad },
        });
        s.addText("40", {
            x: 6.4, y: 3.8, w: 1.4, h: 0.9,
            fontSize: 36, fontFace: HEADER_FONT, color: "FFFFFF", bold: true,
            align: "center", valign: "middle", margin: 0,
        });
        s.addText("Grade F", {
            x: 6.4, y: 4.55, w: 1.4, h: 0.4,
            fontSize: 11, fontFace: BODY_FONT, color: "FFFFFF", bold: true,
            align: "center", margin: 0,
        });

        // Caption
        s.addText("Final score: 40 / 100  (F)", {
            x: 0.5, y: 3.55, w: 4.5, h: 0.4,
            fontSize: 14, fontFace: HEADER_FONT, color: C.navy, bold: true,
            align: "center", valign: "middle", margin: 0,
        });
        s.addText("Deductions spread across all four categories: the anti-pattern load (penalty 49) clamps AP to 0; Architecture loses 9 to dependency-graph coupling; Code Quality 8 to smell density; Service Sizing 3 to one nano service", {
            x: 0.5, y: 3.95, w: 4.5, h: 1.0,
            fontSize: 10, fontFace: BODY_FONT, color: C.muted, italic: true, margin: 0,
        });

        addFooter(s, 8, 12);

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
            "Answer: 'Code Quality is scored on smell density per 1000 LOC, not raw count, so a codebase isn't punished just for its size. The category is deliberately bounded so that architectural anti-patterns remain the dominant signal in the overall score.'"
        );
    }

    // ============================================================
    // SLIDE 9 — EVALUATION RESULTS
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.bg };
        addTitle(s, "Evaluation: eleven open-source projects");

        // Top: results bar chart (project vs score)
        s.addChart(pres.charts.BAR, [{
            name: "Health score",
            labels: ["MicroSocial", "ftgo", "PiggyMetrics", "Site-Where", "RuoYi", "design-pat.", "mall-swarm", "NetworkDisk", "piomin", "Train-Ticket", "recruit"],
            values: [83, 75, 71, 69, 68, 60, 53, 52, 51, 40, 40],
        }], {
            x: 0.4, y: 0.95, w: 5.9, h: 3.0,
            barDir: "col",
            chartColors: [C.teal],
            chartArea:   { fill: { color: C.bg } },
            catAxisLabelColor: C.muted,
            valAxisLabelColor: C.muted,
            catAxisLabelFontSize: 7,
            valAxisLabelFontSize: 9,
            valAxisMinVal: 0, valAxisMaxVal: 100,
            valGridLine: { color: C.cardBorder, size: 0.5 },
            catGridLine: { style: "none" },
            showValue: true, dataLabelPosition: "outEnd",
            dataLabelColor: C.text, dataLabelFontSize: 9,
            showLegend: false,
            showTitle: true, title: "Health scores by project (0–100)",
            titleColor: C.navy, titleFontSize: 13, titleFontFace: HEADER_FONT,
        });

        // Project table on the right
        const tx = 6.6;
        s.addText("Dataset scale  (services / LOC)", {
            x: tx, y: 0.95, w: 3.0, h: 0.3,
            fontSize: 12, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });
        const rows = [
            ["MicroSocial",   "4",  "2k"],
            ["design-pat.",   "16", "19k"],
            ["Site-Where",    "15", "74k"],
            ["Train-Ticket",  "41", "36k"],
            ["NetworkDisk",   "10", "7k"],
            ["recruit",       "8",  "8k"],
            ["ftgo",          "7",  "11k"],
            ["mall-swarm",    "7",  "86k"],
            ["RuoYi",         "8",  "27k"],
            ["PiggyMetrics",  "8",  "4.5k"],
            ["piomin",        "6",  "1.3k"],
            ["Total",         "130", "274k"],
        ];
        rows.forEach((r, i) => {
            const y = 1.30 + i * 0.24;
            const isTotal = i === rows.length - 1;
            s.addShape(pres.shapes.RECTANGLE, {
                x: tx, y: y, w: 3.0, h: 0.21,
                fill: { color: isTotal ? C.navy : C.card },
                line: { color: C.cardBorder, width: 0.5 },
            });
            s.addText(r[0], {
                x: tx + 0.1, y: y, w: 1.4, h: 0.21,
                fontSize: 8.5, fontFace: BODY_FONT,
                color: isTotal ? "FFFFFF" : C.text, bold: isTotal, valign: "middle", margin: 0,
            });
            s.addText(r[1], {
                x: tx + 1.5, y: y, w: 0.5, h: 0.21,
                fontSize: 8.5, fontFace: BODY_FONT,
                color: isTotal ? "FFFFFF" : C.text, bold: isTotal,
                align: "center", valign: "middle", margin: 0,
            });
            s.addText(r[2], {
                x: tx + 2.0, y: y, w: 0.95, h: 0.21,
                fontSize: 8.5, fontFace: BODY_FONT,
                color: isTotal ? "FFFFFF" : C.text, bold: isTotal,
                align: "right", valign: "middle", margin: 0,
            });
        });

        // Bottom takeaways
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.4, y: 4.1, w: 9.2, h: 1.0,
            fill: { color: C.card }, line: { color: C.cardBorder, width: 0.5 },
        });
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.4, y: 4.1, w: 0.06, h: 1.0,
            fill: { color: C.teal }, line: { color: C.teal },
        });
        s.addText("Findings", {
            x: 0.65, y: 4.15, w: 9, h: 0.3,
            fontSize: 12, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });
        s.addText([
            { text: "126 anti-pattern instances across 9 types; Hardcoded Endpoints (43) and API Versioning Absence (37) dominate",
                options: { bullet: true, fontSize: 11, color: C.text, breakLine: true } },
            { text: "PiggyMetrics and piomin broadened ESB Misuse and Nano Service coverage; Cyclic Dependency and Wrong Cuts detected in microservice-recruit",
                options: { bullet: true, fontSize: 11, color: C.text } },
        ], { x: 0.8, y: 4.45, w: 8.7, h: 0.65, fontFace: BODY_FONT, margin: 0, paraSpaceAfter: 2 });

        addFooter(s, 9, 12);

        s.addNotes(
            "Spend ~90 seconds.\n\n" +
            "Talking points:\n" +
            "• 'I evaluated on eleven open-source Spring Boot projects, spanning from piomin at 1.3k LOC to mall-swarm at 86k LOC. 130 microservices, about 274k LOC total.'\n" +
            "• 'Health scores ranged from 40 to 83 — the scoring mechanism produces meaningful spread.'\n" +
            "• 'The dominant findings are configuration and sizing issues: 43 hardcoded endpoints, 37 API versioning absences, 23 nano services. Cyclic Dependency and Wrong Cuts were detected in microservice-recruit. Adding PiggyMetrics and piomin broadened ESB Misuse and Nano Service coverage.'\n" +
            "• '126 total anti-pattern instances across 9 distinct types. Only Distributed Monolith was not detected — plausible given these projects' topologies.'\n\n" +
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

        // Teal vertical bar on the left
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0, y: 0, w: 0.15, h: 5.625,
            fill: { color: C.teal }, line: { color: C.teal },
        });

        // Big "Live Demo" headline
        s.addText("Live demo", {
            x: 0.8, y: 0.85, w: 8.5, h: 0.9,
            fontSize: 54, fontFace: HEADER_FONT, color: "FFFFFF", bold: true, margin: 0,
        });
        s.addText("Cloning and analysing a microservice project in real time", {
            x: 0.8, y: 1.85, w: 8.5, h: 0.45,
            fontSize: 18, fontFace: BODY_FONT, color: C.ice, italic: true, margin: 0,
        });

        // Four-step walk-through card
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.8, y: 2.7, w: 8.5, h: 2.35,
            fill: { color: C.deepBlue }, line: { color: C.deepBlue },
        });

        const steps = [
            { n: "1", title: "Clone a small repo",
                sub: "Paste a public Git URL, submit, watch the pipeline progress" },
            { n: "2", title: "Open the results dashboard",
                sub: "Health score gauge, four-category breakdown, summary metrics" },
            { n: "3", title: "Drill into a finding",
                sub: "Expand an anti-pattern card to see source-code evidence and remediation" },
            { n: "4", title: "Explore the dependency graph",
                sub: "Interactive Cytoscape view of inter-service edges" },
        ];

        const stepY0 = 2.85;
        const stepH  = 0.55;
        steps.forEach((step, i) => {
            const y = stepY0 + i * stepH;
            // Number badge
            s.addShape(pres.shapes.OVAL, {
                x: 1.0, y: y + 0.05, w: 0.42, h: 0.42,
                fill: { color: C.teal }, line: { color: C.teal },
            });
            s.addText(step.n, {
                x: 1.0, y: y + 0.05, w: 0.42, h: 0.42,
                fontSize: 16, fontFace: HEADER_FONT, color: "FFFFFF", bold: true,
                align: "center", valign: "middle", margin: 0,
            });
            // Title
            s.addText(step.title, {
                x: 1.6, y: y + 0.02, w: 7.5, h: 0.28,
                fontSize: 14, fontFace: HEADER_FONT, color: "FFFFFF", bold: true, margin: 0,
            });
            // Subtitle
            s.addText(step.sub, {
                x: 1.6, y: y + 0.28, w: 7.5, h: 0.26,
                fontSize: 11, fontFace: BODY_FONT, color: C.ice, italic: true, margin: 0,
            });
        });

        // Footer
        s.addText("MSA Detector  •  Iacob Andrei  •  UBB FMI", {
            x: 0.8, y: 5.30, w: 6, h: 0.25,
            fontSize: 9, fontFace: BODY_FONT, color: C.ice,
        });
        s.addText("10 / 12", {
            x: 8.4, y: 5.30, w: 0.8, h: 0.25,
            fontSize: 9, fontFace: BODY_FONT, color: C.ice, align: "right",
        });

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
        addTitle(s, "Limitations & future work");

        // Two columns
        // Left: limitations
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 1.0, w: 4.4, h: 4.0,
            fill: { color: C.card }, line: { color: C.cardBorder, width: 0.75 },
        });
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.5, y: 1.0, w: 0.06, h: 4.0,
            fill: { color: C.warn }, line: { color: C.warn },
        });
        iconChip(s, 0.7, 1.15, 0.45, C.warn, icWarn);
        s.addText("Limitations", {
            x: 1.25, y: 1.16, w: 3.5, h: 0.4,
            fontSize: 16, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });
        s.addText([
            { text: "Java / Spring Boot only; polyglot systems not supported",
                options: { bullet: true, fontSize: 11.5, color: C.text, breakLine: true } },
            { text: "Static analysis only; no runtime call frequency data",
                options: { bullet: true, fontSize: 11.5, color: C.text, breakLine: true } },
            { text: "Service detection deployability gate may miss services without framework annotations, Dockerfile, or main method",
                options: { bullet: true, fontSize: 11.5, color: C.text, breakLine: true } },
            { text: "Dynamic URLs (e.g. Spring Cloud Config) are not resolved",
                options: { bullet: true, fontSize: 11.5, color: C.text, breakLine: true } },
            { text: "Health score weights chosen by engineering judgement, not empirically calibrated",
                options: { bullet: true, fontSize: 11.5, color: C.text, breakLine: true } },
            { text: "Evaluation on 11 open-source projects; not a statistical claim",
                options: { bullet: true, fontSize: 11.5, color: C.text } },
        ], { x: 0.8, y: 1.7, w: 4.0, h: 3.2, fontFace: BODY_FONT, margin: 0, paraSpaceAfter: 4 });

        // Right: future work
        s.addShape(pres.shapes.RECTANGLE, {
            x: 5.1, y: 1.0, w: 4.4, h: 4.0,
            fill: { color: C.card }, line: { color: C.cardBorder, width: 0.75 },
        });
        s.addShape(pres.shapes.RECTANGLE, {
            x: 5.1, y: 1.0, w: 0.06, h: 4.0,
            fill: { color: C.teal }, line: { color: C.teal },
        });
        iconChip(s, 5.3, 1.15, 0.45, C.teal, icBulb);
        s.addText("Future work", {
            x: 5.85, y: 1.16, w: 3.5, h: 0.4,
            fontSize: 16, fontFace: HEADER_FONT, color: C.navy, bold: true, margin: 0,
        });
        s.addText([
            { text: "Multi-language support (Python, Go, .NET, Node.js) via language-agnostic dependency graphs from Docker Compose / Kubernetes manifests",
                options: { bullet: true, fontSize: 11.5, color: C.text, breakLine: true } },
            { text: "Extend anti-pattern catalogue (timeout misconfiguration, missing API gateway)",
                options: { bullet: true, fontSize: 11.5, color: C.text, breakLine: true } },
            { text: "ML-based detection: classifiers on labelled datasets or graph neural networks on the dependency graph",
                options: { bullet: true, fontSize: 11.5, color: C.text, breakLine: true } },
            { text: "Empirical threshold calibration across an industrial corpus",
                options: { bullet: true, fontSize: 11.5, color: C.text } },
        ], { x: 5.4, y: 1.7, w: 4.0, h: 3.2, fontFace: BODY_FONT, margin: 0, paraSpaceAfter: 4 });

        addFooter(s, 11, 12);

        s.addNotes(
            "Spend ~60 seconds. This is your honesty slide — committee will respect frankness here.\n\n" +
            "Talking points:\n" +
            "• 'The tool has real limitations. The biggest is the Java/Spring Boot restriction — real-world systems are polyglot, and the current tool can't analyse non-Java services.'\n" +
            "• 'Static analysis trades precision for applicability. A distributed tracing approach would give exact runtime call counts but requires the system to be deployed and instrumented — which prevents the tool from running on a fresh checkout.'\n" +
            "• 'The health score weights aren't empirically validated. They're engineering judgement informed by the literature. Different teams might want different weightings.'\n" +
            "• 'Future work in priority order: multi-language support, then ML-based detection, then empirical calibration of thresholds.'\n\n" +
            "Don't get defensive about limitations. Acknowledging them is more impressive than glossing over them."
        );
    }

    // ============================================================
    // SLIDE 12 — CLOSING
    // ============================================================
    {
        const s = pres.addSlide();
        s.background = { color: C.navy };

        // Decorative element
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0, y: 0, w: 0.15, h: 5.625,
            fill: { color: C.teal }, line: { color: C.teal },
        });

        s.addText("Thank you", {
            x: 0.8, y: 1.0, w: 8.5, h: 0.8,
            fontSize: 48, fontFace: HEADER_FONT, color: "FFFFFF", bold: true, margin: 0,
        });
        s.addText("Questions & discussion", {
            x: 0.8, y: 1.9, w: 8.5, h: 0.45,
            fontSize: 20, fontFace: BODY_FONT, color: C.ice, italic: true, margin: 0,
        });

        // Key takeaways
        s.addShape(pres.shapes.RECTANGLE, {
            x: 0.8, y: 2.85, w: 8.5, h: 1.95,
            fill: { color: C.deepBlue }, line: { color: C.deepBlue },
        });
        s.addText("Recap", {
            x: 1.0, y: 2.95, w: 8.2, h: 0.35,
            fontSize: 14, fontFace: HEADER_FONT, color: C.ice, bold: true, margin: 0,
        });
        s.addText([
            { text: "Multi-level static analysis combining code smells and architectural metrics",
                options: { bullet: true, fontSize: 13, color: "FFFFFF", breakLine: true } },
            { text: "Ten anti-patterns, configurable thresholds, evidence-based reporting",
                options: { bullet: true, fontSize: 13, color: "FFFFFF", breakLine: true } },
            { text: "Composite health score with four-category decomposition and analysis diff",
                options: { bullet: true, fontSize: 13, color: "FFFFFF", breakLine: true } },
            { text: "Open-source web application, ready for CI/CD integration",
                options: { bullet: true, fontSize: 13, color: "FFFFFF" } },
        ], { x: 1.2, y: 3.3, w: 8.0, h: 1.5, fontFace: BODY_FONT, margin: 0, paraSpaceAfter: 2 });

        // Footer line
        s.addText("Iacob Andrei  •  Supervisor: Prof. Dr. Simona Motogna  •  UBB FMI", {
            x: 0.8, y: 5.0, w: 8.5, h: 0.3,
            fontSize: 11, fontFace: BODY_FONT, color: C.ice, margin: 0,
        });

        s.addNotes(
            "Quick close — about 20 seconds.\n\n" +
            "Suggested wording:\n" +
            "'To recap: the MSA Detector is a multi-level static analysis tool for Java/Spring Boot microservices, with ten anti-pattern detectors, a composite health score, and evidence-based reporting. The full implementation, evaluation data, and dissertation are available. Thank you — I'm happy to take questions.'\n\n" +
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
    const outPath = __dirname + "/MSA_Detector_Defense.pptx";
    await pres.writeFile({ fileName: outPath });
    console.log("Wrote:", outPath);
})();