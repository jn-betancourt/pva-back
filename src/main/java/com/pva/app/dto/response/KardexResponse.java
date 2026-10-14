package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.TipoMovimientoInventario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record KardexResponse(
        @JsonProperty("id_producto") Long idProducto,
        @JsonProperty("nombre_producto") String nombreProducto,
        @JsonProperty("stock_actual") Integer stockActual,
        @JsonProperty("costo_promedio") BigDecimal costoPromedio,
        @JsonProperty("movimientos") List<Movimiento> movimientos
) {
    public record Movimiento(
            @JsonProperty("id_movimiento") Long idMovimiento,
            @JsonProperty("tipo_movimiento") TipoMovimientoInventario tipoMovimiento,
            @JsonProperty("cantidad") Integer cantidad,
            @JsonProperty("costo_unitario") BigDecimal costoUnitario,
            @JsonProperty("costo_total") BigDecimal costoTotal,
            @JsonProperty("saldo_resultante") Integer saldoResultante,
            @JsonProperty("id_referencia") String idReferencia,
            @JsonProperty("motivo_ajuste") String motivoAjuste,
            @JsonProperty("nombre_operario") String nombreOperario,
            @JsonProperty("fecha_movimiento") LocalDateTime fechaMovimiento
    ) {
        public static Movimiento fromEntity(MovimientoInventario m) {
            return new Movimiento(
                    m.getIdMovimiento(),
                    m.getTipoMovimiento(),
                    m.getCantidad(),
                    m.getCostoUnitario(),
                    m.getCostoTotal(),
                    m.getSaldoResultante(),
                    m.getIdReferencia(),
                    m.getMotivoAjuste(),
                    m.getOperario().getNombreCompleto(),
                    m.getFechaMovimiento()
            );
        }
    }
}
