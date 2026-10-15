package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.Producto;

import java.math.BigDecimal;

public record ProductoBarcodeResponse(
        @JsonProperty("id_producto")
        Long idProducto,

        @JsonProperty("codigo_barras")
        String codigoBarras,

        @JsonProperty("nombre")
        String nombre,

        @JsonProperty("precio_venta")
        BigDecimal precioVenta,

        @JsonProperty("aplica_iva")
        Boolean aplicaIva,

        @JsonProperty("porcentaje_iva")
        BigDecimal porcentajeIva,

        @JsonProperty("stock_actual")
        Integer stockActual,

        @JsonProperty("unidad_medida")
        String unidadMedida
) {
    public static ProductoBarcodeResponse fromEntity(Producto p) {
        return new ProductoBarcodeResponse(
                p.getIdProducto(),
                p.getCodigoBarras(),
                p.getNombre(),
                p.getPrecioVenta(),
                p.getAplicaIva(),
                p.getPorcentajeIva(),
                p.getStockActual(),
                p.getUnidadMedida()
        );
    }
}
