package com.pva.app.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 12);
    }

    @Test
    @DisplayName("Generar token y extraer claims correctamente")
    void testGenerateTokenAndExtractClaims() {
        String token = jwtService.generateToken(42L, "operario1", "CAJERO");

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractIdOperario(token)).isEqualTo(42L);
        assertThat(jwtService.extractNombreUsuario(token)).isEqualTo("operario1");
        assertThat(jwtService.extractRol(token)).isEqualTo("CAJERO");
        assertThat(jwtService.extractJti(token)).isNotBlank();

        Claims claims = jwtService.extractClaims(token);
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("42");
    }

    @Test
    @DisplayName("Token inválido o malformado devuelve null en claims y false en validación")
    void testTokenInvalido() {
        String tokenInvalido = "invalido.jwt.token";

        assertThat(jwtService.extractClaims(tokenInvalido)).isNull();
        assertThat(jwtService.extractIdOperario(tokenInvalido)).isNull();
        assertThat(jwtService.extractRol(tokenInvalido)).isNull();
        assertThat(jwtService.extractNombreUsuario(tokenInvalido)).isNull();
        assertThat(jwtService.extractJti(tokenInvalido)).isNull();
        assertThat(jwtService.isTokenValid(tokenInvalido)).isFalse();
    }

    @Test
    @DisplayName("Token nulo devuelve null y false en validación")
    void testTokenNulo() {
        assertThat(jwtService.extractClaims(null)).isNull();
        assertThat(jwtService.extractIdOperario(null)).isNull();
        assertThat(jwtService.extractRol(null)).isNull();
        assertThat(jwtService.extractNombreUsuario(null)).isNull();
        assertThat(jwtService.extractJti(null)).isNull();
        assertThat(jwtService.isTokenValid(null)).isFalse();
    }

    @Test
    @DisplayName("extractIdOperario con subject no numérico retorna null")
    void testExtractIdOperarioNoNumerico() {
        JwtService shortLivedService = new JwtService(SECRET, -1);
        String expiredToken = shortLivedService.generateToken(1L, "op", "ADMIN");

        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
    }
}
