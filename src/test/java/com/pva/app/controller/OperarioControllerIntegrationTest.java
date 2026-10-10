package com.pva.app.controller;

import com.pva.app.service.JwtService;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.repository.TurnoCajaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OperarioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperarioRepository operarioRepository;

    @Autowired
    private TurnoCajaRepository turnoCajaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String cajeroToken;
    private Operario adminOperario;
    private Operario cajeroOperario;

    @BeforeEach
    void setUp() {
        adminOperario = operarioRepository.findByNombreUsuario("admin")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Administrador")
                        .numeroDocumento("1000000000")
                        .nombreUsuario("admin")
                        .pinHash(passwordEncoder.encode("1234"))
                        .rol(RolOperario.ADMIN)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        cajeroOperario = operarioRepository.findByNombreUsuario("cajero_base")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Cajero Base")
                        .numeroDocumento("3000000001")
                        .nombreUsuario("cajero_base")
                        .pinHash(passwordEncoder.encode("4321"))
                        .rol(RolOperario.CAJERO)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        adminToken = jwtService.generateToken(adminOperario.getIdOperario(), "admin", "ADMIN");
        cajeroToken = jwtService.generateToken(cajeroOperario.getIdOperario(), "cajero_base", "CAJERO");
    }

    @Test
    @DisplayName("Endpoint protegido /operarios sin token retorna 401 AUTH-TOKEN-INVALIDO")
    void testGetOperariosSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/operarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTH-TOKEN-INVALIDO"));
    }

    @Test
    @DisplayName("Endpoint protegido /operarios con rol CAJERO retorna 403 AUTH-NO-AUTORIZADO")
    void testGetOperariosComoCajero() throws Exception {
        mockMvc.perform(get("/api/v1/operarios")
                        .header("Authorization", "Bearer " + cajeroToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AUTH-NO-AUTORIZADO"));
    }

    @Test
    @DisplayName("GET /operarios como ADMIN retorna 200 OK con lista de operarios")
    void testGetOperariosComoAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/operarios")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("GET /operarios/{id} existente retorna 200; inexistente retorna 404 OPE-NO-ENCONTRADO")
    void testGetOperarioPorId() throws Exception {
        mockMvc.perform(get("/api/v1/operarios/" + adminOperario.getIdOperario())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre_usuario").value("admin"));

        mockMvc.perform(get("/api/v1/operarios/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OPE-NO-ENCONTRADO"));
    }

    @Test
    @DisplayName("US-03 BDD 1: POST /operarios crea exitosamente un operario")
    void testCrearOperarioExitoso() throws Exception {
        String nuevoOperarioJson = """
                {
                    "nombre_completo": "Nuevo Cajero",
                    "numero_documento": "4000000001",
                    "telefono": "3150001122",
                    "nombre_usuario": "nuevo_cajero",
                    "pin": "9876",
                    "rol": "CAJERO"
                }
                """;

        mockMvc.perform(post("/api/v1/operarios")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nuevoOperarioJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id_operario").isNotEmpty())
                .andExpect(jsonPath("$.nombre_usuario").value("nuevo_cajero"))
                .andExpect(jsonPath("$.rol").value("CAJERO"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @DisplayName("POST /operarios con PIN inválido retorna 400 OPE-PIN-INVALIDO")
    void testCrearOperarioPinInvalido() throws Exception {
        String jsonPinCorto = """
                {
                    "nombre_completo": "Cajero PIN Mal",
                    "numero_documento": "4000000002",
                    "nombre_usuario": "cajero_pin_mal",
                    "pin": "12",
                    "rol": "CAJERO"
                }
                """;

        mockMvc.perform(post("/api/v1/operarios")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPinCorto))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("OPE-PIN-INVALIDO"));
    }

    @Test
    @DisplayName("POST /operarios con usuario duplicado retorna 409 OPE-DUPLICADO")
    void testCrearOperarioUsuarioDuplicado() throws Exception {
        String jsonDuplicado = """
                {
                    "nombre_completo": "Duplicado Admin",
                    "numero_documento": "4000000003",
                    "nombre_usuario": "admin",
                    "pin": "1234",
                    "rol": "ADMIN"
                }
                """;

        mockMvc.perform(post("/api/v1/operarios")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonDuplicado))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("OPE-DUPLICADO"))
                .andExpect(jsonPath("$.detalle").value("Campo: nombre_usuario"));
    }

    @Test
    @DisplayName("US-02 BDD 2: PATCH /operarios/{id}/desbloquear restablece intentos_fallidos a 0")
    void testDesbloquearOperario() throws Exception {
        cajeroOperario.setIntentosFallidos(3);
        operarioRepository.save(cajeroOperario);

        mockMvc.perform(patch("/api/v1/operarios/" + cajeroOperario.getIdOperario() + "/desbloquear")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Operario desbloqueado correctamente"))
                .andExpect(jsonPath("$.intentos_fallidos").value(0));
    }

    @Test
    @DisplayName("US-03 BDD 2: PATCH /operarios/{id}/inactivar con turno abierto retorna 409 OPE-TURNO-ACTIVO (RN-OPE-06)")
    void testInactivarConTurnoAbierto() throws Exception {
        turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajeroOperario)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        mockMvc.perform(patch("/api/v1/operarios/" + cajeroOperario.getIdOperario() + "/inactivar")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("OPE-TURNO-ACTIVO"))
                .andExpect(jsonPath("$.detalle").value("Cierre el turno antes de inactivar el operario"));
    }

    @Test
    @DisplayName("PATCH /operarios/{id}/inactivar sin turno abierto inactiva al operario (activo=false)")
    void testInactivarSinTurnoAbierto() throws Exception {
        mockMvc.perform(patch("/api/v1/operarios/" + cajeroOperario.getIdOperario() + "/inactivar")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Operario inactivado correctamente"))
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    @DisplayName("PUT /operarios/{id} actualiza datos del operario")
    void testActualizarOperario() throws Exception {
        String updateJson = """
                {
                    "nombre_completo": "Cajero Nombre Modificado",
                    "telefono": "3200001122"
                }
                """;

        mockMvc.perform(put("/api/v1/operarios/" + cajeroOperario.getIdOperario())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre_completo").value("Cajero Nombre Modificado"))
                .andExpect(jsonPath("$.telefono").value("3200001122"));
    }

    @Test
    @DisplayName("PATCH /operarios/{id}/pin actualiza PIN correctamente")
    void testActualizarPin() throws Exception {
        String pinJson = """
                {
                    "pin_nuevo": "7777"
                }
                """;

        mockMvc.perform(patch("/api/v1/operarios/" + cajeroOperario.getIdOperario() + "/pin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pinJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("PIN actualizado correctamente"))
                .andExpect(jsonPath("$.id_operario").value(cajeroOperario.getIdOperario()));
    }
}
