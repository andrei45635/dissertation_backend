# Multi-Language Support — Analysis & Roadmap

> Assessment of what it takes to extend MSA Detector beyond Java/Spring Boot to support TypeScript/JS, Python, C#, and C++.

---

## Table of Contents

- [Current State](#current-state)
- [What Would Need to Change Per Language](#what-would-need-to-change-per-language)
  - [1. Service Detection](#1-service-detection)
  - [2. Endpoint Detection](#2-endpoint-detection)
  - [3. Inter-Service Call Detection](#3-inter-service-call-detection)
  - [4. Config Parsing (Datasource Detection)](#4-config-parsing-datasource-detection)
  - [5. Code Smell Detection](#5-code-smell-detection)
  - [6. Anti-Pattern Detectors](#6-anti-pattern-detectors)
- [Effort Estimation](#effort-estimation)
- [Recommended Approach](#recommended-approach)

---

## Current State

The entire analysis pipeline is **deeply tied to Java/Spring**:

| Component | Java-Specific Assumptions |
|-----------|--------------------------|
| **MicroserviceDetector** | Only looks for `pom.xml` and `build.gradle` |
| **DependencyGraphBuilder** | Uses **Spoon** (Java-only AST parser) for endpoint detection, scans `src/main/java`, looks for `@RestController`, `@FeignClient`, `RestTemplate`, `WebClient` |
| **DependencyGraphBuilder** | Parses `application.yml` / `application.properties` for Spring datasource config |
| **DesigniteService** | Runs **DesigniteJava** — a Java-only code smell detector |
| **Endpoint entity** | Assumes Spring MVC annotations (`@GetMapping`, etc.) |
| **AntiPatternDetectors** | Rely on Java-specific code smells ("God Class", "Feature Envy") from DesigniteJava |

---

## What Would Need to Change Per Language

### 1. Service Detection

Each language/framework uses different project markers:

| Language | Build Files / Markers |
|----------|----------------------|
| Java | `pom.xml`, `build.gradle` |
| TypeScript/JS | `package.json` (check for Express, NestJS, Fastify, etc.) |
| Python | `requirements.txt`, `Pipfile`, `pyproject.toml` (check for Flask, FastAPI, Django) |
| C# (.NET) | `*.csproj`, `*.sln` |
| C++ | `CMakeLists.txt`, `Makefile` |

**This is the easiest part** — just add more file patterns. Moderate effort.

---

### 2. Endpoint Detection

This is the **hardest part**. Currently uses Spoon (Java AST). Each framework has completely different routing:

| Framework | How Endpoints Are Defined |
|-----------|--------------------------|
| Spring Boot | `@GetMapping("/users")` annotations |
| NestJS | `@Get('/users')` decorators |
| Express.js | `app.get('/users', handler)` |
| FastAPI | `@app.get("/users")` decorators |
| Flask | `@app.route("/users")` decorators |
| ASP.NET | `[HttpGet("users")]` attributes or `MapGet()` |
| C++ (no standard) | No convention — manual routing |

You'd need a **separate AST parser per language**:

- **Java** → Spoon (already done)
- **TypeScript/JS** → TypeScript compiler API, or tree-sitter, or ts-morph
- **Python** → Python `ast` module (would need to shell out to Python) or tree-sitter
- **C#** → Roslyn (would need .NET SDK) or tree-sitter
- **C++** → Realistically impractical — no standard web framework

**Very high effort.** Each language is essentially a separate project.

---

### 3. Inter-Service Call Detection

Same problem — each language has different HTTP client patterns:

| Language | HTTP Client Patterns |
|----------|---------------------|
| Java | `RestTemplate`, `WebClient`, `@FeignClient` |
| JS/TS | `axios`, `fetch`, `HttpModule` (NestJS), `got` |
| Python | `requests`, `httpx`, `aiohttp` |
| C# | `HttpClient`, `RestSharp` |

---

### 4. Config Parsing (Datasource Detection)

| Language | Config Format |
|----------|--------------|
| Java/Spring | `application.yml`, `application.properties` |
| Node.js | `.env`, `config.js`, `docker-compose.yml` |
| Python | `.env`, `settings.py`, `config.yaml` |
| .NET | `appsettings.json` |

Moderate effort — mostly regex/YAML/JSON parsing.

---

### 5. Code Smell Detection

**DesigniteJava only works on Java.** Alternatives:

| Language | Tool | Maturity |
|----------|------|----------|
| Java | DesigniteJava ✅ | Done |
| Python | Pylint, Radon, Wily | Good — but different output format |
| JS/TS | ESLint with complexity rules, SonarQube | Good |
| C# | NDepend, SonarQube | Good but commercial |
| C++ | cppcheck, SonarQube | Limited for architectural smells |

Each tool has a completely different output format — you'd need a parser adapter for each.

---

### 6. Anti-Pattern Detectors

Some detectors are **language-agnostic** (work on graph/metrics, not source code):

| Detector | Language-Agnostic? | Notes |
|----------|-------------------|-------|
| `CyclicDependencyDetector` | ✅ Yes | Works on dependency graph |
| `SharedDatabaseDetector` | ✅ Yes | Works on datasource URLs |
| `NanoServiceDetector` | ✅ Yes | Works on LOC/endpoint counts |
| `ChattyServiceDetector` | ✅ Yes | Works on call counts |
| `DistributedMonolithDetector` | ✅ Yes | Works on coupling metrics |
| `EsbMisuseDetector` | ✅ Yes | Works on dependency graph |
| `WrongCutsDetector` | ⚠️ Partially | Graph part is agnostic, Feature Envy part needs code smell tool |
| `GodServiceDetector` | ❌ No | Relies on "God Class" from DesigniteJava |
| `HardcodedEndpointDetector` | ⚠️ Partially | Regex-based, mostly works but patterns differ |
| `ApiVersioningDetector` | ⚠️ Partially | Works on endpoint paths (agnostic) but detection needs endpoints first |

---

## Effort Estimation

| Approach | Effort | Coverage |
|----------|--------|----------|
| **Add just service detection** (find `package.json`, `.csproj`, etc.) | Low — 1–2 days | Services found, but no endpoints/dependencies/smells |
| **Add service detection + config parsing** | Medium — 1 week | Services + shared DB detection works |
| **Full support for one more language (e.g. TypeScript/NestJS)** | High — 2–3 weeks | Near-complete analysis for that framework |
| **Full support for all 5 languages** | Very high — 2–3 months | Complete multi-language analysis |

---

## Recommended Approach

If you want to pursue this, the most pragmatic path:

### Phase 1 — Immediate (1–2 days)

Extend `MicroserviceDetector` to recognise `package.json`, `.csproj`, `requirements.txt`, `CMakeLists.txt`. This alone fixes issues like the SOA_project repo (services get detected, LOC counted, basic metrics work).

### Phase 2 — Short-term (3–5 days)

Add a **generic regex-based endpoint scanner** that looks for common route patterns across languages (`app.get(`, `@Get(`, `@app.route(`, `[HttpGet(`, etc.) without needing full AST parsing. Rough but functional.

### Phase 3 — Medium-term (2–3 weeks)

Add proper AST-based analysis for **one** additional language. TypeScript/NestJS is the best candidate since it's architecturally very similar to Spring Boot with decorators.

### Skip: C++

There's no standard web framework for C++, so microservice anti-pattern detection doesn't really apply to it.

---

### Bottom Line

Full multi-language support is essentially building 4 more analysis engines. The architecture supports it (the detectors that work on the dependency graph are already language-agnostic), but the data collection layer (endpoints, calls, config) needs to be reimplemented per language.

**The most practical scope for a dissertation would be Java + one more language (TypeScript/NestJS).**

