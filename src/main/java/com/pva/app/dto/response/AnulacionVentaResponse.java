package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.EstadoFactura;

public record AnulacionVentaResponse(
        @JsonProperty("id_venta") Long idVenta,
        @JsonProperty("estado") String estado,
        @JsonProperty("factura") FacturaAnulada factura,
        @JsonProperty("mensaje") String mensaje
) {
    public record FacturaAnulada(
            @JsonProperty("id_factura") Long idFactura,
            @JsonProperty("numero_ticket") String numeroTicket,
            @JsonProperty("estado") EstadoFactura estado,
            @JsonProperty("motivo_anulacion") String motivoAnulacion
    ) {}
}
