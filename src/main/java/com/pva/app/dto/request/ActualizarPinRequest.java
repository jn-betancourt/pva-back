package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ActualizarPinRequest(
        @NotBlank(message = "El PIN nuevo es requerido")
        @Pattern(regexp = "^\\d{4,6}$", message = "El PIN debe contener entre 4 y 6 dígitos numéricos")
        @JsonProperty("pin_nuevo")
        String pinNuevo
) {}
