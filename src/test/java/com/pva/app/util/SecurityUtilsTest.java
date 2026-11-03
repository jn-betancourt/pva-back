package com.pva.app.util;

import com.pva.app.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @Test
    @DisplayName("Extraer ID cuando principal es Long retorna ID numérico")
    void testExtraerIdPrincipalLong() {
        Authentication auth = new UsernamePasswordAuthenticationToken(42L, null);
        Long id = SecurityUtils.extraerIdOperario(auth);
        assertThat(id).isEqualTo(42L);
    }

    @Test
    @DisplayName("Extraer ID cuando principal es String numérico parsea y retorna Long")
    void testExtraerIdPrincipalString() {
        Authentication auth = new UsernamePasswordAuthenticationToken("99", null);
        Long id = SecurityUtils.extraerIdOperario(auth);
        assertThat(id).isEqualTo(99L);
    }

    @Test
    @DisplayName("Extraer ID con Authentication null lanza AppException 401")
    void testExtraerIdAuthNull() {
        assertThatThrownBy(() -> SecurityUtils.extraerIdOperario(null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(appEx.getCodigo()).isEqualTo("AUTH-TOKEN-INVALIDO");
                });
    }

    @Test
    @DisplayName("Extraer ID con principal no numérico lanza AppException 401")
    void testExtraerIdPrincipalInvalido() {
        Authentication auth = new UsernamePasswordAuthenticationToken("no-es-numero", null);
        assertThatThrownBy(() -> SecurityUtils.extraerIdOperario(auth))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(appEx.getCodigo()).isEqualTo("AUTH-TOKEN-INVALIDO");
                });
    }
}
