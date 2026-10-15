package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.TipoMovimientoInventario;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoInventarioResponse(
        @JsonProperty("id_movimiento")
        Long idMovimiento,

        @JsonProperty("id_producto")
        Long idProducto,

        @JsonProperty("nombre_producto")
        String nombreProducto,

        @JsonProperty("tipo_movimiento")
        TipoMovimientoInventario tipoMovimiento,

        @JsonProperty("cantidad")
        Integer cantidad,

        @JsonProperty("costo_unitario")
        BigDecimal costoUnitario,

        @JsonProperty("costo_total")
        BigDecimal costoTotal,

        @JsonProperty("saldo_resultante")
        Integer saldoResultante,

        @JsonProperty("nuevo_costo_promedio")
        BigDecimal nuevoCostoPromedio,

        @JsonProperty("fecha_movimiento")
        LocalDateTime fechaMovimiento
) {
    public static MovimientoInventarioResponse fromEntity(MovimientoInventario m, BigDecimal nuevoCostoPromedio) {
        return new MovimientoInventarioResponse(
                m.getIdMovimiento(),
                m.getProducto().getIdProducto(),
                m.getProducto().getNombre(),
                m.getTipoMovimiento(),
                m.getCantidad(),
                m.getCostoUnitario(),
                m.getCostoTotal(),
                m.getSaldoResultante(),
                nuevoCostoPromedio,
                m.getFechaMovimiento()
        );
    }
}
