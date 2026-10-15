package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CerrarTurnoRequest(
        @NotNull(message = "El monto físico de arqueo es requerido")
        @DecimalMin(value = "0.0", message = "El monto físico de arqueo debe ser mayor o igual a cero")
        @JsonProperty("monto_fisico_arqueo")
        BigDecimal montoFisicoArqueo,

        @JsonProperty("observaciones_cierre")
        String observacionesCierre
) {}
