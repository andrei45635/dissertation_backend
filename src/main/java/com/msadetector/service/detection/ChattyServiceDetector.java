package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.entity.ServiceDependency;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.repository.ServiceDependencyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Detects chatty services — service pairs with an excessive number
 * of fine-grained calls that increase latency and coupling.
 * <p>
 * Uses two complementary approaches:
 * <ol>
 *   <li>Dependency-based: queries persisted {@link ServiceDependency} records with high call counts
 *       (works when inter-service calls resolve to known microservices)</li>
 *   <li>Source-based: scans for interfaces/classes that exhibit chatty patterns —
 *       many fine-grained HTTP client methods (Feign-style interfaces, classes with
 *       many HTTP call sites via HttpURLConnection, RestTemplate, WebClient, etc.)</li>
 * </ol>
 */
@Component
public class ChattyServiceDetector extends BaseDetector {

    private static final String SRC_MAIN_JAVA = "src/main/java";

    private static final Set<String> HTTP_CALL_METHODS = Set.of(
            "openConnection",
            "getForObject", "getForEntity", "postForObject", "postForEntity",
            "exchange", "patchForObject",
            "bodyToMono", "bodyToFlux"
    );

    private static final Set<String> HTTP_METHOD_ANNOTATIONS = Set.of(
            "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping",
            "RequestMapping", "RequestLine"
    );

    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of(
            "Controller", "RestController", "RequestMapping"
    );

    private static final Set<String> HTTP_CLIENT_TYPE_NAMES = Set.of(
            "RestTemplate", "WebClient", "HttpURLConnection",
            "RestClient", "FeignClient", "OkHttpClient", "CloseableHttpClient",
            "HttpClient", "AsyncHttpClient"
    );

    private static final Set<String> HTTP_CLIENT_NAME_KEYWORDS = Set.of(
            "client", "api", "feign", "rest", "http", "proxy", "remote", "gateway"
    );

    private final ServiceDependencyRepository dependencyRepository;
    private final int minCalls;

    public ChattyServiceDetector(ObjectMapper objectMapper,
                                  ServiceDependencyRepository dependencyRepository,
                                  @Value("${app.thresholds.chatty-service-min-calls:5}") int minCalls) {
        super(objectMapper);
        this.dependencyRepository = dependencyRepository;
        this.minCalls = minCalls;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices, AnalysisJob job) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());
        int effectiveMinCalls = job != null && job.getChattyServiceMinCalls() != null
                ? job.getChattyServiceMinCalls()
                : minCalls;

        Set<String> flaggedServices = new HashSet<>();
        List<ServiceDependency> chattyDeps = job != null
                ? dependencyRepository.findChattyDependenciesByAnalysisJob(job, effectiveMinCalls)
                : dependencyRepository.findChattyDependencies(project, effectiveMinCalls);

        for (ServiceDependency dep : chattyDeps) {
            String sourceName = dep.getSourceService().getName();
            String targetName = dep.getTargetService().getName();

            List<Map<String, Object>> snippets = new ArrayList<>();
            if (dep.getEvidenceFile() != null && dep.getEvidenceLine() != null && dep.getEvidenceLine() > 0) {
                Map<String, Object> snippet = readSnippet(
                        projectRoot.resolve(dep.getEvidenceFile()), dep.getEvidenceLine());
                if (snippet != null) snippets.add(snippet);
            } else if (dep.getEvidenceCode() != null) {
                Map<String, Object> snippet = buildSnippet(
                        dep.getEvidenceFile(), dep.getEvidenceLine() != null ? dep.getEvidenceLine() : 0,
                        dep.getEvidenceCode());
                if (snippet != null) snippets.add(snippet);
            }

            DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                    .patternType(AntiPatternType.CHATTY_SERVICE)
                    .severity(Severity.HIGH)
                    .description(String.format(
                            "Excessive communication between '%s' and '%s' (%d calls detected). "
                                    + "Fine-grained calls increase latency and coupling.",
                            sourceName, targetName, dep.getCallCount()
                    ))
                    .affectedServicesJson(toJson(List.of(sourceName, targetName)))
                    .primaryService(dep.getSourceService())
                    .callCount(dep.getCallCount())
                    .codeSnippetsJson(snippetsToJson(snippets))
                    .remediation("Consider aggregating multiple fine-grained calls into a single coarse-grained API, "
                            + "or use asynchronous messaging for non-critical communication")
                    .build();

            patterns.add(pattern);
            flaggedServices.add(sourceName);
            log.info("Chatty service detected (dependency-based): {} -> {} ({} calls)",
                    sourceName, targetName, dep.getCallCount());
        }

        for (Microservice ms : microservices) {
            if (flaggedServices.contains(ms.getName())) continue;

            Path servicePath = projectRoot.resolve(ms.getRelativePath());
            Path srcDir = servicePath.resolve(SRC_MAIN_JAVA);
            if (!Files.exists(srcDir)) continue;

            List<ChattyTypeEvidence> chattyTypes = scanForChattyTypes(
                    srcDir, servicePath, effectiveMinCalls);
            if (!chattyTypes.isEmpty()) {
                for (ChattyTypeEvidence evidence : chattyTypes) {
                    List<Map<String, Object>> snippets = new ArrayList<>();
                    if (evidence.filePath() != null) {
                        Map<String, Object> snippet = readSnippet(
                                servicePath.resolve(evidence.filePath()), evidence.lineNumber(), 10);
                        if (snippet != null) snippets.add(snippet);
                    }

                    DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                            .patternType(AntiPatternType.CHATTY_SERVICE)
                            .severity(Severity.HIGH)
                            .description(String.format(
                                    "Service '%s' contains chatty client '%s' with %d fine-grained HTTP methods. "
                                            + "Each method makes a separate HTTP call instead of using "
                                            + "a coarse-grained aggregate API.",
                                    ms.getName(), evidence.typeName(), evidence.methodCount()
                            ))
                            .affectedServicesJson(toJson(List.of(ms.getName())))
                            .primaryService(ms)
                            .callCount(evidence.methodCount())
                            .detailsJson(toJson(Map.of(
                                    "chattyType", evidence.typeName(),
                                    "methodCount", evidence.methodCount(),
                                    "detectionMethod", evidence.detectionMethod()
                            )))
                            .codeSnippetsJson(snippetsToJson(snippets))
                            .remediation("Consider aggregating multiple fine-grained calls into a single "
                                    + "coarse-grained API call, or use a batch endpoint to reduce round trips")
                            .build();

                    patterns.add(pattern);
                    log.info("Chatty service detected (source-based): {} contains '{}' with {} methods",
                            ms.getName(), evidence.typeName(), evidence.methodCount());
                }
            }
        }

        return patterns;
    }

    /**
     * Scans source code for types that exhibit chatty patterns:
     * <ul>
     *   <li>Interfaces annotated with @FeignClient that have many methods</li>
     *   <li>Interfaces whose methods carry HTTP-mapping annotations
     *       (e.g., @GetMapping, @RequestMapping) — indicating a declarative HTTP client</li>
     *   <li>Classes with many HTTP call sites (RestTemplate, WebClient, HttpURLConnection)
     *       where the invocation target type is a known HTTP client class</li>
     * </ul>
     * Regular domain interfaces/classes with many methods are NOT flagged — only those
     * that exhibit concrete evidence of being HTTP communication abstractions.
     */
    private List<ChattyTypeEvidence> scanForChattyTypes(
            Path srcDir, Path servicePath, int effectiveMinCalls) {
        List<ChattyTypeEvidence> results = new ArrayList<>();

        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(srcDir.toString());
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setComplianceLevel(17);
            launcher.getEnvironment().setShouldCompile(false);
            launcher.getEnvironment().setIgnoreSyntaxErrors(true);
            launcher.getEnvironment().setLevel("OFF");
            launcher.buildModel();
            CtModel model = launcher.getModel();

            for (CtType<?> type : model.getAllTypes()) {
                try {
                    if (type.isInterface()) {
                        boolean isFeignClient = type.getAnnotations().stream()
                                .anyMatch(a -> "FeignClient".equals(a.getAnnotationType().getSimpleName()));

                        if (!isFeignClient) {
                            boolean isController = type.getAnnotations().stream()
                                    .anyMatch(a -> CONTROLLER_ANNOTATIONS.contains(
                                            a.getAnnotationType().getSimpleName()));
                            if (isController) continue;

                            String nameLower = type.getSimpleName().toLowerCase();
                            boolean hasClientKeyword = HTTP_CLIENT_NAME_KEYWORDS.stream()
                                    .anyMatch(nameLower::contains);
                            if (!hasClientKeyword) continue;

                            Set<CtMethod<?>> methods = type.getMethods();
                            long httpMethodCount = methods.stream()
                                    .filter(m -> !m.isDefaultMethod())
                                    .filter(m -> m.getAnnotations().stream()
                                            .anyMatch(a -> HTTP_METHOD_ANNOTATIONS.contains(
                                                    a.getAnnotationType().getSimpleName())))
                                    .count();
                            if (httpMethodCount < effectiveMinCalls) continue;

                            String relPath = type.getPosition().isValidPosition()
                                    ? servicePath.relativize(Path.of(type.getPosition().getFile().getPath())).toString()
                                    : null;
                            int line = type.getPosition().isValidPosition() ? type.getPosition().getLine() : 1;

                            results.add(new ChattyTypeEvidence(
                                    type.getQualifiedName(),
                                    (int) httpMethodCount,
                                    relPath, line,
                                    "HTTP client interface (annotated methods)"
                            ));
                        } else {
                            Set<CtMethod<?>> methods = type.getMethods();
                            int methodCount = (int) methods.stream()
                                    .filter(m -> !m.isDefaultMethod())
                                    .count();

                            if (methodCount >= effectiveMinCalls) {
                                String relPath = type.getPosition().isValidPosition()
                                        ? servicePath.relativize(Path.of(type.getPosition().getFile().getPath())).toString()
                                        : null;
                                int line = type.getPosition().isValidPosition() ? type.getPosition().getLine() : 1;

                                results.add(new ChattyTypeEvidence(
                                        type.getQualifiedName(),
                                        methodCount,
                                        relPath, line,
                                        "FeignClient interface"
                                ));
                            }
                        }
                    }

                    if (type.isClass()) {
                        boolean isController = type.getAnnotations().stream()
                                .anyMatch(a -> CONTROLLER_ANNOTATIONS.contains(
                                        a.getAnnotationType().getSimpleName()));
                        if (isController) continue;

                        long httpCallCount = type.getElements(new TypeFilter<>(CtInvocation.class))
                                .stream()
                                .filter(inv -> {
                                    CtInvocation<?> invocation = (CtInvocation<?>) inv;
                                    if (invocation.getExecutable() == null) return false;
                                    String methodName = invocation.getExecutable().getSimpleName();
                                    if (!HTTP_CALL_METHODS.contains(methodName)) return false;
                                    try {
                                        var declaringType = invocation.getExecutable().getDeclaringType();
                                        if (declaringType != null) {
                                            String declName = declaringType.getSimpleName();
                                            return HTTP_CLIENT_TYPE_NAMES.contains(declName);
                                        }
                                    } catch (Exception ignored) {}
                                    return false;
                                })
                                .count();

                        if (httpCallCount >= effectiveMinCalls) {
                            String relPath = type.getPosition().isValidPosition()
                                    ? servicePath.relativize(Path.of(type.getPosition().getFile().getPath())).toString()
                                    : null;
                            int line = type.getPosition().isValidPosition() ? type.getPosition().getLine() : 1;

                            results.add(new ChattyTypeEvidence(
                                    type.getQualifiedName(),
                                    (int) httpCallCount,
                                    relPath, line,
                                    "class with many HTTP call sites"
                            ));
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skipping type {} for chatty analysis: {}", type.getQualifiedName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan for chatty types in {}: {}", srcDir, e.getMessage());
        }

        return results;
    }

    private record ChattyTypeEvidence(String typeName, int methodCount,
                                       String filePath, int lineNumber,
                                       String detectionMethod) {}
}

