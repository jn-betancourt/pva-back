package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.TipoMovimientoInventario;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RegistrarAjusteRequest(
        @NotNull(message = "El id del producto es obligatorio")
        @JsonProperty("id_producto")
        Long idProducto,

        @NotNull(message = "El tipo de movimiento es obligatorio")
        @JsonProperty("tipo_movimiento")
        TipoMovimientoInventario tipoMovimiento,

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad debe ser mayor a 0")
        @JsonProperty("cantidad")
        Integer cantidad,

        @DecimalMin(value = "0.00", message = "El costo unitario debe ser mayor o igual a 0")
        @JsonProperty("costo_unitario")
        BigDecimal costoUnitario,

        @NotBlank(message = "El motivo del ajuste es obligatorio")
        @Size(max = 255, message = "El motivo de ajuste no puede superar 255 caracteres")
        @JsonProperty("motivo_ajuste")
        String motivoAjuste,

        @Size(max = 50, message = "El id de referencia no puede superar 50 caracteres")
        @JsonProperty("id_referencia")
        String idReferencia
) {}
