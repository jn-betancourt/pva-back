package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OperarioInactivadoResponse(
        @JsonProperty("mensaje")
        String mensaje,

        @JsonProperty("id_operario")
        Long idOperario,

        @JsonProperty("activo")
        Boolean activo
) {}
