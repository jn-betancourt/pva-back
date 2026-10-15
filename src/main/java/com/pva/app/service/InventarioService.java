package com.pva.app.service;

import com.pva.app.exception.AppException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.dto.response.MovimientoInventarioResponse;
import com.pva.app.dto.request.RegistrarAjusteRequest;
import com.pva.app.dto.request.RegistrarCompraRequest;
import com.pva.app.dto.response.AjusteInventarioResponse;
import com.pva.app.dto.response.AlertaStockResponse;
import com.pva.app.dto.response.KardexResponse;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.domain.Operario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.Producto;
import com.pva.app.repository.ProductoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class InventarioService {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final OperarioRepository operarioRepository;

    public InventarioService(ProductoRepository productoRepository,
                            MovimientoInventarioRepository movimientoInventarioRepository,
                            OperarioRepository operarioRepository) {
        this.productoRepository = productoRepository;
        this.movimientoInventarioRepository = movimientoInventarioRepository;
        this.operarioRepository = operarioRepository;
    }

    @Transactional
    public MovimientoInventarioResponse registrarCompra(RegistrarCompraRequest request, Long idOperarioAdmin) {
        // Bloqueo pesimista para evitar condiciones de carrera en el stock y recálculo de CPP
        Producto producto = productoRepository.findByIdForUpdate(request.idProducto())
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado", "PROD-NO-ENCONTRADO"));

        if (Boolean.FALSE.equals(producto.getActivo())) {
            throw new AppException(
                    "No se puede abastecer un producto inactivo",
                    HttpStatus.BAD_REQUEST,
                    "INV-PRODUCTO-INACTIVO"
            );
        }

        Operario operario = operarioRepository.findById(idOperarioAdmin)
                .orElseThrow(() -> new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO"));

        // RN-INV-02: Recálculo de Costo Promedio Ponderado (CPP)
        // nuevo_cpp = ((stock_actual * costo_promedio) + (cantidad * costo_unitario)) / (stock_actual + cantidad)
        BigDecimal stockActualBd = BigDecimal.valueOf(producto.getStockActual());
        BigDecimal cantidadBd = BigDecimal.valueOf(request.cantidad());
        BigDecimal nuevoStockBd = stockActualBd.add(cantidadBd);

        BigDecimal valorActual = stockActualBd.multiply(producto.getCostoPromedio());
        BigDecimal valorCompra = cantidadBd.multiply(request.costoUnitario());
        BigDecimal valorTotal = valorActual.add(valorCompra);

        BigDecimal nuevoCpp = valorTotal.divide(nuevoStockBd, 2, RoundingMode.HALF_UP);

        // Actualizar datos de inventario en Producto
        int nuevoStock = producto.getStockActual() + request.cantidad();
        producto.setStockActual(nuevoStock);
        producto.setCostoPromedio(nuevoCpp);
        productoRepository.save(producto);

        // Kardex Append-Only: Asentar movimiento inmutable
        BigDecimal costoUnitarioBd = request.costoUnitario().setScale(2, RoundingMode.HALF_UP);
        BigDecimal costoTotalBd = costoUnitarioBd.multiply(cantidadBd).setScale(2, RoundingMode.HALF_UP);

        MovimientoInventario movimiento = MovimientoInventario.builder()
                .producto(producto)
                .tipoMovimiento(TipoMovimientoInventario.COMPRA)
                .cantidad(request.cantidad())
                .costoUnitario(costoUnitarioBd)
                .costoTotal(costoTotalBd)
                .saldoResultante(nuevoStock)
                .idReferencia(request.idReferencia())
                .motivoAjuste(request.motivoAjuste())
                .operario(operario)
                .fechaMovimiento(LocalDateTime.now())
                .build();

        MovimientoInventario guardado = movimientoInventarioRepository.save(movimiento);

        return MovimientoInventarioResponse.fromEntity(guardado, nuevoCpp);
    }

    /**
     * US-10 / RN-INV-05: ajuste manual (merma, averia, vencimiento). Exige motivo y registra el Kardex.
     */
    @Transactional
    public AjusteInventarioResponse registrarAjuste(RegistrarAjusteRequest request, Long idOperarioAdmin) {
        if (request.tipoMovimiento() != TipoMovimientoInventario.AJUSTE_ENTRADA
                && request.tipoMovimiento() != TipoMovimientoInventario.AJUSTE_SALIDA) {
            throw new AppException(
                    "El tipo de movimiento debe ser AJUSTE_ENTRADA o AJUSTE_SALIDA",
                    HttpStatus.BAD_REQUEST,
                    "INV-TIPO-AJUSTE-INVALIDO"
            );
        }
        if (request.motivoAjuste() == null || request.motivoAjuste().isBlank()) {
            throw new AppException(
                    "El motivo del ajuste es obligatorio",
                    HttpStatus.BAD_REQUEST,
                    "INV-MOTIVO-REQUERIDO"
            );
        }

        Producto producto = productoRepository.findByIdForUpdate(request.idProducto())
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado", "PROD-NO-ENCONTRADO"));

        if (Boolean.FALSE.equals(producto.getActivo())) {
            throw new AppException(
                    "No se puede ajustar un producto inactivo",
                    HttpStatus.BAD_REQUEST,
                    "INV-PRODUCTO-INACTIVO"
            );
        }

        Operario operario = operarioRepository.findById(idOperarioAdmin)
                .orElseThrow(() -> new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO"));

        int cantidad = request.cantidad();
        int nuevoStock;
        if (request.tipoMovimiento() == TipoMovimientoInventario.AJUSTE_SALIDA) {
            if (producto.getStockActual() < cantidad) {
                throw new AppException(
                        "Stock insuficiente para aplicar el ajuste de salida",
                        HttpStatus.BAD_REQUEST,
                        "INV-STOCK-INSUFICIENTE",
                        "Stock actual: " + producto.getStockActual() + " | Cantidad solicitada: " + cantidad
                );
            }
            nuevoStock = producto.getStockActual() - cantidad;
        } else {
            nuevoStock = producto.getStockActual() + cantidad;
        }

        producto.setStockActual(nuevoStock);
        productoRepository.save(producto);

        BigDecimal costoUnitario = (request.costoUnitario() != null ? request.costoUnitario() : producto.getCostoPromedio())
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal costoTotal = costoUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);

        MovimientoInventario movimiento = MovimientoInventario.builder()
                .producto(producto)
                .tipoMovimiento(request.tipoMovimiento())
                .cantidad(cantidad)
                .costoUnitario(costoUnitario)
                .costoTotal(costoTotal)
                .saldoResultante(nuevoStock)
                .idReferencia(request.idReferencia())
                .motivoAjuste(request.motivoAjuste().trim())
                .operario(operario)
                .fechaMovimiento(LocalDateTime.now())
                .build();

        return AjusteInventarioResponse.fromEntity(movimientoInventarioRepository.save(movimiento));
    }

    /**
     * US-11 / RN-INV-06: consulta de solo lectura del Kardex (append-only) con filtros opcionales.
     */
    @Transactional(readOnly = true)
    public KardexResponse obtenerKardex(Long idProducto, LocalDateTime desde, LocalDateTime hasta,
                                        TipoMovimientoInventario tipo) {
        Producto producto = productoRepository.findById(idProducto)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado", "PROD-NO-ENCONTRADO"));

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new AppException(
                    "El rango de fechas es invalido: 'desde' es posterior a 'hasta'",
                    HttpStatus.BAD_REQUEST,
                    "INV-RANGO-INVALIDO"
            );
        }

        org.springframework.data.jpa.domain.Specification<MovimientoInventario> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicados = new java.util.ArrayList<>();
            predicados.add(cb.equal(root.get("producto"), producto));
            if (desde != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("fechaMovimiento"), desde));
            }
            if (hasta != null) {
                predicados.add(cb.lessThanOrEqualTo(root.get("fechaMovimiento"), hasta));
            }
            if (tipo != null) {
                predicados.add(cb.equal(root.get("tipoMovimiento"), tipo));
            }
            return cb.and(predicados.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        java.util.List<KardexResponse.Movimiento> movimientos = movimientoInventarioRepository
                .findAll(spec, org.springframework.data.domain.Sort.by("fechaMovimiento", "idMovimiento").ascending())
                .stream()
                .map(KardexResponse.Movimiento::fromEntity)
                .toList();

        return new KardexResponse(
                producto.getIdProducto(),
                producto.getNombre(),
                producto.getStockActual(),
                producto.getCostoPromedio(),
                movimientos
        );
    }

    /**
     * US-09 / RN-INV-07: productos activos con stock_actual <= stock_minimo.
     */
    @Transactional(readOnly = true)
    public java.util.List<AlertaStockResponse> listarAlertas() {
        return productoRepository.findAlertasStock().stream()
                .map(AlertaStockResponse::fromEntity)
                .toList();
    }
}

