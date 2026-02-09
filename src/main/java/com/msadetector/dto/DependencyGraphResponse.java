package com.msadetector.dto;

import java.util.List;

public record DependencyGraphResponse(
        List<GraphNodeResponse> nodes,
        List<GraphEdgeResponse> edges
) {}

record GraphNodeResponse(
        String id,
        String name,
        Integer linesOfCode
) {}

record GraphEdgeResponse(
        String source,
        String target,
        String type,
        Integer weight
) {}
