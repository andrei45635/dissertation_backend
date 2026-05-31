package com.msadetector.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.config.FlywayConfig;
import com.msadetector.config.SecurityConfig;
import com.msadetector.dto.*;
import com.msadetector.entity.User;
import com.msadetector.security.*;
import com.msadetector.service.JobService;
import com.msadetector.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = ProjectController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {FlywayConfig.class}))
@Import(SecurityConfig.class)
class ProjectControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private ProjectService projectService;
    @MockitoBean private JobService jobService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    @MockitoBean private AuthenticationManager authenticationManager;

    private UserPrincipal principal() {
        User u = User.builder().name("Test").email("test@test.com").password("pass").build();
        u.setId(1L);
        return new UserPrincipal(u);
    }

    @Test
    void uploadProject_returnsAccepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "project.zip",
                "application/zip", "fake-zip-content".getBytes());

        when(projectService.uploadAndAnalyze(any(), eq("my-project"), eq(1L)))
                .thenReturn(new UploadResponse(10L, 100L));

        mockMvc.perform(multipart("/api/projects/upload")
                        .file(file)
                        .param("name", "my-project")
                        .with(user(principal()))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.projectId").value(10))
                .andExpect(jsonPath("$.jobId").value(100));
    }

    @Test
    void cloneProject_returnsAccepted() throws Exception {
        GitCloneRequest request = new GitCloneRequest(
                "https://github.com/example/repo.git", "test-project", "main");

        when(projectService.cloneAndAnalyze(any(), any(), any(), eq(1L)))
                .thenReturn(new UploadResponse(10L, 100L));

        mockMvc.perform(post("/api/projects/clone")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(user(principal()))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.projectId").value(10));
    }

    @Test
    void getAllProjects_returnsList() throws Exception {
        when(projectService.getProjectsForUser(1L))
                .thenReturn(List.of(new ProjectResponse(10L, "project-1", null,
                        "https://github.com/test", "main",
                        LocalDateTime.now(), List.of(), 1, 100L)));

        mockMvc.perform(get("/api/projects").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("project-1"));
    }

    @Test
    void getProject_returnsProject() throws Exception {
        when(projectService.getProjectForUser(10L, 1L))
                .thenReturn(new ProjectResponse(10L, "project-1", null,
                        "https://github.com/test", "main",
                        LocalDateTime.now(), List.of(), 1, 100L));

        mockMvc.perform(get("/api/projects/10").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void deleteProject_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/projects/10").with(user(principal())).with(csrf()))
                .andExpect(status().isNoContent());

        verify(projectService).deleteProjectForUser(10L, 1L);
    }

    @Test
    void getProjectHistory_returnsList() throws Exception {
        when(jobService.getProjectHistory(10L, 1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/projects/10/history").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void reanalyzeProject_returnsAccepted() throws Exception {
        when(projectService.reanalyze(eq(10L), eq(1L), any(), any()))
                .thenReturn(new UploadResponse(10L, 101L));

        mockMvc.perform(post("/api/projects/10/reanalyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(user(principal()))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(101));
    }

    @Test
    void reuploadProject_returnsAccepted() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "project.zip",
                "application/zip", "fake-content".getBytes());

        when(projectService.reuploadAndAnalyze(eq(10L), any(), eq(1L)))
                .thenReturn(new UploadResponse(10L, 102L));

        mockMvc.perform(multipart("/api/projects/10/reupload")
                        .file(file)
                        .with(user(principal()))
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(102));
    }
}
