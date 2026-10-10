package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InactivarProductoResponse(
        @JsonProperty("mensaje")
        String mensaje,

        @JsonProperty("id_producto")
        Long idProducto,

        @JsonProperty("activo")
        Boolean activo
) {}
