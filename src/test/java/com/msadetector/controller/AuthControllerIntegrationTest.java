package com.msadetector.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msadetector.dto.AuthResponse;
import com.msadetector.dto.LoginRequest;
import com.msadetector.dto.RegisterRequest;
import com.msadetector.dto.UserResponse;
import com.msadetector.security.JwtAuthenticationEntryPoint;
import com.msadetector.security.JwtTokenProvider;
import com.msadetector.security.CustomUserDetailsService;
import com.msadetector.config.FlywayConfig;
import com.msadetector.config.SecurityConfig;
import com.msadetector.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                classes = {SecurityConfig.class, FlywayConfig.class}))
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private AuthService authService;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private CustomUserDetailsService customUserDetailsService;
    @MockitoBean private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    @MockitoBean private AuthenticationManager authenticationManager;

    @Test
    void register_returnsCreated() throws Exception {
        RegisterRequest request = new RegisterRequest("John", "john@example.com", "password123");
        AuthResponse response = new AuthResponse("jwt-token", 86400000L,
                new UserResponse(1L, "John", "john@example.com"));

        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.name").value("John"));
    }

    @Test
    void register_invalidEmail_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("John", "not-an-email", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_blankName_returnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest("", "john@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returnsOk() throws Exception {
        LoginRequest request = new LoginRequest("john@example.com", "password123");
        AuthResponse response = new AuthResponse("jwt-token", 86400000L,
                new UserResponse(1L, "John", "john@example.com"));

        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void login_blankEmail_returnsBadRequest() throws Exception {
        LoginRequest request = new LoginRequest("", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
