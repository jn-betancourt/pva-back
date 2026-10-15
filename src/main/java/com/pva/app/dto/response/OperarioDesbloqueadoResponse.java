package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OperarioDesbloqueadoResponse(
        @JsonProperty("mensaje")
        String mensaje,

        @JsonProperty("id_operario")
        Long idOperario,

        @JsonProperty("intentos_fallidos")
        Integer intentosFallidos
) {}
