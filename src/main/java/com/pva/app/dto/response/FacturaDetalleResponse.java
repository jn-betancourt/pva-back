package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.ConfiguracionNegocio;
import com.pva.app.domain.EstadoFactura;
import com.pva.app.domain.Factura;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record FacturaDetalleResponse(
        @JsonProperty("id_factura") Long idFactura,
        @JsonProperty("numero_ticket") String numeroTicket,
        @JsonProperty("id_venta") Long idVenta,
        @JsonProperty("cliente_identificacion") String clienteIdentificacion,
        @JsonProperty("cliente_nombre") String clienteNombre,
        @JsonProperty("cliente_direccion") String clienteDireccion,
        @JsonProperty("cliente_telefono") String clienteTelefono,
        @JsonProperty("monto_total") BigDecimal montoTotal,
        @JsonProperty("total_impuestos") BigDecimal totalImpuestos,
        @JsonProperty("estado") EstadoFactura estado,
        @JsonProperty("motivo_anulacion") String motivoAnulacion,
        @JsonProperty("fecha_emision") LocalDateTime fechaEmision,
        @JsonProperty("negocio") Negocio negocio,
        @JsonProperty("items") List<Item> items
) {
    public record Negocio(
            @JsonProperty("nombre_establecimiento") String nombreEstablecimiento,
            @JsonProperty("nit_rut") String nitRut,
            @JsonProperty("direccion") String direccion,
            @JsonProperty("telefono_contacto") String telefonoContacto,
            @JsonProperty("pie_ticket") String pieTicket
    ) {
        public static Negocio fromEntity(ConfiguracionNegocio c) {
            if (c == null) {
                return new Negocio(
                        "PuntoVenta Ágil",
                        "900.000.000-1",
                        "Calle Principal # 1-23",
                        "3001234567",
                        "¡Gracias por su compra!"
                );
            }
            return new Negocio(
                    c.getNombreEstablecimiento(),
                    c.getNitRut(),
                    c.getDireccion(),
                    c.getTelefonoContacto(),
                    c.getPieTicket()
            );
        }
    }

    public record Item(
            @JsonProperty("nombre_producto") String nombreProducto,
            @JsonProperty("cantidad") Integer cantidad,
            @JsonProperty("precio_unitario") BigDecimal precioUnitario,
            @JsonProperty("descuento_linea") BigDecimal descuentoLinea,
            @JsonProperty("porcentaje_iva") BigDecimal porcentajeIva,
            @JsonProperty("impuesto_linea") BigDecimal impuestoLinea,
            @JsonProperty("subtotal_linea") BigDecimal subtotalLinea
    ) {}

    public static FacturaDetalleResponse fromEntity(Factura f, ConfiguracionNegocio config) {
        List<Item> items = f.getVenta().getDetalles().stream()
                .map(d -> new Item(
                        d.getProducto().getNombre(),
                        d.getCantidad(),
                        d.getPrecioUnitario(),
                        d.getDescuentoLinea(),
                        d.getPorcentajeIva(),
                        d.getImpuestoLinea(),
                        d.getSubtotalLinea()))
                .toList();
        return new FacturaDetalleResponse(
                f.getIdFactura(),
                f.getNumeroTicket(),
                f.getVenta().getIdVenta(),
                f.getClienteIdentificacion(),
                f.getClienteNombre(),
                f.getClienteDireccion(),
                f.getClienteTelefono(),
                f.getMontoTotal(),
                f.getTotalImpuestos(),
                f.getEstado(),
                f.getMotivoAnulacion(),
                f.getFechaEmision(),
                Negocio.fromEntity(config),
                items
        );
    }
}
