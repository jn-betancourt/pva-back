package com.pva.app.service;

import com.pva.app.domain.Categoria;
import com.pva.app.repository.CategoriaRepository;
import com.pva.app.exception.ConflictException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.Producto;
import com.pva.app.dto.request.*;
import com.pva.app.dto.response.*;
import com.pva.app.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Mock
    private OperarioRepository operarioRepository;

    @InjectMocks
    private ProductoService productoService;

    private Categoria categoria;
    private Operario adminOperario;
    private Producto productoExistente;

    @BeforeEach
    void setUp() {
        categoria = Categoria.builder()
                .idCategoria(1L)
                .nombre("Abarrotes")
                .activo(true)
                .build();

        adminOperario = Operario.builder()
                .idOperario(10L)
                .nombreUsuario("admin")
                .rol(RolOperario.ADMIN)
                .build();

        productoExistente = Producto.builder()
                .idProducto(100L)
                .nombre("Arroz Diana 1kg")
                .codigoBarras("7701234567890")
                .descripcion("Arroz blanco")
                .categoria(categoria)
                .precioVenta(new BigDecimal("3500.00"))
                .costoPromedio(new BigDecimal("2000.00"))
                .stockActual(10)
                .stockMinimo(5)
                .unidadMedida("UNIDAD")
                .aplicaIva(false)
                .porcentajeIva(BigDecimal.ZERO)
                .activo(true)
                .build();
    }

    @Test
    @DisplayName("US-06 Escenario 1: Creación exitosa de producto con stock inicial y registro de Kardex")
    void testCrearProductoConStockInicialRegistraKardex() {
        CrearProductoRequest request = new CrearProductoRequest(
                "7709999999999",
                "Aceite Girasol 1L",
                "Aceite vegetal",
                1L,
                new BigDecimal("8500.00"),
                new BigDecimal("5000.00"),
                15,
                false,
                BigDecimal.ZERO,
                3,
                "BOTELLA"
        );

        when(productoRepository.findByNombre("Aceite Girasol 1L")).thenReturn(Optional.empty());
        when(productoRepository.findByCodigoBarras("7709999999999")).thenReturn(Optional.empty());
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(operarioRepository.findById(10L)).thenReturn(Optional.of(adminOperario));

        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setIdProducto(101L);
            return p;
        });

        CrearProductoResponse response = productoService.crear(request, 10L);

        assertThat(response.idProducto()).isEqualTo(101L);
        assertThat(response.nombre()).isEqualTo("Aceite Girasol 1L");
        assertThat(response.stockActual()).isEqualTo(15);
        assertThat(response.costoPromedio()).isEqualByComparingTo("5000.00");
        assertThat(response.activo()).isTrue();

        // Verifica inserción atómica en Kardex tipo COMPRA
        verify(movimientoInventarioRepository).save(any(MovimientoInventario.class));
    }

    @Test
    @DisplayName("US-06 Escenario 2: Rechazo por nombre duplicado lanza PROD-DUPLICADO")
    void testCrearProductoNombreDuplicadoLanzaConflicto() {
        CrearProductoRequest request = new CrearProductoRequest(
                "7708888888888",
                "Arroz Diana 1kg",
                null,
                1L,
                new BigDecimal("3500.00"),
                new BigDecimal("2000.00"),
                0,
                false,
                BigDecimal.ZERO,
                5,
                "UNIDAD"
        );

        when(productoRepository.findByNombre("Arroz Diana 1kg")).thenReturn(Optional.of(productoExistente));

        assertThatThrownBy(() -> productoService.crear(request, 10L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ya existe un producto con ese nombre o código de barras")
                .matches(ex -> ((ConflictException) ex).getCodigo().equals("PROD-DUPLICADO"));

        verify(productoRepository, never()).save(any());
        verify(movimientoInventarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechazo por código de barras duplicado lanza PROD-DUPLICADO")
    void testCrearProductoBarcodeDuplicadoLanzaConflicto() {
        CrearProductoRequest request = new CrearProductoRequest(
                "7701234567890",
                "Arroz Florhuila 1kg",
                null,
                1L,
                new BigDecimal("3500.00"),
                new BigDecimal("2000.00"),
                0,
                false,
                BigDecimal.ZERO,
                5,
                "UNIDAD"
        );

        when(productoRepository.findByNombre("Arroz Florhuila 1kg")).thenReturn(Optional.empty());
        when(productoRepository.findByCodigoBarras("7701234567890")).thenReturn(Optional.of(productoExistente));

        assertThatThrownBy(() -> productoService.crear(request, 10L))
                .isInstanceOf(ConflictException.class)
                .matches(ex -> ((ConflictException) ex).getCodigo().equals("PROD-DUPLICADO"));
    }

    @Test
    @DisplayName("Actualizar producto actualiza datos maestros y no toca stock ni costo")
    void testActualizarProductoNoModificaStockNiCosto() {
        ActualizarProductoRequest request = new ActualizarProductoRequest(
                "7701234567890",
                "Arroz Diana 1kg Premium",
                "Arroz seleccionado",
                1L,
                new BigDecimal("4000.00"),
                false,
                BigDecimal.ZERO,
                8,
                "KG"
        );

        when(productoRepository.findById(100L)).thenReturn(Optional.of(productoExistente));
        when(productoRepository.findByNombre("Arroz Diana 1kg Premium")).thenReturn(Optional.empty());
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse response = productoService.actualizar(100L, request);

        assertThat(response.nombre()).isEqualTo("Arroz Diana 1kg Premium");
        assertThat(response.precioVenta()).isEqualByComparingTo("4000.00");
        assertThat(response.stockMinimo()).isEqualTo(8);
        assertThat(response.unidadMedida()).isEqualTo("KG");
        // Verifica que stock_actual y costo_promedio permanecieron intactos
        assertThat(response.stockActual()).isEqualTo(10);
        assertThat(response.costoPromedio()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("Inactivar producto realiza baja lógica")
    void testInactivarProductoBajaLogica() {
        when(productoRepository.findById(100L)).thenReturn(Optional.of(productoExistente));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        InactivarProductoResponse response = productoService.inactivar(100L);

        assertThat(response.idProducto()).isEqualTo(100L);
        assertThat(response.activo()).isFalse();
        assertThat(productoExistente.getActivo()).isFalse();
    }

    @Test
    @DisplayName("US-07 Escenario 1: Búsqueda exacta por código de barras")
    void testBuscarPorBarcodeExacto() {
        when(productoRepository.findByCodigoBarras("7701234567890")).thenReturn(Optional.of(productoExistente));

        ProductoBarcodeResponse response = productoService.buscarPorBarcode("7701234567890");

        assertThat(response.idProducto()).isEqualTo(100L);
        assertThat(response.codigoBarras()).isEqualTo("7701234567890");
        assertThat(response.nombre()).isEqualTo("Arroz Diana 1kg");
        assertThat(response.stockActual()).isEqualTo(10);
    }

    @Test
    @DisplayName("Búsqueda por código de barras inexistente lanza PROD-BARCODE-NO-ENCONTRADO")
    void testBuscarPorBarcodeInexistenteLanzaNotFound() {
        when(productoRepository.findByCodigoBarras("0000000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.buscarPorBarcode("0000000000000"))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(ex -> ((EntityNotFoundException) ex).getCodigo().equals("PROD-BARCODE-NO-ENCONTRADO"));
    }

    @Test
    @DisplayName("US-07 Escenario 2: Listar productos con filtros y texto libre")
    void testListarProductosConFiltroTexto() {
        when(productoRepository.findAll(any(Specification.class))).thenReturn(List.of(productoExistente));

        List<ProductoResponse> resultado = productoService.listar(null, true, null, "Diana");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nombre()).isEqualTo("Arroz Diana 1kg");
    }

    @Test
    @DisplayName("Crear producto con stock inicial 0 no crea movimiento en Kardex y maneja unidadMedida nula")
    void testCrearProductoStockCero() {
        CrearProductoRequest request = new CrearProductoRequest(
                null,
                "Sal Refinada 1kg",
                null,
                1L,
                new BigDecimal("1500.00"),
                new BigDecimal("800.00"),
                0,
                true,
                null,
                5,
                null
        );

        when(productoRepository.findByNombre("Sal Refinada 1kg")).thenReturn(Optional.empty());
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            p.setIdProducto(102L);
            return p;
        });

        CrearProductoResponse response = productoService.crear(request, 10L);

        assertThat(response.idProducto()).isEqualTo(102L);
        assertThat(response.stockActual()).isEqualTo(0);
        assertThat(response.activo()).isTrue();
        verify(movimientoInventarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Crear producto con categoría inexistente lanza CAT-NO-ENCONTRADA")
    void testCrearProductoCategoriaInexistente() {
        CrearProductoRequest request = new CrearProductoRequest(
                null, "Producto X", null, 99L,
                new BigDecimal("1000.00"), new BigDecimal("500.00"), 0, false, null, 1, "UNIDAD"
        );
        when(productoRepository.findByNombre("Producto X")).thenReturn(Optional.empty());
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.crear(request, 10L))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(ex -> ((EntityNotFoundException) ex).getCodigo().equals("CAT-NO-ENCONTRADA"));
    }

    @Test
    @DisplayName("Crear producto con stock inicial pero operario admin no encontrado lanza OPE-NO-ENCONTRADO")
    void testCrearProductoOperarioAdminNoEncontrado() {
        CrearProductoRequest request = new CrearProductoRequest(
                null, "Producto Y", null, 1L,
                new BigDecimal("1000.00"), new BigDecimal("500.00"), 10, false, null, 1, "UNIDAD"
        );
        when(productoRepository.findByNombre("Producto Y")).thenReturn(Optional.empty());
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.crear(request, 99L))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(ex -> ((EntityNotFoundException) ex).getCodigo().equals("OPE-NO-ENCONTRADO"));
    }

    @Test
    @DisplayName("Actualizar producto con código de barras duplicado de otro producto lanza ConflictException")
    void testActualizarProductoBarcodeDuplicado() {
        Producto otro = Producto.builder().idProducto(200L).codigoBarras("7709999999999").build();
        ActualizarProductoRequest request = new ActualizarProductoRequest(
                "7709999999999", "Arroz Diana 1kg", null, 1L,
                new BigDecimal("3500.00"), false, BigDecimal.ZERO, 5, "UNIDAD"
        );

        when(productoRepository.findById(100L)).thenReturn(Optional.of(productoExistente));
        when(productoRepository.findByNombre("Arroz Diana 1kg")).thenReturn(Optional.of(productoExistente));
        when(productoRepository.findByCodigoBarras("7709999999999")).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> productoService.actualizar(100L, request))
                .isInstanceOf(ConflictException.class)
                .matches(ex -> ((ConflictException) ex).getCodigo().equals("PROD-DUPLICADO"));
    }

    @Test
    @DisplayName("Actualizar producto con categoría inexistente lanza CAT-NO-ENCONTRADA")
    void testActualizarProductoCategoriaNoEncontrada() {
        ActualizarProductoRequest request = new ActualizarProductoRequest(
                null, "Arroz Diana 1kg", null, 99L,
                new BigDecimal("3500.00"), false, BigDecimal.ZERO, 5, "UNIDAD"
        );

        when(productoRepository.findById(100L)).thenReturn(Optional.of(productoExistente));
        when(productoRepository.findByNombre("Arroz Diana 1kg")).thenReturn(Optional.of(productoExistente));
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.actualizar(100L, request))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(ex -> ((EntityNotFoundException) ex).getCodigo().equals("CAT-NO-ENCONTRADA"));
    }

    @Test
    @DisplayName("Buscar por barcode nulo o vacío lanza PROD-BARCODE-NO-ENCONTRADO")
    void testBuscarPorBarcodeNuloOVacio() {
        assertThatThrownBy(() -> productoService.buscarPorBarcode(null))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(ex -> ((EntityNotFoundException) ex).getCodigo().equals("PROD-BARCODE-NO-ENCONTRADO"));

        assertThatThrownBy(() -> productoService.buscarPorBarcode("   "))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(ex -> ((EntityNotFoundException) ex).getCodigo().equals("PROD-BARCODE-NO-ENCONTRADO"));
    }

    @Test
    @DisplayName("Obtener por ID existente e inexistente")
    void testObtenerPorId() {
        when(productoRepository.findById(100L)).thenReturn(Optional.of(productoExistente));
        when(productoRepository.findById(999L)).thenReturn(Optional.empty());

        ProductoResponse response = productoService.obtenerPorId(100L);
        assertThat(response.idProducto()).isEqualTo(100L);

        assertThatThrownBy(() -> productoService.obtenerPorId(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(ex -> ((EntityNotFoundException) ex).getCodigo().equals("PROD-NO-ENCONTRADO"));
    }
}

