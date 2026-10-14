package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.EstadoFactura;
import com.pva.app.domain.Factura;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FacturaResumenResponse(
        @JsonProperty("id_factura") Long idFactura,
        @JsonProperty("numero_ticket") String numeroTicket,
        @JsonProperty("cliente_nombre") String clienteNombre,
        @JsonProperty("cliente_identificacion") String clienteIdentificacion,
        @JsonProperty("monto_total") BigDecimal montoTotal,
        @JsonProperty("estado") EstadoFactura estado,
        @JsonProperty("fecha_emision") LocalDateTime fechaEmision
) {
    public static FacturaResumenResponse fromEntity(Factura f) {
        return new FacturaResumenResponse(
                f.getIdFactura(),
                f.getNumeroTicket(),
                f.getClienteNombre(),
                f.getClienteIdentificacion(),
                f.getMontoTotal(),
                f.getEstado(),
                f.getFechaEmision()
        );
    }
}
