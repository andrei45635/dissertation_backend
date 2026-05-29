package com.msadetector.service.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.entity.CodeSmell;
import com.msadetector.entity.DetectedAntiPattern;
import com.msadetector.entity.Microservice;
import com.msadetector.entity.Project;
import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;
import com.msadetector.enums.SourceType;
import com.msadetector.repository.CodeSmellRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GodServiceDetectorTest {

    @Mock
    private CodeSmellRepository codeSmellRepository;

    private GodServiceDetector detector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        detector = new GodServiceDetector(new ObjectMapper(), codeSmellRepository, 1);
    }

    private Project createProject() {
        Project project = new Project();
        project.setName("test-project");
        project.setSourceType(SourceType.UPLOAD);
        project.setLocalPath(tempDir.toString());
        return project;
    }

    private Microservice createMs(String name) {
        Microservice ms = new Microservice();
        ms.setName(name);
        ms.setRelativePath(name);
        return ms;
    }

    private CodeSmell createGodClassSmell(Microservice ms) {
        CodeSmell smell = new CodeSmell();
        smell.setSmellType("God Class");
        smell.setClassName("com.example.BigService");
        smell.setMicroservice(ms);
        smell.setSeverity(Severity.HIGH);
        return smell;
    }

    @Test
    void detect_noGodClassSmells_returnsEmpty() {
        Project project = createProject();
        Microservice ms = createMs("clean-svc");

        when(codeSmellRepository.findByMicroservice(ms)).thenReturn(List.of());

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_hasDesigniteGodClass_returnsPattern() {
        Project project = createProject();
        Microservice ms = createMs("big-svc");

        CodeSmell godSmell = createGodClassSmell(ms);
        when(codeSmellRepository.findByMicroservice(ms)).thenReturn(List.of(godSmell));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertEquals(1, results.size());
        DetectedAntiPattern pattern = results.getFirst();
        assertEquals(AntiPatternType.GOD_SERVICE, pattern.getPatternType());
        assertEquals(Severity.HIGH, pattern.getSeverity());
        assertTrue(pattern.getDescription().contains("big-svc"));
        assertTrue(pattern.getDescription().contains("God Class"));
    }

    @Test
    void detect_multipleGodClasses_reportsCount() {
        Project project = createProject();
        Microservice ms = createMs("mega-svc");

        CodeSmell smell1 = createGodClassSmell(ms);
        CodeSmell smell2 = createGodClassSmell(ms);
        smell2.setClassName("com.example.AnotherBigClass");

        when(codeSmellRepository.findByMicroservice(ms)).thenReturn(List.of(smell1, smell2));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertEquals(1, results.size());
        assertTrue(results.getFirst().getDescription().contains("2 God Class"));
    }

    @Test
    void detect_nonGodClassSmells_notFlagged() {
        Project project = createProject();
        Microservice ms = createMs("smelly-svc");

        CodeSmell featureEnvy = new CodeSmell();
        featureEnvy.setSmellType("Feature Envy");
        featureEnvy.setMicroservice(ms);
        featureEnvy.setSeverity(Severity.MEDIUM);

        when(codeSmellRepository.findByMicroservice(ms)).thenReturn(List.of(featureEnvy));

        // No src/main/java dir exists so Spoon won't find anything either
        List<DetectedAntiPattern> results = detector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_minGodClassesThreshold_respected() {
        // Require at least 3 god classes
        GodServiceDetector strictDetector = new GodServiceDetector(
                new ObjectMapper(), codeSmellRepository, 3);

        Project project = createProject();
        Microservice ms = createMs("svc");

        // Only 2 god classes — below threshold of 3
        when(codeSmellRepository.findByMicroservice(ms))
                .thenReturn(List.of(createGodClassSmell(ms), createGodClassSmell(ms)));

        // No src dir, so Spoon fallback won't detect anything
        List<DetectedAntiPattern> results = strictDetector.detect(project, List.of(ms));

        assertTrue(results.isEmpty());
    }

    @Test
    void detect_multipleServices_onlyFlagsGodOnes() {
        Project project = createProject();
        Microservice clean = createMs("clean-svc");
        Microservice god = createMs("god-svc");

        when(codeSmellRepository.findByMicroservice(clean)).thenReturn(List.of());
        when(codeSmellRepository.findByMicroservice(god))
                .thenReturn(List.of(createGodClassSmell(god)));

        List<DetectedAntiPattern> results = detector.detect(project, List.of(clean, god));

        assertEquals(1, results.size());
        assertTrue(results.getFirst().getDescription().contains("god-svc"));
    }
}

