package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.RolOperario;

public record LoginResponse(
        @JsonProperty("token")
        String token,

        @JsonProperty("id_operario")
        Long idOperario,

        @JsonProperty("nombre_completo")
        String nombreCompleto,

        @JsonProperty("nombre_usuario")
        String nombreUsuario,

        @JsonProperty("rol")
        RolOperario rol,

        @JsonProperty("turno_activo")
        LoginTurnoActivoResponse turnoActivo
) {}
