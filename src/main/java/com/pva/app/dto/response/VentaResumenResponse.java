package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.EstadoVenta;
import com.pva.app.domain.MedioPago;
import com.pva.app.domain.Venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VentaResumenResponse(
        @JsonProperty("id_venta") Long idVenta,
        @JsonProperty("numero_ticket") String numeroTicket,
        @JsonProperty("nombre_operario") String nombreOperario,
        @JsonProperty("total_a_pagar") BigDecimal totalAPagar,
        @JsonProperty("medio_pago") MedioPago medioPago,
        @JsonProperty("estado") EstadoVenta estado,
        @JsonProperty("fecha_venta") LocalDateTime fechaVenta
) {
    public static VentaResumenResponse fromEntity(Venta v) {
        return new VentaResumenResponse(
                v.getIdVenta(),
                v.getFactura() != null ? v.getFactura().getNumeroTicket() : null,
                v.getOperario().getNombreCompleto(),
                v.getTotalAPagar(),
                v.getMedioPago(),
                v.getEstado(),
                v.getFechaVenta()
        );
    }
}
