package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearCategoriaRequest(
        @NotBlank(message = "El nombre de la categoría es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
        @JsonProperty("nombre")
        String nombre,

        @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
        @JsonProperty("descripcion")
        String descripcion
) {}
