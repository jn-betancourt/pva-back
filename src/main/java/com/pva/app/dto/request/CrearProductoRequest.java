package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CrearProductoRequest(
        @JsonProperty("codigo_barras")
        @Size(max = 50, message = "El código de barras no puede superar 50 caracteres")
        String codigoBarras,

        @NotBlank(message = "El nombre del producto es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
        @JsonProperty("nombre")
        String nombre,

        @JsonProperty("descripcion")
        String descripcion,

        @NotNull(message = "El id de categoría es obligatorio")
        @JsonProperty("id_categoria")
        Long idCategoria,

        @NotNull(message = "El precio de venta es obligatorio")
        @DecimalMin(value = "0.00", message = "El precio de venta debe ser mayor o igual a 0")
        @JsonProperty("precio_venta")
        BigDecimal precioVenta,

        @NotNull(message = "El costo inicial es obligatorio")
        @DecimalMin(value = "0.00", message = "El costo inicial debe ser mayor o igual a 0")
        @JsonProperty("costo_inicial")
        BigDecimal costoInicial,

        @NotNull(message = "El stock inicial es obligatorio")
        @Min(value = 0, message = "El stock inicial debe ser mayor o igual a 0")
        @JsonProperty("stock_inicial")
        Integer stockInicial,

        @NotNull(message = "El campo aplica_iva es obligatorio")
        @JsonProperty("aplica_iva")
        Boolean aplicaIva,

        @DecimalMin(value = "0.00", message = "El porcentaje de IVA debe ser mayor o igual a 0")
        @JsonProperty("porcentaje_iva")
        BigDecimal porcentajeIva,

        @NotNull(message = "El stock mínimo es obligatorio")
        @Min(value = 0, message = "El stock mínimo debe ser mayor o igual a 0")
        @JsonProperty("stock_minimo")
        Integer stockMinimo,

        @Size(max = 20, message = "La unidad de medida no puede superar 20 caracteres")
        @JsonProperty("unidad_medida")
        String unidadMedida
) {}
