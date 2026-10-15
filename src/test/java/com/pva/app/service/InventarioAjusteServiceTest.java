package com.pva.app.service;

import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.Operario;
import com.pva.app.domain.Producto;
import com.pva.app.domain.RolOperario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.dto.request.RegistrarAjusteRequest;
import com.pva.app.dto.response.AjusteInventarioResponse;
import com.pva.app.dto.response.AlertaStockResponse;
import com.pva.app.dto.response.KardexResponse;
import com.pva.app.exception.AppException;
import com.pva.app.exception.EntityNotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDateTime;
import java.util.ArrayList;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.repository.ProductoRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventarioAjusteServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Mock
    private OperarioRepository operarioRepository;

    @InjectMocks
    private InventarioService inventarioService;

    private Operario admin;
    private Producto producto;

    @BeforeEach
    void setUp() {
        admin = Operario.builder()
                .idOperario(1L)
                .nombreCompleto("Administrador Principal")
                .nombreUsuario("admin")
                .rol(RolOperario.ADMIN)
                .build();
        producto = Producto.builder()
                .idProducto(50L)
                .nombre("Arroz Diana 1kg")
                .stockActual(10)
                .stockMinimo(5)
                .costoPromedio(new BigDecimal("2000.00"))
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("US-10 Escenario 1: merma justificada descuenta stock y asienta AJUSTE_SALIDA con motivo y usuario")
    void ajusteSalidaJustificado() {
        when(productoRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(producto));
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(movimientoInventarioRepository.save(any(MovimientoInventario.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RegistrarAjusteRequest request = new RegistrarAjusteRequest(
                50L, TipoMovimientoInventario.AJUSTE_SALIDA, 3, null, "Frasco roto en estanteria", null);

        AjusteInventarioResponse response = inventarioService.registrarAjuste(request, 1L);

        assertThat(producto.getStockActual()).isEqualTo(7);
        assertThat(response.tipoMovimiento()).isEqualTo(TipoMovimientoInventario.AJUSTE_SALIDA);
        assertThat(response.saldoResultante()).isEqualTo(7);
        assertThat(response.motivoAjuste()).isEqualTo("Frasco roto en estanteria");
        assertThat(response.costoUnitario()).isEqualByComparingTo("2000.00");
        assertThat(response.costoTotal()).isEqualByComparingTo("6000.00");

        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoInventarioRepository).save(captor.capture());
        assertThat(captor.getValue().getOperario()).isSameAs(admin);
    }

    @Test
    @DisplayName("US-10 Escenario 2: motivo vacio es rechazado y no altera el stock")
    void ajusteSinMotivoEsRechazado() {
        RegistrarAjusteRequest request = new RegistrarAjusteRequest(
                50L, TipoMovimientoInventario.AJUSTE_SALIDA, 3, null, "   ", null);

        assertThatThrownBy(() -> inventarioService.registrarAjuste(request, 1L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("INV-MOTIVO-REQUERIDO");

        assertThat(producto.getStockActual()).isEqualTo(10);
        verify(movimientoInventarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("US-10: una salida mayor al stock se rechaza con INV-STOCK-INSUFICIENTE")
    void ajusteSalidaConStockInsuficiente() {
        when(productoRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(producto));
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        RegistrarAjusteRequest request = new RegistrarAjusteRequest(
                50L, TipoMovimientoInventario.AJUSTE_SALIDA, 11, null, "Vencimiento", null);

        assertThatThrownBy(() -> inventarioService.registrarAjuste(request, 1L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("INV-STOCK-INSUFICIENTE");

        assertThat(producto.getStockActual()).isEqualTo(10);
    }

    @Test
    @DisplayName("US-10: los tipos COMPRA y VENTA no son validos para un ajuste manual")
    void ajusteConTipoInvalido() {
        RegistrarAjusteRequest request = new RegistrarAjusteRequest(
                50L, TipoMovimientoInventario.VENTA, 1, null, "Motivo", null);

        assertThatThrownBy(() -> inventarioService.registrarAjuste(request, 1L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("INV-TIPO-AJUSTE-INVALIDO");
    }

    @Test
    @DisplayName("US-09: las alertas incluyen el deficit (stock_minimo - stock_actual)")
    void alertasDeStock() {
        producto.setStockActual(4);
        when(productoRepository.findAlertasStock()).thenReturn(List.of(producto));

        List<AlertaStockResponse> alertas = inventarioService.listarAlertas();

        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0).deficit()).isEqualTo(1);
        assertThat(alertas.get(0).stockActual()).isEqualTo(4);
    }

    @Test
    @DisplayName("US-10: ajuste de entrada incrementa stock y usa costo unitario especificado")
    void ajusteEntradaConCostoEspecificado() {
        when(productoRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(producto));
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(movimientoInventarioRepository.save(any(MovimientoInventario.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RegistrarAjusteRequest request = new RegistrarAjusteRequest(
                50L, TipoMovimientoInventario.AJUSTE_ENTRADA, 5, new BigDecimal("2500.00"), "Sobrante fisico de conteo", null);

        AjusteInventarioResponse response = inventarioService.registrarAjuste(request, 1L);

        assertThat(producto.getStockActual()).isEqualTo(15);
        assertThat(response.tipoMovimiento()).isEqualTo(TipoMovimientoInventario.AJUSTE_ENTRADA);
        assertThat(response.saldoResultante()).isEqualTo(15);
        assertThat(response.costoUnitario()).isEqualByComparingTo("2500.00");
        assertThat(response.costoTotal()).isEqualByComparingTo("12500.00");
    }

    @Test
    @DisplayName("US-10: ajuste sobre producto inactivo lanza INV-PRODUCTO-INACTIVO")
    void ajusteProductoInactivoLanzaExcepcion() {
        producto.setActivo(false);
        when(productoRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(producto));

        RegistrarAjusteRequest request = new RegistrarAjusteRequest(
                50L, TipoMovimientoInventario.AJUSTE_ENTRADA, 2, null, "Recuperacion", null);

        assertThatThrownBy(() -> inventarioService.registrarAjuste(request, 1L))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("INV-PRODUCTO-INACTIVO");
    }

    @Test
    @DisplayName("US-10: ajuste con operario inexistente lanza EntityNotFoundException")
    void ajusteOperarioNoExisteLanzaExcepcion() {
        when(productoRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(producto));
        when(operarioRepository.findById(99L)).thenReturn(Optional.empty());

        RegistrarAjusteRequest request = new RegistrarAjusteRequest(
                50L, TipoMovimientoInventario.AJUSTE_ENTRADA, 2, null, "Recuperacion", null);

        assertThatThrownBy(() -> inventarioService.registrarAjuste(request, 99L))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("codigo").isEqualTo("OPE-NO-ENCONTRADO");
    }

    @Test
    @DisplayName("US-11: obtenerKardex exitoso retorna producto y lista de movimientos ordenados")
    void obtenerKardexExitoso() {
        when(productoRepository.findById(50L)).thenReturn(Optional.of(producto));

        MovimientoInventario mov = MovimientoInventario.builder()
                .idMovimiento(1L)
                .producto(producto)
                .tipoMovimiento(TipoMovimientoInventario.COMPRA)
                .cantidad(10)
                .costoUnitario(new BigDecimal("2000.00"))
                .costoTotal(new BigDecimal("20000.00"))
                .saldoResultante(10)
                .operario(admin)
                .fechaMovimiento(LocalDateTime.now().minusDays(1))
                .build();

        when(movimientoInventarioRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(mov));

        LocalDateTime desde = LocalDateTime.now().minusDays(2);
        LocalDateTime hasta = LocalDateTime.now();

        KardexResponse response = inventarioService.obtenerKardex(50L, desde, hasta, TipoMovimientoInventario.COMPRA);

        assertThat(response).isNotNull();
        assertThat(response.idProducto()).isEqualTo(50L);
        assertThat(response.nombreProducto()).isEqualTo("Arroz Diana 1kg");
        assertThat(response.stockActual()).isEqualTo(10);
        assertThat(response.movimientos()).hasSize(1);
        assertThat(response.movimientos().get(0).tipoMovimiento()).isEqualTo(TipoMovimientoInventario.COMPRA);
    }

    @Test
    @DisplayName("US-11: obtenerKardex con rango de fechas invertido lanza INV-RANGO-INVALIDO")
    void obtenerKardexRangoFechasInvalido() {
        when(productoRepository.findById(50L)).thenReturn(Optional.of(producto));

        LocalDateTime desde = LocalDateTime.now();
        LocalDateTime hasta = desde.minusDays(1);

        assertThatThrownBy(() -> inventarioService.obtenerKardex(50L, desde, hasta, null))
                .isInstanceOf(AppException.class)
                .extracting("codigo").isEqualTo("INV-RANGO-INVALIDO");
    }

    @Test
    @DisplayName("US-11: obtenerKardex con producto no existente lanza PROD-NO-ENCONTRADO")
    void obtenerKardexProductoNoEncontrado() {
        when(productoRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventarioService.obtenerKardex(999L, null, null, null))
                .isInstanceOf(EntityNotFoundException.class)
                .extracting("codigo").isEqualTo("PROD-NO-ENCONTRADO");
    }
}
