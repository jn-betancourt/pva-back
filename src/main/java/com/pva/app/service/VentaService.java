package com.pva.app.service;

import com.pva.app.domain.ConfiguracionNegocio;
import com.pva.app.domain.DetalleVenta;
import com.pva.app.domain.EstadoFactura;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.EstadoVenta;
import com.pva.app.domain.Factura;
import com.pva.app.domain.MedioPago;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.Operario;
import com.pva.app.domain.Producto;
import com.pva.app.domain.RolOperario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.domain.Venta;
import com.pva.app.dto.request.AnularVentaRequest;
import com.pva.app.dto.request.CrearVentaRequest;
import com.pva.app.dto.response.AnulacionVentaResponse;
import com.pva.app.dto.response.VentaResponse;
import com.pva.app.dto.response.VentaResumenResponse;
import com.pva.app.exception.AppException;
import com.pva.app.exception.BadCredentialsException;
import com.pva.app.exception.ConflictException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.repository.ConfiguracionNegocioRepository;
import com.pva.app.repository.FacturaRepository;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.repository.ProductoRepository;
import com.pva.app.repository.TurnoCajaRepository;
import com.pva.app.repository.VentaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class VentaService {

    private static final String CLIENTE_ID_DEFECTO = "222222222222";
    private static final String CLIENTE_NOMBRE_DEFECTO = "CONSUMIDOR FINAL";
    private static final BigDecimal CIEN = new BigDecimal("100");

    private final VentaRepository ventaRepository;
    private final FacturaRepository facturaRepository;
    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final TurnoCajaRepository turnoCajaRepository;
    private final OperarioRepository operarioRepository;
    private final ConfiguracionNegocioRepository configuracionNegocioRepository;
    private final PasswordEncoder passwordEncoder;

    public VentaService(VentaRepository ventaRepository,
                        FacturaRepository facturaRepository,
                        ProductoRepository productoRepository,
                        MovimientoInventarioRepository movimientoInventarioRepository,
                        TurnoCajaRepository turnoCajaRepository,
                        OperarioRepository operarioRepository,
                        ConfiguracionNegocioRepository configuracionNegocioRepository,
                        PasswordEncoder passwordEncoder) {
        this.ventaRepository = ventaRepository;
        this.facturaRepository = facturaRepository;
        this.productoRepository = productoRepository;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
        this.turnoCajaRepository = turnoCajaRepository;
        this.operarioRepository = operarioRepository;
        this.configuracionNegocioRepository = configuracionNegocioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * US-12, US-13, US-14, US-15: crea la venta, descuenta stock, asienta el Kardex, acumula en el turno
     * y genera la factura correlativa en una unica transaccion ACID (cualquier excepcion revierte todo).
     */
    @Transactional
    public VentaResponse crearVenta(CrearVentaRequest request, Long idOperario) {
        Operario operario = buscarOperario(idOperario);

        // Idempotencia para ventas offline (RN-OFF-02): si ya existe, se devuelve la original.
        if (request.uuidOffline() != null && !request.uuidOffline().isBlank()) {
            Optional<Venta> existente = ventaRepository.findByUuidOffline(request.uuidOffline());
            if (existente.isPresent()) {
                return VentaResponse.fromEntity(existente.get());
            }
        }

        // RN-OPE-04 / RN-VTA-01: se exige un turno ABIERTO del operario.
        TurnoCaja turno = turnoCajaRepository.findByOperarioAndEstado(operario, EstadoTurno.ABIERTO)
                .orElseThrow(() -> new AppException(
                        "No hay turno de caja abierto",
                        HttpStatus.FORBIDDEN,
                        "VTA-SIN-TURNO",
                        "Abra un turno antes de procesar ventas"));
        if (!turno.getIdTurno().equals(request.idTurno())) {
            throw new ConflictException(
                    "El turno indicado no corresponde al turno abierto del operario",
                    "VTA-TURNO-NO-COINCIDE",
                    "Turno abierto: " + turno.getIdTurno() + " | Turno recibido: " + request.idTurno());
        }

        // Bloqueo pesimista por fila, en orden por id para evitar deadlocks entre ventas concurrentes.
        Map<Long, Producto> productos = new HashMap<>();
        request.items().stream()
                .map(CrearVentaRequest.Item::idProducto)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .forEach(id -> productos.put(id, productoRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Producto no encontrado", "PROD-NO-ENCONTRADO", "id_producto: " + id))));

        Venta venta = Venta.builder()
                .turno(turno)
                .operario(operario)
                .uuidOffline(blankToNull(request.uuidOffline()))
                .build();

        List<MovimientoInventario> movimientos = new ArrayList<>();
        List<Object[]> pendientesKardex = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal impuestoTotal = BigDecimal.ZERO;
        BigDecimal brutoTotal = BigDecimal.ZERO;
        BigDecimal descuentoLineasTotal = BigDecimal.ZERO;

        for (CrearVentaRequest.Item item : request.items()) {
            Producto producto = productos.get(item.idProducto());

            if (Boolean.FALSE.equals(producto.getActivo())) {
                throw new AppException(
                        "El producto esta inactivo y no puede venderse",
                        HttpStatus.BAD_REQUEST,
                        "VTA-PRODUCTO-INACTIVO",
                        "Producto '" + producto.getNombre() + "'");
            }
            boolean esOffline = request.uuidOffline() != null && !request.uuidOffline().isBlank();
            if (!esOffline && producto.getStockActual() < item.cantidad()) {
                throw new AppException(
                        "Stock insuficiente para uno o mas productos",
                        HttpStatus.BAD_REQUEST,
                        "VTA-STOCK-INSUFICIENTE",
                        "Producto '" + producto.getNombre() + "' tiene " + producto.getStockActual()
                                + " unidades. Solicitadas: " + item.cantidad());
            }

            BigDecimal cantidad = BigDecimal.valueOf(item.cantidad());
            BigDecimal precio = producto.getPrecioVenta().setScale(2, RoundingMode.HALF_UP);
            BigDecimal bruto = precio.multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
            BigDecimal descuentoLinea = nvl(item.descuentoLinea()).setScale(2, RoundingMode.HALF_UP);
            if (descuentoLinea.compareTo(bruto) > 0) {
                throw new AppException(
                        "El descuento de linea no puede superar el valor de la linea",
                        HttpStatus.BAD_REQUEST,
                        "VTA-DESCUENTO-INVALIDO",
                        "Producto '" + producto.getNombre() + "' | Linea: " + bruto + " | Descuento: " + descuentoLinea);
            }

            BigDecimal porcentajeIva = Boolean.TRUE.equals(producto.getAplicaIva())
                    ? producto.getPorcentajeIva() : BigDecimal.ZERO;
            BigDecimal subtotalLinea = bruto.subtract(descuentoLinea);
            BigDecimal impuestoLinea = subtotalLinea.multiply(porcentajeIva)
                    .divide(CIEN, 2, RoundingMode.HALF_UP);

            // US-14: descuento atomico de stock (precio y costo quedan congelados en el detalle).
            int nuevoStock = producto.getStockActual() - item.cantidad();
            producto.setStockActual(nuevoStock);
            productoRepository.save(producto);

            DetalleVenta detalle = DetalleVenta.builder()
                    .venta(venta)
                    .producto(producto)
                    .cantidad(item.cantidad())
                    .precioUnitario(precio)
                    .costoUnitarioHistorico(producto.getCostoPromedio())
                    .descuentoLinea(descuentoLinea)
                    .porcentajeIva(porcentajeIva)
                    .impuestoLinea(impuestoLinea)
                    .subtotalLinea(subtotalLinea)
                    .build();
            venta.getDetalles().add(detalle);

            pendientesKardex.add(new Object[]{producto, item.cantidad(), nuevoStock});

            brutoTotal = brutoTotal.add(bruto);
            descuentoLineasTotal = descuentoLineasTotal.add(descuentoLinea);
            subtotal = subtotal.add(subtotalLinea);
            impuestoTotal = impuestoTotal.add(impuestoLinea);
        }

        BigDecimal descuentoGlobal = nvl(request.descuentoGlobal()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal baseConImpuesto = subtotal.add(impuestoTotal);
        if (descuentoGlobal.compareTo(baseConImpuesto) > 0) {
            throw new AppException(
                    "El descuento global no puede superar el total de la venta",
                    HttpStatus.BAD_REQUEST,
                    "VTA-DESCUENTO-INVALIDO",
                    "Total antes de descuento: " + baseConImpuesto + " | Descuento global: " + descuentoGlobal);
        }
        validarTopeDescuentoCajero(operario, brutoTotal, descuentoLineasTotal.add(descuentoGlobal));

        BigDecimal total = baseConImpuesto.subtract(descuentoGlobal);

        // US-13: liquidacion multimedio y calculo de vuelto.
        BigDecimal montoRecibido = request.montoRecibido().setScale(2, RoundingMode.HALF_UP);
        BigDecimal cambio = BigDecimal.ZERO.setScale(2);
        if (request.medioPago() == MedioPago.EFECTIVO) {
            if (montoRecibido.compareTo(total) < 0) {
                throw new AppException(
                        "El monto recibido es inferior al total a pagar",
                        HttpStatus.BAD_REQUEST,
                        "VTA-MONTO-INSUFICIENTE",
                        "Total: " + total + " | Recibido: " + montoRecibido);
            }
            cambio = montoRecibido.subtract(total);
        } else {
            if (request.referenciaPago() == null || request.referenciaPago().isBlank()) {
                throw new AppException(
                        "La referencia de pago es obligatoria para transferencia o tarjeta",
                        HttpStatus.BAD_REQUEST,
                        "VTA-REFERENCIA-REQUERIDA");
            }
            montoRecibido = total;
        }

        venta.setSubtotal(subtotal);
        venta.setDescuentoGlobal(descuentoGlobal);
        venta.setImpuestoTotal(impuestoTotal);
        venta.setTotalAPagar(total);
        venta.setMedioPago(request.medioPago());
        venta.setMontoRecibido(montoRecibido);
        venta.setCambioEntregado(cambio);
        venta.setReferenciaPago(blankToNull(request.referenciaPago()));
        venta.setEstado(EstadoVenta.CONFIRMADA);
        venta.setSincronizada(true);
        venta.setFechaVenta(LocalDateTime.now());
        Venta guardada = ventaRepository.save(venta);

        // US-15: factura con consecutivo correlativo (POS-000001) derivado de su id, unico e inmutable.
        CrearVentaRequest.Cliente cliente = request.cliente();
        Factura factura = Factura.builder()
                .venta(guardada)
                .numeroTicket("TMP" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
                .clienteIdentificacion(cliente != null && notBlank(cliente.identificacion())
                        ? cliente.identificacion().trim() : CLIENTE_ID_DEFECTO)
                .clienteNombre(cliente != null && notBlank(cliente.nombre())
                        ? cliente.nombre().trim() : CLIENTE_NOMBRE_DEFECTO)
                .clienteDireccion(cliente != null ? blankToNull(cliente.direccion()) : null)
                .clienteTelefono(cliente != null ? blankToNull(cliente.telefono()) : null)
                .montoTotal(total)
                .totalImpuestos(impuestoTotal)
                .estado(EstadoFactura.EMITIDA)
                .fechaEmision(guardada.getFechaVenta())
                .build();
        factura = facturaRepository.save(factura);
        factura.setNumeroTicket(String.format("POS-%06d", factura.getIdFactura()));
        factura = facturaRepository.save(factura);
        guardada.setFactura(factura);

        // US-14: Kardex append-only, una fila VENTA por linea.
        for (Object[] p : pendientesKardex) {
            Producto producto = (Producto) p[0];
            int cantidad = (Integer) p[1];
            int saldo = (Integer) p[2];
            BigDecimal costo = producto.getCostoPromedio().setScale(2, RoundingMode.HALF_UP);
            movimientos.add(MovimientoInventario.builder()
                    .producto(producto)
                    .tipoMovimiento(TipoMovimientoInventario.VENTA)
                    .cantidad(cantidad)
                    .costoUnitario(costo)
                    .costoTotal(costo.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP))
                    .saldoResultante(saldo)
                    .idReferencia(factura.getNumeroTicket())
                    .operario(operario)
                    .fechaMovimiento(LocalDateTime.now())
                    .build());
        }
        movimientoInventarioRepository.saveAll(movimientos);

        // Acumulado del turno segun medio de pago (base del arqueo, US-05).
        if (request.medioPago() == MedioPago.EFECTIVO) {
            turno.setTotalEfectivoVentas(turno.getTotalEfectivoVentas().add(total));
        } else {
            turno.setTotalDigitalVentas(turno.getTotalDigitalVentas().add(total));
        }
        turnoCajaRepository.save(turno);

        return VentaResponse.fromEntity(guardada);
    }

    @Transactional(readOnly = true)
    public List<VentaResumenResponse> listar(Long idOperario, Long idTurno, LocalDateTime desde, LocalDateTime hasta,
                                             EstadoVenta estado, MedioPago medioPago) {
        Operario operario = buscarOperario(idOperario);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new AppException(
                    "El rango de fechas es invalido: 'desde' es posterior a 'hasta'",
                    HttpStatus.BAD_REQUEST,
                    "VTA-RANGO-INVALIDO");
        }

        Specification<Venta> spec = (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (operario.getRol() != RolOperario.ADMIN) {
                predicados.add(cb.equal(root.get("operario"), operario));
            }
            if (idTurno != null) {
                predicados.add(cb.equal(root.get("turno").get("idTurno"), idTurno));
            }
            if (desde != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("fechaVenta"), desde));
            }
            if (hasta != null) {
                predicados.add(cb.lessThanOrEqualTo(root.get("fechaVenta"), hasta));
            }
            if (estado != null) {
                predicados.add(cb.equal(root.get("estado"), estado));
            }
            if (medioPago != null) {
                predicados.add(cb.equal(root.get("medioPago"), medioPago));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };

        return ventaRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "fechaVenta", "idVenta")).stream()
                .map(VentaResumenResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public VentaResponse obtenerPorId(Long idVenta, Long idOperario) {
        Operario operario = buscarOperario(idOperario);
        Venta venta = ventaRepository.findById(idVenta)
                .orElseThrow(() -> new EntityNotFoundException("Venta no encontrada", "VTA-NO-ENCONTRADA"));
        if (operario.getRol() != RolOperario.ADMIN
                && !venta.getOperario().getIdOperario().equals(operario.getIdOperario())) {
            throw new AppException(
                    "Acceso denegado",
                    HttpStatus.FORBIDDEN,
                    "AUTH-NO-AUTORIZADO",
                    "No tiene permisos suficientes para realizar esta accion");
        }
        return VentaResponse.fromEntity(venta);
    }

    /**
     * US-16 / RN-VTA-05: anulacion supervisada (solo ADMIN). Transaccion compensatoria: reingresa el stock
     * con AJUSTE_ENTRADA en el Kardex (sin borrar nada) y marca venta y factura como ANULADA.
     */
    @Transactional
    public AnulacionVentaResponse anularVenta(Long idVenta, AnularVentaRequest request, Long idOperarioAdmin) {
        Operario admin = buscarOperario(idOperarioAdmin);
        if (admin.getRol() != RolOperario.ADMIN) {
            throw new AppException(
                    "Acceso denegado",
                    HttpStatus.FORBIDDEN,
                    "AUTH-NO-AUTORIZADO",
                    "Solo un administrador puede anular ventas");
        }

        if (request.pin() != null && !request.pin().isBlank()) {
            if (!passwordEncoder.matches(request.pin(), admin.getPinHash())) {
                throw new BadCredentialsException("PIN de administrador incorrecto", "OPE-PIN-INVALIDO", null);
            }
        }

        Venta venta = ventaRepository.findById(idVenta)
                .orElseThrow(() -> new EntityNotFoundException("Venta no encontrada", "VTA-NO-ENCONTRADA"));
        if (venta.getEstado() == EstadoVenta.ANULADA) {
            throw new ConflictException("La venta ya se encuentra anulada", "VTA-YA-ANULADA");
        }

        Factura factura = facturaRepository.findByVenta(venta)
                .orElseThrow(() -> new EntityNotFoundException(
                        "La venta no tiene factura asociada", "FAC-NO-ENCONTRADA"));
        String motivo = request.motivoAnulacion().trim();

        // Reingreso de stock con bloqueo por fila, en orden por producto.
        List<DetalleVenta> detalles = new ArrayList<>(venta.getDetalles());
        detalles.sort(Comparator.comparing(d -> d.getProducto().getIdProducto()));
        List<MovimientoInventario> movimientos = new ArrayList<>();
        for (DetalleVenta detalle : detalles) {
            Producto producto = productoRepository.findByIdForUpdate(detalle.getProducto().getIdProducto())
                    .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado", "PROD-NO-ENCONTRADO"));
            int nuevoStock = producto.getStockActual() + detalle.getCantidad();
            producto.setStockActual(nuevoStock);
            productoRepository.save(producto);

            BigDecimal costo = detalle.getCostoUnitarioHistorico().setScale(2, RoundingMode.HALF_UP);
            movimientos.add(MovimientoInventario.builder()
                    .producto(producto)
                    .tipoMovimiento(TipoMovimientoInventario.AJUSTE_ENTRADA)
                    .cantidad(detalle.getCantidad())
                    .costoUnitario(costo)
                    .costoTotal(costo.multiply(BigDecimal.valueOf(detalle.getCantidad()))
                            .setScale(2, RoundingMode.HALF_UP))
                    .saldoResultante(nuevoStock)
                    .idReferencia(factura.getNumeroTicket())
                    .motivoAjuste("Anulacion de venta " + factura.getNumeroTicket() + ": " + motivo)
                    .operario(admin)
                    .fechaMovimiento(LocalDateTime.now())
                    .build());
        }
        movimientoInventarioRepository.saveAll(movimientos);

        // Se descuenta del turno de la venta solo si sigue abierto; un turno cerrado conserva su arqueo.
        TurnoCaja turno = venta.getTurno();
        if (turno.getEstado() == EstadoTurno.ABIERTO) {
            if (venta.getMedioPago() == MedioPago.EFECTIVO) {
                turno.setTotalEfectivoVentas(
                        turno.getTotalEfectivoVentas().subtract(venta.getTotalAPagar()).max(BigDecimal.ZERO));
            } else {
                turno.setTotalDigitalVentas(
                        turno.getTotalDigitalVentas().subtract(venta.getTotalAPagar()).max(BigDecimal.ZERO));
            }
            turnoCajaRepository.save(turno);
        }

        venta.setEstado(EstadoVenta.ANULADA);
        ventaRepository.save(venta);

        factura.setEstado(EstadoFactura.ANULADA);
        factura.setMotivoAnulacion(motivo);
        factura.setAnuladaPor(admin);
        facturaRepository.save(factura);

        return new AnulacionVentaResponse(
                venta.getIdVenta(),
                EstadoVenta.ANULADA.name(),
                new AnulacionVentaResponse.FacturaAnulada(
                        factura.getIdFactura(),
                        factura.getNumeroTicket(),
                        factura.getEstado(),
                        factura.getMotivoAnulacion()),
                "Venta anulada correctamente. Stock reintegrado.");
    }

    private void validarTopeDescuentoCajero(Operario operario, BigDecimal bruto, BigDecimal descuentoTotal) {
        if (operario.getRol() == RolOperario.ADMIN || bruto.signum() == 0 || descuentoTotal.signum() == 0) {
            return;
        }
        Optional<ConfiguracionNegocio> config = configuracionNegocioRepository.findAll().stream().findFirst();
        if (config.isEmpty()) {
            return;
        }
        BigDecimal maximo = config.get().getDescuentoMaximoCajero();
        BigDecimal porcentaje = descuentoTotal.multiply(CIEN).divide(bruto, 2, RoundingMode.HALF_UP);
        if (porcentaje.compareTo(maximo) > 0) {
            throw new AppException(
                    "El descuento excede el maximo permitido para el cajero",
                    HttpStatus.FORBIDDEN,
                    "VTA-DESCUENTO-EXCEDIDO",
                    "Maximo permitido: " + maximo + "% | Aplicado: " + porcentaje + "%");
        }
    }

    private Operario buscarOperario(Long idOperario) {
        return operarioRepository.findById(idOperario)
                .orElseThrow(() -> new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO"));
    }

    private static BigDecimal nvl(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return notBlank(s) ? s.trim() : null;
    }
}
