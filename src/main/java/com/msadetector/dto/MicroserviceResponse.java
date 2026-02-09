package com.msadetector.dto;

public record MicroserviceResponse(
        Long id,
        String name,
        String relativePath,
        Integer linesOfCode,
        Integer numberOfEndpoints
) {}
