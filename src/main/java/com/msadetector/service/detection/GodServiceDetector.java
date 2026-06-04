package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.AnalysisJob;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects god services - services handling too many responsibilities.
 * <p>
 * Uses a Spoon-based multi-metric analysis that scans each class for structural
 * indicators of God Class behaviour - high field count, high public method count,
 * high LOC, many import domains, many constructor parameters, and low cohesion (TCC).
 * A class is flagged as a God Class when enough metrics exceed their configured thresholds
 * simultaneously, and a service is reported as a God Service if it contains at
 * least one such class.
 */
@Component
public class GodServiceDetector extends BaseDetector {

    private static final String SRC_MAIN_JAVA = "src/main/java";

    private final int fieldCountThreshold;
    private final int publicMethodThreshold;
    private final int locThreshold;
    private final int importDomainThreshold;
    private final int constructorParamThreshold;
    private final double tccThreshold;
    private final int minMetricsExceeded;

    private static final Set<String> DATA_CLASS_ANNOTATIONS = Set.of(
            "Entity", "Table", "Document", "MappedSuperclass",
            "Embeddable", "Data", "Value", "Builder",
            "JsonIgnoreProperties", "XmlRootElement", "XmlType",
            "Generated", "AutoValue"
    );
    private static final Set<String> DATA_CLASS_NAME_SUFFIXES = Set.of(
            "dto", "entity", "model", "request", "response",
            "event", "message", "command", "query",
            "vo", "pojo", "bean", "record", "config",
            "properties", "settings", "options", "params",
            "result", "payload", "wrapper"
    );

    public GodServiceDetector(
            ObjectMapper objectMapper,
            @Value("${app.thresholds.god-service-field-count:25}") int fieldCountThreshold,
            @Value("${app.thresholds.god-service-public-methods:30}") int publicMethodThreshold,
            @Value("${app.thresholds.god-service-loc:1000}") int locThreshold,
            @Value("${app.thresholds.god-service-import-domains:20}") int importDomainThreshold,
            @Value("${app.thresholds.god-service-constructor-params:12}") int constructorParamThreshold,
            @Value("${app.thresholds.god-service-tcc-threshold:0.5}") double tccThreshold,
            @Value("${app.thresholds.god-service-min-metrics:3}") int minMetricsExceeded) {
        super(objectMapper);
        this.fieldCountThreshold = fieldCountThreshold;
        this.publicMethodThreshold = publicMethodThreshold;
        this.locThreshold = locThreshold;
        this.importDomainThreshold = importDomainThreshold;
        this.constructorParamThreshold = constructorParamThreshold;
        this.tccThreshold = tccThreshold;
        this.minMetricsExceeded = minMetricsExceeded;
    }

    @Override
    public List<DetectedAntiPattern> detect(Project project, List<Microservice> microservices, AnalysisJob job) {
        List<DetectedAntiPattern> patterns = new ArrayList<>();
        Path projectRoot = Path.of(project.getLocalPath());

        for (Microservice ms : microservices) {
            Path serviceSrcDir = projectRoot.resolve(ms.getRelativePath()).resolve(SRC_MAIN_JAVA);
            if (!Files.exists(serviceSrcDir)) {
                continue;
            }

            List<GodClassEvidence> godClasses = scanForGodClasses(serviceSrcDir);
            if (godClasses.isEmpty()) {
                continue;
            }

            StringBuilder desc = new StringBuilder();
            desc.append(String.format("Service '%s' exhibits God Service characteristics: ", ms.getName()));
            List<String> reasons = new ArrayList<>();
            for (GodClassEvidence ev : godClasses) {
                reasons.add(String.format("class '%s' exceeds %d God Class metrics (%s)",
                        ev.className, ev.exceededMetrics.size(),
                        String.join(", ", ev.exceededMetrics)));
            }
            desc.append(String.join("; ", reasons));

            String descriptionStr = desc.length() > 1000
                    ? desc.substring(0, 997) + "..."
                    : desc.toString();

            List<Map<String, Object>> snippets = new ArrayList<>();
            for (GodClassEvidence ev : godClasses) {
                if (ev.filePath != null) {
                    Map<String, Object> snippet = readSnippet(Path.of(ev.filePath), ev.lineNumber, 10);
                    if (snippet != null) snippets.add(snippet);
                }
            }

            List<Map<String, Object>> godClassDetails = new ArrayList<>();
            for (GodClassEvidence ev : godClasses) {
                godClassDetails.add(Map.of(
                        "className", ev.className,
                        "metrics", ev.metrics,
                        "exceededMetrics", ev.exceededMetrics
                ));
            }

            DetectedAntiPattern pattern = DetectedAntiPattern.builder()
                    .patternType(AntiPatternType.GOD_SERVICE)
                    .severity(Severity.HIGH)
                    .description(descriptionStr)
                    .affectedServicesJson(toJson(List.of(ms.getName())))
                    .primaryService(ms)
                    .detailsJson(toJson(Map.of(
                            "godClasses", godClassDetails
                    )))
                    .codeSnippetsJson(snippetsToJson(snippets))
                    .remediation("Consider decomposing into smaller, focused services with single responsibilities. "
                            + "Extract unrelated concerns (e.g. email, file handling, caching) into dedicated services.")
                    .build();

            patterns.add(pattern);
        }

        return patterns;
    }

    /**
     * Scans all classes in a source directory and returns those that exhibit
     * God Class characteristics based on multiple structural metrics.
     * A class is flagged when enough structural metrics exceed their configured
     * thresholds:
     * <ul>
     *   <li>Field count</li>
     *   <li>Public method count</li>
     *   <li>Lines of code</li>
     *   <li>Import domain count</li>
     *   <li>Constructor parameter count</li>
     *   <li>Tight Class Cohesion (TCC)</li>
     * </ul>
     */
    private List<GodClassEvidence> scanForGodClasses(Path srcDir) {
        List<GodClassEvidence> results = new ArrayList<>();

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
                if (!type.isClass() || type.isAbstract()) continue;
                if (type.isEnum() || type.getSimpleName().isEmpty()) continue;
                if (isDataClass(type)) continue;

                try {
                    GodClassEvidence evidence = analyzeClass(type);
                    if (evidence != null) {
                        results.add(evidence);
                    }
                } catch (Exception e) {
                    log.debug("Skipping class {} for God Class analysis: {}",
                            type.getQualifiedName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to build Spoon model for God Class analysis in {}: {}", srcDir, e.getMessage());
        }

        return results;
    }

    /**
     * Checks whether a class is a data/model class (entity, DTO, VO, etc.)
     * that should be excluded from God Class detection. Data classes naturally
     * have many fields and low cohesion but are not architectural anti-patterns.
     * <p>
     * A class is considered a data class when any of the following holds:
     * <ul>
     *   <li>It carries a data-class annotation (e.g. {@code @Entity}, {@code @Data})</li>
     *   <li>Its name ends with a data-class suffix (e.g. Dto, Entity, Model)</li>
     *   <li>More than 60% of its public methods are getters or setters</li>
     * </ul>
     */
    private boolean isDataClass(CtType<?> type) {
        boolean hasDataAnnotation = type.getAnnotations().stream()
                .anyMatch(a -> DATA_CLASS_ANNOTATIONS.contains(a.getAnnotationType().getSimpleName()));
        if (hasDataAnnotation) return true;

        String nameLower = type.getSimpleName().toLowerCase();
        boolean hasDataSuffix = DATA_CLASS_NAME_SUFFIXES.stream()
                .anyMatch(nameLower::endsWith);
        if (hasDataSuffix) return true;

        Set<CtMethod<?>> publicMethods = type.getMethods().stream()
                .filter(m -> m.hasModifier(ModifierKind.PUBLIC))
                .collect(Collectors.toSet());
        if (publicMethods.size() >= 4) {
            long accessorCount = publicMethods.stream()
                    .filter(m -> {
                        String name = m.getSimpleName();
                        return (name.startsWith("get") || name.startsWith("set")
                                || name.startsWith("is")) && m.getParameters().size() <= 1;
                    })
                    .count();
            if ((double) accessorCount / publicMethods.size() > 0.6) return true;
        }

        return false;
    }

    /**
     * Analyzes a single class against God Class metrics.
     * Returns evidence if the configured minimum number of metrics are exceeded,
     * null otherwise.
     */
    private GodClassEvidence analyzeClass(CtType<?> type) {
        int fieldCount = type.getFields().size();

        int publicMethodCount = (int) type.getMethods().stream()
                .filter(m -> m.hasModifier(ModifierKind.PUBLIC))
                .count();

        int loc = 0;
        if (type.getPosition().isValidPosition()) {
            loc = type.getPosition().getEndLine() - type.getPosition().getLine() + 1;
        }

        Set<String> importDomains = new HashSet<>();
        for (CtTypeReference<?> ref : type.getReferencedTypes()) {
            String qualifiedName = ref.getQualifiedName();
            if (qualifiedName != null && qualifiedName.contains(".")) {
                String[] parts = qualifiedName.split("\\.");
                if (parts.length >= 2) {
                    String domain = parts[0] + "." + parts[1];
                    if (!"java.lang".equals(domain)) {
                        importDomains.add(domain);
                    }
                }
            }
        }
        int importDomainCount = importDomains.size();

        int maxConstructorParams = 0;
        if (type instanceof CtClass<?> ctClass) {
            for (CtConstructor<?> constructor : ctClass.getConstructors()) {
                maxConstructorParams = Math.max(maxConstructorParams, constructor.getParameters().size());
            }
        }

        double tcc = computeTCC(type);

        List<String> exceededMetrics = new ArrayList<>();
        if (fieldCount >= fieldCountThreshold)
            exceededMetrics.add("fields=" + fieldCount + " (>=" + fieldCountThreshold + ")");
        if (publicMethodCount >= publicMethodThreshold)
            exceededMetrics.add("publicMethods=" + publicMethodCount + " (>=" + publicMethodThreshold + ")");
        if (loc >= locThreshold)
            exceededMetrics.add("LOC=" + loc + " (>=" + locThreshold + ")");
        if (importDomainCount >= importDomainThreshold)
            exceededMetrics.add("importDomains=" + importDomainCount + " (>=" + importDomainThreshold + ")");
        if (maxConstructorParams >= constructorParamThreshold)
            exceededMetrics.add("constructorParams=" + maxConstructorParams + " (>=" + constructorParamThreshold + ")");
        if (tcc >= 0 && tcc < tccThreshold)
            exceededMetrics.add(String.format("TCC=%.2f (<%.2f)", tcc, tccThreshold));

        if (exceededMetrics.size() >= minMetricsExceeded) {
            String filePath = type.getPosition().isValidPosition()
                    ? type.getPosition().getFile().getPath() : null;
            int lineNumber = type.getPosition().isValidPosition()
                    ? type.getPosition().getLine() : 1;

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("fieldCount", fieldCount);
            metrics.put("publicMethodCount", publicMethodCount);
            metrics.put("loc", loc);
            metrics.put("importDomainCount", importDomainCount);
            metrics.put("maxConstructorParams", maxConstructorParams);
            metrics.put("tcc", tcc);

            return new GodClassEvidence(type.getQualifiedName(), filePath, lineNumber,
                    metrics, exceededMetrics);
        }

        return null;
    }

    /**
     * Computes Tight Class Cohesion (TCC) - the fraction of method pairs that
     * share at least one instance field access.
     * <p>
     * TCC = (connected pairs) / (total pairs)
     * <p>
     * Returns -1 if the class has fewer than 2 methods (TCC is undefined).
     */
    private double computeTCC(CtType<?> type) {
        List<CtMethod<?>> methods = type.getMethods().stream()
                .filter(m -> m.hasModifier(ModifierKind.PUBLIC))
                .collect(Collectors.toList());
        if (methods.size() < 2) return -1;

        List<Set<String>> methodFieldSets = new ArrayList<>();
        Set<String> instanceFieldNames = type.getFields().stream()
                .filter(f -> !f.hasModifier(ModifierKind.STATIC))
                .map(CtNamedElement::getSimpleName)
                .collect(Collectors.toSet());

        for (CtMethod<?> method : methods) {
            Set<String> accessedFields = new HashSet<>();

            for (CtFieldRead<?> read : method.getElements(new TypeFilter<>(CtFieldRead.class))) {
                String fieldName = read.getVariable().getSimpleName();
                if (instanceFieldNames.contains(fieldName)) {
                    accessedFields.add(fieldName);
                }
            }
            for (CtFieldWrite<?> write : method.getElements(new TypeFilter<>(CtFieldWrite.class))) {
                String fieldName = write.getVariable().getSimpleName();
                if (instanceFieldNames.contains(fieldName)) {
                    accessedFields.add(fieldName);
                }
            }

            methodFieldSets.add(accessedFields);
        }

        int totalPairs = 0;
        int connectedPairs = 0;
        for (int i = 0; i < methodFieldSets.size(); i++) {
            for (int j = i + 1; j < methodFieldSets.size(); j++) {
                totalPairs++;
                Set<String> fieldsI = methodFieldSets.get(i);
                Set<String> fieldsJ = methodFieldSets.get(j);
                if (!fieldsI.isEmpty() && !fieldsJ.isEmpty()) {
                    boolean shared = fieldsI.stream().anyMatch(fieldsJ::contains);
                    if (shared) connectedPairs++;
                }
            }
        }

        return totalPairs > 0 ? (double) connectedPairs / totalPairs : -1;
    }

    private record GodClassEvidence(
            String className,
            String filePath,
            int lineNumber,
            Map<String, Object> metrics,
            List<String> exceededMetrics
    ) {}
}
