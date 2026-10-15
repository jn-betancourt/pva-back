package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.RolOperario;

import java.time.LocalDateTime;

public record OperarioCreadoResponse(
        @JsonProperty("id_operario")
        Long idOperario,

        @JsonProperty("nombre_completo")
        String nombreCompleto,

        @JsonProperty("nombre_usuario")
        String nombreUsuario,

        @JsonProperty("rol")
        RolOperario rol,

        @JsonProperty("activo")
        Boolean activo,

        @JsonProperty("creado_en")
        LocalDateTime creadoEn
) {}
