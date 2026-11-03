package com.pva.app.controller;

import com.pva.app.domain.*;
import com.pva.app.repository.*;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FacturaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FacturaRepository facturaRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private TurnoCajaRepository turnoCajaRepository;

    @Autowired
    private OperarioRepository operarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String cajeroToken;
    private Factura facturaGuardada;

    @BeforeEach
    void setUp() {
        Operario admin = operarioRepository.save(Operario.builder()
                .nombreCompleto("Admin Factura Test")
                .numeroDocumento("9900000001")
                .nombreUsuario("admin_fac_test")
                .pinHash(passwordEncoder.encode("1234"))
                .rol(RolOperario.ADMIN)
                .activo(true)
                .intentosFallidos(0)
                .build());

        Operario cajero = operarioRepository.save(Operario.builder()
                .nombreCompleto("Cajero Factura Test")
                .numeroDocumento("9900000002")
                .nombreUsuario("cajero_fac_test")
                .pinHash(passwordEncoder.encode("5678"))
                .rol(RolOperario.CAJERO)
                .activo(true)
                .intentosFallidos(0)
                .build());

        adminToken = jwtService.generateToken(admin.getIdOperario(), admin.getNombreUsuario(), admin.getRol().name());
        cajeroToken = jwtService.generateToken(cajero.getIdOperario(), cajero.getNombreUsuario(), cajero.getRol().name());

        TurnoCaja turno = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .totalEfectivoVentas(new BigDecimal("10000.00"))
                .totalDigitalVentas(BigDecimal.ZERO)
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        Categoria cat = categoriaRepository.save(Categoria.builder()
                .nombre("Abarrotes Fac Test")
                .activo(true)
                .build());

        Producto prod = productoRepository.save(Producto.builder()
                .categoria(cat)
                .nombre("Lenteja 500g")
                .precioVenta(new BigDecimal("3000.00"))
                .costoPromedio(new BigDecimal("2200.00"))
                .stockActual(30)
                .stockMinimo(5)
                .unidadMedida("UNIDAD")
                .aplicaIva(false)
                .porcentajeIva(BigDecimal.ZERO)
                .activo(true)
                .build());

        Venta venta = Venta.builder()
                .turno(turno)
                .operario(cajero)
                .subtotal(new BigDecimal("6000.00"))
                .descuentoGlobal(BigDecimal.ZERO)
                .impuestoTotal(BigDecimal.ZERO)
                .totalAPagar(new BigDecimal("6000.00"))
                .medioPago(MedioPago.EFECTIVO)
                .montoRecibido(new BigDecimal("10000.00"))
                .cambioEntregado(new BigDecimal("4000.00"))
                .estado(EstadoVenta.CONFIRMADA)
                .sincronizada(true)
                .fechaVenta(LocalDateTime.now())
                .build();

        DetalleVenta det = DetalleVenta.builder()
                .venta(venta)
                .producto(prod)
                .cantidad(2)
                .precioUnitario(new BigDecimal("3000.00"))
                .costoUnitarioHistorico(new BigDecimal("2200.00"))
                .descuentoLinea(BigDecimal.ZERO)
                .porcentajeIva(BigDecimal.ZERO)
                .impuestoLinea(BigDecimal.ZERO)
                .subtotalLinea(new BigDecimal("6000.00"))
                .build();
        venta.addDetalle(det);
        Venta ventaGuardada = ventaRepository.save(venta);

        facturaGuardada = facturaRepository.save(Factura.builder()
                .venta(ventaGuardada)
                .numeroTicket("POS-009999")
                .clienteIdentificacion("222222222222")
                .clienteNombre("CONSUMIDOR FINAL")
                .montoTotal(new BigDecimal("6000.00"))
                .totalImpuestos(BigDecimal.ZERO)
                .estado(EstadoFactura.EMITIDA)
                .fechaEmision(LocalDateTime.now())
                .build());
    }

    @Test
    @DisplayName("GET /api/v1/facturas lista comprobantes con token válido")
    void testListarFacturasOk() throws Exception {
        mockMvc.perform(get("/api/v1/facturas")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[0].numero_ticket", is("POS-009999")));
    }

    @Test
    @DisplayName("GET /api/v1/facturas/{id} devuelve detalle completo de comprobante para reimpresión")
    void testObtenerDetalleFacturaOk() throws Exception {
        mockMvc.perform(get("/api/v1/facturas/" + facturaGuardada.getIdFactura())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_factura", is(facturaGuardada.getIdFactura().intValue())))
                .andExpect(jsonPath("$.numero_ticket", is("POS-009999")))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].nombre_producto", is("Lenteja 500g")));
    }

    @Test
    @DisplayName("GET /api/v1/facturas sin autenticación devuelve 401 AUTH-TOKEN-INVALIDO")
    void testListarSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/facturas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo", is("AUTH-TOKEN-INVALIDO")));
    }
}
