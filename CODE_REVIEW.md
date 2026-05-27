# In-Depth Code Review — MSA Detector

**Reviewer:** Senior Software Engineer / Architect  
**Date:** 2025-03-27  
**Scope:** Full codebase — Spring Boot backend for microservice anti-pattern detection  
**Spring Boot version:** 4.0.1 | **Java target:** 25

---

## Table of Contents

1. [Critical / Show-Stopper Bugs](#1-critical--show-stopper-bugs)
2. [Major Bugs & Correctness Issues](#2-major-bugs--correctness-issues)
3. [Security Concerns](#3-security-concerns)
4. [Architectural & Design Issues](#4-architectural--design-issues)
5. [Concurrency & Transaction Issues](#5-concurrency--transaction-issues)
6. [Performance Concerns](#6-performance-concerns)
7. [Code Quality & Maintainability](#7-code-quality--maintainability)
8. [Configuration & Deployment Issues](#8-configuration--deployment-issues)
9. [Testing](#9-testing)
10. [Minor / Stylistic Observations](#10-minor--stylistic-observations)
11. [Summary Verdict](#11-summary-verdict)

---

## 1. Critical / Show-Stopper Bugs

### ~~🔴 1.1 `MsaDetectorApplication.main()` is package-private — application will not boot~~ ✅ RETRACTED

**File:** `MsaDetectorApplication.java:12`

~~The `main` method is missing the `public` modifier.~~

**Correction:** Spring Boot's fat JAR launcher (`JarLauncher`) invokes the main class via reflection, and the `start-class` property is set in `pom.xml`. Reflection can access package-private methods. Additionally, `spring-boot:run` also uses reflection. The application boots correctly despite the missing `public` modifier. This is **not a bug** in this deployment context.

> **Style note:** While functional, adding `public` would be conventional and avoids confusion for developers unfamiliar with Spring Boot's launcher internals.

---

### ~~🔴 1.2 Zip Slip vulnerability~~ ✅ RETRACTED

**File:** `ProjectService.java:310-320` — `extractZip()`

```java
Path entryPath = projectDir.resolve(entry.getName()).normalize();

if (!entryPath.startsWith(projectDir)) {
    throw new InvalidFileException("Invalid zip entry: " + entry.getName());
}
```

**Correction:** The Zip Slip protection is effective. `projectDir` is constructed as `workspaceDir.resolve(projectId.toString())` where `projectId` is a database-generated `Long` — it cannot contain `..` segments or special characters. The `.normalize()` on `entryPath` collapses any `..` from the zip entry name, and the `.startsWith(projectDir)` check correctly rejects traversal attempts. This is a standard and correct guard.

---

### ~~🟡 1.3 `AnalysisWorker.processJob()` — no cleanup on failure~~ ✅ RETRACTED

**File:** `AnalysisWorker.java:61-99`

~~The entire `processJob()` method runs without a transaction, causing silent data loss.~~

**Correction:** The absence of a wrapping `@Transactional` is **intentional and correct** for a long-running analysis pipeline. Holding a single transaction open for potentially 30+ minutes would exhaust the connection pool and risk timeouts. The design uses:
- Individual auto-commit saves for each microservice and code smell
- `JobProgressUpdater` with `Propagation.REQUIRES_NEW` so progress is immediately visible to polling clients
- The `catch` block marks the job as `FAILED` via `progressUpdater.failJob()`

The original "remaining concern" about orphaned data on failure is also a non-issue: partially-saved microservices are associated with the project entity and are cleaned up on re-analysis (via `project.getMicroservices().clear()` with `orphanRemoval = true`) or on project deletion (via cascade delete). There is no data integrity risk here.

---

## 2. Major Bugs & Correctness Issues

### ~~🟠 2.1 `@Async` self-invocation bypass~~ ✅ RETRACTED

This was noted as "not yet broken" in the original review. Since `processJob()` is only called from `ProjectService` (a different Spring bean), the `@Async` proxy is always used correctly. Removing this as a finding — it is a general Spring knowledge note, not a project issue.

### 🟠 2.2 `DependencyGraphResponse` inner records are package-private — JSON deserialization fails

**File:** `DependencyGraphResponse.java:10-21`

```java
record GraphNodeResponse(...) {}
record GraphEdgeResponse(...) {}
```

These inner records are declared without `public`. `JobService.parseDependencyGraph()` deserializes JSON into `DependencyGraphResponse` using Jackson. Jackson needs public access to `GraphNodeResponse` and `GraphEdgeResponse` to instantiate them during deserialization. In some configurations this causes:

```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: Cannot construct instance of GraphNodeResponse
```

They should be `public record` or nested inside `DependencyGraphResponse`.

### 🟠 2.3 Flyway disabled but `FlywayConfig` manually runs migrations — conflict risk

**File:** `application.yml:13` and `FlywayConfig.java`

```yaml
spring:
  flyway:
    enabled: false
```

Spring Boot auto-configured Flyway is disabled, but `FlywayConfig` runs Flyway manually in a `@PostConstruct`. This creates a race: Spring Boot might still try to create a `Flyway` bean (depending on the version), and you now have **two Flyway instances** with potentially different configurations. With Spring Boot 4.0, the behavior of `spring.flyway.enabled=false` may differ from 3.x. This is fragile and should be one or the other.

### 🟠 2.4 `JacksonConfig` overrides Spring Boot's auto-configured `ObjectMapper` — loses settings from `application.yml`

**File:** `JacksonConfig.java`

```java
@Bean
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
}
```

This replaces Spring Boot's `ObjectMapper` entirely. The settings from `application.yml` (`default-property-inclusion: non_null`, `date-format`, etc.) are **silently ignored** because they configure Spring Boot's auto-configured `ObjectMapper`, which is now overridden. This means null fields are serialized, and date formatting reverts to Jackson defaults.

### 🟠 2.5 `@EnableAsync` is declared twice

**Files:** `MsaDetectorApplication.java` and `AppConfig.java`

Both classes carry `@EnableAsync`. While Spring tolerates this, it is confusing and can lead to subtle ordering issues if the two configurations disagree on executor settings.

### 🟠 2.6 `ChattyServiceDetector` query may trigger N+1 LazyInitializationException

**File:** `ChattyServiceDetector.java:43`

```java
List<ServiceDependency> chattyDeps = dependencyRepository.findChattyDependencies(project, minCalls);
```

The `findChattyDependencies` JPQL query does **not** `JOIN FETCH` the `sourceService` and `targetService`. Lines 46-47 then call `dep.getSourceService().getName()` and `dep.getTargetService().getName()`. Since `open-in-view` is explicitly `false` in `application.yml`, these lazy loads will throw:

```
org.hibernate.LazyInitializationException: could not initialize proxy — no Session
```

This will crash every chatty service detection run. **The same pattern exists in several other detectors** that call repository methods without `JOIN FETCH` and then access lazy associations outside a transaction.

### 🟠 2.7 `AnalysisWorker.processJob()` — `DesigniteService` runs but results are not committed before anti-pattern detection

**File:** `AnalysisWorker.java:80-86`

`designiteService.analyzeService(ms, servicePath)` saves code smells via `codeSmellRepository.saveAll(smells)` in its own implicit transaction. Then `antiPatternDetector.detectAntiPatterns()` queries for code smells. Since there is no explicit transaction wrapping the worker, whether the smells are visible depends on transaction isolation levels and propagation. This can cause **intermittent anti-pattern detection failures** (e.g., `GodServiceDetector` and `WrongCutsDetector` finding zero code smells when they should find many).

### 🟠 2.8 `deleteDirectory` in `ProjectService` leaks file handles — `Files.walk()` stream not closed

**File:** `ProjectService.java:354-363`

```java
private void deleteDirectory(Path dir) throws IOException {
    if (Files.exists(dir)) {
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> { ... });
    }
}
```

`Files.walk()` returns a `Stream<Path>` backed by a `DirectoryStream` which **must be closed** (try-with-resources). Failing to close it leaks file descriptors. Under load, this can exhaust the OS file descriptor limit and cause `IOException: Too many open files`. Note: `GitCloneService.cleanupDirectory()` correctly uses try-with-resources, but `ProjectService` does not.

---

## 3. Security Concerns

### 🔴 3.1 JWT secret is hardcoded in default configuration

**File:** `application.yml:40`

```yaml
app:
  jwt:
    secret: ${JWT_SECRET:msa-detector-jwt-secret-key-change-this-in-production-please}
```

The default secret is a well-known string embedded in source control. If the `JWT_SECRET` env var is not set (which is likely in dev), **any attacker can forge valid JWT tokens** by signing with this key. The default should be removed or the app should **refuse to start** if no secret is configured.

### 🟠 3.2 CORS allows any origin with credentials — session fixation / CSRF risk

**File:** `CorsFilterConfig.java:23-27`

```java
response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
response.setHeader("Access-Control-Allow-Credentials", "true");
response.setHeader("Access-Control-Allow-Headers", "*");
```

This reflects any `Origin` header back verbatim while also allowing credentials. This is functionally equivalent to `Access-Control-Allow-Origin: *` with credentials, which browsers intentionally block. Some browsers (older or non-standard) may still honor this, enabling **cross-site request attacks**. A whitelist of allowed origins should be used.

### 🟠 3.3 GitHub tokens stored in plaintext

**File:** `User.java:40`

```java
@Column(name = "github_token")
private String githubToken;
```

The GitHub token is stored unencrypted in the database. If the database is compromised, all users' GitHub tokens are exposed. Tokens should be encrypted at rest (e.g., using JPA `@Convert` with an `AttributeConverter` that encrypts/decrypts).

### 🟠 3.4 No password strength enforcement beyond length

**File:** `RegisterRequest.java`

```java
@Size(min = 6, max = 120, message = "Password must be between 6 and 120 characters")
String password
```

A 6-character minimum with no complexity requirements is weak. Consider requiring mixed case, digits, and special characters.

### 🟠 3.5 Error messages leak internal details

**File:** `GlobalExceptionHandler.java:91-96`

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
    ...
    "message", ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred",
    ...
}
```

Unhandled exceptions (including database errors, `NullPointerException`, etc.) have their **raw exception message** returned to the client. This can leak table names, SQL queries, file paths, and stack trace fragments. The generic handler should return a fixed message and only log the details server-side.

### 🟠 3.6 No rate limiting on auth endpoints

**File:** `SecurityConfig.java` and `AuthController.java`

The `/api/auth/login` and `/api/auth/register` endpoints are fully public with no rate limiting. This enables brute-force password attacks and account enumeration (the registration endpoint reveals whether an email is already registered).

### 🟠 3.7 OS Command Injection risk in `DesigniteService`

**File:** `DesigniteService.java:74-78`

```java
ProcessBuilder pb = new ProcessBuilder(
    "java", "-jar", designiteJarPath,
    "-i", inputPath.toString(),
    "-o", outputPath.toString()
);
```

`inputPath` and `outputPath` are derived from user-controlled project names and paths. While `ProcessBuilder` with a list (not a single string) mitigates shell injection, a malicious path containing special characters could still cause unexpected behavior. Input paths should be validated to contain only safe characters.

---

## 4. Architectural & Design Issues

### 4.1 JSON stored as `TEXT` columns instead of using JSONB

**Files:** `DetectedAntiPattern.java` (multiple `columnDefinition = "TEXT"` fields), `AnalysisResult.java:77`

PostgreSQL supports a native `JSONB` type with indexing and querying capabilities. Storing JSON as `TEXT` means:
- No database-level validation of JSON structure
- No ability to query inside the JSON from JPQL/SQL
- Wasted opportunity for GIN indexes on JSON fields

### 4.2 No DTO mapper — manual mapping everywhere

**Files:** `JobService.java`, `ProjectService.java`

Entity-to-DTO mapping is done with manual `toResponse()` / `toJobResponse()` methods. MapStruct is declared as a dependency in `pom.xml` and the annotation processor is configured, but **it is never actually used**. This is dead configuration weight.

### 4.3 Detectors ignore `AnalysisJob` feature flags

**File:** `AnalysisJob.java:65-87` declares boolean flags like `runDesignite`, `detectCyclicDependencies`, `detectSharedDatabases`, etc.

However, `AntiPatternDetectorService.detectAntiPatterns()` runs **all** registered detectors unconditionally:

```java
for (AntiPatternDetector detector : detectors) {
    List<DetectedAntiPattern> detected = detector.detect(project, microservices);
    ...
}
```

The per-job feature flags in `AnalysisJob` are **completely ignored**. Users cannot selectively enable/disable specific detectors.

### 4.4 No cleanup of workspace files

When a project is deleted (`ProjectService.deleteProjectForUser()`), the local files are cleaned up. However, if a job **fails** midway, the cloned/extracted files in `WORKSPACE_DIR` are **never cleaned up**. There is no scheduled task to garbage-collect old workspace directories. Over time this will exhaust disk space.

### 4.5 `AnalysisResult.calculateHealthScore()` vs `HealthScoreCalculator` — two competing scoring systems

**File:** `AnalysisResult.java:91-99` and `HealthScoreCalculator.java`

The `AnalysisResult` entity has a `calculateHealthScore()` method that uses a simple linear deduction formula. `HealthScoreCalculator` uses a completely different category-based formula. Both are called:
- `AntiPatternDetectorService.java:125` calls `result.calculateHealthScore()` (the simple formula)
- `JobService.toResultResponse()` calls `healthScoreCalculator.calculate(result)` and uses **that** score in the response

So the `healthScore` field stored in the DB (from the entity method) differs from what the API returns (from `HealthScoreCalculator`). This is confusing and the stored value is misleading.

### 4.6 `DependencyGraphBuilder` is an 894-line God Class

**File:** `DependencyGraphBuilder.java` — 894 lines

This single class handles YAML parsing, Spoon model building, endpoint detection, Feign client detection, RestTemplate detection, WebClient detection, URL resolution, service name resolution, and more. It should be decomposed into focused collaborators (e.g., `EndpointScanner`, `FeignClientScanner`, `RestTemplateScanner`, `ConfigParser`).

---

## 5. Concurrency & Transaction Issues

### 5.1 `JobProgressUpdater.completeJob()` — detached entity merge risk

**File:** `JobProgressUpdater.java:45-50`

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void completeJob(Long jobId, AnalysisResult result) {
    AnalysisJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null) return;
    job.complete(result);
    jobRepository.saveAndFlush(job);
}
```

The `AnalysisResult` passed in was created and saved in `AntiPatternDetectorService.detectAntiPatterns()` in a **different transaction**. When `job.complete(result)` sets `this.result = result` and `result.setAnalysisJob(this)`, the `result` entity is now **detached** from the persistence context of this `REQUIRES_NEW` transaction. Calling `saveAndFlush(job)` attempts to cascade to the detached `result`, which may throw:
```
org.hibernate.PersistentObjectException: detached entity passed to persist
```
or silently create a duplicate depending on cascade/merge configuration. This is a **data integrity risk**.

### 5.2 No optimistic locking — concurrent analysis of the same project corrupts data

**File:** `BaseEntity.java`

The `BaseEntity` has no `@Version` field. If two users (or the same user twice) trigger re-analysis of the same project concurrently:
1. Both calls clear `project.getMicroservices()` in `createReanalysisJob()`
2. Both detect microservices and save them
3. The result is **duplicate microservices** and **corrupt analysis data**

### 5.3 `project.getMicroservices().clear()` may not fully cascade deletes

**File:** `ProjectService.java:258`

```java
project.getMicroservices().clear();
projectRepository.saveAndFlush(project);
```

This relies on `orphanRemoval = true` to delete the old microservices. However, each `Microservice` has cascading relationships to `Endpoint`, `ServiceDependency`, and `CodeSmell`. The JPA cascade will generate many individual DELETE statements (one per entity), which is extremely slow for large projects. A single `DELETE FROM microservices WHERE project_id = ?` would be orders of magnitude faster.

---

## 6. Performance Concerns

### 6.1 N+1 query problem in `ProjectService.toResponse()`

**File:** `ProjectService.java:365-387`

```java
private ProjectResponse toResponse(Project project) {
    List<MicroserviceResponse> microservices = project.getMicroservices().stream()...
    int analysisCount = analysisJobRepository.countByProject(project);
    Long latestJobId = analysisJobRepository.findFirstByProjectOrderByCreatedAtDesc(project)...
```

Each call to `toResponse()` fires **3 queries** (microservices collection + count + latest job). When called from `getProjectsForUser()` which iterates over all projects, this creates an **N+1 query storm**: 1 query for projects + 3N queries for N projects.

### 6.2 `Files.walk()` without depth limit in `MicroserviceDetector.findBuildFiles()`

**File:** `MicroserviceDetector.java:98`

```java
try (Stream<Path> walk = Files.walk(root)) {
```

For large monorepos with hundreds of thousands of files, this walk has no depth limit and will traverse the entire directory tree including `node_modules`, `.git` objects, etc. (the filters come after the walk). Consider `Files.walk(root, maxDepth)` with a reasonable limit.

### 6.3 Spoon model is built multiple times per service

**File:** `DependencyGraphBuilder.java`

`parseEndpoints()` calls `buildSpoonModel(srcDir)` and then `detectInterServiceCalls()` calls `buildSpoonModel(srcDir)` again for the same service. Spoon model building is **very expensive** (it parses all Java files into an AST). Building it twice per microservice doubles the analysis time unnecessarily.

### 6.4 `AnalysisResultRepository.findAllByProjectWithAntiPatterns()` — Cartesian product

**File:** `AnalysisResultRepository.java:41-43`

```java
@Query("SELECT ar FROM AnalysisResult ar JOIN FETCH ar.analysisJob LEFT JOIN FETCH ar.detectedAntiPatterns ...")
```

This fetches all results with their anti-patterns in a single query. If a project has 10 results with 20 anti-patterns each, the result set is 200 rows (Cartesian product), which Hibernate must de-duplicate in memory. For projects with many analyses, this can cause significant memory pressure and performance degradation.

---

## 7. Code Quality & Maintainability

### 7.1 Inconsistent constructor injection vs. field injection

All classes use **constructor injection**, which is good. However, some use Lombok `@AllArgsConstructor` on entities (which generates a constructor with all fields), while services use explicit constructors. This is not a bug but is inconsistent. Consider using `@RequiredArgsConstructor` on services that have only `final` dependencies.

### 7.2 Raw `Object[]` returns from repository queries

**Files:** `CodeSmellRepository.java`, `DetectedAntiPatternRepository.java`, `ServiceDependencyRepository.java`

Multiple repository methods return `List<Object[]>` for aggregate queries:
```java
List<Object[]> countByTypeForProject(...);
List<Object[]> countBySeverityForProject(...);
```

These are type-unsafe and fragile. These should use **JPQL constructor expressions** or **projection interfaces** (e.g., `record TypeCount(String type, long count) {}`).

### 7.3 Magic strings and numbers scattered throughout

- Severity mapping in `DesigniteService.mapSeverity()` uses hardcoded lowercase strings
- `HealthScoreCalculator` has hardcoded point budgets (40, 20, 25, 15) with no documentation on why those values were chosen
- `DependencyGraphBuilder` has `"OFF"` for Spoon log level as a string

### 7.4 `@Builder` on JPA entities — dangerous default usage

**Files:** All entities use `@Builder`

Lombok's `@Builder` combined with `@AllArgsConstructor` and `@NoArgsConstructor` on JPA entities can cause issues:
- `@Builder` does not call `@Builder.Default` initializers if the field is not set in the builder, leading to `null` instead of the default value in some Lombok versions
- The combination can confuse Hibernate proxying

While modern Lombok (1.18.38) handles this correctly, it's a known source of subtle bugs and should be documented.

### 7.5 Unused repository methods

Multiple repository methods appear unused in the codebase:
- `CodeSmellRepository.findByProjectAndSeverity()`
- `CodeSmellRepository.countByTypeForProject()`
- `CodeSmellRepository.countBySeverityForProject()`
- `MicroserviceRepository.findByProjectWithEndpoints()`
- `MicroserviceRepository.findByIdWithDetails()`
- `MicroserviceRepository.findPotentialNanoServices()`
- `ServiceDependencyRepository.findMostCalledServices()`
- `AnalysisJobRepository.findPendingJobs()`
- `AnalysisJobRepository.findRunningJobs()`
- `EndpointRepository.hasVersionedEndpoints()`

These add maintenance burden and compilation time without providing value.

---

## 8. Configuration & Deployment Issues

### 🔴 8.1 Spring Boot 4.0.1 with Java 25 — bleeding edge, likely unstable

**File:** `pom.xml:10, 21`

```xml
<version>4.0.1</version>
...
<java.version>25</java.version>
```

As of the review date, Spring Boot 4.0.1 and Java 25 are very recent. Many dependencies (Spoon 11.1.0, springdoc-openapi 2.7.0, JGit 7.1.0) may not be tested against Spring Boot 4.x / Java 25. The Spoon compliance level is hardcoded to 17:

```java
launcher.getEnvironment().setComplianceLevel(17);
```

This means Spoon cannot parse Java 25 syntax features (records, sealed classes, pattern matching, etc.) in the analyzed projects. This is a functional limitation.

### 🟠 8.2 `Dockerfile` does not copy `DesigniteJava.jar`

**File:** `Dockerfile`

The Dockerfile does not `COPY` the `lib/DesigniteJava.jar` into the image. It's mounted as a volume in `docker-compose.yml`:
```yaml
- ./lib/DesigniteJava.jar:/app/lib/DesigniteJava.jar:ro
```

This means the Docker image **cannot run standalone** — it requires docker-compose or manual volume mounting. For production deployment, the JAR should be baked into the image.

### 🟠 8.3 `spring.jpa.open-in-view: false` + lazy associations = runtime errors

This is correct best practice, but as noted in §2.6, several code paths access lazy associations outside of a transaction/session boundary. The combination is currently causing `LazyInitializationException` in production.

### 🟠 8.4 Deprecated `version` key in `docker-compose.yml`

**File:** `docker-compose.yml:1`

```yaml
version: '3.8'
```

The `version` key is deprecated in modern Docker Compose and is ignored. It should be removed.

---

## 9. Testing

### 🔴 9.1 No tests exist

The project has `spring-boot-starter-test`, `spring-security-test`, and Testcontainers as dependencies, but there are **zero test files** in the `src/test` directory (the directory doesn't even appear in the project structure).

For a dissertation project implementing static analysis and anti-pattern detection, the absence of tests is a critical gap:
- No unit tests for detectors (the core domain logic)
- No integration tests for the analysis pipeline
- No tests for JWT authentication
- No tests for the repository queries
- No tests for edge cases in ZIP extraction, git cloning, etc.

This makes it impossible to verify correctness, and any refactoring is high-risk.

---

## 10. Minor / Stylistic Observations

| # | File | Observation |
|---|------|-------------|
| 10.1 | `AnalysisJob.java` | 23 `@Builder.Default` fields create an excessively wide entity. Consider extracting configuration fields into an `@Embeddable` `AnalysisConfig`. |
| 10.2 | `SecurityConfig.java` | `CustomUserDetailsService` is injected but never used directly (it's used by the `AuthenticationManager` auto-configuration). The field can be removed. |
| 10.3 | `BaseEntity.java` | `createdAt` has `updatable = false` but `updatedAt` has no `insertable` constraint — it will be `null` on first insert, which is fine but inconsistent with some coding standards. |
| 10.4 | `AntiPatternType.java` | The enum carries `displayName` and `description` but has no `@JsonValue` — the API serializes the enum constant name (e.g., `CYCLIC_DEPENDENCY`) rather than the display name. This may not match frontend expectations. |
| 10.5 | `DependencyGraphBuilder.java:487` | `callMap.computeIfAbsent(normalizedTarget, _ -> new ArrayList<>())` uses Java 22+ unnamed variable `_`. While valid in Java 25, this limits compatibility if the project ever targets older JVMs. |
| 10.6 | `DesigniteService.java` | Silently returns without analysis if the DesigniteJava JAR is missing. There's no indication in the analysis result that code smell detection was skipped, which could mislead users into thinking their code has zero smells. |
| 10.7 | `AuthService.register()` | Method is `@Transactional` — if token generation fails, the user save is correctly rolled back. No issue here. |
| 10.8 | `ProjectService.cloneAndAnalyze()` | The git clone happens **inside** a `@Transactional` method. If the clone takes 300 seconds (the configured timeout), the database connection is held for the entire duration, potentially exhausting the connection pool. |
| 10.9 | Multiple files | Log messages use `{}` placeholders (SLF4J style) consistently — good practice. |
| 10.10 | `DependencyGraphBuilder.java` | The `resolveTargetService()` fuzzy matching (§line 430-440) can produce false positives. E.g., a service named "api" would match "api-gateway", "user-api", etc. |

---

## 11. Summary Verdict

| Severity | Count | Examples |
|----------|-------|---------|
| 🔴 Critical | 2 | JWT secret hardcoded, no tests |
| 🟠 Major | 12 | LazyInitializationException in detectors, CORS wildcard, file handle leak, Jackson override, detached entity cascade, detectors ignore feature flags |
| 🟡 Moderate | 10+ | N+1 queries, no optimistic locking, Spoon built twice, dead code |
| ⚪ Minor | 10 | Style, unused dependencies, deprecated docker-compose syntax |

### Overall Assessment

The codebase demonstrates a **solid domain understanding** of microservice anti-patterns and has a well-thought-out detection pipeline with good use of Spoon for static analysis. The REST API design is clean with proper DTOs, validation, and error handling. The long-running analysis pipeline is correctly designed to avoid holding database transactions open, using `REQUIRES_NEW` progress updates for real-time polling. The ZIP extraction includes proper path traversal protection.

The most impactful remaining issues are: the `LazyInitializationException` crashes in detectors (since `open-in-view: false` but several queries lack `JOIN FETCH` for accessed associations), the hardcoded JWT secret default, and the complete absence of automated tests. The `JacksonConfig` silently overriding Spring Boot's auto-configured `ObjectMapper` means `application.yml` serialization settings are ignored.

**Recommended priority:**
1. Fix lazy loading in detectors — add `JOIN FETCH` or `@Transactional` boundaries (runtime crashes)
2. Force a strong JWT secret (fail-fast on startup)
3. Fix the `JacksonConfig` to customize rather than replace the auto-configured `ObjectMapper`
4. Address the `DependencyGraphResponse` inner record visibility
5. Wire up the `AnalysisJob` feature flags to the detector orchestration
6. Add unit tests for all detectors
7. Close the `Files.walk()` stream in `ProjectService.deleteDirectory()`

