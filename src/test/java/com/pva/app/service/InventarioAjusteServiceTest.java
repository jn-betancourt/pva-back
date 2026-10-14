package com.pva.app.service;

import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.Operario;
import com.pva.app.domain.Producto;
import com.pva.app.domain.RolOperario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.dto.request.RegistrarAjusteRequest;
import com.pva.app.dto.response.AjusteInventarioResponse;
import com.pva.app.dto.response.AlertaStockResponse;
import com.pva.app.exception.AppException;
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
        admin = Operario.builder().idOperario(1L).nombreUsuario("admin").rol(RolOperario.ADMIN).build();
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
}
