package com.msadetector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;

public record GitCloneRequest(

        @NotBlank(message = "Repository URL is required")
        @Pattern(
                regexp = "^https://(github\\.com|gitlab\\.com)/[\\w.\\-]+/[\\w.\\-]+(\\.git)?$",
                message = "Only public GitHub and GitLab HTTPS URLs are supported"
        )
        String repoUrl,

        @NotBlank(message = "Project name is required")
        String name,

        String branch,

        @Valid
        AnalysisOptionsRequest options
) {}
