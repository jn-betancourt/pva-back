package com.pva.app.service;

import com.pva.app.exception.AppException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.dto.response.MovimientoInventarioResponse;
import com.pva.app.dto.request.RegistrarCompraRequest;
import com.pva.app.repository.MovimientoInventarioRepository;
import com.pva.app.domain.Operario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.Producto;
import com.pva.app.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InventarioService {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final OperarioRepository operarioRepository;

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
}
