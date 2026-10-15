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
class TurnoCajaControllerIntegrationTest {

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

    private Operario adminOperario;
    private Operario cajero1;
    private Operario cajero2;

    private String adminToken;
    private String cajero1Token;
    private String cajero2Token;

    @BeforeEach
    void setUp() {
        adminOperario = operarioRepository.findByNombreUsuario("admin_turno_it")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Administrador IT")
                        .numeroDocumento("9100000001")
                        .nombreUsuario("admin_turno_it")
                        .pinHash(passwordEncoder.encode("1234"))
                        .rol(RolOperario.ADMIN)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        cajero1 = operarioRepository.findByNombreUsuario("cajero_turno_1")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Cajero Uno IT")
                        .numeroDocumento("9100000002")
                        .nombreUsuario("cajero_turno_1")
                        .pinHash(passwordEncoder.encode("4321"))
                        .rol(RolOperario.CAJERO)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        cajero2 = operarioRepository.findByNombreUsuario("cajero_turno_2")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Cajero Dos IT")
                        .numeroDocumento("9100000003")
                        .nombreUsuario("cajero_turno_2")
                        .pinHash(passwordEncoder.encode("5678"))
                        .rol(RolOperario.CAJERO)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        adminToken = jwtService.generateToken(adminOperario.getIdOperario(), "admin_turno_it", "ADMIN");
        cajero1Token = jwtService.generateToken(cajero1.getIdOperario(), "cajero_turno_1", "CAJERO");
        cajero2Token = jwtService.generateToken(cajero2.getIdOperario(), "cajero_turno_2", "CAJERO");
    }

    // --- SEGURIDAD: 401 sin token ---

    @Test
    @DisplayName("Endpoints protegidos de turnos sin token retornan 401 AUTH-TOKEN-INVALIDO")
    void testEndpointsSinTokenRetornan401() throws Exception {
        mockMvc.perform(get("/api/v1/turnos/activo"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTH-TOKEN-INVALIDO"));

        mockMvc.perform(post("/api/v1/turnos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"base_efectivo_inicial\": 50000.00}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTH-TOKEN-INVALIDO"));
    }

    // --- GET /api/v1/turnos/activo ---

    @Test
    @DisplayName("GET /api/v1/turnos/activo sin turno retorna 200 con mensaje y campos nulos")
    void testGetTurnoActivoSinTurno() throws Exception {
        mockMvc.perform(get("/api/v1/turnos/activo")
                        .header("Authorization", "Bearer " + cajero1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_turno").doesNotExist())
                .andExpect(jsonPath("$.estado").doesNotExist())
                .andExpect(jsonPath("$.mensaje").value("El operario no tiene un turno abierto"));
    }

    @Test
    @DisplayName("GET /api/v1/turnos/activo con turno abierto retorna 200 con datos del turno")
    void testGetTurnoActivoConTurno() throws Exception {
        turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("30000.00"))
                .totalEfectivoVentas(new BigDecimal("10000.00"))
                .totalDigitalVentas(new BigDecimal("5000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/turnos/activo")
                        .header("Authorization", "Bearer " + cajero1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_turno").isNotEmpty())
                .andExpect(jsonPath("$.id_operario").value(cajero1.getIdOperario()))
                .andExpect(jsonPath("$.nombre_operario").value("Cajero Uno IT"))
                .andExpect(jsonPath("$.base_efectivo_inicial").value(30000.00))
                .andExpect(jsonPath("$.total_efectivo_ventas").value(10000.00))
                .andExpect(jsonPath("$.total_digital_ventas").value(5000.00))
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    // --- US-04: POST /api/v1/turnos/abrir ---

    @Test
    @DisplayName("US-04 Escenario 1: POST /api/v1/turnos/abrir exitoso crea turno con 201 CREATED")
    void testAbrirTurnoExitoso() throws Exception {
        String json = """
                {
                    "base_efectivo_inicial": 50000.00
                }
                """;

        mockMvc.perform(post("/api/v1/turnos/abrir")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id_turno").isNotEmpty())
                .andExpect(jsonPath("$.id_operario").value(cajero1.getIdOperario()))
                .andExpect(jsonPath("$.nombre_operario").value("Cajero Uno IT"))
                .andExpect(jsonPath("$.base_efectivo_inicial").value(50000.00))
                .andExpect(jsonPath("$.total_efectivo_ventas").value(0.00))
                .andExpect(jsonPath("$.total_digital_ventas").value(0.00))
                .andExpect(jsonPath("$.estado").value("ABIERTO"))
                .andExpect(jsonPath("$.fecha_apertura").isNotEmpty())
                .andExpect(jsonPath("$.fecha_cierre").doesNotExist());
    }

    @Test
    @DisplayName("US-04 Escenario 2: POST /api/v1/turnos/abrir con base negativa rechaza con 400 DATO-INVALIDO")
    void testAbrirTurnoBaseNegativa() throws Exception {
        String json = """
                {
                    "base_efectivo_inicial": -5000.00
                }
                """;

        mockMvc.perform(post("/api/v1/turnos/abrir")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATO-INVALIDO"));
    }

    @Test
    @DisplayName("US-04 RN-OPE-04: Abrir turno cuando ya existe uno abierto retorna 409 TURNO-YA-ABIERTO")
    void testAbrirTurnoYaAbiertoRetornaConflicto() throws Exception {
        turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("20000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        String json = """
                {
                    "base_efectivo_inicial": 30000.00
                }
                """;

        mockMvc.perform(post("/api/v1/turnos/abrir")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO-YA-ABIERTO"))
                .andExpect(jsonPath("$.error").value("El operario ya tiene un turno abierto"));
    }

    // --- US-05: POST /api/v1/turnos/{id}/cerrar ---

    @Test
    @DisplayName("US-05 Escenario 1: Arqueo ciego conforme con discrepancia 0 retorna 200 CERRADO")
    void testCerrarTurnoConforme() throws Exception {
        TurnoCaja turno = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .totalEfectivoVentas(new BigDecimal("25000.00"))
                .totalDigitalVentas(new BigDecimal("10000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        // Esperado en efectivo: 50,000 + 25,000 = 75,000. Arqueo ciego declara 75,000
        String json = """
                {
                    "monto_fisico_arqueo": 75000.00
                }
                """;

        mockMvc.perform(post("/api/v1/turnos/" + turno.getIdTurno() + "/cerrar")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_turno").value(turno.getIdTurno()))
                .andExpect(jsonPath("$.base_efectivo_inicial").value(50000.00))
                .andExpect(jsonPath("$.total_efectivo_ventas").value(25000.00))
                .andExpect(jsonPath("$.total_digital_ventas").value(10000.00))
                .andExpect(jsonPath("$.monto_fisico_arqueo").value(75000.00))
                .andExpect(jsonPath("$.discrepancia").value(0.00))
                .andExpect(jsonPath("$.estado").value("CERRADO"))
                .andExpect(jsonPath("$.fecha_cierre").isNotEmpty());
    }

    @Test
    @DisplayName("US-05 Escenario 2: Arqueo con descuadre sin observaciones retorna 400 TURNO-OBSERVACIONES-REQUERIDAS")
    void testCerrarTurnoDescuadreSinObservaciones() throws Exception {
        TurnoCaja turno = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .totalEfectivoVentas(new BigDecimal("25000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        // Esperado: 75,000. Declara: 70,000 (Faltante 5,000) sin observaciones
        String json = """
                {
                    "monto_fisico_arqueo": 70000.00,
                    "observaciones_cierre": ""
                }
                """;

        mockMvc.perform(post("/api/v1/turnos/" + turno.getIdTurno() + "/cerrar")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("TURNO-OBSERVACIONES-REQUERIDAS"));
    }

    @Test
    @DisplayName("US-05 Escenario 2: Arqueo con descuadre y observaciones retorna 200 CERRADO y guarda discrepancia")
    void testCerrarTurnoDescuadreConObservaciones() throws Exception {
        TurnoCaja turno = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .totalEfectivoVentas(new BigDecimal("25000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        // Esperado: 75,000. Declara: 72000.00 (Faltante -3000.00) con observaciones
        String json = """
                {
                    "monto_fisico_arqueo": 72000.00,
                    "observaciones_cierre": "Faltante por error en vuelto entregado en la tarde"
                }
                """;

        mockMvc.perform(post("/api/v1/turnos/" + turno.getIdTurno() + "/cerrar")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_turno").value(turno.getIdTurno()))
                .andExpect(jsonPath("$.discrepancia").value(-3000.00))
                .andExpect(jsonPath("$.estado").value("CERRADO"))
                .andExpect(jsonPath("$.observaciones_cierre").value("Faltante por error en vuelto entregado en la tarde"));
    }

    @Test
    @DisplayName("Cerrar turno ya cerrado retorna 409 TURNO-YA-CERRADO")
    void testCerrarTurnoYaCerrado() throws Exception {
        TurnoCaja turno = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .estado(EstadoTurno.CERRADO)
                .fechaApertura(LocalDateTime.now().minusHours(8))
                .fechaCierre(LocalDateTime.now().minusHours(1))
                .build());

        String json = """
                {
                    "monto_fisico_arqueo": 50000.00
                }
                """;

        mockMvc.perform(post("/api/v1/turnos/" + turno.getIdTurno() + "/cerrar")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("TURNO-YA-CERRADO"));
    }

    @Test
    @DisplayName("Cajero no puede cerrar el turno de otro operario -> 403 AUTH-NO-AUTORIZADO")
    void testCajeroNoPuedeCerrarTurnoAjeno() throws Exception {
        TurnoCaja turnoCajero1 = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        String json = """
                {
                    "monto_fisico_arqueo": 50000.00
                }
                """;

        mockMvc.perform(post("/api/v1/turnos/" + turnoCajero1.getIdTurno() + "/cerrar")
                        .header("Authorization", "Bearer " + cajero2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AUTH-NO-AUTORIZADO"));
    }

    @Test
    @DisplayName("ADMIN puede cerrar el turno de cualquier cajero -> 200 OK")
    void testAdminPuedeCerrarTurnoDeCajero() throws Exception {
        TurnoCaja turnoCajero1 = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        String json = """
                {
                    "monto_fisico_arqueo": 50000.00,
                    "observaciones_cierre": "Cierre administrativo por fin de jornada"
                }
                """;

        mockMvc.perform(post("/api/v1/turnos/" + turnoCajero1.getIdTurno() + "/cerrar")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADO"));
    }

    // --- GET /api/v1/turnos/{id} ---

    @Test
    @DisplayName("Cajero consulta su propio turno exitosamente -> 200 OK")
    void testCajeroConsultaSuPropioTurno() throws Exception {
        TurnoCaja turno = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("40000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/turnos/" + turno.getIdTurno())
                        .header("Authorization", "Bearer " + cajero1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_turno").value(turno.getIdTurno()))
                .andExpect(jsonPath("$.nombre_operario").value("Cajero Uno IT"));
    }

    @Test
    @DisplayName("Cajero intenta consultar turno ajeno -> 403 AUTH-NO-AUTORIZADO")
    void testCajeroIntentaConsultarTurnoAjeno() throws Exception {
        TurnoCaja turnoCajero1 = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("40000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/turnos/" + turnoCajero1.getIdTurno())
                        .header("Authorization", "Bearer " + cajero2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AUTH-NO-AUTORIZADO"));
    }

    @Test
    @DisplayName("ADMIN consulta turno ajeno exitosamente -> 200 OK")
    void testAdminConsultaTurnoAjeno() throws Exception {
        TurnoCaja turnoCajero1 = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(new BigDecimal("40000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        mockMvc.perform(get("/api/v1/turnos/" + turnoCajero1.getIdTurno())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_turno").value(turnoCajero1.getIdTurno()))
                .andExpect(jsonPath("$.id_operario").value(cajero1.getIdOperario()));
    }

    @Test
    @DisplayName("Consulta de turno inexistente retorna 404 TURNO-NO-ENCONTRADO")
    void testConsultarTurnoInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/turnos/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TURNO-NO-ENCONTRADO"));
    }
}
