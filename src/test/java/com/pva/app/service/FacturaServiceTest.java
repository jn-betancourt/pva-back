package com.pva.app.service;

import com.pva.app.domain.ConfiguracionNegocio;
import com.pva.app.domain.EstadoFactura;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.Factura;
import com.pva.app.domain.MedioPago;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.domain.Venta;
import com.pva.app.dto.response.FacturaDetalleResponse;
import com.pva.app.dto.response.FacturaResumenResponse;
import com.pva.app.exception.AppException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.repository.ConfiguracionNegocioRepository;
import com.pva.app.repository.FacturaRepository;
import com.pva.app.repository.OperarioRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacturaServiceTest {

    @Mock
    private FacturaRepository facturaRepository;

    @Mock
    private OperarioRepository operarioRepository;

    @Mock
    private ConfiguracionNegocioRepository configuracionNegocioRepository;

    @InjectMocks
    private FacturaService facturaService;

    private Operario admin;
    private Operario cajero;
    private Operario otroCajero;
    private TurnoCaja turno;
    private Venta ventaCajero;
    private Factura facturaCajero;
    private ConfiguracionNegocio configuracion;

    @BeforeEach
    void setUp() {
        admin = Operario.builder()
                .idOperario(1L)
                .nombreCompleto("Administrador Principal")
                .nombreUsuario("admin")
                .numeroDocumento("1000000000")
                .rol(RolOperario.ADMIN)
                .activo(true)
                .build();

        cajero = Operario.builder()
                .idOperario(2L)
                .nombreCompleto("Cajero Uno")
                .nombreUsuario("cajero1")
                .numeroDocumento("1000000002")
                .rol(RolOperario.CAJERO)
                .activo(true)
                .build();

        otroCajero = Operario.builder()
                .idOperario(3L)
                .nombreCompleto("Cajero Dos")
                .nombreUsuario("cajero2")
                .numeroDocumento("1000000003")
                .rol(RolOperario.CAJERO)
                .activo(true)
                .build();

        turno = TurnoCaja.builder()
                .idTurno(10L)
                .operario(cajero)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .estado(EstadoTurno.ABIERTO)
                .build();

        ventaCajero = Venta.builder()
                .idVenta(100L)
                .operario(cajero)
                .turno(turno)
                .subtotal(new BigDecimal("20000.00"))
                .impuestoTotal(new BigDecimal("3800.00"))
                .descuentoGlobal(BigDecimal.ZERO)
                .totalAPagar(new BigDecimal("23800.00"))
                .medioPago(MedioPago.EFECTIVO)
                .montoRecibido(new BigDecimal("30000.00"))
                .cambioEntregado(new BigDecimal("6200.00"))
                .detalles(new ArrayList<>())
                .fechaVenta(LocalDateTime.now())
                .build();

        facturaCajero = Factura.builder()
                .idFactura(50L)
                .numeroTicket("POS-000050")
                .clienteIdentificacion("222222222222")
                .clienteNombre("CONSUMIDOR FINAL")
                .montoTotal(new BigDecimal("23800.00"))
                .totalImpuestos(new BigDecimal("3800.00"))
                .estado(EstadoFactura.EMITIDA)
                .fechaEmision(LocalDateTime.now())
                .venta(ventaCajero)
                .build();

        configuracion = ConfiguracionNegocio.builder()
                .idConfiguracion(1L)
                .administrador(admin)
                .nombreEstablecimiento("PVA Store")
                .nitRut("900999888-1")
                .direccion("Calle 10 # 20-30")
                .telefonoContacto("3001234567")
                .pieTicket("Gracias por su compra")
                .build();
    }

    @Test
    @DisplayName("listar: Administrador lista todas las facturas con filtros válidos")
    void listar_ComoAdmin_RetornaListaFacturas() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(facturaRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(facturaCajero));

        List<FacturaResumenResponse> resultado = facturaService.listar(
                1L, "POS-000050", "222222222222",
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), EstadoFactura.EMITIDA
        );

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).numeroTicket()).isEqualTo("POS-000050");
        assertThat(resultado.get(0).montoTotal()).isEqualByComparingTo("23800.00");
        verify(facturaRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("listar: Cajero solo ve las facturas de sus propias ventas")
    void listar_ComoCajero_AplicaFiltroOperario() {
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        when(facturaRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(facturaCajero));

        List<FacturaResumenResponse> resultado = facturaService.listar(
                2L, null, null, null, null, null
        );

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).numeroTicket()).isEqualTo("POS-000050");
        verify(facturaRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("listar: Lanza excepción cuando 'desde' es posterior a 'hasta'")
    void listar_RangoFechasInvalido_LanzaAppException() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        LocalDateTime desde = LocalDateTime.now();
        LocalDateTime hasta = desde.minusDays(1);

        assertThatThrownBy(() -> facturaService.listar(1L, null, null, desde, hasta, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("El rango de fechas es invalido")
                .extracting("codigo")
                .isEqualTo("FAC-RANGO-INVALIDO");

        verify(facturaRepository, never()).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("listar: Lanza excepción cuando el operario no existe")
    void listar_OperarioNoExiste_LanzaEntityNotFoundException() {
        when(operarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facturaService.listar(99L, null, null, null, null, null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Operario no encontrado")
                .extracting("codigo")
                .isEqualTo("OPE-NO-ENCONTRADO");
    }

    @Test
    @DisplayName("obtenerPorId: Admin puede obtener el detalle de cualquier factura con datos del negocio")
    void obtenerPorId_ComoAdminConConfiguracion_RetornaDetalleCompleto() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(facturaCajero));
        when(configuracionNegocioRepository.findAll()).thenReturn(List.of(configuracion));

        FacturaDetalleResponse response = facturaService.obtenerPorId(50L, 1L);

        assertThat(response).isNotNull();
        assertThat(response.numeroTicket()).isEqualTo("POS-000050");
        assertThat(response.montoTotal()).isEqualByComparingTo("23800.00");
        assertThat(response.negocio()).isNotNull();
        assertThat(response.negocio().nombreEstablecimiento()).isEqualTo("PVA Store");
        assertThat(response.negocio().nitRut()).isEqualTo("900999888-1");
    }

    @Test
    @DisplayName("obtenerPorId: Cajero que emitió la factura puede consultarla sin configuración del negocio")
    void obtenerPorId_ComoCajeroPropiaSinConfiguracion_RetornaDetalleConFallback() {
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(facturaCajero));
        when(configuracionNegocioRepository.findAll()).thenReturn(List.of());

        FacturaDetalleResponse response = facturaService.obtenerPorId(50L, 2L);

        assertThat(response).isNotNull();
        assertThat(response.numeroTicket()).isEqualTo("POS-000050");
        assertThat(response.negocio()).isNotNull();
        assertThat(response.negocio().nombreEstablecimiento()).isEqualTo("PuntoVenta Ágil");
    }

    @Test
    @DisplayName("obtenerPorId: Cajero ajeno a la venta es rechazado con 403 AUTH-NO-AUTORIZADO")
    void obtenerPorId_ComoOtroCajero_LanzaAppException403() {
        when(operarioRepository.findById(3L)).thenReturn(Optional.of(otroCajero));
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(facturaCajero));

        assertThatThrownBy(() -> facturaService.obtenerPorId(50L, 3L))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(appEx.getCodigo()).isEqualTo("AUTH-NO-AUTORIZADO");
                });
    }

    @Test
    @DisplayName("obtenerPorId: Lanza excepción cuando la factura no existe")
    void obtenerPorId_FacturaNoExiste_LanzaEntityNotFoundException() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(facturaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facturaService.obtenerPorId(999L, 1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Factura no encontrada")
                .extracting("codigo")
                .isEqualTo("FAC-NO-ENCONTRADA");
    }

    @Test
    @DisplayName("obtenerPorId: Lanza excepción cuando el operario no existe")
    void obtenerPorId_OperarioNoExiste_LanzaEntityNotFoundException() {
        when(operarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facturaService.obtenerPorId(50L, 99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Operario no encontrado")
                .extracting("codigo")
                .isEqualTo("OPE-NO-ENCONTRADO");
    }

    @Test
    @DisplayName("listar: Verifica evaluación completa de predicados de Specification para Admin y Cajero")
    @SuppressWarnings("unchecked")
    void listar_EvaluaPredicadosSpecification() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        ArgumentCaptor<Specification<Factura>> captorAdmin = ArgumentCaptor.forClass(Specification.class);
        when(facturaRepository.findAll(captorAdmin.capture(), any(Sort.class))).thenReturn(List.of(facturaCajero));

        facturaService.listar(1L, "POS-000050", "222222222222",
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), EstadoFactura.EMITIDA);

        Root<Factura> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path path = mock(Path.class);
        when(root.get(anyString())).thenReturn(path);
        when(path.get(anyString())).thenReturn(path);
        lenient().when(cb.equal(any(jakarta.persistence.criteria.Expression.class), any())).thenReturn(mock(Predicate.class));
        lenient().when(cb.equal(any(jakarta.persistence.criteria.Expression.class), any(jakarta.persistence.criteria.Expression.class))).thenReturn(mock(Predicate.class));
        lenient().when(cb.greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), any(Comparable.class))).thenReturn(mock(Predicate.class));
        lenient().when(cb.lessThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), any(Comparable.class))).thenReturn(mock(Predicate.class));
        lenient().when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));

        Predicate predicateAdmin = captorAdmin.getValue().toPredicate(root, query, cb);
        assertThat(predicateAdmin).isNotNull();

        // Para cajero (agrega predicado de venta.operario)
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        ArgumentCaptor<Specification<Factura>> captorCajero = ArgumentCaptor.forClass(Specification.class);
        when(facturaRepository.findAll(captorCajero.capture(), any(Sort.class))).thenReturn(List.of(facturaCajero));

        facturaService.listar(2L, null, null, null, null, null);
        Predicate predicateCajero = captorCajero.getValue().toPredicate(root, query, cb);
        assertThat(predicateCajero).isNotNull();
    }
}
