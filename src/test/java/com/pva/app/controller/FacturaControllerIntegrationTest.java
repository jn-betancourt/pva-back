package com.pva.app.controller;

import com.pva.app.domain.Categoria;
import com.pva.app.domain.ConfiguracionNegocio;
import com.pva.app.domain.EstadoFactura;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.EstadoVenta;
import com.pva.app.domain.Factura;
import com.pva.app.domain.MedioPago;
import com.pva.app.domain.Operario;
import com.pva.app.domain.Producto;
import com.pva.app.domain.RolOperario;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.domain.Venta;
import com.pva.app.repository.CategoriaRepository;
import com.pva.app.repository.ConfiguracionNegocioRepository;
import com.pva.app.repository.FacturaRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FacturaControllerIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Operario admin;
    private Operario cajero1;
    private Operario cajero2;

    private String adminToken;
    private String cajero1Token;
    private String cajero2Token;

    private Factura facturaCajero1;

    @BeforeEach
    void setUp() {
        admin = operarioRepository.findByNombreUsuario("admin_fac_it")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Admin Facturas IT")
                        .numeroDocumento("9600000001")
                        .nombreUsuario("admin_fac_it")
                        .pinHash(passwordEncoder.encode("1234"))
                        .rol(RolOperario.ADMIN)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        cajero1 = operarioRepository.findByNombreUsuario("cajero_fac_1")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Cajero Facturas Uno")
                        .numeroDocumento("9600000002")
                        .nombreUsuario("cajero_fac_1")
                        .pinHash(passwordEncoder.encode("4321"))
                        .rol(RolOperario.CAJERO)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        cajero2 = operarioRepository.findByNombreUsuario("cajero_fac_2")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Cajero Facturas Dos")
                        .numeroDocumento("9600000003")
                        .nombreUsuario("cajero_fac_2")
                        .pinHash(passwordEncoder.encode("5678"))
                        .rol(RolOperario.CAJERO)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        adminToken = jwtService.generateToken(admin.getIdOperario(), "admin_fac_it", "ADMIN");
        cajero1Token = jwtService.generateToken(cajero1.getIdOperario(), "cajero_fac_1", "CAJERO");
        cajero2Token = jwtService.generateToken(cajero2.getIdOperario(), "cajero_fac_2", "CAJERO");

        // Configuración de negocio para datos fiscales en ticket
        if (configuracionNegocioRepository.findAll().isEmpty()) {
            configuracionNegocioRepository.save(ConfiguracionNegocio.builder()
                    .administrador(admin)
                    .nombreEstablecimiento("Supermercado Central PVA")
                    .nitRut("900123456-7")
                    .direccion("Carrera 5 # 10-20")
                    .telefonoContacto("3101234567")
                    .pieTicket("Gracias por su compra en PVA")
                    .porcentajeIvaDefecto(BigDecimal.valueOf(19.00))
                    .descuentoMaximoCajero(BigDecimal.valueOf(10.00))
                    .build());
        }

        Categoria categoria = categoriaRepository.findByNombre("Snacks Factura IT")
                .orElseGet(() -> categoriaRepository.save(Categoria.builder()
                        .nombre("Snacks Factura IT")
                        .descripcion("Snacks para pruebas de factura")
                        .activo(true)
                        .build()));

        Producto producto = productoRepository.findByNombre("Papas Fritas 115g")
                .orElseGet(() -> productoRepository.save(Producto.builder()
                        .nombre("Papas Fritas 115g")
                        .descripcion("Papas fritas")
                        .categoria(categoria)
                        .precioVenta(BigDecimal.valueOf(2500.00))
                        .costoPromedio(BigDecimal.valueOf(1200.00))
                        .stockActual(30)
                        .stockMinimo(5)
                        .aplicaIva(true)
                        .porcentajeIva(BigDecimal.valueOf(19.00))
                        .activo(true)
                        .unidadMedida("UNIDAD")
                        .codigoBarras("770999900001")
                        .build()));

        TurnoCaja turno = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero1)
                .baseEfectivoInicial(BigDecimal.valueOf(20000.00))
                .totalEfectivoVentas(BigDecimal.valueOf(2975.00))
                .totalDigitalVentas(BigDecimal.ZERO)
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        Venta venta = ventaRepository.save(Venta.builder()
                .turno(turno)
                .operario(cajero1)
                .subtotal(BigDecimal.valueOf(2500.00))
                .descuentoGlobal(BigDecimal.ZERO)
                .impuestoTotal(BigDecimal.valueOf(475.00))
                .totalAPagar(BigDecimal.valueOf(2975.00))
                .medioPago(MedioPago.EFECTIVO)
                .montoRecibido(BigDecimal.valueOf(5000.00))
                .cambioEntregado(BigDecimal.valueOf(2025.00))
                .estado(EstadoVenta.CONFIRMADA)
                .sincronizada(true)
                .fechaVenta(LocalDateTime.now())
                .build());

        facturaCajero1 = facturaRepository.save(Factura.builder()
                .venta(venta)
                .numeroTicket("POS-000100")
                .clienteIdentificacion("123456789")
                .clienteNombre("Maria Gomez")
                .clienteDireccion("Calle 1 # 2-3")
                .clienteTelefono("3201112233")
                .montoTotal(BigDecimal.valueOf(2975.00))
                .totalImpuestos(BigDecimal.valueOf(475.00))
                .estado(EstadoFactura.EMITIDA)
                .fechaEmision(LocalDateTime.now())
                .build());

        venta.setFactura(facturaCajero1);
        ventaRepository.save(venta);
    }

    // --- SEGURIDAD: 401 sin token ---

    @Test
    @DisplayName("Endpoints de factura sin token retornan 401 AUTH-TOKEN-INVALIDO")
    void testEndpointsSinTokenRetornan401() throws Exception {
        mockMvc.perform(get("/api/v1/facturas"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/facturas/" + facturaCajero1.getIdFactura()))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/v1/facturas ---

    @Test
    @DisplayName("GET /api/v1/facturas: Administrador lista facturas y puede filtrar por ticket y estado")
    void listar_ComoAdmin_RetornaFacturasYFiltra() throws Exception {
        mockMvc.perform(get("/api/v1/facturas")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("numero_ticket", "POS-000100")
                        .param("estado", "EMITIDA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].numero_ticket").value("POS-000100"))
                .andExpect(jsonPath("$[0].estado").value("EMITIDA"))
                .andExpect(jsonPath("$[0].cliente_nombre").value("Maria Gomez"));
    }

    @Test
    @DisplayName("GET /api/v1/facturas: Cajero solo lista facturas de sus propias ventas")
    void listar_ComoCajero_AplicaFiltroOperario() throws Exception {
        // Cajero 1 ve su factura
        mockMvc.perform(get("/api/v1/facturas")
                        .header("Authorization", "Bearer " + cajero1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].numero_ticket").value("POS-000100"));

        // Cajero 2 no tiene ventas, ve lista vacía
        mockMvc.perform(get("/api/v1/facturas")
                        .header("Authorization", "Bearer " + cajero2Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    @DisplayName("GET /api/v1/facturas: Rechaza rango de fechas con 'desde' posterior a 'hasta' con 400 FAC-RANGO-INVALIDO")
    void listar_RangoFechasInvalido_Retorna400() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        mockMvc.perform(get("/api/v1/facturas")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("desde", ahora.toString())
                        .param("hasta", ahora.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("FAC-RANGO-INVALIDO"));
    }

    // --- GET /api/v1/facturas/{id} ---

    @Test
    @DisplayName("GET /api/v1/facturas/{id}: Admin obtiene comprobante completo con datos fiscales para impresión")
    void obtenerPorId_ComoAdmin_RetornaDetalleCompleto() throws Exception {
        mockMvc.perform(get("/api/v1/facturas/" + facturaCajero1.getIdFactura())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_factura").value(facturaCajero1.getIdFactura()))
                .andExpect(jsonPath("$.numero_ticket").value("POS-000100"))
                .andExpect(jsonPath("$.estado").value("EMITIDA"))
                .andExpect(jsonPath("$.negocio.nombre_establecimiento").value("Supermercado Central PVA"))
                .andExpect(jsonPath("$.negocio.nit_rut").value("900123456-7"))
                .andExpect(jsonPath("$.negocio.pie_ticket").value("Gracias por su compra en PVA"))
                .andExpect(jsonPath("$.cliente_identificacion").value("123456789"))
                .andExpect(jsonPath("$.cliente_nombre").value("Maria Gomez"))
                .andExpect(jsonPath("$.monto_total").value(2975.00))
                .andExpect(jsonPath("$.total_impuestos").value(475.00));
    }

    @Test
    @DisplayName("GET /api/v1/facturas/{id}: Cajero propietario puede consultar su comprobante")
    void obtenerPorId_ComoCajeroPropietario_Retorna200() throws Exception {
        mockMvc.perform(get("/api/v1/facturas/" + facturaCajero1.getIdFactura())
                        .header("Authorization", "Bearer " + cajero1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_factura").value(facturaCajero1.getIdFactura()))
                .andExpect(jsonPath("$.numero_ticket").value("POS-000100"));
    }

    @Test
    @DisplayName("GET /api/v1/facturas/{id}: Cajero ajeno es rechazado con 403 AUTH-NO-AUTORIZADO")
    void obtenerPorId_ComoOtroCajero_Retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/facturas/" + facturaCajero1.getIdFactura())
                        .header("Authorization", "Bearer " + cajero2Token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AUTH-NO-AUTORIZADO"));
    }

    @Test
    @DisplayName("GET /api/v1/facturas/{id}: Factura inexistente retorna 404 FAC-NO-ENCONTRADA")
    void obtenerPorId_NoExiste_Retorna404() throws Exception {
        mockMvc.perform(get("/api/v1/facturas/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("FAC-NO-ENCONTRADA"));
    }
}
