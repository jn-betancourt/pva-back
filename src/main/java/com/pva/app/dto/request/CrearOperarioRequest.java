package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.RolOperario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CrearOperarioRequest(
        @NotBlank(message = "El nombre completo es requerido")
        @JsonProperty("nombre_completo")
        String nombreCompleto,

        @NotBlank(message = "El número de documento es requerido")
        @JsonProperty("numero_documento")
        String numeroDocumento,

        @JsonProperty("telefono")
        String telefono,

        @NotBlank(message = "El nombre de usuario es requerido")
        @JsonProperty("nombre_usuario")
        String nombreUsuario,

        @NotBlank(message = "El PIN es requerido")
        @Pattern(regexp = "^\\d{4,6}$", message = "El PIN debe contener entre 4 y 6 dígitos numéricos")
        @JsonProperty("pin")
        String pin,

        @NotNull(message = "El rol es requerido")
        @JsonProperty("rol")
        RolOperario rol
) {}
