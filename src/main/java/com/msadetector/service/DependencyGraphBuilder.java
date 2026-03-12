package com.msadetector.service;

import com.msadetector.entity.Endpoint;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.entity.ServiceDependency;
import com.msadetector.enums.DependencyType;
import com.msadetector.enums.HttpMethod;
import com.msadetector.repository.EndpointRepository;
import com.msadetector.repository.MicroserviceRepository;
import com.msadetector.repository.ServiceDependencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Builds the dependency graph for a microservices project by scanning
 * source code using Spoon and parsing configuration files.
 * <p>
 * Responsibilities:
 * - Parse application.yml / application.properties for datasource URLs
 * - Detect REST endpoints via Spring MVC annotations
 * - Detect inter-service calls via @FeignClient, RestTemplate, WebClient
 * - Populate Endpoint and ServiceDependency entities
 */
@Service
public class DependencyGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    private static final String SRC_MAIN_JAVA = "src/main/java";

    private static final Pattern API_VERSION_PATTERN = Pattern.compile("/v\\d+[/.]");

    private final MicroserviceRepository microserviceRepository;
    private final ServiceDependencyRepository dependencyRepository;
    private final EndpointRepository endpointRepository;

    public DependencyGraphBuilder(MicroserviceRepository microserviceRepository,
                                   ServiceDependencyRepository dependencyRepository,
                                   EndpointRepository endpointRepository) {
        this.microserviceRepository = microserviceRepository;
        this.dependencyRepository = dependencyRepository;
        this.endpointRepository = endpointRepository;
    }

    /**
     * Scans each microservice's source code to build the dependency graph.
     */
    public void buildDependencyGraph(Project project) {
        List<Microservice> microservices = microserviceRepository.findByProject(project);
        Path projectRoot = Path.of(project.getLocalPath());

        // Phase 1: Parse configuration files and detect endpoints
        for (Microservice ms : microservices) {
            Path servicePath = projectRoot.resolve(ms.getRelativePath());
            parseDatasourceConfig(ms, servicePath);
            parseEndpoints(ms, servicePath);
        }

        // Build a lookup of service names for matching
        Map<String, Microservice> serviceByName = microservices.stream()
                .collect(Collectors.toMap(
                        ms -> ms.getName().toLowerCase(),
                        ms -> ms,
                        (a, _) -> a
                ));

        // Phase 2: Detect inter-service calls and build dependencies
        for (Microservice ms : microservices) {
            Path servicePath = projectRoot.resolve(ms.getRelativePath());
            detectInterServiceCalls(ms, servicePath, serviceByName);
        }
    }

    // ========================================================================================
    // CONFIGURATION PARSING
    // ========================================================================================

    private void parseDatasourceConfig(Microservice ms, Path servicePath) {
        Path resourcesDir = servicePath.resolve("src/main/resources");

        String datasourceUrl = null;
        String datasourceName = null;

        for (String filename : List.of("application.yml", "application.yaml")) {
            Path yamlFile = resourcesDir.resolve(filename);
            if (Files.exists(yamlFile)) {
                try (InputStream is = Files.newInputStream(yamlFile)) {
                    Yaml yaml = new Yaml();
                    Map<String, Object> config = yaml.load(is);
                    if (config != null) {
                        datasourceUrl = extractNestedValue(config, "spring.datasource.url");
                        datasourceName = extractNestedValue(config, "spring.datasource.name");
                        if (datasourceName == null && datasourceUrl != null) {
                            datasourceName = extractDbNameFromUrl(datasourceUrl);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse YAML config for service {}: {}", ms.getName(), e.getMessage());
                }
                break;
            }
        }

        if (datasourceUrl == null) {
            Path propsFile = resourcesDir.resolve("application.properties");
            if (Files.exists(propsFile)) {
                try {
                    Properties props = new Properties();
                    props.load(Files.newInputStream(propsFile));
                    datasourceUrl = props.getProperty("spring.datasource.url");
                    datasourceName = props.getProperty("spring.datasource.name");
                    if (datasourceName == null && datasourceUrl != null) {
                        datasourceName = extractDbNameFromUrl(datasourceUrl);
                    }
                } catch (IOException e) {
                    log.warn("Failed to parse properties config for service {}: {}", ms.getName(), e.getMessage());
                }
            }
        }

        if (datasourceUrl != null) {
            datasourceUrl = resolveDefaultPlaceholders(datasourceUrl);
            ms.setDatasourceUrl(datasourceUrl);
            ms.setDatasourceName(datasourceName);
            microserviceRepository.save(ms);
            log.debug("Service {} datasource: {}", ms.getName(), datasourceUrl);
        }
    }

    // ========================================================================================
    // ENDPOINT DETECTION (Spoon)
    // ========================================================================================

    private void parseEndpoints(Microservice ms, Path servicePath) {
        Path srcDir = servicePath.resolve(SRC_MAIN_JAVA);
        if (!Files.exists(srcDir)) {
            log.debug("No src/main/java found for service {}", ms.getName());
            return;
        }

        try {
            CtModel model = buildSpoonModel(srcDir);
            List<Endpoint> endpoints = new ArrayList<>();

            for (CtType<?> type : model.getAllTypes()) {
                if (!isRestController(type)) continue;

                String classLevelPath = extractRequestMappingPath(type);

                for (CtMethod<?> method : type.getMethods()) {
                    EndpointInfo info = extractEndpointInfo(method, classLevelPath);
                    if (info != null) {
                        boolean hasVersioning = API_VERSION_PATTERN.matcher(info.path()).find();

                        Endpoint endpoint = Endpoint.builder()
                                .path(info.path())
                                .httpMethod(info.httpMethod())
                                .controllerClass(type.getQualifiedName())
                                .methodName(method.getSimpleName())
                                .hasVersioning(hasVersioning)
                                .apiVersion(hasVersioning ? extractVersionFromPath(info.path()) : null)
                                .microservice(ms)
                                .build();

                        endpoints.add(endpoint);
                    }
                }
            }

            if (!endpoints.isEmpty()) {
                endpointRepository.saveAll(endpoints);
                ms.setNumberOfEndpoints(endpoints.size());
                microserviceRepository.save(ms);
                log.info("Detected {} endpoints in service {}", endpoints.size(), ms.getName());
            }

        } catch (Exception e) {
            log.error("Failed to parse endpoints for service {}: {}", ms.getName(), e.getMessage());
        }
    }

    // ========================================================================================
    // INTER-SERVICE CALL DETECTION (Spoon)
    // ========================================================================================

    private void detectInterServiceCalls(Microservice sourceService, Path servicePath,
                                          Map<String, Microservice> serviceByName) {
        Path srcDir = servicePath.resolve(SRC_MAIN_JAVA);
        if (!Files.exists(srcDir)) return;

        try {
            CtModel model = buildSpoonModel(srcDir);
            Map<String, List<CallEvidence>> callMap = new HashMap<>();

            for (CtType<?> type : model.getAllTypes()) {
                detectFeignClients(type, callMap);
                detectRestTemplateCalls(type, callMap);
                detectWebClientCalls(type, callMap);
            }

            for (Map.Entry<String, List<CallEvidence>> entry : callMap.entrySet()) {
                Microservice targetService = serviceByName.get(entry.getKey().toLowerCase());
                if (targetService != null && !targetService.getId().equals(sourceService.getId())) {
                    List<CallEvidence> evidenceList = entry.getValue();
                    DependencyType depType = evidenceList.getFirst().dependencyType();

                    ServiceDependency dependency = ServiceDependency.builder()
                            .sourceService(sourceService)
                            .targetService(targetService)
                            .dependencyType(depType)
                            .callCount(evidenceList.size())
                            .evidenceFile(evidenceList.getFirst().file())
                            .evidenceLine(evidenceList.getFirst().line())
                            .evidenceCode(truncate(evidenceList.getFirst().code(), 1000))
                            .targetUrl(evidenceList.getFirst().url())
                            .build();

                    dependencyRepository.save(dependency);
                    log.info("Dependency: {} -> {} ({} calls, type: {})",
                            sourceService.getName(), targetService.getName(),
                            evidenceList.size(), depType);
                }
            }

        } catch (Exception e) {
            log.error("Failed to detect inter-service calls for {}: {}", sourceService.getName(), e.getMessage());
        }
    }

    private void detectFeignClients(CtType<?> type, Map<String, List<CallEvidence>> callMap) {
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            String annotationName = annotation.getAnnotationType().getSimpleName();
            if ("FeignClient".equals(annotationName)) {
                String targetName = extractFeignClientTarget(annotation);
                if (targetName != null) {
                    String normalizedTarget = normalizeServiceName(targetName);
                    callMap.computeIfAbsent(normalizedTarget, _ -> new ArrayList<>())
                            .add(new CallEvidence(
                                    DependencyType.FEIGN_CLIENT,
                                    positionFile(type), positionLine(type),
                                    "@FeignClient targeting " + targetName,
                                    targetName
                            ));
                    log.debug("Found @FeignClient in {} targeting {}", type.getQualifiedName(), targetName);
                }
            }
        }
    }

    private void detectRestTemplateCalls(CtType<?> type, Map<String, List<CallEvidence>> callMap) {
        Set<String> restTemplateMethods = Set.of(
                "getForObject", "getForEntity",
                "postForObject", "postForEntity",
                "exchange", "put", "delete", "patchForObject"
        );

        for (CtInvocation<?> invocation : type.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (invocation.getExecutable() == null) continue;

            String methodName = invocation.getExecutable().getSimpleName();
            if (!restTemplateMethods.contains(methodName)) continue;

            if (invocation.getTarget() != null) {
                CtTypeReference<?> targetType = invocation.getTarget().getType();
                if (targetType != null && !targetType.getSimpleName().equals("RestTemplate")) continue;
            }

            if (invocation.getArguments().isEmpty()) continue;
            String urlValue = extractStringValue(invocation.getArguments().getFirst());

            if (urlValue != null) {
                String targetServiceName = extractServiceNameFromUrl(urlValue);
                if (targetServiceName != null) {
                    callMap.computeIfAbsent(targetServiceName.toLowerCase(), _ -> new ArrayList<>())
                            .add(new CallEvidence(
                                    DependencyType.REST_TEMPLATE,
                                    positionFile(invocation), positionLine(invocation),
                                    invocation.toString(), urlValue
                            ));
                }
            }
        }
    }

    private void detectWebClientCalls(CtType<?> type, Map<String, List<CallEvidence>> callMap) {
        for (CtInvocation<?> invocation : type.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (invocation.getExecutable() == null) continue;

            String methodName = invocation.getExecutable().getSimpleName();

            if ("uri".equals(methodName) && !invocation.getArguments().isEmpty() && isWebClientChain(invocation)) {
                String urlValue = extractStringValue(invocation.getArguments().getFirst());
                if (urlValue != null) {
                    String targetServiceName = extractServiceNameFromUrl(urlValue);
                    if (targetServiceName != null) {
                        callMap.computeIfAbsent(targetServiceName.toLowerCase(), _ -> new ArrayList<>())
                                .add(new CallEvidence(
                                        DependencyType.WEB_CLIENT,
                                        positionFile(invocation), positionLine(invocation),
                                        invocation.toString(), urlValue
                                ));
                    }
                }
            }

            if ("create".equals(methodName) && !invocation.getArguments().isEmpty()
                    && invocation.getTarget() != null) {
                String targetTypeName = invocation.getTarget().getType() != null
                        ? invocation.getTarget().getType().getSimpleName() : "";
                if ("WebClient".equals(targetTypeName)) {
                    String urlValue = extractStringValue(invocation.getArguments().getFirst());
                    if (urlValue != null) {
                        String targetServiceName = extractServiceNameFromUrl(urlValue);
                        if (targetServiceName != null) {
                            callMap.computeIfAbsent(targetServiceName.toLowerCase(), _ -> new ArrayList<>())
                                    .add(new CallEvidence(
                                            DependencyType.WEB_CLIENT,
                                            positionFile(invocation), positionLine(invocation),
                                            invocation.toString(), urlValue
                                    ));
                        }
                    }
                }
            }
        }
    }

    // ========================================================================================
    // SPOON HELPERS
    // ========================================================================================

    private CtModel buildSpoonModel(Path srcDir) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(srcDir.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.buildModel();
        return launcher.getModel();
    }

    private boolean isRestController(CtType<?> type) {
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            String name = annotation.getAnnotationType().getSimpleName();
            if ("RestController".equals(name) || "Controller".equals(name)) return true;
        }
        return false;
    }

    private String extractRequestMappingPath(CtType<?> type) {
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            if ("RequestMapping".equals(annotation.getAnnotationType().getSimpleName())) {
                return extractPathFromAnnotation(annotation);
            }
        }
        return "";
    }

    private record EndpointInfo(String path, HttpMethod httpMethod) {}

    private EndpointInfo extractEndpointInfo(CtMethod<?> method, String classLevelPath) {
        Map<String, HttpMethod> mappingAnnotations = Map.of(
                "GetMapping", HttpMethod.GET,
                "PostMapping", HttpMethod.POST,
                "PutMapping", HttpMethod.PUT,
                "DeleteMapping", HttpMethod.DELETE,
                "PatchMapping", HttpMethod.PATCH,
                "RequestMapping", HttpMethod.GET
        );

        for (CtAnnotation<?> annotation : method.getAnnotations()) {
            String annotationName = annotation.getAnnotationType().getSimpleName();
            HttpMethod httpMethod = mappingAnnotations.get(annotationName);

            if (httpMethod != null) {
                if ("RequestMapping".equals(annotationName)) {
                    httpMethod = extractHttpMethodFromRequestMapping(annotation);
                }
                String methodPath = extractPathFromAnnotation(annotation);
                return new EndpointInfo(normalizePath(classLevelPath + methodPath), httpMethod);
            }
        }
        return null;
    }

    private String extractPathFromAnnotation(CtAnnotation<?> annotation) {
        CtExpression<?> pathExpr = annotation.getValue("value");
        if (pathExpr == null) pathExpr = annotation.getValue("path");

        if (pathExpr != null) {
            String value = extractStringValue(pathExpr);
            if (value != null) return value;

            if (pathExpr instanceof CtNewArray<?> arrayExpr) {
                List<CtExpression<?>> elements = arrayExpr.getElements();
                if (!elements.isEmpty()) return extractStringValue(elements.getFirst());
            }
        }
        return "";
    }

    private HttpMethod extractHttpMethodFromRequestMapping(CtAnnotation<?> annotation) {
        CtExpression<?> methodExpr = annotation.getValue("method");
        if (methodExpr != null) {
            String methodStr = methodExpr.toString().toUpperCase();
            if (methodStr.contains("POST")) return HttpMethod.POST;
            if (methodStr.contains("PUT")) return HttpMethod.PUT;
            if (methodStr.contains("DELETE")) return HttpMethod.DELETE;
            if (methodStr.contains("PATCH")) return HttpMethod.PATCH;
            if (methodStr.contains("HEAD")) return HttpMethod.HEAD;
            if (methodStr.contains("OPTIONS")) return HttpMethod.OPTIONS;
        }
        return HttpMethod.GET;
    }

    private String extractFeignClientTarget(CtAnnotation<?> annotation) {
        for (String attr : List.of("name", "value", "url")) {
            CtExpression<?> expr = annotation.getValue(attr);
            if (expr != null) {
                String value = extractStringValue(expr);
                if (value != null && !value.isBlank()) return value;
            }
        }
        return null;
    }

    private String extractStringValue(CtExpression<?> expr) {
        if (expr instanceof CtLiteral<?> literal) {
            Object value = literal.getValue();
            return value != null ? value.toString() : null;
        }
        if (expr instanceof CtBinaryOperator<?> binOp) {
            String left = extractStringValue(binOp.getLeftHandOperand());
            String right = extractStringValue(binOp.getRightHandOperand());
            if (left != null && right != null) return left + right;
        }
        String repr = expr.toString();
        if (repr.startsWith("\"") && repr.endsWith("\"")) {
            return repr.substring(1, repr.length() - 1);
        }
        return null;
    }

    private boolean isWebClientChain(CtInvocation<?> invocation) {
        CtElement current = invocation;
        int depth = 0;
        while (current instanceof CtInvocation<?> inv && depth < 10) {
            if (inv.getTarget() != null) {
                CtTypeReference<?> targetType = inv.getTarget().getType();
                if (targetType != null && targetType.getSimpleName().contains("WebClient")) return true;
                if (inv.getTarget() instanceof CtInvocation<?> targetInv) {
                    current = targetInv;
                    depth++;
                    continue;
                }
            }
            break;
        }
        return false;
    }

    // ========================================================================================
    // URL / SERVICE NAME HELPERS
    // ========================================================================================

    private String extractServiceNameFromUrl(String url) {
        if (url == null) return null;
        String stripped = url.replaceFirst("https?://", "");
        int slashIdx = stripped.indexOf('/');
        String host = slashIdx > 0 ? stripped.substring(0, slashIdx) : stripped;
        int colonIdx = host.indexOf(':');
        if (colonIdx > 0) host = host.substring(0, colonIdx);
        if (host.equals("localhost") || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
                || host.isEmpty() || host.equals("0.0.0.0")) {
            return null;
        }
        return normalizeServiceName(host);
    }

    private String normalizeServiceName(String name) {
        if (name == null) return null;
        name = name.replaceFirst("https?://", "").toLowerCase().trim();
        int colonIdx = name.indexOf(':');
        if (colonIdx > 0) name = name.substring(0, colonIdx);
        int slashIdx = name.indexOf('/');
        if (slashIdx > 0) name = name.substring(0, slashIdx);
        return name;
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        return path.replaceAll("//+", "/");
    }

    private String extractVersionFromPath(String path) {
        Matcher matcher = Pattern.compile("/v(\\d+)").matcher(path);
        return matcher.find() ? "v" + matcher.group(1) : null;
    }

    // ========================================================================================
    // CONFIG HELPERS
    // ========================================================================================

    @SuppressWarnings("unchecked")
    private String extractNestedValue(Map<String, Object> map, String dotPath) {
        String[] keys = dotPath.split("\\.");
        Object current = map;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }
        return current != null ? current.toString() : null;
    }

    private String extractDbNameFromUrl(String url) {
        if (url == null) return null;
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash > 0 && lastSlash < url.length() - 1) {
            String dbPart = url.substring(lastSlash + 1);
            int paramIdx = dbPart.indexOf('?');
            return paramIdx > 0 ? dbPart.substring(0, paramIdx) : dbPart;
        }
        return null;
    }

    private String resolveDefaultPlaceholders(String value) {
        if (value == null) return null;
        return value.replaceAll("\\$\\{[^:}]+:([^}]+)}", "$1")
                .replaceAll("\\$\\{[^}]+}", "");
    }

    // ========================================================================================
    // POSITION HELPERS
    // ========================================================================================

    private String positionFile(CtElement element) {
        return element.getPosition().isValidPosition() ? element.getPosition().getFile().getPath() : "unknown";
    }

    private int positionLine(CtElement element) {
        return element.getPosition().isValidPosition() ? element.getPosition().getLine() : 0;
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    // ========================================================================================
    // INTERNAL TYPES
    // ========================================================================================

    private record CallEvidence(DependencyType dependencyType, String file, int line, String code, String url) {}
}

