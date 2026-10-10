package com.pva.app.service;

import com.pva.app.domain.Categoria;
import com.pva.app.repository.CategoriaRepository;
import com.pva.app.exception.ConflictException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.domain.Operario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.Producto;
import com.pva.app.dto.request.*;
import com.pva.app.dto.response.*;
import com.pva.app.repository.ProductoRepository;
import com.pva.app.repository.ProductoSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final OperarioRepository operarioRepository;

    @Transactional
    public CrearProductoResponse crear(CrearProductoRequest request, Long idOperarioAdmin) {
        String nombreNormalizado = request.nombre().trim();
        if (productoRepository.findByNombre(nombreNormalizado).isPresent()) {
            throw new ConflictException(
                    "Ya existe un producto con ese nombre o código de barras",
                    "PROD-DUPLICADO",
                    "Campo: nombre"
            );
        }

        String codigoBarrasNormalizado = normalizarCodigoBarras(request.codigoBarras());
        if (codigoBarrasNormalizado != null && productoRepository.findByCodigoBarras(codigoBarrasNormalizado).isPresent()) {
            throw new ConflictException(
                    "Ya existe un producto con ese nombre o código de barras",
                    "PROD-DUPLICADO",
                    "Campo: codigo_barras"
            );
        }

        Categoria categoria = categoriaRepository.findById(request.idCategoria())
                .orElseThrow(() -> new EntityNotFoundException("Categoría no encontrada", "CAT-NO-ENCONTRADA"));

        BigDecimal porcentajeIva = Boolean.TRUE.equals(request.aplicaIva())
                ? (request.porcentajeIva() != null ? request.porcentajeIva() : BigDecimal.ZERO)
                : BigDecimal.ZERO;

        String unidadMedida = (request.unidadMedida() != null && !request.unidadMedida().trim().isEmpty())
                ? request.unidadMedida().trim()
                : "UNIDAD";

        Producto producto = Producto.builder()
                .nombre(nombreNormalizado)
                .codigoBarras(codigoBarrasNormalizado)
                .descripcion(request.descripcion())
                .categoria(categoria)
                .precioVenta(request.precioVenta().setScale(2, RoundingMode.HALF_UP))
                .costoPromedio(request.costoInicial().setScale(2, RoundingMode.HALF_UP))
                .stockActual(request.stockInicial())
                .stockMinimo(request.stockMinimo())
                .unidadMedida(unidadMedida)
                .aplicaIva(Boolean.TRUE.equals(request.aplicaIva()))
                .porcentajeIva(porcentajeIva.setScale(2, RoundingMode.HALF_UP))
                .activo(true)
                .build();

        Producto guardado = productoRepository.save(producto);

        if (request.stockInicial() > 0) {
            Operario operario = operarioRepository.findById(idOperarioAdmin)
                    .orElseThrow(() -> new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO"));

            BigDecimal costoUnitario = request.costoInicial().setScale(2, RoundingMode.HALF_UP);
            BigDecimal cantidadBd = BigDecimal.valueOf(request.stockInicial());
            BigDecimal costoTotal = costoUnitario.multiply(cantidadBd).setScale(2, RoundingMode.HALF_UP);

            MovimientoInventario movimientoInicial = MovimientoInventario.builder()
                    .producto(guardado)
                    .tipoMovimiento(TipoMovimientoInventario.COMPRA)
                    .cantidad(request.stockInicial())
                    .costoUnitario(costoUnitario)
                    .costoTotal(costoTotal)
                    .saldoResultante(guardado.getStockActual())
                    .idReferencia("INVENTARIO_INICIAL")
                    .motivoAjuste("Inventario inicial al crear producto")
                    .operario(operario)
                    .fechaMovimiento(LocalDateTime.now())
                    .build();

            movimientoInventarioRepository.save(movimientoInicial);
        }

        return CrearProductoResponse.fromEntity(guardado);
    }

    @Transactional
    public ProductoResponse actualizar(Long idProducto, ActualizarProductoRequest request) {
        Producto producto = buscarPorIdOError(idProducto);

        String nuevoNombre = request.nombre().trim();
        Optional<Producto> prodConNombre = productoRepository.findByNombre(nuevoNombre);
        if (prodConNombre.isPresent() && !prodConNombre.get().getIdProducto().equals(idProducto)) {
            throw new ConflictException(
                    "Ya existe un producto con ese nombre o código de barras",
                    "PROD-DUPLICADO",
                    "Campo: nombre"
            );
        }

        String nuevoBarcode = normalizarCodigoBarras(request.codigoBarras());
        if (nuevoBarcode != null) {
            Optional<Producto> prodConBarcode = productoRepository.findByCodigoBarras(nuevoBarcode);
            if (prodConBarcode.isPresent() && !prodConBarcode.get().getIdProducto().equals(idProducto)) {
                throw new ConflictException(
                        "Ya existe un producto con ese nombre o código de barras",
                        "PROD-DUPLICADO",
                        "Campo: codigo_barras"
                );
            }
        }

        Categoria categoria = categoriaRepository.findById(request.idCategoria())
                .orElseThrow(() -> new EntityNotFoundException("Categoría no encontrada", "CAT-NO-ENCONTRADA"));

        BigDecimal porcentajeIva = Boolean.TRUE.equals(request.aplicaIva())
                ? (request.porcentajeIva() != null ? request.porcentajeIva() : BigDecimal.ZERO)
                : BigDecimal.ZERO;

        producto.setNombre(nuevoNombre);
        producto.setCodigoBarras(nuevoBarcode);
        producto.setDescripcion(request.descripcion());
        producto.setCategoria(categoria);
        producto.setPrecioVenta(request.precioVenta().setScale(2, RoundingMode.HALF_UP));
        producto.setAplicaIva(Boolean.TRUE.equals(request.aplicaIva()));
        producto.setPorcentajeIva(porcentajeIva.setScale(2, RoundingMode.HALF_UP));
        producto.setStockMinimo(request.stockMinimo());

        if (request.unidadMedida() != null && !request.unidadMedida().trim().isEmpty()) {
            producto.setUnidadMedida(request.unidadMedida().trim());
        }

        // Regla: No modifica directamente stock_actual ni costo_promedio
        Producto guardado = productoRepository.save(producto);
        return ProductoResponse.fromEntity(guardado);
    }

    @Transactional
    public InactivarProductoResponse inactivar(Long idProducto) {
        Producto producto = buscarPorIdOError(idProducto);
        producto.setActivo(false);
        productoRepository.save(producto);

        return new InactivarProductoResponse(
                "Producto inactivado correctamente",
                idProducto,
                false
        );
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtenerPorId(Long idProducto) {
        Producto producto = buscarPorIdOError(idProducto);
        return ProductoResponse.fromEntity(producto);
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(Long idCategoria, Boolean activo, Boolean stockBajo, String q) {
        Specification<Producto> spec = ProductoSpecifications.conFiltros(idCategoria, activo, stockBajo, q);
        List<Producto> productos = productoRepository.findAll(spec);
        return productos.stream()
                .map(ProductoResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductoBarcodeResponse buscarPorBarcode(String codigoBarras) {
        if (codigoBarras == null || codigoBarras.trim().isEmpty()) {
            throw new EntityNotFoundException("Producto no encontrado con ese código de barras", "PROD-BARCODE-NO-ENCONTRADO");
        }

        Producto producto = productoRepository.findByCodigoBarras(codigoBarras.trim())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Producto no encontrado con ese código de barras",
                        "PROD-BARCODE-NO-ENCONTRADO"
                ));

        return ProductoBarcodeResponse.fromEntity(producto);
    }

    private Producto buscarPorIdOError(Long idProducto) {
        return productoRepository.findById(idProducto)
                .orElseThrow(() -> new EntityNotFoundException("Producto no encontrado", "PROD-NO-ENCONTRADO"));
    }

    private String normalizarCodigoBarras(String codigoBarras) {
        if (codigoBarras == null) {
            return null;
        }
        String recortado = codigoBarras.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}
