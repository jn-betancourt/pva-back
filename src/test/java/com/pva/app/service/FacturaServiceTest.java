package com.pva.app.service;

import com.pva.app.domain.*;
import com.pva.app.dto.response.FacturaDetalleResponse;
import com.pva.app.dto.response.FacturaResumenResponse;
import com.pva.app.exception.AppException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.repository.ConfiguracionNegocioRepository;
import com.pva.app.repository.FacturaRepository;
import com.pva.app.repository.OperarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    private Venta venta;
    private Factura factura;
    private ConfiguracionNegocio config;

    @BeforeEach
    void setUp() {
        admin = Operario.builder()
                .idOperario(1L)
                .nombreCompleto("Administrador Principal")
                .nombreUsuario("admin")
                .rol(RolOperario.ADMIN)
                .activo(true)
                .build();

        cajero = Operario.builder()
                .idOperario(2L)
                .nombreCompleto("Cajero Uno")
                .nombreUsuario("cajero1")
                .rol(RolOperario.CAJERO)
                .activo(true)
                .build();

        otroCajero = Operario.builder()
                .idOperario(3L)
                .nombreCompleto("Cajero Dos")
                .nombreUsuario("cajero2")
                .rol(RolOperario.CAJERO)
                .activo(true)
                .build();

        Producto producto = Producto.builder()
                .idProducto(10L)
                .nombre("Arroz 1kg")
                .precioVenta(new BigDecimal("4500.00"))
                .costoPromedio(new BigDecimal("3500.00"))
                .stockActual(50)
                .aplicaIva(true)
                .porcentajeIva(new BigDecimal("19.00"))
                .build();

        venta = Venta.builder()
                .idVenta(100L)
                .operario(cajero)
                .subtotal(new BigDecimal("4500.00"))
                .totalAPagar(new BigDecimal("5355.00"))
                .medioPago(MedioPago.EFECTIVO)
                .estado(EstadoVenta.CONFIRMADA)
                .fechaVenta(LocalDateTime.now())
                .detalles(new ArrayList<>())
                .build();

        DetalleVenta detalle = DetalleVenta.builder()
                .idDetalle(1L)
                .venta(venta)
                .producto(producto)
                .cantidad(1)
                .precioUnitario(new BigDecimal("4500.00"))
                .descuentoLinea(BigDecimal.ZERO)
                .porcentajeIva(new BigDecimal("19.00"))
                .impuestoLinea(new BigDecimal("855.00"))
                .subtotalLinea(new BigDecimal("4500.00"))
                .build();
        venta.getDetalles().add(detalle);

        factura = Factura.builder()
                .idFactura(50L)
                .numeroTicket("POS-000050")
                .venta(venta)
                .clienteIdentificacion("123456789")
                .clienteNombre("Cliente Prueba")
                .clienteDireccion("Calle 123")
                .clienteTelefono("3000000000")
                .montoTotal(new BigDecimal("5355.00"))
                .totalImpuestos(new BigDecimal("855.00"))
                .estado(EstadoFactura.EMITIDA)
                .fechaEmision(LocalDateTime.now())
                .build();
        venta.setFactura(factura);

        config = ConfiguracionNegocio.builder()
                .idConfiguracion(1L)
                .nombreEstablecimiento("Supermercado PVA")
                .nitRut("900.123.456-7")
                .direccion("Cra 15 # 20-30")
                .telefonoContacto("3101234567")
                .pieTicket("Gracias por preferirnos")
                .porcentajeIvaDefecto(new BigDecimal("19.00"))
                .descuentoMaximoCajero(new BigDecimal("10.00"))
                .build();
    }

    @Test
    @DisplayName("Listar facturas exitosamente como ADMIN")
    void testListarFacturasComoAdmin() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(facturaRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(factura));

        List<FacturaResumenResponse> resultado = facturaService.listar(
                1L, "POS-000050", "123456789",
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1), EstadoFactura.EMITIDA);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).numeroTicket()).isEqualTo("POS-000050");
        assertThat(resultado.get(0).montoTotal()).isEqualByComparingTo("5355.00");
    }

    @Test
    @DisplayName("Listar facturas como CAJERO aplica restricción de sus ventas")
    void testListarFacturasComoCajero() {
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        when(facturaRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(factura));

        List<FacturaResumenResponse> resultado = facturaService.listar(
                2L, null, null, null, null, null);

        assertThat(resultado).hasSize(1);
        verify(facturaRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("Listar facturas lanza error si fecha 'desde' es posterior a 'hasta'")
    void testListarFacturasRangoInvalido() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        LocalDateTime desde = LocalDateTime.now();
        LocalDateTime hasta = desde.minusDays(2);

        assertThatThrownBy(() -> facturaService.listar(1L, null, null, desde, hasta, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("El rango de fechas es invalido")
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(appEx.getCodigo()).isEqualTo("FAC-RANGO-INVALIDO");
                });
    }

    @Test
    @DisplayName("Listar facturas lanza error si operario no existe")
    void testListarFacturasOperarioNoEncontrado() {
        when(operarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facturaService.listar(99L, null, null, null, null, null))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Operario no encontrado");
    }

    @Test
    @DisplayName("Obtener factura por ID exitosamente como ADMIN con configuración de negocio")
    void testObtenerPorIdComoAdmin() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(factura));
        when(configuracionNegocioRepository.findAll()).thenReturn(List.of(config));

        FacturaDetalleResponse response = facturaService.obtenerPorId(50L, 1L);

        assertThat(response).isNotNull();
        assertThat(response.idFactura()).isEqualTo(50L);
        assertThat(response.numeroTicket()).isEqualTo("POS-000050");
        assertThat(response.negocio()).isNotNull();
        assertThat(response.negocio().nombreEstablecimiento()).isEqualTo("Supermercado PVA");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).nombreProducto()).isEqualTo("Arroz 1kg");
    }

    @Test
    @DisplayName("Obtener factura por ID exitosamente como cajero dueño de la venta")
    void testObtenerPorIdComoCajeroPropietario() {
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(factura));
        when(configuracionNegocioRepository.findAll()).thenReturn(List.of());

        FacturaDetalleResponse response = facturaService.obtenerPorId(50L, 2L);

        assertThat(response).isNotNull();
        assertThat(response.idFactura()).isEqualTo(50L);
        assertThat(response.negocio().nombreEstablecimiento()).isEqualTo("PuntoVenta Ágil");
    }

    @Test
    @DisplayName("Obtener factura por ID lanza FORBIDDEN cuando un cajero intenta ver factura de otro")
    void testObtenerPorIdAccesoDenegado() {
        when(operarioRepository.findById(3L)).thenReturn(Optional.of(otroCajero));
        when(facturaRepository.findById(50L)).thenReturn(Optional.of(factura));

        assertThatThrownBy(() -> facturaService.obtenerPorId(50L, 3L))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Acceso denegado")
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(appEx.getCodigo()).isEqualTo("AUTH-NO-AUTORIZADO");
                });
    }

    @Test
    @DisplayName("Obtener factura por ID lanza EntityNotFoundException si la factura no existe")
    void testObtenerPorIdFacturaNoEncontrada() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(facturaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facturaService.obtenerPorId(999L, 1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Factura no encontrada");
    }
}
