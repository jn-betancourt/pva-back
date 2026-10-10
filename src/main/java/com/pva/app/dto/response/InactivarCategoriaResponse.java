package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InactivarCategoriaResponse(
        @JsonProperty("mensaje")
        String mensaje,

        @JsonProperty("id_categoria")
        Long idCategoria,

        @JsonProperty("activo")
        Boolean activo
) {}
