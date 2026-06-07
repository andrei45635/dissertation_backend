package com.msadetector.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Optional body for {@code POST /api/projects/{id}/reanalyze}.
 * <p>
 * If {@code repoUrl} is provided, the project's source is switched to Git
 * and the repo is cloned. If omitted (or the body is empty), the existing
 * source is reused (re-clone for Git projects, re-scan for uploads).
 */
public record ReanalyzeRequest(

        @Pattern(
                regexp = "^https://(github\\.com|gitlab\\.com)/[\\w.\\-]+/[\\w.\\-]+(\\.git)?$",
                message = "Only public GitHub and GitLab HTTPS URLs are supported"
        )
        String repoUrl,

        String branch,

        @Valid
        AnalysisOptionsRequest options
) {}

