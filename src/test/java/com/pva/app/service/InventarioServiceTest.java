package com.pva.app.service;

import com.pva.app.exception.AppException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.dto.response.MovimientoInventarioResponse;
import com.pva.app.dto.request.RegistrarCompraRequest;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.Producto;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventarioServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Mock
    private OperarioRepository operarioRepository;

    @InjectMocks
    private InventarioService inventarioService;

    private Operario adminOperario;
    private Producto producto;

    @BeforeEach
    void setUp() {
        adminOperario = Operario.builder()
                .idOperario(1L)
                .nombreUsuario("admin")
                .rol(RolOperario.ADMIN)
                .build();

        // Producto inicial: stock_actual = 10, costo_promedio = $2000.00
        producto = Producto.builder()
                .idProducto(50L)
                .nombre("Arroz Diana 1kg")
                .stockActual(10)
                .costoPromedio(new BigDecimal("2000.00"))
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("US-08 Escenario 1: Recepción de compra y cálculo exacto de CPP (10u@2000 + 10u@3000 = 2500)")
    void testCalculoExactoCppYAsientoKardex() {
        // Given: producto con stock 10 y cpp 2000.00
        RegistrarCompraRequest request = new RegistrarCompraRequest(
                50L,
                10,
                new BigDecimal("3000.00"),
                "FAC-PROV-001",
                "Abastecimiento semanal"
        );

        when(productoRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(producto));
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(adminOperario));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movimientoInventarioRepository.save(any(MovimientoInventario.class))).thenAnswer(inv -> {
            MovimientoInventario m = inv.getArgument(0);
            return MovimientoInventario.builder()
                    .idMovimiento(1001L)
                    .producto(m.getProducto())
                    .tipoMovimiento(m.getTipoMovimiento())
                    .cantidad(m.getCantidad())
                    .costoUnitario(m.getCostoUnitario())
                    .costoTotal(m.getCostoTotal())
                    .saldoResultante(m.getSaldoResultante())
                    .idReferencia(m.getIdReferencia())
                    .motivoAjuste(m.getMotivoAjuste())
                    .operario(m.getOperario())
                    .fechaMovimiento(m.getFechaMovimiento())
                    .build();
        });

        // When
        MovimientoInventarioResponse response = inventarioService.registrarCompra(request, 1L);

        // Then:
        // stock_actual = 10 + 10 = 20
        // nuevo_cpp = ((10 * 2000) + (10 * 3000)) / (10 + 10) = 50000 / 20 = 2500.00
        assertThat(producto.getStockActual()).isEqualTo(20);
        assertThat(producto.getCostoPromedio()).isEqualByComparingTo("2500.00");

        assertThat(response.idMovimiento()).isEqualTo(1001L);
        assertThat(response.idProducto()).isEqualTo(50L);
        assertThat(response.tipoMovimiento()).isEqualTo(TipoMovimientoInventario.COMPRA);
        assertThat(response.cantidad()).isEqualTo(10);
        assertThat(response.costoUnitario()).isEqualByComparingTo("3000.00");
        assertThat(response.costoTotal()).isEqualByComparingTo("30000.00");
        assertThat(response.saldoResultante()).isEqualTo(20);
        assertThat(response.nuevoCostoPromedio()).isEqualByComparingTo("2500.00");

        // Verifica que se llamó findByIdForUpdate (bloqueo pesimista)
        verify(productoRepository).findByIdForUpdate(50L);

        // Verifica Kardex inmutable
        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoInventarioRepository).save(captor.capture());
        MovimientoInventario movGuardado = captor.getValue();
        assertThat(movGuardado.getTipoMovimiento()).isEqualTo(TipoMovimientoInventario.COMPRA);
        assertThat(movGuardado.getCantidad()).isEqualTo(10);
        assertThat(movGuardado.getSaldoResultante()).isEqualTo(20);
    }

    @Test
    @DisplayName("Rechazar compra de producto inexistente lanza PROD-NO-ENCONTRADO")
    void testCompraProductoInexistenteLanzaNotFound() {
        RegistrarCompraRequest request = new RegistrarCompraRequest(
                999L,
                5,
                new BigDecimal("1500.00"),
                null,
                null
        );

        when(productoRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventarioService.registrarCompra(request, 1L))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(ex -> ((EntityNotFoundException) ex).getCodigo().equals("PROD-NO-ENCONTRADO"));

        verify(movimientoInventarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechazar compra de producto inactivo lanza INV-PRODUCTO-INACTIVO")
    void testCompraProductoInactivoLanzaError() {
        producto.setActivo(false);
        RegistrarCompraRequest request = new RegistrarCompraRequest(
                50L,
                5,
                new BigDecimal("1500.00"),
                null,
                null
        );

        when(productoRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(producto));

        assertThatThrownBy(() -> inventarioService.registrarCompra(request, 1L))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getCodigo().equals("INV-PRODUCTO-INACTIVO"));

        verify(movimientoInventarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechazar compra si el operario admin no existe lanza OPE-NO-ENCONTRADO")
    void testCompraOperarioNoExisteLanzaNotFound() {
        RegistrarCompraRequest request = new RegistrarCompraRequest(
                50L,
                5,
                new BigDecimal("1500.00"),
                null,
                null
        );

        when(productoRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(producto));
        when(operarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventarioService.registrarCompra(request, 99L))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(ex -> ((EntityNotFoundException) ex).getCodigo().equals("OPE-NO-ENCONTRADO"));

        verify(movimientoInventarioRepository, never()).save(any());
    }
}

