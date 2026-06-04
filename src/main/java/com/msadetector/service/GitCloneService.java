package com.msadetector.service;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * Handles cloning public Git repositories from GitHub and GitLab.
 */
@Service
public class GitCloneService {

    private static final Logger log = LoggerFactory.getLogger(GitCloneService.class);

    private static final Pattern REPO_URL_PATTERN = Pattern.compile(
            "^https://(github\\.com|gitlab\\.com)/[\\w.\\-]+/[\\w.\\-]+(\\.git)?$"
    );

    private final Path workspaceDir;
    private final int cloneTimeoutSeconds;

    public GitCloneService(
            @Value("${app.analysis.workspace-dir}") String workspaceDir,
            @Value("${app.analysis.clone-timeout-seconds:300}") int cloneTimeoutSeconds
    ) {
        this.workspaceDir = Path.of(workspaceDir);
        this.cloneTimeoutSeconds = cloneTimeoutSeconds;
    }

    /**
     * Validates that the URL is a supported public GitHub/GitLab HTTPS URL.
     *
     * @throws IllegalArgumentException if the URL is invalid or unsupported
     */
    public void validateRepoUrl(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("Repository URL is required");
        }

        if (!REPO_URL_PATTERN.matcher(repoUrl).matches()) {
            throw new IllegalArgumentException(
                    "Invalid repository URL. Only public GitHub and GitLab HTTPS URLs are supported. "
                            + "Expected format: https://github.com/owner/repo or https://gitlab.com/owner/repo"
            );
        }
    }

    /**
     * Clones a public Git repository into the workspace directory.
     *
     * @param repoUrl   the HTTPS clone URL (GitHub or GitLab)
     * @param projectId used to create a unique directory name
     * @param branch    the branch to clone; if null/blank, uses the remote's default branch
     * @return the path to the cloned repository on disk
     */
    public Path cloneRepository(String repoUrl, Long projectId, String branch) {
        validateRepoUrl(repoUrl);

        String normalizedUrl = repoUrl.endsWith(".git") ? repoUrl : repoUrl + ".git";

        Path targetDir = workspaceDir.resolve(projectId.toString());

        try {
            Files.createDirectories(targetDir);

            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(normalizedUrl)
                    .setDirectory(targetDir.toFile())
                    .setDepth(1)
                    .setTimeout(cloneTimeoutSeconds);

            if (branch != null && !branch.isBlank()) {
                log.info("Cloning {} (branch: {}) into {}", normalizedUrl, branch, targetDir);
                cloneCommand.setBranch(branch);
            } else {
                log.info("Cloning {} (default branch) into {}", normalizedUrl, targetDir);
            }

            try (Git git = cloneCommand.call()) {
                String clonedBranch = git.getRepository().getBranch();
                log.info("Successfully cloned {} to {} (branch: {})", normalizedUrl, targetDir, clonedBranch);
            }

            return targetDir;

        } catch (GitAPIException e) {
            cleanupDirectory(targetDir);
            String message = e.getMessage() != null ? e.getMessage() : "Unknown Git error";
            log.error("Failed to clone {}: {}", normalizedUrl, message);

            if (message.contains("not found") || message.contains("Authentication")
                    || message.contains("not authorized")) {
                throw new IllegalArgumentException(
                        "Repository not found or is private. Only public repositories are supported.");
            }
            if (message.contains("Ref") || message.contains("branch") || message.contains("cannot be resolved")) {
                throw new IllegalArgumentException(
                        "Branch '" + branch + "' not found. Check the branch name or leave it empty "
                                + "to use the repository's default branch.");
            }
            throw new IllegalArgumentException("Failed to clone repository: " + message);

        } catch (IOException e) {
            cleanupDirectory(targetDir);
            log.error("Failed to create workspace directory for {}: {}", normalizedUrl, e.getMessage());
            throw new IllegalArgumentException("Failed to create workspace directory: " + e.getMessage());
        }
    }

    /**
     * Extracts the repository name from a GitHub/GitLab URL.
     * e.g. "https://github.com/owner/my-project.git" → "my-project"
     */
    public String extractRepoName(String repoUrl) {
        if (repoUrl == null) return "unknown";
        String path = repoUrl.replaceFirst("https://[^/]+/", "");
        String repo = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return repo.replace(".git", "");
    }

    /**
     * Determines the SourceType based on the host in the URL.
     */
    public String extractHost(String repoUrl) {
        if (repoUrl == null) return "unknown";
        return repoUrl.replaceFirst("https://", "").split("/")[0];
    }

    private void cleanupDirectory(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var paths = Files.walk(dir)) {
                    paths.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                }
                            });
                }
            }
        } catch (IOException e) {
        }
    }
}

