package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AbrirTurnoRequest(
        @NotNull(message = "La base inicial de efectivo es requerida")
        @DecimalMin(value = "0.0", message = "La base inicial en efectivo debe ser mayor o igual a cero")
        @JsonProperty("base_efectivo_inicial")
        BigDecimal baseEfectivoInicial
) {}
