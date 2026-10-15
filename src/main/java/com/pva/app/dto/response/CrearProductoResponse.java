package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.Producto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CrearProductoResponse(
        @JsonProperty("id_producto")
        Long idProducto,

        @JsonProperty("nombre")
        String nombre,

        @JsonProperty("codigo_barras")
        String codigoBarras,

        @JsonProperty("stock_actual")
        Integer stockActual,

        @JsonProperty("costo_promedio")
        BigDecimal costoPromedio,

        @JsonProperty("activo")
        Boolean activo,

        @JsonProperty("creado_en")
        LocalDateTime creadoEn
) {
    public static CrearProductoResponse fromEntity(Producto p) {
        return new CrearProductoResponse(
                p.getIdProducto(),
                p.getNombre(),
                p.getCodigoBarras(),
                p.getStockActual(),
                p.getCostoPromedio(),
                p.getActivo(),
                p.getCreadoEn() != null ? p.getCreadoEn() : LocalDateTime.now()
        );
    }
}
