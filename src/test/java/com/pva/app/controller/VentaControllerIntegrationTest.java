package com.pva.app.controller;

import com.pva.app.domain.Categoria;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.EstadoVenta;
import com.pva.app.domain.Operario;
import com.pva.app.domain.Producto;
import com.pva.app.domain.RolOperario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.domain.Venta;
import com.pva.app.domain.ConfiguracionNegocio;
import com.pva.app.repository.CategoriaRepository;
import com.pva.app.repository.ConfiguracionNegocioRepository;
import com.pva.app.repository.FacturaRepository;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.repository.ProductoRepository;
import com.pva.app.repository.TurnoCajaRepository;
import com.pva.app.repository.VentaRepository;
import com.pva.app.service.JwtService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VentaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperarioRepository operarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private TurnoCajaRepository turnoCajaRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private FacturaRepository facturaRepository;

    @Autowired
    private ConfiguracionNegocioRepository configuracionNegocioRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Operario admin;
    private Operario cajero1;
    private Operario cajero2;

    private String adminToken;
    private String cajero1Token;
    private String cajero2Token;

    private Producto productoGaseosa;
    private Producto productoAgua;
    private TurnoCaja turnoCajero1;

    @BeforeEach
    void setUp() {
        admin = operarioRepository.findByNombreUsuario("admin_vta_it")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Admin Ventas IT")
                        .numeroDocumento("9500000001")
                        .nombreUsuario("admin_vta_it")
                        .pinHash(passwordEncoder.encode("1234"))
                        .rol(RolOperario.ADMIN)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        cajero1 = operarioRepository.findByNombreUsuario("cajero_vta_1")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Cajero Ventas Uno")
                        .numeroDocumento("9500000002")
                        .nombreUsuario("cajero_vta_1")
                        .pinHash(passwordEncoder.encode("4321"))
                        .rol(RolOperario.CAJERO)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        cajero2 = operarioRepository.findByNombreUsuario("cajero_vta_2")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Cajero Ventas Dos")
                        .numeroDocumento("9500000003")
                        .nombreUsuario("cajero_vta_2")
                        .pinHash(passwordEncoder.encode("5678"))
                        .rol(RolOperario.CAJERO)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        adminToken = jwtService.generateToken(admin.getIdOperario(), "admin_vta_it", "ADMIN");
        cajero1Token = jwtService.generateToken(cajero1.getIdOperario(), "cajero_vta_1", "CAJERO");
        cajero2Token = jwtService.generateToken(cajero2.getIdOperario(), "cajero_vta_2", "CAJERO");

        if (configuracionNegocioRepository.findAll().isEmpty()) {
            configuracionNegocioRepository.save(ConfiguracionNegocio.builder()
                    .administrador(admin)
                    .nombreEstablecimiento("PuntoVenta Agil IT")
                    .nitRut("900111222-3")
                    .direccion("Calle 100")
                    .telefonoContacto("3000000000")
                    .pieTicket("Gracias")
                    .porcentajeIvaDefecto(BigDecimal.valueOf(19.00))
                    .descuentoMaximoCajero(BigDecimal.valueOf(5.00))
                    .build());
        }

        Categoria categoria = categoriaRepository.findByNombre("Bebidas Venta IT")
                .orElseGet(() -> categoriaRepository.save(Categoria.builder()
                        .nombre("Bebidas Venta IT")
                        .descripcion("Bebidas para pruebas de venta")
                        .activo(true)
                        .build()));

        productoGaseosa = productoRepository.findByNombre("Gaseosa VTA 350ml")
                .orElseGet(() -> productoRepository.save(Producto.builder()
                        .nombre("Gaseosa VTA 350ml")
                        .descripcion("Gaseosa prueba venta")
                        .categoria(categoria)
                        .precioVenta(BigDecimal.valueOf(3000.00))
                        .costoPromedio(BigDecimal.valueOf(1500.00))
                        .stockActual(20)
                        .stockMinimo(5)
                        .aplicaIva(true)
                        .porcentajeIva(BigDecimal.valueOf(19.00))
                        .activo(true)
                        .unidadMedida("UNIDAD")
                        .codigoBarras("7701234567890")
                        .build()));
        productoGaseosa.setStockActual(20);
        productoRepository.save(productoGaseosa);

        productoAgua = productoRepository.findByNombre("Agua VTA 500ml")
                .orElseGet(() -> productoRepository.save(Producto.builder()
                        .nombre("Agua VTA 500ml")
                        .descripcion("Agua prueba venta")
                        .categoria(categoria)
                        .precioVenta(BigDecimal.valueOf(1500.00))
                        .costoPromedio(BigDecimal.valueOf(800.00))
                        .stockActual(5)
                        .stockMinimo(2)
                        .aplicaIva(false)
                        .porcentajeIva(BigDecimal.ZERO)
                        .activo(true)
                        .unidadMedida("UNIDAD")
                        .codigoBarras("7701234567891")
                        .build()));
        productoAgua.setStockActual(5);
        productoRepository.save(productoAgua);

        // Turno abierto para Cajero 1
        turnoCajero1 = turnoCajaRepository.findByOperarioAndEstado(cajero1, EstadoTurno.ABIERTO)
                .orElseGet(() -> turnoCajaRepository.save(TurnoCaja.builder()
                        .operario(cajero1)
                        .baseEfectivoInicial(BigDecimal.valueOf(50000.00))
                        .totalEfectivoVentas(BigDecimal.ZERO)
                        .totalDigitalVentas(BigDecimal.ZERO)
                        .estado(EstadoTurno.ABIERTO)
                        .fechaApertura(LocalDateTime.now())
                        .build()));
    }

    private Long extraerIdVenta(String responseBody) {
        return Long.parseLong(responseBody.split("\"id_venta\":")[1].split("[,}]")[0].trim());
    }

    // --- SEGURIDAD: 401 sin token ---

    @Test
    @DisplayName("Endpoints de venta sin token retornan 401 AUTH-TOKEN-INVALIDO")
    void testEndpointsSinTokenRetornan401() throws Exception {
        mockMvc.perform(get("/api/v1/ventas"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/ventas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /api/v1/ventas ---

    @Test
    @DisplayName("POST /api/v1/ventas: Venta exitosa en efectivo actualiza stock, Kardex, turno y emite factura")
    void crearVenta_EfectivoExitoso_Retorna201YActualizaStockYKardexYTurno() throws Exception {
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 2, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "EFECTIVO",
                  "monto_recibido": 10000.00,
                  "cliente": {
                    "identificacion": "11223344",
                    "nombre": "Carlos Lopez",
                    "direccion": "Calle 10",
                    "telefono": "3001234567"
                  }
                }
                """.formatted(turnoCajero1.getIdTurno(), productoGaseosa.getIdProducto());

        mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id_venta").isNumber())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.medio_pago").value("EFECTIVO"))
                .andExpect(jsonPath("$.subtotal").value(6000.00))
                .andExpect(jsonPath("$.impuesto_total").value(1140.00))
                .andExpect(jsonPath("$.total_a_pagar").value(7140.00))
                .andExpect(jsonPath("$.monto_recibido").value(10000.00))
                .andExpect(jsonPath("$.cambio_entregado").value(2860.00))
                .andExpect(jsonPath("$.factura.numero_ticket").value(startsWith("POS-")))
                .andExpect(jsonPath("$.items", hasSize(1)));

        // Verificar descuento de stock (20 - 2 = 18)
        Producto prodActualizado = productoRepository.findById(productoGaseosa.getIdProducto()).orElseThrow();
        assertThat(prodActualizado.getStockActual()).isEqualTo(18);

        // Verificar acumulado en turno de caja (0 + 7140)
        TurnoCaja turnoActualizado = turnoCajaRepository.findById(turnoCajero1.getIdTurno()).orElseThrow();
        assertThat(turnoActualizado.getTotalEfectivoVentas()).isEqualByComparingTo(BigDecimal.valueOf(7140.00));

        // Verificar movimiento en Kardex (append-only)
        List<com.pva.app.domain.MovimientoInventario> movimientos = movimientoInventarioRepository
                .findByProductoOrderByFechaMovimientoDesc(productoGaseosa);
        assertThat(movimientos).isNotEmpty();
        com.pva.app.domain.MovimientoInventario ultimo = movimientos.get(0);
        assertThat(ultimo.getTipoMovimiento()).isEqualTo(TipoMovimientoInventario.VENTA);
        assertThat(ultimo.getCantidad()).isEqualTo(2);
        assertThat(ultimo.getSaldoResultante()).isEqualTo(18);
    }

    @Test
    @DisplayName("POST /api/v1/ventas: Venta exitosa por medio digital incrementa total_digital_ventas")
    void crearVenta_DigitalExitoso_Retorna201YActualizaTurnoDigital() throws Exception {
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 1, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "TRANSFERENCIA",
                  "monto_recibido": 1500.00,
                  "referencia_pago": "NEQ-987654321"
                }
                """.formatted(turnoCajero1.getIdTurno(), productoAgua.getIdProducto());

        String res = mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.medio_pago").value("TRANSFERENCIA"))
                .andExpect(jsonPath("$.total_a_pagar").value(1500.00))
                .andExpect(jsonPath("$.factura.cliente_nombre").value("CONSUMIDOR FINAL"))
                .andReturn().getResponse().getContentAsString();

        Long idVenta = extraerIdVenta(res);
        assertThat(ventaRepository.findById(idVenta).orElseThrow().getReferenciaPago()).isEqualTo("NEQ-987654321");

        TurnoCaja turnoActualizado = turnoCajaRepository.findById(turnoCajero1.getIdTurno()).orElseThrow();
        assertThat(turnoActualizado.getTotalDigitalVentas()).isEqualByComparingTo(BigDecimal.valueOf(1500.00));
    }

    @Test
    @DisplayName("POST /api/v1/ventas: Rechaza medio digital sin referencia de pago con 400 VTA-REFERENCIA-REQUERIDA")
    void crearVenta_DigitalSinReferencia_Retorna400() throws Exception {
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 1, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "TRANSFERENCIA",
                  "monto_recibido": 1500.00,
                  "referencia_pago": "   "
                }
                """.formatted(turnoCajero1.getIdTurno(), productoAgua.getIdProducto());

        mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VTA-REFERENCIA-REQUERIDA"));
    }

    @Test
    @DisplayName("POST /api/v1/ventas: Rechaza venta si el operario no tiene turno abierto con 403 VTA-SIN-TURNO")
    void crearVenta_SinTurnoAbierto_Retorna403() throws Exception {
        // Cajero 2 no tiene turno abierto
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 1, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "EFECTIVO",
                  "monto_recibido": 2000.00
                }
                """.formatted(turnoCajero1.getIdTurno(), productoAgua.getIdProducto());

        mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("VTA-SIN-TURNO"));
    }

    @Test
    @DisplayName("POST /api/v1/ventas: Rechaza venta si el monto recibido es menor al total con 400 VTA-MONTO-INSUFICIENTE")
    void crearVenta_MontoRecibidoInsuficiente_Retorna400() throws Exception {
        // total = 3000, recibido = 2000
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 2, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "EFECTIVO",
                  "monto_recibido": 2000.00
                }
                """.formatted(turnoCajero1.getIdTurno(), productoAgua.getIdProducto());

        mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VTA-MONTO-INSUFICIENTE"));
    }

    @Test
    @DisplayName("POST /api/v1/ventas: Rechaza venta si no hay stock suficiente en línea con 400 VTA-STOCK-INSUFICIENTE")
    void crearVenta_StockInsuficiente_Retorna400() throws Exception {
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 50, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "EFECTIVO",
                  "monto_recibido": 100000.00
                }
                """.formatted(turnoCajero1.getIdTurno(), productoAgua.getIdProducto());

        mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VTA-STOCK-INSUFICIENTE"));
    }

    @Test
    @DisplayName("POST /api/v1/ventas: Rechaza descuento mayor al 5% para cajero con 403 VTA-DESCUENTO-EXCEDIDO")
    void crearVenta_DescuentoExcedidoCajero_Retorna403() throws Exception {
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 10, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 2000.00,
                  "medio_pago": "EFECTIVO",
                  "monto_recibido": 40000.00
                }
                """.formatted(turnoCajero1.getIdTurno(), productoGaseosa.getIdProducto());

        mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("VTA-DESCUENTO-EXCEDIDO"));
    }

    // --- GET /api/v1/ventas y GET /api/v1/ventas/{id} ---

    @Test
    @DisplayName("GET /api/v1/ventas: Administrador lista todas las ventas y Cajero solo las suyas")
    void listar_DiferenciaPermisosPorRol() throws Exception {
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 1, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "EFECTIVO",
                  "monto_recibido": 2000.00
                }
                """.formatted(turnoCajero1.getIdTurno(), productoAgua.getIdProducto());

        mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        // Admin ve al menos 1
        mockMvc.perform(get("/api/v1/ventas")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));

        // Cajero 1 ve al menos 1
        mockMvc.perform(get("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));

        // Cajero 2 ve lista vacía (no ha registrado ventas)
        mockMvc.perform(get("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    @DisplayName("GET /api/v1/ventas: Rechaza rango de fechas con 'desde' posterior a 'hasta' con 400 VTA-RANGO-INVALIDO")
    void listar_RangoFechasInvalido_Retorna400() throws Exception {
        LocalDateTime desde = LocalDateTime.now();
        LocalDateTime hasta = desde.minusDays(2);

        mockMvc.perform(get("/api/v1/ventas")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("desde", desde.toString())
                        .param("hasta", hasta.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VTA-RANGO-INVALIDO"));
    }

    @Test
    @DisplayName("GET /api/v1/ventas/{id}: Cajero ajeno recibe 403 AUTH-NO-AUTORIZADO")
    void obtenerPorId_CajeroAjeno_Retorna403() throws Exception {
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 1, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "EFECTIVO",
                  "monto_recibido": 2000.00
                }
                """.formatted(turnoCajero1.getIdTurno(), productoAgua.getIdProducto());

        String response = mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idVenta = extraerIdVenta(response);

        // Cajero 2 intenta consultar la venta de Cajero 1 -> 403
        mockMvc.perform(get("/api/v1/ventas/" + idVenta)
                        .header("Authorization", "Bearer " + cajero2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AUTH-NO-AUTORIZADO"));

        // Admin puede consultarla -> 200
        mockMvc.perform(get("/api/v1/ventas/" + idVenta)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_venta").value(idVenta));
    }

    // --- PATCH /api/v1/ventas/{id}/anular ---

    @Test
    @DisplayName("PATCH /api/v1/ventas/{id}/anular: Admin anula venta exitosamente, reingresa stock y registra ajuste en Kardex")
    void anular_ComoAdminConPinValido_Retorna200YReingresaStock() throws Exception {
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 3, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "EFECTIVO",
                  "monto_recibido": 15000.00
                }
                """.formatted(turnoCajero1.getIdTurno(), productoGaseosa.getIdProducto());

        String createResponse = mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idVenta = extraerIdVenta(createResponse);
        assertThat(productoRepository.findById(productoGaseosa.getIdProducto()).orElseThrow().getStockActual())
                .isEqualTo(17);

        // Admin anula venta con PIN correcto "1234"
        String anularJson = """
                {
                  "motivo_anulacion": "Cliente devolvio el producto",
                  "pin": "1234"
                }
                """;

        mockMvc.perform(patch("/api/v1/ventas/" + idVenta + "/anular")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anularJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_venta").value(idVenta))
                .andExpect(jsonPath("$.estado").value("ANULADA"))
                .andExpect(jsonPath("$.factura.estado").value("ANULADA"))
                .andExpect(jsonPath("$.mensaje").value(containsString("Stock reintegrado")));

        // Verificar que el stock reingresó (17 + 3 = 20)
        assertThat(productoRepository.findById(productoGaseosa.getIdProducto()).orElseThrow().getStockActual())
                .isEqualTo(20);

        // Verificar venta y factura marcadas como ANULADA
        Venta ventaAnulada = ventaRepository.findById(idVenta).orElseThrow();
        assertThat(ventaAnulada.getEstado()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(ventaAnulada.getFactura().getEstado()).isEqualTo(com.pva.app.domain.EstadoFactura.ANULADA);

        // Verificar movimiento compensatorio en Kardex (AJUSTE_ENTRADA)
        List<com.pva.app.domain.MovimientoInventario> movimientos = movimientoInventarioRepository
                .findByProductoOrderByFechaMovimientoDesc(productoGaseosa);
        com.pva.app.domain.MovimientoInventario ultimo = movimientos.get(0);
        assertThat(ultimo.getTipoMovimiento()).isEqualTo(TipoMovimientoInventario.AJUSTE_ENTRADA);
        assertThat(ultimo.getCantidad()).isEqualTo(3);
        assertThat(ultimo.getSaldoResultante()).isEqualTo(20);
    }

    @Test
    @DisplayName("PATCH /api/v1/ventas/{id}/anular: Cajero no tiene permiso y es rechazado con 403 Forbidden")
    void anular_ComoCajero_Retorna403() throws Exception {
        String anularJson = """
                {
                  "motivo_anulacion": "Intento no autorizado",
                  "pin": "4321"
                }
                """;

        mockMvc.perform(patch("/api/v1/ventas/999/anular")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anularJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/v1/ventas/{id}/anular: Rechaza anulación si el PIN de admin es incorrecto con 401 OPE-PIN-INVALIDO")
    void anular_AdminPinInvalido_Retorna401() throws Exception {
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 1, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "EFECTIVO",
                  "monto_recibido": 2000.00
                }
                """.formatted(turnoCajero1.getIdTurno(), productoAgua.getIdProducto());

        String response = mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idVenta = extraerIdVenta(response);

        String anularJson = """
                {
                  "motivo_anulacion": "Motivo valido",
                  "pin": "9999"
                }
                """;

        mockMvc.perform(patch("/api/v1/ventas/" + idVenta + "/anular")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anularJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("OPE-PIN-INVALIDO"));
    }

    @Test
    @DisplayName("PATCH /api/v1/ventas/{id}/anular: Rechaza anular una venta ya anulada con 409 VTA-YA-ANULADA")
    void anular_VentaYaAnulada_Retorna409() throws Exception {
        String json = """
                {
                  "id_turno": %d,
                  "items": [
                    { "id_producto": %d, "cantidad": 1, "descuento_linea": 0.00 }
                  ],
                  "descuento_global": 0.00,
                  "medio_pago": "EFECTIVO",
                  "monto_recibido": 2000.00
                }
                """.formatted(turnoCajero1.getIdTurno(), productoAgua.getIdProducto());

        String response = mockMvc.perform(post("/api/v1/ventas")
                        .header("Authorization", "Bearer " + cajero1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long idVenta = extraerIdVenta(response);

        String anularJson = """
                {
                  "motivo_anulacion": "Primera anulacion",
                  "pin": "1234"
                }
                """;

        mockMvc.perform(patch("/api/v1/ventas/" + idVenta + "/anular")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anularJson))
                .andExpect(status().isOk());

        // Segunda anulación -> 409
        mockMvc.perform(patch("/api/v1/ventas/" + idVenta + "/anular")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(anularJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("VTA-YA-ANULADA"));
    }
}
