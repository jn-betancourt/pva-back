package com.pva.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistServiceTest {

    private TokenBlacklistService tokenBlacklistService;

    @BeforeEach
    void setUp() {
        tokenBlacklistService = new TokenBlacklistService();
    }

    @Test
    @DisplayName("Revocar token y verificar estado revocado")
    void testRevokeAndIsRevoked() {
        String token = "sample.jwt.token";

        assertThat(tokenBlacklistService.isTokenRevoked(token)).isFalse();

        tokenBlacklistService.revokeToken(token);

        assertThat(tokenBlacklistService.isTokenRevoked(token)).isTrue();
    }

    @Test
    @DisplayName("Tokens nulos o vacíos no se revocan ni causan errores")
    void testRevokeNullOrEmpty() {
        tokenBlacklistService.revokeToken(null);
        tokenBlacklistService.revokeToken("");
        tokenBlacklistService.revokeToken("   ");

        assertThat(tokenBlacklistService.isTokenRevoked(null)).isFalse();
        assertThat(tokenBlacklistService.isTokenRevoked("")).isFalse();
        assertThat(tokenBlacklistService.isTokenRevoked("   ")).isFalse();
    }
}
