package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record LoginTurnoActivoResponse(
        @JsonProperty("id_turno")
        Long idTurno,

        @JsonProperty("estado")
        String estado,

        @JsonProperty("fecha_apertura")
        LocalDateTime fechaApertura
) {}
