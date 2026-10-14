package com.pva.app.service;

import com.pva.app.domain.EstadoFactura;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.EstadoVenta;
import com.pva.app.domain.Factura;
import com.pva.app.domain.MedioPago;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.Operario;
import com.pva.app.domain.Producto;
import com.pva.app.domain.RolOperario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.domain.Venta;
import com.pva.app.dto.request.AnularVentaRequest;
import com.pva.app.dto.request.CrearVentaRequest;
import com.pva.app.dto.response.AnulacionVentaResponse;
import com.pva.app.dto.response.VentaResponse;
import com.pva.app.exception.AppException;
import com.pva.app.repository.ConfiguracionNegocioRepository;
import com.pva.app.repository.FacturaRepository;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.repository.ProductoRepository;
import com.pva.app.repository.TurnoCajaRepository;
import com.pva.app.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private FacturaRepository facturaRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private MovimientoInventarioRepository movimientoInventarioRepository;
    @Mock
    private TurnoCajaRepository turnoCajaRepository;
    @Mock
    private OperarioRepository operarioRepository;
    @Mock
    private ConfiguracionNegocioRepository configuracionNegocioRepository;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private VentaService ventaService;

    private Operario cajero;
    private Operario admin;
    private TurnoCaja turno;
    private Producto gaseosa;
    private Producto pan;

    @BeforeEach
    void setUp() {
        cajero = Operario.builder().idOperario(2L).nombreCompleto("Cajero Uno").rol(RolOperario.CAJERO).build();
        admin = Operario.builder().idOperario(1L).nombreCompleto("Admin").rol(RolOperario.ADMIN).build();
        turno = TurnoCaja.builder()
                .idTurno(10L)
                .operario(cajero)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .estado(EstadoTurno.ABIERTO)
                .build();
        gaseosa = Producto.builder()
                .idProducto(1L).nombre("Gaseosa 350ml")
                .precioVenta(new BigDecimal("3000.00"))
                .costoPromedio(new BigDecimal("1800.00"))
                .aplicaIva(true).porcentajeIva(new BigDecimal("19.00"))
                .stockActual(10).stockMinimo(5).activo(true).build();
        pan = Producto.builder()
                .idProducto(2L).nombre("Pan")
                .precioVenta(new BigDecimal("1000.00"))
                .costoPromedio(new BigDecimal("500.00"))
                .aplicaIva(false).porcentajeIva(BigDecimal.ZERO)
                .stockActual(20).stockMinimo(5).activo(true).build();
    }

    private void stubVentaExitosa() {
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        when(turnoCajaRepository.findByOperarioAndEstado(cajero, EstadoTurno.ABIERTO)).thenReturn(Optional.of(turno));
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(gaseosa));
        when(productoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(pan));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            v.setIdVenta(100L);
            return v;
        });
        when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> {
            Factura f = inv.getArgument(0);
            f.setIdFactura(104L);
            return f;
        });
    }

    private CrearVentaRequest ventaDosItems(MedioPago medio, String monto, String referencia) {
        return new CrearVentaRequest(
                null, 10L,
                List.of(new CrearVentaRequest.Item(1L, 2, null), new CrearVentaRequest.Item(2L, 3, null)),
                null, medio, new BigDecimal(monto), referencia, null);
    }

    @Test
    @DisplayName("US-12/13/14/15: venta en efectivo calcula IVA, vuelto, descuenta stock, asienta Kardex y emite POS-000104")
    void ventaEfectivoCompleta() {
        stubVentaExitosa();

        // 2 x 3000 (IVA 19% = 1140) + 3 x 1000 (sin IVA) => subtotal 9000, impuesto 1140, total 10140
        VentaResponse response = ventaService.crearVenta(ventaDosItems(MedioPago.EFECTIVO, "20000.00", null), 2L);

        assertThat(response.subtotal()).isEqualByComparingTo("9000.00");
        assertThat(response.impuestoTotal()).isEqualByComparingTo("1140.00");
        assertThat(response.totalAPagar()).isEqualByComparingTo("10140.00");
        assertThat(response.cambioEntregado()).isEqualByComparingTo("9860.00");
        assertThat(response.estado()).isEqualTo(EstadoVenta.CONFIRMADA);
        assertThat(response.items()).hasSize(2);
        assertThat(response.factura().numeroTicket()).isEqualTo("POS-000104");
        assertThat(response.factura().clienteNombre()).isEqualTo("CONSUMIDOR FINAL");
        assertThat(response.factura().clienteIdentificacion()).isEqualTo("222222222222");
        assertThat(response.factura().estado()).isEqualTo(EstadoFactura.EMITIDA);

        assertThat(gaseosa.getStockActual()).isEqualTo(8);
        assertThat(pan.getStockActual()).isEqualTo(17);
        assertThat(turno.getTotalEfectivoVentas()).isEqualByComparingTo("10140.00");
        assertThat(turno.getTotalDigitalVentas()).isEqualByComparingTo("0.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MovimientoInventario>> captor = ArgumentCaptor.forClass(List.class);
        verify(movimientoInventarioRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2)
                .allSatisfy(m -> {
                    assertThat(m.getTipoMovimiento()).isEqualTo(TipoMovimientoInventario.VENTA);
                    assertThat(m.getIdReferencia()).isEqualTo("POS-000104");
                });
    }

    @Test
    @DisplayName("US-13 Escenario 2: efectivo insuficiente se rechaza sin tocar stock ni Kardex")
    void efectivoInsuficiente() {
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        when(turnoCajaRepository.findByOperarioAndEstado(cajero, EstadoTurno.ABIERTO)).thenReturn(Optional.of(turno));
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(gaseosa));
        when(productoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(pan));

        assertThatThrownBy(() -> ventaService.crearVenta(ventaDosItems(MedioPago.EFECTIVO, "5000.00", null), 2L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("VTA-MONTO-INSUFICIENTE");

        verify(ventaRepository, never()).save(any());
        verify(movimientoInventarioRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("US-13: transferencia exige referencia y se acumula como digital sin vuelto")
    void transferenciaRequiereReferencia() {
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        when(turnoCajaRepository.findByOperarioAndEstado(cajero, EstadoTurno.ABIERTO)).thenReturn(Optional.of(turno));
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(gaseosa));
        when(productoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(pan));

        assertThatThrownBy(() -> ventaService.crearVenta(ventaDosItems(MedioPago.TRANSFERENCIA, "10140.00", null), 2L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("VTA-REFERENCIA-REQUERIDA");
    }

    @Test
    @DisplayName("US-13: pago digital con referencia suma al total digital del turno y el vuelto es cero")
    void transferenciaExitosa() {
        stubVentaExitosa();

        VentaResponse response = ventaService.crearVenta(ventaDosItems(MedioPago.TRANSFERENCIA, "0.00", "NEQUI-123"), 2L);

        assertThat(response.cambioEntregado()).isEqualByComparingTo("0.00");
        assertThat(response.montoRecibido()).isEqualByComparingTo("10140.00");
        assertThat(turno.getTotalDigitalVentas()).isEqualByComparingTo("10140.00");
        assertThat(turno.getTotalEfectivoVentas()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("US-12 Escenario 2: sin turno abierto la venta se bloquea con VTA-SIN-TURNO")
    void sinTurnoAbierto() {
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        when(turnoCajaRepository.findByOperarioAndEstado(cajero, EstadoTurno.ABIERTO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ventaService.crearVenta(ventaDosItems(MedioPago.EFECTIVO, "20000.00", null), 2L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("VTA-SIN-TURNO");
    }

    @Test
    @DisplayName("US-14 Escenario 2: stock insuficiente aborta la venta sin persistir nada")
    void stockInsuficiente() {
        gaseosa.setStockActual(1);
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        when(turnoCajaRepository.findByOperarioAndEstado(cajero, EstadoTurno.ABIERTO)).thenReturn(Optional.of(turno));
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(gaseosa));
        when(productoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(pan));

        assertThatThrownBy(() -> ventaService.crearVenta(ventaDosItems(MedioPago.EFECTIVO, "20000.00", null), 2L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("VTA-STOCK-INSUFICIENTE");

        verify(ventaRepository, never()).save(any());
        verify(movimientoInventarioRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("US-15: una venta offline ya procesada (uuid_offline) devuelve la original sin duplicar")
    void ventaOfflineIdempotente() {
        Venta existente = Venta.builder()
                .idVenta(55L).uuidOffline("uuid-1").turno(turno).operario(cajero)
                .subtotal(BigDecimal.TEN).totalAPagar(BigDecimal.TEN)
                .medioPago(MedioPago.EFECTIVO).montoRecibido(BigDecimal.TEN).build();
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));
        when(ventaRepository.findByUuidOffline("uuid-1")).thenReturn(Optional.of(existente));

        CrearVentaRequest request = new CrearVentaRequest(
                "uuid-1", 10L, List.of(new CrearVentaRequest.Item(1L, 1, null)),
                null, MedioPago.EFECTIVO, BigDecimal.TEN, null, null);

        VentaResponse response = ventaService.crearVenta(request, 2L);

        assertThat(response.idVenta()).isEqualTo(55L);
        verify(ventaRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-16 Escenario 1: anulacion reintegra stock con AJUSTE_ENTRADA y marca venta y factura ANULADA")
    void anulacionExitosa() {
        stubVentaExitosa();
        VentaResponse creada = ventaService.crearVenta(ventaDosItems(MedioPago.EFECTIVO, "20000.00", null), 2L);
        assertThat(creada.estado()).isEqualTo(EstadoVenta.CONFIRMADA);

        Venta venta = ventaConFactura();
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(ventaRepository.findById(100L)).thenReturn(Optional.of(venta));
        when(facturaRepository.findByVenta(venta)).thenReturn(Optional.of(venta.getFactura()));

        AnulacionVentaResponse response =
                ventaService.anularVenta(100L, new AnularVentaRequest("Error de digitacion"), 1L);

        assertThat(response.estado()).isEqualTo("ANULADA");
        assertThat(response.factura().estado()).isEqualTo(EstadoFactura.ANULADA);
        assertThat(response.factura().motivoAnulacion()).isEqualTo("Error de digitacion");
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(venta.getFactura().getAnuladaPor()).isSameAs(admin);
        assertThat(gaseosa.getStockActual()).isEqualTo(10);
        assertThat(pan.getStockActual()).isEqualTo(20);
        assertThat(turno.getTotalEfectivoVentas()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("US-16 Escenario 2: un cajero no puede anular ventas")
    void cajeroNoPuedeAnular() {
        when(operarioRepository.findById(2L)).thenReturn(Optional.of(cajero));

        assertThatThrownBy(() -> ventaService.anularVenta(100L, new AnularVentaRequest("Motivo"), 2L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("AUTH-NO-AUTORIZADO");

        verify(ventaRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-16: no se puede anular dos veces la misma venta")
    void noSeAnulaDosVeces() {
        Venta venta = ventaConFactura();
        venta.setEstado(EstadoVenta.ANULADA);
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(ventaRepository.findById(100L)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> ventaService.anularVenta(100L, new AnularVentaRequest("Motivo"), 1L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("VTA-YA-ANULADA");
    }

    @Test
    @DisplayName("US-16 BDD: si se provee PIN en la anulacion y es incorrecto, se rechaza")
    void anulacionConPinInvalidoRechaza() {
        admin.setPinHash("$2a$10$hashValido");
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("9999", "$2a$10$hashValido")).thenReturn(false);

        assertThatThrownBy(() -> ventaService.anularVenta(100L, new AnularVentaRequest("Motivo", "9999"), 1L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("OPE-PIN-INVALIDO");
    }

    @Test
    @DisplayName("US-14 / RN-INV-04 / RN-OFF-04: venta offline se acepta aun con stock insuficiente para no invalidar el cobro")
    void ventaOfflinePermiteStockNegativo() {
        stubVentaExitosa();
        gaseosa.setStockActual(1); // Requiere 2 en la venta
        CrearVentaRequest request = new CrearVentaRequest(
                "uuid-offline-abc", 10L,
                List.of(new CrearVentaRequest.Item(1L, 2, null), new CrearVentaRequest.Item(2L, 3, null)),
                null, MedioPago.EFECTIVO, new BigDecimal("20000.00"), null, null);

        VentaResponse response = ventaService.crearVenta(request, 2L);

        assertThat(response.estado()).isEqualTo(EstadoVenta.CONFIRMADA);
        assertThat(gaseosa.getStockActual()).isEqualTo(-1); // Stock negativo permitido en sync offline
    }

    /** Reconstruye la venta de {@link #ventaDosItems} tal como la dejo crearVenta (stock ya descontado). */
    private Venta ventaConFactura() {
        gaseosa.setStockActual(8);
        pan.setStockActual(17);
        turno.setTotalEfectivoVentas(new BigDecimal("10140.00"));
        org.mockito.Mockito.lenient().when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(gaseosa));
        org.mockito.Mockito.lenient().when(productoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(pan));

        Venta venta = Venta.builder()
                .idVenta(100L).turno(turno).operario(cajero)
                .totalAPagar(new BigDecimal("10140.00"))
                .medioPago(MedioPago.EFECTIVO)
                .estado(EstadoVenta.CONFIRMADA)
                .build();
        venta.getDetalles().add(com.pva.app.domain.DetalleVenta.builder()
                .venta(venta).producto(gaseosa).cantidad(2)
                .precioUnitario(new BigDecimal("3000.00"))
                .costoUnitarioHistorico(new BigDecimal("1800.00"))
                .subtotalLinea(new BigDecimal("6000.00")).build());
        venta.getDetalles().add(com.pva.app.domain.DetalleVenta.builder()
                .venta(venta).producto(pan).cantidad(3)
                .precioUnitario(new BigDecimal("1000.00"))
                .costoUnitarioHistorico(new BigDecimal("500.00"))
                .subtotalLinea(new BigDecimal("3000.00")).build());
        Factura factura = Factura.builder()
                .idFactura(104L).venta(venta).numeroTicket("POS-000104")
                .montoTotal(new BigDecimal("10140.00")).build();
        venta.setFactura(factura);
        return venta;
    }
}
