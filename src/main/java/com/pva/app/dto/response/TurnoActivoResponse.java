package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.TurnoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TurnoActivoResponse(
        @JsonProperty("id_turno")
        Long idTurno,

        @JsonProperty("id_operario")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Long idOperario,

        @JsonProperty("nombre_operario")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String nombreOperario,

        @JsonProperty("base_efectivo_inicial")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal baseEfectivoInicial,

        @JsonProperty("total_efectivo_ventas")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal totalEfectivoVentas,

        @JsonProperty("total_digital_ventas")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        BigDecimal totalDigitalVentas,

        @JsonProperty("estado")
        EstadoTurno estado,

        @JsonProperty("fecha_apertura")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        LocalDateTime fechaApertura,

        @JsonProperty("mensaje")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String mensaje
) {
    public static TurnoActivoResponse activo(TurnoCaja turno) {
        return new TurnoActivoResponse(
                turno.getIdTurno(),
                turno.getOperario() != null ? turno.getOperario().getIdOperario() : null,
                turno.getOperario() != null ? turno.getOperario().getNombreCompleto() : null,
                turno.getBaseEfectivoInicial(),
                turno.getTotalEfectivoVentas(),
                turno.getTotalDigitalVentas(),
                turno.getEstado(),
                turno.getFechaApertura(),
                null
        );
    }

    public static TurnoActivoResponse sinTurno() {
        return new TurnoActivoResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "El operario no tiene un turno abierto"
        );
    }
}
