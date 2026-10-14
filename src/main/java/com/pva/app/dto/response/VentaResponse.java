package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.DetalleVenta;
import com.pva.app.domain.EstadoFactura;
import com.pva.app.domain.EstadoVenta;
import com.pva.app.domain.Factura;
import com.pva.app.domain.MedioPago;
import com.pva.app.domain.Venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VentaResponse(
        @JsonProperty("id_venta") Long idVenta,
        @JsonProperty("uuid_offline") String uuidOffline,
        @JsonProperty("subtotal") BigDecimal subtotal,
        @JsonProperty("descuento_global") BigDecimal descuentoGlobal,
        @JsonProperty("impuesto_total") BigDecimal impuestoTotal,
        @JsonProperty("total_a_pagar") BigDecimal totalAPagar,
        @JsonProperty("monto_recibido") BigDecimal montoRecibido,
        @JsonProperty("cambio_entregado") BigDecimal cambioEntregado,
        @JsonProperty("medio_pago") MedioPago medioPago,
        @JsonProperty("estado") EstadoVenta estado,
        @JsonProperty("fecha_venta") LocalDateTime fechaVenta,
        @JsonProperty("factura") FacturaResumen factura,
        @JsonProperty("items") List<Item> items
) {
    public record FacturaResumen(
            @JsonProperty("id_factura") Long idFactura,
            @JsonProperty("numero_ticket") String numeroTicket,
            @JsonProperty("cliente_nombre") String clienteNombre,
            @JsonProperty("cliente_identificacion") String clienteIdentificacion,
            @JsonProperty("monto_total") BigDecimal montoTotal,
            @JsonProperty("total_impuestos") BigDecimal totalImpuestos,
            @JsonProperty("estado") EstadoFactura estado,
            @JsonProperty("fecha_emision") LocalDateTime fechaEmision
    ) {
        public static FacturaResumen fromEntity(Factura f) {
            if (f == null) {
                return null;
            }
            return new FacturaResumen(
                    f.getIdFactura(),
                    f.getNumeroTicket(),
                    f.getClienteNombre(),
                    f.getClienteIdentificacion(),
                    f.getMontoTotal(),
                    f.getTotalImpuestos(),
                    f.getEstado(),
                    f.getFechaEmision()
            );
        }
    }

    public record Item(
            @JsonProperty("id_detalle") Long idDetalle,
            @JsonProperty("id_producto") Long idProducto,
            @JsonProperty("nombre_producto") String nombreProducto,
            @JsonProperty("cantidad") Integer cantidad,
            @JsonProperty("precio_unitario") BigDecimal precioUnitario,
            @JsonProperty("descuento_linea") BigDecimal descuentoLinea,
            @JsonProperty("porcentaje_iva") BigDecimal porcentajeIva,
            @JsonProperty("impuesto_linea") BigDecimal impuestoLinea,
            @JsonProperty("subtotal_linea") BigDecimal subtotalLinea
    ) {
        public static Item fromEntity(DetalleVenta d) {
            return new Item(
                    d.getIdDetalle(),
                    d.getProducto().getIdProducto(),
                    d.getProducto().getNombre(),
                    d.getCantidad(),
                    d.getPrecioUnitario(),
                    d.getDescuentoLinea(),
                    d.getPorcentajeIva(),
                    d.getImpuestoLinea(),
                    d.getSubtotalLinea()
            );
        }
    }

    public static VentaResponse fromEntity(Venta v) {
        return new VentaResponse(
                v.getIdVenta(),
                v.getUuidOffline(),
                v.getSubtotal(),
                v.getDescuentoGlobal(),
                v.getImpuestoTotal(),
                v.getTotalAPagar(),
                v.getMontoRecibido(),
                v.getCambioEntregado(),
                v.getMedioPago(),
                v.getEstado(),
                v.getFechaVenta(),
                FacturaResumen.fromEntity(v.getFactura()),
                v.getDetalles().stream().map(Item::fromEntity).toList()
        );
    }
}
