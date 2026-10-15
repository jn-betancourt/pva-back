package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.TurnoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TurnoResponse(
        @JsonProperty("id_turno")
        Long idTurno,

        @JsonProperty("id_operario")
        Long idOperario,

        @JsonProperty("nombre_operario")
        String nombreOperario,

        @JsonProperty("base_efectivo_inicial")
        BigDecimal baseEfectivoInicial,

        @JsonProperty("total_efectivo_ventas")
        BigDecimal totalEfectivoVentas,

        @JsonProperty("total_digital_ventas")
        BigDecimal totalDigitalVentas,

        @JsonProperty("monto_fisico_arqueo")
        BigDecimal montoFisicoArqueo,

        @JsonProperty("discrepancia")
        BigDecimal discrepancia,

        @JsonProperty("estado")
        EstadoTurno estado,

        @JsonProperty("observaciones_cierre")
        String observacionesCierre,

        @JsonProperty("fecha_apertura")
        LocalDateTime fechaApertura,

        @JsonProperty("fecha_cierre")
        LocalDateTime fechaCierre
) {
    public static TurnoResponse fromEntity(TurnoCaja turno) {
        return new TurnoResponse(
                turno.getIdTurno(),
                turno.getOperario() != null ? turno.getOperario().getIdOperario() : null,
                turno.getOperario() != null ? turno.getOperario().getNombreCompleto() : null,
                turno.getBaseEfectivoInicial(),
                turno.getTotalEfectivoVentas(),
                turno.getTotalDigitalVentas(),
                turno.getMontoFisicoArqueo(),
                turno.getDiscrepancia(),
                turno.getEstado(),
                turno.getObservacionesCierre(),
                turno.getFechaApertura(),
                turno.getFechaCierre()
        );
    }
}
