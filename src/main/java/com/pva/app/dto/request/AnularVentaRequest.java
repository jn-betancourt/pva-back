package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnularVentaRequest(
        @NotBlank(message = "El motivo de anulacion es obligatorio")
        @Size(max = 255, message = "El motivo de anulacion no puede superar 255 caracteres")
        @JsonProperty("motivo_anulacion")
        String motivoAnulacion,

        @JsonProperty("pin")
        String pin
) {
    public AnularVentaRequest(String motivoAnulacion) {
        this(motivoAnulacion, null);
    }
}
