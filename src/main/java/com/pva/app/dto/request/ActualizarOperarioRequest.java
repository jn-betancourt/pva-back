package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.RolOperario;

public record ActualizarOperarioRequest(
        @JsonProperty("nombre_completo")
        String nombreCompleto,

        @JsonProperty("telefono")
        String telefono,

        @JsonProperty("nombre_usuario")
        String nombreUsuario,

        @JsonProperty("rol")
        RolOperario rol
) {}
