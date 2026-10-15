package com.pva.app.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.MedioPago;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CrearVentaRequest(
        @Size(max = 36, message = "El uuid_offline no puede superar 36 caracteres")
        @JsonProperty("uuid_offline")
        String uuidOffline,

        @NotNull(message = "El id del turno es obligatorio")
        @JsonProperty("id_turno")
        Long idTurno,

        @NotEmpty(message = "La venta debe contener al menos un item")
        @Valid
        @JsonProperty("items")
        List<Item> items,

        @DecimalMin(value = "0.00", message = "El descuento global no puede ser negativo")
        @JsonProperty("descuento_global")
        BigDecimal descuentoGlobal,

        @NotNull(message = "El medio de pago es obligatorio")
        @JsonProperty("medio_pago")
        MedioPago medioPago,

        @NotNull(message = "El monto recibido es obligatorio")
        @DecimalMin(value = "0.00", message = "El monto recibido no puede ser negativo")
        @JsonProperty("monto_recibido")
        BigDecimal montoRecibido,

        @Size(max = 100, message = "La referencia de pago no puede superar 100 caracteres")
        @JsonProperty("referencia_pago")
        String referenciaPago,

        @Valid
        @JsonProperty("cliente")
        Cliente cliente
) {
    public record Item(
            @NotNull(message = "El id del producto es obligatorio")
            @JsonProperty("id_producto")
            Long idProducto,

            @NotNull(message = "La cantidad es obligatoria")
            @Min(value = 1, message = "La cantidad debe ser mayor a 0")
            @JsonProperty("cantidad")
            Integer cantidad,

            @DecimalMin(value = "0.00", message = "El descuento de linea no puede ser negativo")
            @JsonProperty("descuento_linea")
            BigDecimal descuentoLinea
    ) {}

    public record Cliente(
            @Size(max = 20, message = "La identificacion no puede superar 20 caracteres")
            @JsonProperty("identificacion")
            String identificacion,

            @Size(max = 255, message = "El nombre no puede superar 255 caracteres")
            @JsonProperty("nombre")
            String nombre,

            @Size(max = 255, message = "La direccion no puede superar 255 caracteres")
            @JsonProperty("direccion")
            String direccion,

            @Size(max = 20, message = "El telefono no puede superar 20 caracteres")
            @JsonProperty("telefono")
            String telefono
    ) {}
}
