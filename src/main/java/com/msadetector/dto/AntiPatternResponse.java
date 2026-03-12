package com.msadetector.dto;

import com.msadetector.enums.AntiPatternType;
import com.msadetector.enums.Severity;

import java.util.List;

public record AntiPatternResponse(
        Long id,
        AntiPatternType patternType,
        Severity severity,
        String description,
        List<String> affectedServices,
        String remediation,
        List<CodeSnippet> codeSnippets
) {
    public record CodeSnippet(
            String file,
            int startLine,
            int endLine,
            int highlightLine,
            String snippet
    ) {}
}
