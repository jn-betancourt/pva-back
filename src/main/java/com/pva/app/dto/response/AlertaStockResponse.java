package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.Producto;

public record AlertaStockResponse(
        @JsonProperty("id_producto") Long idProducto,
        @JsonProperty("nombre") String nombre,
        @JsonProperty("codigo_barras") String codigoBarras,
        @JsonProperty("stock_actual") Integer stockActual,
        @JsonProperty("stock_minimo") Integer stockMinimo,
        @JsonProperty("deficit") Integer deficit
) {
    public static AlertaStockResponse fromEntity(Producto p) {
        return new AlertaStockResponse(
                p.getIdProducto(),
                p.getNombre(),
                p.getCodigoBarras(),
                p.getStockActual(),
                p.getStockMinimo(),
                p.getStockMinimo() - p.getStockActual()
        );
    }
}
