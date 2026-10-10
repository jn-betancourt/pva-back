package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.Producto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductoResponse(
        @JsonProperty("id_producto")
        Long idProducto,

        @JsonProperty("codigo_barras")
        String codigoBarras,

        @JsonProperty("nombre")
        String nombre,

        @JsonProperty("descripcion")
        String descripcion,

        @JsonProperty("id_categoria")
        Long idCategoria,

        @JsonProperty("nombre_categoria")
        String nombreCategoria,

        @JsonProperty("precio_venta")
        BigDecimal precioVenta,

        @JsonProperty("costo_promedio")
        BigDecimal costoPromedio,

        @JsonProperty("aplica_iva")
        Boolean aplicaIva,

        @JsonProperty("porcentaje_iva")
        BigDecimal porcentajeIva,

        @JsonProperty("stock_actual")
        Integer stockActual,

        @JsonProperty("stock_minimo")
        Integer stockMinimo,

        @JsonProperty("unidad_medida")
        String unidadMedida,

        @JsonProperty("activo")
        Boolean activo,

        @JsonProperty("creado_en")
        LocalDateTime creadoEn,

        @JsonProperty("actualizado_en")
        LocalDateTime actualizadoEn
) {
    public static ProductoResponse fromEntity(Producto p) {
        Long idCat = p.getCategoria() != null ? p.getCategoria().getIdCategoria() : null;
        String nomCat = p.getCategoria() != null ? p.getCategoria().getNombre() : null;
        return new ProductoResponse(
                p.getIdProducto(),
                p.getCodigoBarras(),
                p.getNombre(),
                p.getDescripcion(),
                idCat,
                nomCat,
                p.getPrecioVenta(),
                p.getCostoPromedio(),
                p.getAplicaIva(),
                p.getPorcentajeIva(),
                p.getStockActual(),
                p.getStockMinimo(),
                p.getUnidadMedida(),
                p.getActivo(),
                p.getCreadoEn(),
                p.getActualizadoEn()
        );
    }
}
