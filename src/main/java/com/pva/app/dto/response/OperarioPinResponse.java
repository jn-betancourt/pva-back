package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OperarioPinResponse(
        @JsonProperty("mensaje")
        String mensaje,

        @JsonProperty("id_operario")
        Long idOperario
) {}
