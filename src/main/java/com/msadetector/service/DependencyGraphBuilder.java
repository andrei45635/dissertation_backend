package com.msadetector.service;

import com.msadetector.entity.Endpoint;
import com.msadetector.entity.AnalysisJob;
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

/**
 * Builds the dependency graph for a microservices project by scanning source
 * code with Spoon and parsing configuration files.
 */
@Service
public class DependencyGraphBuilder {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    private static final String SRC_MAIN_JAVA = "src/main/java";

    private static final Pattern API_VERSION_PATTERN = Pattern.compile("/v(\\d+)([/.]|$)");
    private static final Pattern HEADER_VERSION_PATTERN = Pattern.compile(
            "(?i)(x-api-version|api-version|accept-version|api\\.version)"
    );
    private static final Pattern MEDIA_TYPE_VERSION_PATTERN = Pattern.compile("(?i)vnd\\.[\\w.-]+\\.v\\d+");
    private static final Pattern QUERY_VERSION_PATTERN = Pattern.compile(
            "(?i)(^|[^a-z0-9_-])(version|api-version|api\\.version|v)\\s*="
    );

    /** Matches Spring property placeholders like ${some.prop} or ${some.prop:default} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?}");

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
    public void buildDependencyGraph(Project project, AnalysisJob job) {
        List<Microservice> microservices = microserviceRepository.findByAnalysisJob(job);
        Path projectRoot = Path.of(project.getLocalPath());

        Map<String, String> portToServiceName = new HashMap<>();
        Map<String, Microservice> serviceByName = new HashMap<>();

        for (Microservice ms : microservices) {
            Path servicePath = projectRoot.resolve(ms.getRelativePath());
            parseDatasourceConfig(ms, servicePath);
            parseEndpoints(ms, servicePath);

            Map<String, String> configProps = readConfigProperties(servicePath);
            String appName = configProps.get("spring.application.name");
            String serverPort = configProps.get("server.port");

            serviceByName.put(ms.getName().toLowerCase(), ms);

            if (appName != null && !appName.isBlank()) {
                String resolved = resolveDefaultPlaceholders(appName).toLowerCase().trim();
                if (!resolved.isBlank()) {
                    serviceByName.putIfAbsent(resolved, ms);
                    log.debug("Registered alias '{}' for service '{}'", resolved, ms.getName());
                }
            }

            if (serverPort != null && !serverPort.isBlank()) {
                String resolvedPort = resolveDefaultPlaceholders(serverPort).trim();
                if (!resolvedPort.isBlank() && resolvedPort.matches("\\d+")) {
                    portToServiceName.put(resolvedPort, ms.getName().toLowerCase());
                    log.debug("Registered port {} for service '{}'", resolvedPort, ms.getName());
                }
            }
        }

        log.info("Service name lookup has {} entries: {}", serviceByName.size(), serviceByName.keySet());

        for (Microservice ms : microservices) {
            Path servicePath = projectRoot.resolve(ms.getRelativePath());
            detectInterServiceCalls(ms, servicePath, serviceByName, portToServiceName);
        }
    }

    /**
     * Reads common configuration properties from a service's application.yml or
     * application.properties. Returns a flat map of property keys to values.
     */
    private Map<String, String> readConfigProperties(Path servicePath) {
        Map<String, String> props = new HashMap<>();
        Path resourcesDir = servicePath.resolve("src/main/resources");

        for (String filename : List.of("application.yml", "application.yaml")) {
            Path yamlFile = resourcesDir.resolve(filename);
            if (Files.exists(yamlFile)) {
                try (InputStream is = Files.newInputStream(yamlFile)) {
                    Yaml yaml = new Yaml();
                    Map<String, Object> config = yaml.load(is);
                    if (config != null) {
                        flattenYaml("", config, props);
                    }
                } catch (Exception e) {
                    log.debug("Could not read config from {}: {}", yamlFile, e.getMessage());
                }
                return props;
            }
        }

        Path propsFile = resourcesDir.resolve("application.properties");
        if (Files.exists(propsFile)) {
            try {
                Properties javaProps = new Properties();
                javaProps.load(Files.newInputStream(propsFile));
                javaProps.forEach((k, v) -> props.put(k.toString(), v.toString()));
            } catch (IOException e) {
                log.debug("Could not read config from {}: {}", propsFile, e.getMessage());
            }
        }

        return props;
    }

    /**
     * Flattens a nested YAML map into dot-separated property keys.
     */
    @SuppressWarnings("unchecked")
    private void flattenYaml(String prefix, Map<String, Object> map, Map<String, String> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenYaml(key, (Map<String, Object>) value, result);
            } else if (value != null) {
                result.put(key, value.toString());
            }
        }
    }

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
                try {
                    if (!isRestController(type)) continue;

                    String classLevelPath = extractRequestMappingPath(type);

                    for (CtMethod<?> method : type.getMethods()) {
                        EndpointInfo info = extractEndpointInfo(type, method, classLevelPath);
                        if (info != null) {
                            Endpoint endpoint = Endpoint.builder()
                                    .path(info.path())
                                    .httpMethod(info.httpMethod())
                                    .controllerClass(type.getQualifiedName())
                                    .methodName(method.getSimpleName())
                                    .hasVersioning(info.hasVersioning())
                                    .apiVersion(info.apiVersion())
                                    .microservice(ms)
                                    .build();

                            endpoints.add(endpoint);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Skipping class {} in service {}: {}", type.getQualifiedName(), ms.getName(), e.getMessage());
                }
            }

            if (!endpoints.isEmpty()) {
                endpointRepository.saveAll(endpoints);
                ms.setNumberOfEndpoints(endpoints.size());
                microserviceRepository.save(ms);
                log.info("Detected {} endpoints in service {}", endpoints.size(), ms.getName());
            }

        } catch (Exception e) {
            log.warn("Failed to build Spoon model for service {}: {}", ms.getName(), e.getMessage());
        }
    }

    private void detectInterServiceCalls(Microservice sourceService, Path servicePath,
                                          Map<String, Microservice> serviceByName,
                                          Map<String, String> portToServiceName) {
        Path srcDir = servicePath.resolve(SRC_MAIN_JAVA);
        if (!Files.exists(srcDir)) return;

        Map<String, String> serviceConfig = readConfigProperties(servicePath);

        try {
            CtModel model = buildSpoonModel(srcDir);

            Map<String, String> valueFieldUrls = extractValueAnnotatedFields(model, serviceConfig);

            Map<String, List<CallEvidence>> callMap = new HashMap<>();

            for (CtType<?> type : model.getAllTypes()) {
                try {
                    detectFeignClients(type, callMap, serviceConfig);
                    detectRestTemplateCalls(type, callMap, valueFieldUrls);
                    detectWebClientCalls(type, callMap, valueFieldUrls);
                } catch (Exception e) {
                    log.debug("Skipping class {} for inter-service call detection: {}", type.getQualifiedName(), e.getMessage());
                }
            }

            for (Map.Entry<String, List<CallEvidence>> entry : callMap.entrySet()) {
                String rawTarget = entry.getKey().toLowerCase();
                Microservice targetService = resolveTargetService(rawTarget, serviceByName, portToServiceName);

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
                    log.info("Dependency: {} -> {} ({} calls, type: {}, raw target: '{}')",
                            sourceService.getName(), targetService.getName(),
                            evidenceList.size(), depType, rawTarget);
                } else if (targetService == null) {
                    log.debug("Unresolved dependency target '{}' from service '{}' ({} calls)",
                            rawTarget, sourceService.getName(), entry.getValue().size());
                }
            }

        } catch (Exception e) {
            log.warn("Failed to detect inter-service calls for {}: {}", sourceService.getName(), e.getMessage());
        }
    }

    /**
     * Resolves a raw target name (from a Feign client or URL) to an actual Microservice.
     * Tries multiple strategies:
     * 1. Exact match against service names / spring.application.name aliases
     * 2. Port-based matching for localhost:PORT
     * 3. Fuzzy substring matching (target contains service name or vice versa)
     */
    private Microservice resolveTargetService(String rawTarget,
                                               Map<String, Microservice> serviceByName,
                                               Map<String, String> portToServiceName) {
        Microservice exact = serviceByName.get(rawTarget);
        if (exact != null) return exact;

        String port = extractPortFromTarget(rawTarget);
        if (port != null) {
            String serviceName = portToServiceName.get(port);
            if (serviceName != null) {
                Microservice byPort = serviceByName.get(serviceName);
                if (byPort != null) {
                    log.debug("Resolved '{}' via port {} -> '{}'", rawTarget, port, serviceName);
                    return byPort;
                }
            }
        }

        for (Map.Entry<String, Microservice> entry : serviceByName.entrySet()) {
            String knownName = entry.getKey();
            if (rawTarget.contains(knownName) || knownName.contains(rawTarget)) {
                log.debug("Fuzzy matched '{}' to known service '{}'", rawTarget, knownName);
                return entry.getValue();
            }
            String stripped = knownName.replaceAll("-(service|svc|ms|api|server|app)$", "");
            if (!stripped.equals(knownName) && (rawTarget.contains(stripped) || stripped.contains(rawTarget))) {
                log.debug("Fuzzy matched '{}' to known service '{}' (stripped: '{}')", rawTarget, knownName, stripped);
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Extracts port number from a raw target that might look like "localhost:8081"
     * or just "8081".
     */
    private String extractPortFromTarget(String rawTarget) {
        int colonIdx = rawTarget.lastIndexOf(':');
        if (colonIdx >= 0 && colonIdx < rawTarget.length() - 1) {
            String portPart = rawTarget.substring(colonIdx + 1);
            int slashIdx = portPart.indexOf('/');
            if (slashIdx > 0) portPart = portPart.substring(0, slashIdx);
            if (portPart.matches("\\d{4,5}")) return portPart;
        }
        return null;
    }

    /**
     * Pre-scans the Spoon model for fields annotated with @Value that contain URL-like
     * values, resolving them against the service's configuration properties.
     * Returns a map of field name -> resolved URL value.
     */
    private Map<String, String> extractValueAnnotatedFields(CtModel model, Map<String, String> serviceConfig) {
        Map<String, String> fieldUrls = new HashMap<>();

        for (CtType<?> type : model.getAllTypes()) {
            for (CtField<?> field : type.getFields()) {
                for (CtAnnotation<?> annotation : field.getAnnotations()) {
                    try {
                        if ("Value".equals(annotation.getAnnotationType().getSimpleName())) {
                            String valueExpr = extractStringValue(annotation.getValue("value"));
                            if (valueExpr == null) continue;

                            String resolvedUrl = resolveSpringPlaceholder(valueExpr, serviceConfig);
                            if (resolvedUrl != null && !resolvedUrl.isBlank()) {
                                fieldUrls.put(field.getSimpleName(), resolvedUrl);
                                log.debug("@Value field '{}' resolved to '{}'", field.getSimpleName(), resolvedUrl);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return fieldUrls;
    }

    /**
     * Resolves a Spring property placeholder like "${user-service.url:http://localhost:8081}"
     * against the provided config properties. Falls back to the default value if present.
     */
    private String resolveSpringPlaceholder(String expression, Map<String, String> config) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(expression);
        if (matcher.find()) {
            String propKey = matcher.group(1);
            String defaultValue = matcher.group(2);

            String configValue = config.get(propKey);
            if (configValue != null && !configValue.isBlank()) {
                return resolveDefaultPlaceholders(configValue);
            }
            if (defaultValue != null && !defaultValue.isBlank()) {
                return defaultValue;
            }
        }
        if (expression.startsWith("http://") || expression.startsWith("https://")) {
            return expression;
        }
        return null;
    }

    private void detectFeignClients(CtType<?> type, Map<String, List<CallEvidence>> callMap,
                                     Map<String, String> serviceConfig) {
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            try {
                String annotationName = annotation.getAnnotationType().getSimpleName();
                if ("FeignClient".equals(annotationName)) {
                    String targetName = extractFeignClientTarget(annotation);
                    if (targetName != null) {
                        String resolved = resolveSpringPlaceholder(targetName, serviceConfig);
                        if (resolved != null) targetName = resolved;

                        String normalizedTarget = normalizeServiceName(targetName);

                        Set<CtMethod<?>> methods = type.getMethods();
                        if (methods.isEmpty()) {
                            callMap.computeIfAbsent(normalizedTarget, _ -> new ArrayList<>())
                                    .add(new CallEvidence(
                                            DependencyType.FEIGN_CLIENT,
                                            positionFile(type), positionLine(type),
                                            "@FeignClient targeting " + targetName,
                                            targetName
                                    ));
                        } else {
                            for (CtMethod<?> method : methods) {
                                if (method.isDefaultMethod() || method.hasModifier(spoon.reflect.declaration.ModifierKind.STATIC)) continue;

                                callMap.computeIfAbsent(normalizedTarget, _ -> new ArrayList<>())
                                        .add(new CallEvidence(
                                                DependencyType.FEIGN_CLIENT,
                                                positionFile(method), positionLine(method),
                                                "@FeignClient method " + type.getSimpleName() + "." + method.getSimpleName() + "()",
                                                targetName
                                        ));
                            }
                        }

                        log.debug("Found @FeignClient in {} targeting '{}' (normalized: '{}', {} methods)",
                                type.getQualifiedName(), targetName, normalizedTarget, methods.size());
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void detectRestTemplateCalls(CtType<?> type, Map<String, List<CallEvidence>> callMap,
                                          Map<String, String> valueFieldUrls) {
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

            String urlValue = extractUrlFromExpression(invocation.getArguments().getFirst(), valueFieldUrls);

            if (urlValue != null) {
                String targetServiceName = extractServiceNameFromUrl(urlValue);
                if (targetServiceName != null) {
                    callMap.computeIfAbsent(targetServiceName.toLowerCase(), _ -> new ArrayList<>())
                            .add(new CallEvidence(
                                    DependencyType.REST_TEMPLATE,
                                    positionFile(invocation), positionLine(invocation),
                                    truncate(invocation.toString(), 200), urlValue
                            ));
                }
            }
        }
    }

    private void detectWebClientCalls(CtType<?> type, Map<String, List<CallEvidence>> callMap,
                                       Map<String, String> valueFieldUrls) {
        for (CtInvocation<?> invocation : type.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (invocation.getExecutable() == null) continue;

            String methodName = invocation.getExecutable().getSimpleName();

            if ("uri".equals(methodName) && !invocation.getArguments().isEmpty() && isWebClientChain(invocation)) {
                String urlValue = extractUrlFromExpression(invocation.getArguments().getFirst(), valueFieldUrls);
                if (urlValue != null) {
                    String targetServiceName = extractServiceNameFromUrl(urlValue);
                    if (targetServiceName != null) {
                        callMap.computeIfAbsent(targetServiceName.toLowerCase(), _ -> new ArrayList<>())
                                .add(new CallEvidence(
                                        DependencyType.WEB_CLIENT,
                                        positionFile(invocation), positionLine(invocation),
                                        truncate(invocation.toString(), 200), urlValue
                                ));
                    }
                }
            }

            if (("create".equals(methodName) || "baseUrl".equals(methodName))
                    && !invocation.getArguments().isEmpty()) {
                boolean isWebClient = false;
                if (invocation.getTarget() != null) {
                    String targetTypeName = invocation.getTarget().getType() != null
                            ? invocation.getTarget().getType().getSimpleName() : "";
                    isWebClient = targetTypeName.contains("WebClient");
                }
                if (isWebClient || isWebClientChain(invocation)) {
                    String urlValue = extractUrlFromExpression(invocation.getArguments().getFirst(), valueFieldUrls);
                    if (urlValue != null) {
                        String targetServiceName = extractServiceNameFromUrl(urlValue);
                        if (targetServiceName != null) {
                            callMap.computeIfAbsent(targetServiceName.toLowerCase(), _ -> new ArrayList<>())
                                    .add(new CallEvidence(
                                            DependencyType.WEB_CLIENT,
                                            positionFile(invocation), positionLine(invocation),
                                            truncate(invocation.toString(), 200), urlValue
                                    ));
                        }
                    }
                }
            }
        }
    }

    /**
     * Attempts to extract a URL string from a Spoon expression. Tries multiple strategies:
     * 1. Direct string literal extraction
     * 2. If the expression is a field access, check if that field has an @Value URL
     * 3. If it's a concatenation with a field, resolve the field part
     */
    private String extractUrlFromExpression(CtExpression<?> expr, Map<String, String> valueFieldUrls) {
        String direct = extractStringValue(expr);
        if (direct != null) return direct;

        if (expr instanceof CtFieldRead<?> fieldRead) {
            String fieldName = fieldRead.getVariable().getSimpleName();
            String resolved = valueFieldUrls.get(fieldName);
            if (resolved != null) {
                log.debug("Resolved field '{}' to URL '{}'", fieldName, resolved);
                return resolved;
            }
        }

        if (expr instanceof CtBinaryOperator<?> binOp) {
            String left = extractUrlFromExpression(binOp.getLeftHandOperand(), valueFieldUrls);
            String right = extractStringValue(binOp.getRightHandOperand());
            if (left != null && right != null) return left + right;
            if (left != null) return left;
        }

        if (expr instanceof CtVariableRead<?> varRead) {
            String varName = varRead.getVariable().getSimpleName();
            String resolved = valueFieldUrls.get(varName);
            if (resolved != null) return resolved;
        }

        return null;
    }

    private CtModel buildSpoonModel(Path srcDir) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(srcDir.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.getEnvironment().setShouldCompile(false);
        launcher.getEnvironment().setIgnoreSyntaxErrors(true);
        launcher.getEnvironment().setLevel("OFF");
        launcher.buildModel();
        return launcher.getModel();
    }

    private boolean isRestController(CtType<?> type) {
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            try {
                String name = annotation.getAnnotationType().getSimpleName();
                if ("RestController".equals(name) || "Controller".equals(name)) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String extractRequestMappingPath(CtType<?> type) {
        for (CtAnnotation<?> annotation : type.getAnnotations()) {
            try {
                if ("RequestMapping".equals(annotation.getAnnotationType().getSimpleName())) {
                    return extractPathFromAnnotation(annotation);
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private record EndpointInfo(String path, HttpMethod httpMethod, boolean hasVersioning, String apiVersion) {}

    private EndpointInfo extractEndpointInfo(CtType<?> type, CtMethod<?> method, String classLevelPath) {
        Map<String, HttpMethod> mappingAnnotations = Map.of(
                "GetMapping", HttpMethod.GET,
                "PostMapping", HttpMethod.POST,
                "PutMapping", HttpMethod.PUT,
                "DeleteMapping", HttpMethod.DELETE,
                "PatchMapping", HttpMethod.PATCH,
                "RequestMapping", HttpMethod.GET
        );

        for (CtAnnotation<?> annotation : method.getAnnotations()) {
            try {
                String annotationName = annotation.getAnnotationType().getSimpleName();
                HttpMethod httpMethod = mappingAnnotations.get(annotationName);

                if (httpMethod != null) {
                    if ("RequestMapping".equals(annotationName)) {
                        httpMethod = extractHttpMethodFromRequestMapping(annotation);
                    }
                    String methodPath = extractPathFromAnnotation(annotation);
                    String fullPath = normalizePath(classLevelPath + methodPath);
                    VersionInfo versionInfo = detectVersioning(fullPath, type.getAnnotations(), method.getAnnotations());
                    return new EndpointInfo(fullPath, httpMethod, versionInfo.hasVersioning(), versionInfo.apiVersion());
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private record VersionInfo(boolean hasVersioning, String apiVersion) {}

    private VersionInfo detectVersioning(String path,
                                         Collection<CtAnnotation<?>> classAnnotations,
                                         Collection<CtAnnotation<?>> methodAnnotations) {
        String pathVersion = extractVersionFromPath(path);
        if (pathVersion != null) {
            return new VersionInfo(true, pathVersion);
        }

        for (CtAnnotation<?> annotation : classAnnotations) {
            VersionInfo info = detectAnnotationVersioning(annotation);
            if (info.hasVersioning()) return info;
        }
        for (CtAnnotation<?> annotation : methodAnnotations) {
            VersionInfo info = detectAnnotationVersioning(annotation);
            if (info.hasVersioning()) return info;
        }

        return new VersionInfo(false, null);
    }

    private VersionInfo detectAnnotationVersioning(CtAnnotation<?> annotation) {
        String text = annotation.toString();
        String lower = text.toLowerCase(Locale.ROOT);

        if (lower.contains("headers") && HEADER_VERSION_PATTERN.matcher(text).find()) {
            return new VersionInfo(true, "header");
        }

        if ((lower.contains("produces") || lower.contains("consumes"))
                && MEDIA_TYPE_VERSION_PATTERN.matcher(text).find()) {
            return new VersionInfo(true, "header");
        }

        if (lower.contains("params") && QUERY_VERSION_PATTERN.matcher(text).find()) {
            return new VersionInfo(true, "query");
        }

        return new VersionInfo(false, null);
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

    /**
     * Extracts a target service identifier from a URL. Now handles localhost:PORT by
     * preserving the port information for later resolution.
     */
    private String extractServiceNameFromUrl(String url) {
        if (url == null) return null;
        String stripped = url.replaceFirst("https?://", "");
        int slashIdx = stripped.indexOf('/');
        String hostPort = slashIdx > 0 ? stripped.substring(0, slashIdx) : stripped;

        int colonIdx = hostPort.indexOf(':');
        String host = colonIdx > 0 ? hostPort.substring(0, colonIdx) : hostPort;
        String port = colonIdx > 0 ? hostPort.substring(colonIdx + 1) : null;

        if (host.isEmpty() || host.equals("0.0.0.0")) {
            return null;
        }

        if (host.equals("localhost") || host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            if (port != null && port.matches("\\d+")) {
                return "localhost:" + port;
            }
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
        Matcher matcher = API_VERSION_PATTERN.matcher(path);
        return matcher.find() ? "v" + matcher.group(1) : null;
    }

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

    private record CallEvidence(DependencyType dependencyType, String file, int line, String code, String url) {}
}
