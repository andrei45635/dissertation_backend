package com.msadetector.controller;

import com.msadetector.config.FlywayConfig;
import com.msadetector.config.SecurityConfig;
import com.msadetector.dto.AnalysisJobResponse;
import com.msadetector.dto.AnalysisResultResponse;
import com.msadetector.entity.User;
import com.msadetector.enums.JobStatus;
import com.msadetector.exception.ResourceNotFoundException;
import com.msadetector.security.CustomUserDetailsService;
import com.msadetector.security.JwtAuthenticationEntryPoint;
import com.msadetector.security.JwtTokenProvider;
import com.msadetector.security.UserPrincipal;
import com.msadetector.service.JobService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = JobController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, FlywayConfig.class}))
@ExtendWith(SpringExtension.class)
class JobControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private JobService jobService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    @MockitoBean private AuthenticationManager authenticationManager;

    @TestConfiguration
    static class SecurityOverride {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }
    }

    private UserPrincipal principal() {
        User u = User.builder().name("Test").email("test@test.com").password("pass").build();
        u.setId(1L);
        return new UserPrincipal(u);
    }

    @Test
    void getJobStatus_returnsJob() throws Exception {
        AnalysisJobResponse response = new AnalysisJobResponse(
                100L, 10L, JobStatus.COMPLETED, "Done", null,
                3, 3, 100, LocalDateTime.now(), LocalDateTime.now(), null, 1);

        when(jobService.getJobStatus(eq(100L), eq(1L))).thenReturn(response);

        mockMvc.perform(get("/api/jobs/100").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getJobStatus_notFound_returns404() throws Exception {
        when(jobService.getJobStatus(eq(999L), eq(1L)))
                .thenThrow(new ResourceNotFoundException("Job not found: 999"));

        mockMvc.perform(get("/api/jobs/999").with(user(principal())))
                .andExpect(status().isNotFound());
    }

    @Test
    void getJobResults_returnsResult() throws Exception {
        AnalysisResultResponse response = new AnalysisResultResponse(
                200L, 100L, 85, 3, 1, 5, 0, 1, 0, 0,
                10000, 3333.0, 0, List.of(), null, null, null);
        // 17 args matches the record: id, jobId, healthScore, servicesAnalyzed,
        // totalAntiPatterns, totalCodeSmells, criticalIssues, highIssues, mediumIssues,
        // lowIssues, totalLinesOfCode, averageServiceSize, cycleCount, antiPatterns,
        // dependencyGraph, healthScoreBreakdown, diff

        when(jobService.getJobResults(eq(100L), eq(1L))).thenReturn(response);

        mockMvc.perform(get("/api/jobs/100/results").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthScore").value(85));
    }

    @Test
    void getRecentJobs_returnsList() throws Exception {
        when(jobService.getRecentJobsForUser(eq(1L))).thenReturn(List.of());

        mockMvc.perform(get("/api/jobs/recent").with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void cancelJob_returnsOk() throws Exception {
        mockMvc.perform(post("/api/jobs/100/cancel").with(user(principal())))
                .andExpect(status().isOk());
    }
}


