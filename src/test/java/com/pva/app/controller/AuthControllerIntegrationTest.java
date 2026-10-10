package com.pva.app.controller;

import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.repository.OperarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperarioRepository operarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("US-01 BDD 1: Login exitoso del usuario admin semilla retorna token JWT y rol ADMIN")
    void testLoginAdminExitoso() throws Exception {
        String loginJson = """
                {
                    "nombre_usuario": "admin",
                    "pin": "1234"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.nombre_usuario").value("admin"))
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.turno_activo").exists());
    }

    @Test
    @DisplayName("US-01: Login con usuario inexistente retorna 404 AUTH-USUARIO-NO-ENCONTRADO")
    void testLoginUsuarioInexistente() throws Exception {
        String loginJson = """
                {
                    "nombre_usuario": "usuario_falso",
                    "pin": "1234"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("AUTH-USUARIO-NO-ENCONTRADO"))
                .andExpect(jsonPath("$.error").value("Operario no existe"));
    }

    @Test
    @DisplayName("US-01 BDD 2: Login con PIN incorrecto retorna 401 AUTH-PIN-INCORRECTO y decrementa restantes")
    void testLoginPinIncorrecto() throws Exception {
        Operario cajero = operarioRepository.save(Operario.builder()
                .nombreCompleto("Cajero Test")
                .numeroDocumento("2000000001")
                .nombreUsuario("cajero_test")
                .pinHash(passwordEncoder.encode("4321"))
                .rol(RolOperario.CAJERO)
                .activo(true)
                .intentosFallidos(0)
                .build());

        String loginJson = """
                {
                    "nombre_usuario": "cajero_test",
                    "pin": "0000"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTH-PIN-INCORRECTO"))
                .andExpect(jsonPath("$.detalle").value("Intentos fallidos restantes: 2"));
    }

    @Test
    @DisplayName("US-02 BDD 1: Bloqueo automático tras 3 intentos fallidos consecutivos retorna 423 AUTH-OPERARIO-BLOQUEADO")
    void testBloqueoTrasTresIntentos() throws Exception {
        Operario cajero = operarioRepository.save(Operario.builder()
                .nombreCompleto("Cajero Bloqueo")
                .numeroDocumento("2000000002")
                .nombreUsuario("cajero_bloqueo")
                .pinHash(passwordEncoder.encode("5555"))
                .rol(RolOperario.CAJERO)
                .activo(true)
                .intentosFallidos(0)
                .build());

        String loginJsonErrado = """
                {
                    "nombre_usuario": "cajero_bloqueo",
                    "pin": "1111"
                }
                """;

        // Intento 1 -> 401 (restantes 2)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJsonErrado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTH-PIN-INCORRECTO"));

        // Intento 2 -> 401 (restantes 1)
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJsonErrado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTH-PIN-INCORRECTO"));

        // Intento 3 -> 423 AUTH-OPERARIO-BLOQUEADO
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJsonErrado))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("AUTH-OPERARIO-BLOQUEADO"))
                .andExpect(jsonPath("$.detalle").value("Contacte al administrador para desbloquear el acceso"));

        // Intento 4 posterior -> sigue retornando 423
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJsonErrado))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("AUTH-OPERARIO-BLOQUEADO"));
    }

    @Test
    @DisplayName("US-01: Logout invalida token e impide acceso a endpoints protegidos")
    void testLogoutInvalidaToken() throws Exception {
        String loginJson = """
                {
                    "nombre_usuario": "admin",
                    "pin": "1234"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        // Extraer token
        String token = responseBody.split("\"token\":\"")[1].split("\"")[0];

        // Acceso previo exitoso
        mockMvc.perform(get("/api/v1/operarios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Acceso posterior denegado con token revocado
        mockMvc.perform(get("/api/v1/operarios")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTH-TOKEN-INVALIDO"));
    }
}
