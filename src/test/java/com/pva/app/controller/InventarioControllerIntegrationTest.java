package com.pva.app.controller;

import com.pva.app.service.JwtService;
import com.pva.app.domain.Categoria;
import com.pva.app.repository.CategoriaRepository;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.Producto;
import com.pva.app.repository.ProductoRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventarioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private OperarioRepository operarioRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String cajeroToken;
    private Producto producto;

    @BeforeEach
    void setUp() {
        Operario admin = operarioRepository.findByNombreUsuario("admin_inv_it")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Admin Inventario IT")
                        .numeroDocumento("9400000001")
                        .nombreUsuario("admin_inv_it")
                        .pinHash(passwordEncoder.encode("1234"))
                        .rol(RolOperario.ADMIN)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        Operario cajero = operarioRepository.findByNombreUsuario("cajero_inv_it")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Cajero Inventario IT")
                        .numeroDocumento("9400000002")
                        .nombreUsuario("cajero_inv_it")
                        .pinHash(passwordEncoder.encode("1234"))
                        .rol(RolOperario.CAJERO)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        adminToken = jwtService.generateToken(admin.getIdOperario(), "admin_inv_it", "ADMIN");
        cajeroToken = jwtService.generateToken(cajero.getIdOperario(), "cajero_inv_it", "CAJERO");

        Categoria categoria = categoriaRepository.findByNombre("Granos Básicos")
                .orElseGet(() -> categoriaRepository.save(Categoria.builder()
                        .nombre("Granos Básicos")
                        .descripcion("Cereales y legumbres")
                        .activo(true)
                        .build()));

        // Producto inicial: 10 unidades a costo 2000.00
        producto = productoRepository.findByNombre("Lentejas 500g")
                .orElseGet(() -> productoRepository.save(Producto.builder()
                        .nombre("Lentejas 500g")
                        .codigoBarras("7705556667778")
                        .descripcion("Lentejas seleccionadas")
                        .categoria(categoria)
                        .precioVenta(new BigDecimal("3500.00"))
                        .costoPromedio(new BigDecimal("2000.00"))
                        .stockActual(10)
                        .stockMinimo(5)
                        .unidadMedida("UNIDAD")
                        .aplicaIva(false)
                        .porcentajeIva(BigDecimal.ZERO)
                        .activo(true)
                        .build()));
    }

    @Test
    @DisplayName("POST /api/v1/inventario/compra sin token retorna 401 AUTH-TOKEN-INVALIDO")
    void testCompraSinTokenRetorna401() throws Exception {
        mockMvc.perform(post("/api/v1/inventario/compra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id_producto\": 1, \"cantidad\": 5, \"costo_unitario\": 2000.00}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTH-TOKEN-INVALIDO"));
    }

    @Test
    @DisplayName("POST /api/v1/inventario/compra por CAJERO retorna 403 AUTH-NO-AUTORIZADO")
    void testCompraPorCajeroRetorna403() throws Exception {
        String json = """
                {
                  "id_producto": %d,
                  "cantidad": 5,
                  "costo_unitario": 2000.00
                }
                """.formatted(producto.getIdProducto());

        mockMvc.perform(post("/api/v1/inventario/compra")
                        .header("Authorization", "Bearer " + cajeroToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AUTH-NO-AUTORIZADO"));
    }

    @Test
    @DisplayName("US-08 Escenario 1: Abastecimiento y recálculo de CPP (10u@2000 + 10u@3000 = 2500) por ADMIN")
    void testRegistrarCompraCalculoCppExitoso() throws Exception {
        String json = """
                {
                  "id_producto": %d,
                  "cantidad": 10,
                  "costo_unitario": 3000.00,
                  "id_referencia": "FAC-PROV-9988",
                  "motivo_ajuste": "Compra directa a distribuidor"
                }
                """.formatted(producto.getIdProducto());

        mockMvc.perform(post("/api/v1/inventario/compra")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id_movimiento").isNumber())
                .andExpect(jsonPath("$.id_producto").value(producto.getIdProducto()))
                .andExpect(jsonPath("$.nombre_producto").value("Lentejas 500g"))
                .andExpect(jsonPath("$.tipo_movimiento").value("COMPRA"))
                .andExpect(jsonPath("$.cantidad").value(10))
                .andExpect(jsonPath("$.costo_unitario").value(3000.00))
                .andExpect(jsonPath("$.costo_total").value(30000.00))
                .andExpect(jsonPath("$.saldo_resultante").value(20))
                .andExpect(jsonPath("$.nuevo_costo_promedio").value(2500.00));

        // Verificar persistencia de stock y CPP en base de datos
        Producto actualizado = productoRepository.findById(producto.getIdProducto()).orElseThrow();
        assertThat(actualizado.getStockActual()).isEqualTo(20);
        assertThat(actualizado.getCostoPromedio()).isEqualByComparingTo("2500.00");

        // Verificar registro inmutable en Kardex
        List<MovimientoInventario> movimientos = movimientoInventarioRepository.findByProductoOrderByFechaMovimientoDesc(actualizado);
        assertThat(movimientos).isNotEmpty();
        MovimientoInventario ultimoMov = movimientos.get(0);
        assertThat(ultimoMov.getTipoMovimiento()).isEqualTo(TipoMovimientoInventario.COMPRA);
        assertThat(ultimoMov.getCantidad()).isEqualTo(10);
        assertThat(ultimoMov.getSaldoResultante()).isEqualTo(20);
        assertThat(ultimoMov.getIdReferencia()).isEqualTo("FAC-PROV-9988");
    }

    @Test
    @DisplayName("POST /api/v1/inventario/compra con cantidad <= 0 retorna 400 DATO-INVALIDO")
    void testCompraCantidadInvalidaRetorna400() throws Exception {
        String json = """
                {
                  "id_producto": %d,
                  "cantidad": 0,
                  "costo_unitario": 2000.00
                }
                """.formatted(producto.getIdProducto());

        mockMvc.perform(post("/api/v1/inventario/compra")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATO-INVALIDO"));
    }
}
