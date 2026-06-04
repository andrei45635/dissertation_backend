package com.msadetector.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    private static final String SECRET = "a]kG9Rd2!Fp7sXe3Qw8#Yl5Bn6Hm4Jt1Cv0Zx_AoUiIkNrTfDhMbWqPjSyLzEg";
    private static final long EXPIRATION_MS = 86400000L;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_returnsNonNullToken() {
        String token = tokenProvider.generateToken(1L, "user@example.com");

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void getUserIdFromToken_returnsCorrectUserId() {
        String token = tokenProvider.generateToken(42L, "user@example.com");

        Long userId = tokenProvider.getUserIdFromToken(token);

        assertEquals(42L, userId);
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = tokenProvider.generateToken(1L, "user@example.com");

        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertFalse(tokenProvider.validateToken("invalid.jwt.token"));
    }

    @Test
    void validateToken_nullToken_returnsFalse() {
        assertFalse(tokenProvider.validateToken(null));
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(SECRET, 0L);
        String token = expiredProvider.generateToken(1L, "user@example.com");

        assertFalse(expiredProvider.validateToken(token));
    }

    @Test
    void validateToken_wrongSecret_returnsFalse() {
        String token = tokenProvider.generateToken(1L, "user@example.com");

        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "Bx9Rd2!Fp7sXe3Qw8#Yl5Bn6Hm4Jt1Cv0Zx_AoUiIkNrTfDhMbWqPjSyLzEgAA", EXPIRATION_MS);

        assertFalse(otherProvider.validateToken(token));
    }

    @Test
    void getExpirationMs_returnsConfiguredValue() {
        assertEquals(EXPIRATION_MS, tokenProvider.getExpirationMs());
    }
}

