package com.pva.app.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "factura")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_factura")
    private Long idFactura;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_venta", nullable = false, unique = true)
    private Venta venta;

    @Column(name = "numero_ticket", nullable = false, length = 30, unique = true)
    private String numeroTicket;

    @Column(name = "cliente_identificacion", nullable = false, length = 20)
    @Builder.Default
    private String clienteIdentificacion = "222222222222";

    @Column(name = "cliente_nombre", nullable = false, length = 255)
    @Builder.Default
    private String clienteNombre = "CONSUMIDOR FINAL";

    @Column(name = "cliente_direccion", length = 255)
    private String clienteDireccion;

    @Column(name = "cliente_telefono", length = 20)
    private String clienteTelefono;

    @Column(name = "monto_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "total_impuestos", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalImpuestos = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoFactura estado = EstadoFactura.EMITIDA;

    @Column(name = "motivo_anulacion", length = 255)
    private String motivoAnulacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulada_por")
    private Operario anuladaPor;

    @Column(name = "fecha_emision", nullable = false)
    @Builder.Default
    private LocalDateTime fechaEmision = LocalDateTime.now();
}
