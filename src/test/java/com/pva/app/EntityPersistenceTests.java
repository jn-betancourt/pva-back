package com.pva.app;

import com.pva.app.domain.Categoria;
import com.pva.app.repository.CategoriaRepository;
import com.pva.app.domain.ConfiguracionNegocio;
import com.pva.app.repository.ConfiguracionNegocioRepository;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.Producto;
import com.pva.app.repository.ProductoRepository;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.repository.TurnoCajaRepository;
import com.pva.app.domain.*;
import com.pva.app.repository.DetalleVentaRepository;
import com.pva.app.repository.FacturaRepository;
import com.pva.app.repository.VentaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EntityPersistenceTests {

    @Autowired
    private OperarioRepository operarioRepository;

    @Autowired
    private ConfiguracionNegocioRepository configuracionNegocioRepository;

    @Autowired
    private TurnoCajaRepository turnoCajaRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private DetalleVentaRepository detalleVentaRepository;

    @Autowired
    private FacturaRepository facturaRepository;

    @Test
    @DisplayName("Debe persistir y consultar Operario y ConfiguracionNegocio")
    void testOperarioYConfiguracion() {
        Operario admin = Operario.builder()
                .nombreCompleto("Administrador Principal")
                .numeroDocumento("1000000001")
                .nombreUsuario("admin_persistence")
                .pinHash("$2a$10$hashedpinvalueforexample")
                .rol(RolOperario.ADMIN)
                .activo(true)
                .intentosFallidos(0)
                .build();
        admin = operarioRepository.save(admin);

        assertThat(admin.getIdOperario()).isNotNull();

        ConfiguracionNegocio config = ConfiguracionNegocio.builder()
                .administrador(admin)
                .nombreEstablecimiento("Tienda de Barrio PVA")
                .nitRut("900123456-1")
                .direccion("Calle 10 # 5-20")
                .telefonoContacto("3001234567")
                .porcentajeIvaDefecto(new BigDecimal("19.00"))
                .descuentoMaximoCajero(new BigDecimal("10.00"))
                .pieTicket("¡Gracias por su compra!")
                .build();
        config = configuracionNegocioRepository.save(config);

        assertThat(config.getIdConfiguracion()).isNotNull();
        assertThat(config.getAdministrador().getIdOperario()).isEqualTo(admin.getIdOperario());
    }

    @Test
    @DisplayName("Debe persistir TurnoCaja, Categoria, Producto y Kardex (MovimientoInventario)")
    void testTurnoProductoYKardex() {
        Operario cajero = operarioRepository.save(Operario.builder()
                .nombreCompleto("Cajero Uno")
                .numeroDocumento("1000000002")
                .nombreUsuario("cajero1")
                .pinHash("$2a$10$hashedpinvalueforexample")
                .rol(RolOperario.CAJERO)
                .activo(true)
                .intentosFallidos(0)
                .build());

        TurnoCaja turno = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero)
                .baseEfectivoInicial(new BigDecimal("100000.00"))
                .totalEfectivoVentas(BigDecimal.ZERO)
                .totalDigitalVentas(BigDecimal.ZERO)
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        assertThat(turno.getIdTurno()).isNotNull();

        Categoria categoria = categoriaRepository.save(Categoria.builder()
                .nombre("Bebidas")
                .descripcion("Gaseosas, jugos y aguas")
                .activo(true)
                .build());

        assertThat(categoria.getIdCategoria()).isNotNull();

        Producto producto = productoRepository.save(Producto.builder()
                .codigoBarras("7701234567890")
                .nombre("Gaseosa 500ml")
                .descripcion("Bebida gaseosa sabor cola")
                .categoria(categoria)
                .precioVenta(new BigDecimal("3500.00"))
                .costoPromedio(new BigDecimal("2200.00"))
                .aplicaIva(true)
                .porcentajeIva(new BigDecimal("19.00"))
                .stockActual(50)
                .stockMinimo(10)
                .unidadMedida("UNIDAD")
                .activo(true)
                .build());

        assertThat(producto.getIdProducto()).isNotNull();

        MovimientoInventario kardex = MovimientoInventario.builder()
                .producto(producto)
                .tipoMovimiento(TipoMovimientoInventario.COMPRA)
                .cantidad(50)
                .costoUnitario(new BigDecimal("2200.00"))
                .costoTotal(new BigDecimal("110000.00"))
                .saldoResultante(50)
                .idReferencia("FAC-PROV-001")
                .operario(cajero)
                .fechaMovimiento(LocalDateTime.now())
                .build();
        kardex = movimientoInventarioRepository.save(kardex);

        assertThat(kardex.getIdMovimiento()).isNotNull();
        assertThat(kardex.getProducto().getIdProducto()).isEqualTo(producto.getIdProducto());
    }

    @Test
    @DisplayName("Debe persistir Venta, DetalleVenta y Factura con relaciones consistentes")
    void testVentaDetalleYFactura() {
        Operario cajero = operarioRepository.save(Operario.builder()
                .nombreCompleto("Cajero Dos")
                .numeroDocumento("1000000003")
                .nombreUsuario("cajero2")
                .pinHash("$2a$10$hashedpinvalueforexample")
                .rol(RolOperario.CAJERO)
                .activo(true)
                .intentosFallidos(0)
                .build());

        TurnoCaja turno = turnoCajaRepository.save(TurnoCaja.builder()
                .operario(cajero)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .totalEfectivoVentas(BigDecimal.ZERO)
                .totalDigitalVentas(BigDecimal.ZERO)
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build());

        Categoria cat = categoriaRepository.save(Categoria.builder()
                .nombre("Snacks")
                .descripcion("Papas y galletas")
                .activo(true)
                .build());

        Producto prod = productoRepository.save(Producto.builder()
                .codigoBarras("7709876543210")
                .nombre("Papas Fritas 100g")
                .categoria(cat)
                .precioVenta(new BigDecimal("2500.00"))
                .costoPromedio(new BigDecimal("1500.00"))
                .aplicaIva(false)
                .porcentajeIva(BigDecimal.ZERO)
                .stockActual(20)
                .stockMinimo(5)
                .unidadMedida("UNIDAD")
                .activo(true)
                .build());

        Venta venta = Venta.builder()
                .turno(turno)
                .operario(cajero)
                .uuidOffline("test-uuid-0001")
                .subtotal(new BigDecimal("5000.00"))
                .descuentoGlobal(BigDecimal.ZERO)
                .impuestoTotal(BigDecimal.ZERO)
                .totalAPagar(new BigDecimal("5000.00"))
                .medioPago(MedioPago.EFECTIVO)
                .montoRecibido(new BigDecimal("10000.00"))
                .cambioEntregado(new BigDecimal("5000.00"))
                .estado(EstadoVenta.CONFIRMADA)
                .sincronizada(true)
                .fechaVenta(LocalDateTime.now())
                .build();
        venta = ventaRepository.save(venta);

        DetalleVenta detalle = DetalleVenta.builder()
                .venta(venta)
                .producto(prod)
                .cantidad(2)
                .precioUnitario(new BigDecimal("2500.00"))
                .costoUnitarioHistorico(new BigDecimal("1500.00"))
                .descuentoLinea(BigDecimal.ZERO)
                .porcentajeIva(BigDecimal.ZERO)
                .impuestoLinea(BigDecimal.ZERO)
                .subtotalLinea(new BigDecimal("5000.00"))
                .build();
        detalle = detalleVentaRepository.save(detalle);

        Factura factura = Factura.builder()
                .venta(venta)
                .numeroTicket("POS-000001")
                .clienteIdentificacion("222222222222")
                .clienteNombre("CONSUMIDOR FINAL")
                .montoTotal(new BigDecimal("5000.00"))
                .totalImpuestos(BigDecimal.ZERO)
                .estado(EstadoFactura.EMITIDA)
                .fechaEmision(LocalDateTime.now())
                .build();
        factura = facturaRepository.save(factura);

        assertThat(venta.getIdVenta()).isNotNull();
        assertThat(detalle.getIdDetalle()).isNotNull();
        assertThat(factura.getIdFactura()).isNotNull();

        Optional<Factura> buscada = facturaRepository.findByNumeroTicket("POS-000001");
        assertThat(buscada).isPresent();
        assertThat(buscada.get().getClienteNombre()).isEqualTo("CONSUMIDOR FINAL");
        assertThat(buscada.get().getVenta().getIdVenta()).isEqualTo(venta.getIdVenta());
    }
}
