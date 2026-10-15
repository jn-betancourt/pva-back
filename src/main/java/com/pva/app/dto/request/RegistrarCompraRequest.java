package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegistrarCompraRequest(
        @NotNull(message = "El id del producto es obligatorio")
        @JsonProperty("id_producto")
        Long idProducto,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor a 0")
        @JsonProperty("cantidad")
        Integer cantidad,

        @NotNull(message = "El costo unitario es obligatorio")
        @DecimalMin(value = "0.00", message = "El costo unitario debe ser mayor o igual a 0")
        @JsonProperty("costo_unitario")
        BigDecimal costoUnitario,

        @Size(max = 50, message = "El id de referencia no puede superar 50 caracteres")
        @JsonProperty("id_referencia")
        String idReferencia,

        @Size(max = 255, message = "El motivo de ajuste no puede superar 255 caracteres")
        @JsonProperty("motivo_ajuste")
        String motivoAjuste
) {}
