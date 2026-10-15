package com.pva.app.service;

import com.pva.app.exception.AppException;
import com.pva.app.exception.ConflictException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.dto.request.AbrirTurnoRequest;
import com.pva.app.dto.request.CerrarTurnoRequest;
import com.pva.app.dto.response.TurnoActivoResponse;
import com.pva.app.dto.response.TurnoResponse;
import com.pva.app.repository.TurnoCajaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TurnoCajaServiceTest {

    @Mock
    private TurnoCajaRepository turnoCajaRepository;

    @Mock
    private OperarioRepository operarioRepository;

    @InjectMocks
    private TurnoCajaService turnoCajaService;

    private Operario cajero;
    private Operario otroCajero;
    private Operario admin;
    private TurnoCaja turnoAbierto;

    @BeforeEach
    void setUp() {
        cajero = Operario.builder()
                .idOperario(1L)
                .nombreCompleto("Cajero Principal")
                .numeroDocumento("1000000001")
                .nombreUsuario("cajero_1")
                .rol(RolOperario.CAJERO)
                .activo(true)
                .intentosFallidos(0)
                .build();

        otroCajero = Operario.builder()
                .idOperario(2L)
                .nombreCompleto("Otro Cajero")
                .numeroDocumento("1000000002")
                .nombreUsuario("cajero_2")
                .rol(RolOperario.CAJERO)
                .activo(true)
                .intentosFallidos(0)
                .build();

        admin = Operario.builder()
                .idOperario(3L)
                .nombreCompleto("Administrador Sistema")
                .numeroDocumento("1000000003")
                .nombreUsuario("admin")
                .rol(RolOperario.ADMIN)
                .activo(true)
                .intentosFallidos(0)
                .build();

        turnoAbierto = TurnoCaja.builder()
                .idTurno(10L)
                .operario(cajero)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .totalEfectivoVentas(new BigDecimal("35000.00"))
                .totalDigitalVentas(new BigDecimal("15000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now().minusHours(4))
                .build();
    }

    // --- PRUEBAS: obtenerTurnoActivo ---

    @Test
    @DisplayName("obtenerTurnoActivo retorna datos del turno cuando existe uno ABIERTO")
    void testObtenerTurnoActivoConTurnoAbierto() {
        when(turnoCajaRepository.findByOperarioAndEstado(cajero, EstadoTurno.ABIERTO))
                .thenReturn(Optional.of(turnoAbierto));

        TurnoActivoResponse response = turnoCajaService.obtenerTurnoActivo(cajero);

        assertThat(response.idTurno()).isEqualTo(10L);
        assertThat(response.idOperario()).isEqualTo(1L);
        assertThat(response.nombreOperario()).isEqualTo("Cajero Principal");
        assertThat(response.baseEfectivoInicial()).isEqualByComparingTo("50000.00");
        assertThat(response.totalEfectivoVentas()).isEqualByComparingTo("35000.00");
        assertThat(response.totalDigitalVentas()).isEqualByComparingTo("15000.00");
        assertThat(response.estado()).isEqualTo(EstadoTurno.ABIERTO);
        assertThat(response.mensaje()).isNull();
    }

    @Test
    @DisplayName("obtenerTurnoActivo retorna sinTurno cuando no existe turno ABIERTO")
    void testObtenerTurnoActivoSinTurno() {
        when(turnoCajaRepository.findByOperarioAndEstado(cajero, EstadoTurno.ABIERTO))
                .thenReturn(Optional.empty());

        TurnoActivoResponse response = turnoCajaService.obtenerTurnoActivo(cajero);

        assertThat(response.idTurno()).isNull();
        assertThat(response.estado()).isNull();
        assertThat(response.mensaje()).isEqualTo("El operario no tiene un turno abierto");
    }

    @Test
    @DisplayName("obtenerTurnoActivo con ID busca operario y retorna su turno activo")
    void testObtenerTurnoActivoPorIdOperario() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(cajero));
        when(turnoCajaRepository.findByOperarioAndEstado(cajero, EstadoTurno.ABIERTO))
                .thenReturn(Optional.of(turnoAbierto));

        TurnoActivoResponse response = turnoCajaService.obtenerTurnoActivo(1L);
        assertThat(response.idTurno()).isEqualTo(10L);
    }

    // --- PRUEBAS: US-04 abrirTurno ---

    @Test
    @DisplayName("US-04 Escenario 1: Apertura exitosa de turno con base >= 0")
    void testAbrirTurnoExitoso() {
        when(turnoCajaRepository.findByOperarioAndEstado(cajero, EstadoTurno.ABIERTO))
                .thenReturn(Optional.empty());
        when(turnoCajaRepository.save(any(TurnoCaja.class))).thenAnswer(invocation -> {
            TurnoCaja tc = invocation.getArgument(0);
            tc.setIdTurno(20L);
            return tc;
        });

        AbrirTurnoRequest request = new AbrirTurnoRequest(new BigDecimal("100000.00"));
        TurnoResponse response = turnoCajaService.abrirTurno(cajero, request);

        assertThat(response.idTurno()).isEqualTo(20L);
        assertThat(response.idOperario()).isEqualTo(1L);
        assertThat(response.nombreOperario()).isEqualTo("Cajero Principal");
        assertThat(response.baseEfectivoInicial()).isEqualByComparingTo("100000.00");
        assertThat(response.totalEfectivoVentas()).isEqualByComparingTo("0.00");
        assertThat(response.totalDigitalVentas()).isEqualByComparingTo("0.00");
        assertThat(response.estado()).isEqualTo(EstadoTurno.ABIERTO);
        assertThat(response.fechaApertura()).isNotNull();
        assertThat(response.fechaCierre()).isNull();

        verify(turnoCajaRepository, times(1)).save(any(TurnoCaja.class));
    }

    @Test
    @DisplayName("US-04 RN-OPE-04: Abrir turno rechaza con 409 TURNO-YA-ABIERTO si el operario ya tiene un turno abierto")
    void testAbrirTurnoYaAbiertoLanzaConflicto() {
        when(turnoCajaRepository.findByOperarioAndEstado(cajero, EstadoTurno.ABIERTO))
                .thenReturn(Optional.of(turnoAbierto));

        AbrirTurnoRequest request = new AbrirTurnoRequest(new BigDecimal("50000.00"));

        assertThatThrownBy(() -> turnoCajaService.abrirTurno(cajero, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("El operario ya tiene un turno abierto")
                .extracting("codigo")
                .isEqualTo("TURNO-YA-ABIERTO");

        verify(turnoCajaRepository, never()).save(any(TurnoCaja.class));
    }

    @Test
    @DisplayName("abrirTurno por idOperario lanza 404 OPE-NO-ENCONTRADO si el operario no existe")
    void testAbrirTurnoOperarioNoExiste() {
        when(operarioRepository.findById(99L)).thenReturn(Optional.empty());

        AbrirTurnoRequest request = new AbrirTurnoRequest(new BigDecimal("50000.00"));

        assertThatThrownBy(() -> turnoCajaService.abrirTurno(99L, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Operario no encontrado")
                .extracting("codigo")
                .isEqualTo("OPE-NO-ENCONTRADO");
    }

    // --- PRUEBAS: US-05 cerrarTurno y Arqueo Ciego ---

    @Test
    @DisplayName("US-05 Escenario 1: Arqueo ciego conforme con discrepancia 0.00 cierra turno")
    void testCerrarTurnoConformeSinDiscrepancia() {
        // Base: 50,000 + Ventas efectivo: 35,000 = Total esperado: 85,000
        BigDecimal montoContado = new BigDecimal("85000.00");
        CerrarTurnoRequest request = new CerrarTurnoRequest(montoContado, null);

        when(turnoCajaRepository.findById(10L)).thenReturn(Optional.of(turnoAbierto));
        when(turnoCajaRepository.save(any(TurnoCaja.class))).thenAnswer(i -> i.getArgument(0));

        TurnoResponse response = turnoCajaService.cerrarTurno(10L, cajero, request);

        assertThat(response.idTurno()).isEqualTo(10L);
        assertThat(response.estado()).isEqualTo(EstadoTurno.CERRADO);
        assertThat(response.montoFisicoArqueo()).isEqualByComparingTo("85000.00");
        assertThat(response.discrepancia()).isEqualByComparingTo("0.00");
        assertThat(response.fechaCierre()).isNotNull();
        assertThat(response.observacionesCierre()).isNull();
    }

    @Test
    @DisplayName("US-05 Escenario 2: Discrepancia sin observaciones lanza 400 TURNO-OBSERVACIONES-REQUERIDAS")
    void testCerrarTurnoDiscrepanciaSinObservacionesLanzaExcepcion() {
        // Base: 50,000 + Ventas efectivo: 35,000 = Esperado: 85,000. Contado: 83,000 (Faltante 2,000)
        BigDecimal montoContado = new BigDecimal("83000.00");
        CerrarTurnoRequest requestSinObs = new CerrarTurnoRequest(montoContado, "");

        when(turnoCajaRepository.findById(10L)).thenReturn(Optional.of(turnoAbierto));

        assertThatThrownBy(() -> turnoCajaService.cerrarTurno(10L, cajero, requestSinObs))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST)
                .hasFieldOrPropertyWithValue("codigo", "TURNO-OBSERVACIONES-REQUERIDAS");

        verify(turnoCajaRepository, never()).save(any(TurnoCaja.class));
    }

    @Test
    @DisplayName("US-05 Escenario 2: Discrepancia con justificación obligatoria asienta el cierre")
    void testCerrarTurnoDiscrepanciaConObservacionesExitoso() {
        // Base: 50,000 + Ventas efectivo: 35,000 = Esperado: 85,000. Contado: 87,000 (Sobrante +2,000)
        BigDecimal montoContado = new BigDecimal("87000.00");
        CerrarTurnoRequest requestConObs = new CerrarTurnoRequest(montoContado, "Sobrante por propina dejada en caja");

        when(turnoCajaRepository.findById(10L)).thenReturn(Optional.of(turnoAbierto));
        when(turnoCajaRepository.save(any(TurnoCaja.class))).thenAnswer(i -> i.getArgument(0));

        TurnoResponse response = turnoCajaService.cerrarTurno(10L, cajero, requestConObs);

        assertThat(response.estado()).isEqualTo(EstadoTurno.CERRADO);
        assertThat(response.discrepancia()).isEqualByComparingTo("2000.00");
        assertThat(response.observacionesCierre()).isEqualTo("Sobrante por propina dejada en caja");
        assertThat(response.montoFisicoArqueo()).isEqualByComparingTo("87000.00");
    }

    @Test
    @DisplayName("Cerrar turno ya cerrado lanza 409 TURNO-YA-CERRADO")
    void testCerrarTurnoYaCerradoLanzaConflicto() {
        turnoAbierto.setEstado(EstadoTurno.CERRADO);
        when(turnoCajaRepository.findById(10L)).thenReturn(Optional.of(turnoAbierto));

        CerrarTurnoRequest request = new CerrarTurnoRequest(new BigDecimal("85000.00"), null);

        assertThatThrownBy(() -> turnoCajaService.cerrarTurno(10L, cajero, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("El turno ya se encuentra cerrado")
                .extracting("codigo")
                .isEqualTo("TURNO-YA-CERRADO");
    }

    @Test
    @DisplayName("Cerrar turno inexistente lanza 404 TURNO-NO-ENCONTRADO")
    void testCerrarTurnoInexistenteLanzaNotFound() {
        when(turnoCajaRepository.findById(999L)).thenReturn(Optional.empty());

        CerrarTurnoRequest request = new CerrarTurnoRequest(new BigDecimal("85000.00"), null);

        assertThatThrownBy(() -> turnoCajaService.cerrarTurno(999L, cajero, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Turno no encontrado")
                .extracting("codigo")
                .isEqualTo("TURNO-NO-ENCONTRADO");
    }

    @Test
    @DisplayName("Cajero no puede cerrar el turno de otro operario -> 403 AUTH-NO-AUTORIZADO")
    void testCerrarTurnoOtroCajeroLanzaForbidden() {
        when(turnoCajaRepository.findById(10L)).thenReturn(Optional.of(turnoAbierto));

        CerrarTurnoRequest request = new CerrarTurnoRequest(new BigDecimal("85000.00"), null);

        assertThatThrownBy(() -> turnoCajaService.cerrarTurno(10L, otroCajero, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN)
                .hasFieldOrPropertyWithValue("codigo", "AUTH-NO-AUTORIZADO");
    }

    @Test
    @DisplayName("ADMIN sí puede cerrar el turno de un cajero")
    void testCerrarTurnoAdminPuedeCerrarTurnoCajero() {
        when(turnoCajaRepository.findById(10L)).thenReturn(Optional.of(turnoAbierto));
        when(turnoCajaRepository.save(any(TurnoCaja.class))).thenAnswer(i -> i.getArgument(0));

        CerrarTurnoRequest request = new CerrarTurnoRequest(new BigDecimal("85000.00"), "Cierre por supervisor");

        TurnoResponse response = turnoCajaService.cerrarTurno(10L, admin, request);
        assertThat(response.estado()).isEqualTo(EstadoTurno.CERRADO);
    }

    // --- PRUEBAS: obtenerPorId ---

    @Test
    @DisplayName("obtenerPorId: Cajero consulta su propio turno exitosamente")
    void testObtenerPorIdCajeroSuPropioTurno() {
        when(turnoCajaRepository.findById(10L)).thenReturn(Optional.of(turnoAbierto));

        TurnoResponse response = turnoCajaService.obtenerPorId(10L, cajero);
        assertThat(response.idTurno()).isEqualTo(10L);
        assertThat(response.nombreOperario()).isEqualTo("Cajero Principal");
    }

    @Test
    @DisplayName("obtenerPorId: Cajero intenta consultar turno ajeno -> 403 AUTH-NO-AUTORIZADO")
    void testObtenerPorIdCajeroTurnoAjenoLanzaForbidden() {
        when(turnoCajaRepository.findById(10L)).thenReturn(Optional.of(turnoAbierto));

        assertThatThrownBy(() -> turnoCajaService.obtenerPorId(10L, otroCajero))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN)
                .hasFieldOrPropertyWithValue("codigo", "AUTH-NO-AUTORIZADO");
    }

    @Test
    @DisplayName("obtenerPorId: ADMIN consulta turno ajeno exitosamente")
    void testObtenerPorIdAdminTurnoAjenoExitoso() {
        when(turnoCajaRepository.findById(10L)).thenReturn(Optional.of(turnoAbierto));

        TurnoResponse response = turnoCajaService.obtenerPorId(10L, admin);
        assertThat(response.idTurno()).isEqualTo(10L);
        assertThat(response.idOperario()).isEqualTo(1L);
    }

    @Test
    @DisplayName("obtenerPorId: Turno inexistente -> 404 TURNO-NO-ENCONTRADO")
    void testObtenerPorIdTurnoNoExisteLanzaNotFound() {
        when(turnoCajaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> turnoCajaService.obtenerPorId(999L, admin))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Turno no encontrado")
                .extracting("codigo")
                .isEqualTo("TURNO-NO-ENCONTRADO");
    }

    @Test
    @DisplayName("Validación de operario nulo en métodos de TurnoCajaService")
    void testOperarioNuloLanzaException() {
        assertThatThrownBy(() -> turnoCajaService.obtenerTurnoActivo((Operario) null))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("OPE-NO-ENCONTRADO"));

        assertThatThrownBy(() -> turnoCajaService.abrirTurno((Operario) null, new AbrirTurnoRequest(BigDecimal.ZERO)))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("OPE-NO-ENCONTRADO"));

        assertThatThrownBy(() -> turnoCajaService.cerrarTurno(10L, (Operario) null, new CerrarTurnoRequest(BigDecimal.ZERO, null)))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("OPE-NO-ENCONTRADO"));

        assertThatThrownBy(() -> turnoCajaService.obtenerPorId(10L, (Operario) null))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("OPE-NO-ENCONTRADO"));
    }

    @Test
    @DisplayName("Cerrar turno por ID de operario inexistente lanza OPE-NO-ENCONTRADO")
    void testCerrarTurnoOperarioInexistente() {
        when(operarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> turnoCajaService.cerrarTurno(10L, 999L, new CerrarTurnoRequest(BigDecimal.ZERO, null)))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("OPE-NO-ENCONTRADO"));

        assertThatThrownBy(() -> turnoCajaService.obtenerPorId(10L, 999L))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("OPE-NO-ENCONTRADO"));
    }

    @Test
    @DisplayName("Cerrar turno con totalEfectivoVentas nulo usa cero por defecto")
    void testCerrarTurnoTotalEfectivoNulo() {
        turnoAbierto.setTotalEfectivoVentas(null);
        when(turnoCajaRepository.findById(10L)).thenReturn(Optional.of(turnoAbierto));
        when(turnoCajaRepository.save(any(TurnoCaja.class))).thenAnswer(i -> i.getArgument(0));

        // Base: 50,000 + 0 = 50,000. Contado: 50,000 -> discrepancia 0
        CerrarTurnoRequest request = new CerrarTurnoRequest(new BigDecimal("50000.00"), null);
        TurnoResponse response = turnoCajaService.cerrarTurno(10L, cajero, request);

        assertThat(response.discrepancia()).isEqualByComparingTo("0.00");
    }
}

