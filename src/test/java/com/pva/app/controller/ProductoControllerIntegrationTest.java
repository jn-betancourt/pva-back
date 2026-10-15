package com.pva.app.controller;

import com.pva.app.service.JwtService;
import com.pva.app.domain.Categoria;
import com.pva.app.repository.CategoriaRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private OperarioRepository operarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String cajeroToken;
    private Categoria categoria;
    private Producto producto1;

    @BeforeEach
    void setUp() {
        Operario admin = operarioRepository.findByNombreUsuario("admin_prod_it")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Admin Producto IT")
                        .numeroDocumento("9300000001")
                        .nombreUsuario("admin_prod_it")
                        .pinHash(passwordEncoder.encode("1234"))
                        .rol(RolOperario.ADMIN)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        Operario cajero = operarioRepository.findByNombreUsuario("cajero_prod_it")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Cajero Producto IT")
                        .numeroDocumento("9300000002")
                        .nombreUsuario("cajero_prod_it")
                        .pinHash(passwordEncoder.encode("1234"))
                        .rol(RolOperario.CAJERO)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        adminToken = jwtService.generateToken(admin.getIdOperario(), "admin_prod_it", "ADMIN");
        cajeroToken = jwtService.generateToken(cajero.getIdOperario(), "cajero_prod_it", "CAJERO");

        categoria = categoriaRepository.findByNombre("Bebidas Gaseosas")
                .orElseGet(() -> categoriaRepository.save(Categoria.builder()
                        .nombre("Bebidas Gaseosas")
                        .descripcion("Gaseosas y refrescos")
                        .activo(true)
                        .build()));

        producto1 = productoRepository.findByNombre("Coca Cola 350ml")
                .orElseGet(() -> productoRepository.save(Producto.builder()
                        .nombre("Coca Cola 350ml")
                        .codigoBarras("7701234567890")
                        .descripcion("Refresco de cola personal")
                        .categoria(categoria)
                        .precioVenta(new BigDecimal("2500.00"))
                        .costoPromedio(new BigDecimal("1500.00"))
                        .stockActual(20)
                        .stockMinimo(5)
                        .unidadMedida("UNIDAD")
                        .aplicaIva(true)
                        .porcentajeIva(new BigDecimal("19.00"))
                        .activo(true)
                        .build()));
    }

    @Test
    @DisplayName("GET /api/v1/productos sin token retorna 401")
    void testListarSinTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTH-TOKEN-INVALIDO"));
    }

    @Test
    @DisplayName("US-06: POST /api/v1/productos con ADMIN crea producto exitosamente (201 Created)")
    void testCrearProductoConAdminRetorna201() throws Exception {
        String json = """
                {
                  "codigo_barras": "7709876543210",
                  "nombre": "Pepsi 350ml",
                  "descripcion": "Refresco de cola",
                  "id_categoria": %d,
                  "precio_venta": 2200.00,
                  "costo_inicial": 1400.00,
                  "stock_inicial": 10,
                  "aplica_iva": true,
                  "porcentaje_iva": 19.00,
                  "stock_minimo": 4,
                  "unidad_medida": "UNIDAD"
                }
                """.formatted(categoria.getIdCategoria());

        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id_producto").isNumber())
                .andExpect(jsonPath("$.nombre").value("Pepsi 350ml"))
                .andExpect(jsonPath("$.codigo_barras").value("7709876543210"))
                .andExpect(jsonPath("$.stock_actual").value(10))
                .andExpect(jsonPath("$.costo_promedio").value(1400.00))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @DisplayName("US-06 Escenario 2: Rechazo por nombre duplicado retorna 409 PROD-DUPLICADO")
    void testCrearProductoNombreDuplicadoRetorna409() throws Exception {
        String json = """
                {
                  "codigo_barras": "7705555555555",
                  "nombre": "Coca Cola 350ml",
                  "id_categoria": %d,
                  "precio_venta": 2600.00,
                  "costo_inicial": 1500.00,
                  "stock_inicial": 5,
                  "aplica_iva": false,
                  "stock_minimo": 2
                }
                """.formatted(categoria.getIdCategoria());

        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROD-DUPLICADO"));
    }

    @Test
    @DisplayName("POST /api/v1/productos con barcode duplicado retorna 409 PROD-DUPLICADO")
    void testCrearProductoBarcodeDuplicadoRetorna409() throws Exception {
        String json = """
                {
                  "codigo_barras": "7701234567890",
                  "nombre": "Otra Bebida",
                  "id_categoria": %d,
                  "precio_venta": 2600.00,
                  "costo_inicial": 1500.00,
                  "stock_inicial": 5,
                  "aplica_iva": false,
                  "stock_minimo": 2
                }
                """.formatted(categoria.getIdCategoria());

        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROD-DUPLICADO"));
    }

    @Test
    @DisplayName("POST /api/v1/productos por CAJERO retorna 403 AUTH-NO-AUTORIZADO")
    void testCrearProductoConCajeroRetorna403() throws Exception {
        String json = """
                {
                  "nombre": "Sprite 350ml",
                  "id_categoria": %d,
                  "precio_venta": 2200.00,
                  "costo_inicial": 1400.00,
                  "stock_inicial": 5,
                  "aplica_iva": false,
                  "stock_minimo": 2
                }
                """.formatted(categoria.getIdCategoria());

        mockMvc.perform(post("/api/v1/productos")
                        .header("Authorization", "Bearer " + cajeroToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AUTH-NO-AUTORIZADO"));
    }

    @Test
    @DisplayName("PUT /api/v1/productos/{id} actualiza datos maestros sin alterar stock ni costo")
    void testActualizarProductoRetorna200() throws Exception {
        String json = """
                {
                  "codigo_barras": "7701234567890",
                  "nombre": "Coca Cola 350ml Vidrio",
                  "descripcion": "Presentación en botella de vidrio",
                  "id_categoria": %d,
                  "precio_venta": 2800.00,
                  "aplica_iva": true,
                  "porcentaje_iva": 19.00,
                  "stock_minimo": 8,
                  "unidad_medida": "BOTELLA"
                }
                """.formatted(categoria.getIdCategoria());

        mockMvc.perform(put("/api/v1/productos/" + producto1.getIdProducto())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Coca Cola 350ml Vidrio"))
                .andExpect(jsonPath("$.precio_venta").value(2800.00))
                .andExpect(jsonPath("$.stock_actual").value(20))
                .andExpect(jsonPath("$.costo_promedio").value(1500.00))
                .andExpect(jsonPath("$.unidad_medida").value("BOTELLA"));
    }

    @Test
    @DisplayName("PATCH /api/v1/productos/{id}/inactivar realiza baja lógica")
    void testInactivarProductoRetorna200() throws Exception {
        mockMvc.perform(patch("/api/v1/productos/" + producto1.getIdProducto() + "/inactivar")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_producto").value(producto1.getIdProducto()))
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    @DisplayName("US-07 Escenario 1: GET /api/v1/productos/barcode/{codigo} por CAJERO retorna 200")
    void testBuscarPorBarcodeExactoRetorna200() throws Exception {
        mockMvc.perform(get("/api/v1/productos/barcode/7701234567890")
                        .header("Authorization", "Bearer " + cajeroToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_producto").value(producto1.getIdProducto()))
                .andExpect(jsonPath("$.codigo_barras").value("7701234567890"))
                .andExpect(jsonPath("$.nombre").value("Coca Cola 350ml"))
                .andExpect(jsonPath("$.precio_venta").value(2500.00))
                .andExpect(jsonPath("$.stock_actual").value(20));
    }

    @Test
    @DisplayName("GET /api/v1/productos/barcode/{codigo} inexistente retorna 404 PROD-BARCODE-NO-ENCONTRADO")
    void testBuscarPorBarcodeInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/productos/barcode/0000000000000")
                        .header("Authorization", "Bearer " + cajeroToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("PROD-BARCODE-NO-ENCONTRADO"));
    }

    @Test
    @DisplayName("US-07 Escenario 2: GET /api/v1/productos con búsqueda por fragmento q retorna filtrados")
    void testListarConFiltroTextoRetorna200() throws Exception {
        mockMvc.perform(get("/api/v1/productos")
                        .header("Authorization", "Bearer " + cajeroToken)
                        .param("q", "Cola"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].nombre").value("Coca Cola 350ml"));
    }
}
