package com.pva.app.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "venta")
@Getter
@Setter
@Builder
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Long idVenta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_turno", nullable = false)
    private TurnoCaja turno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_operario", nullable = false)
    private Operario operario;

    @Column(name = "uuid_offline", length = 36, unique = true)
    private String uuidOffline;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "descuento_global", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal descuentoGlobal = BigDecimal.ZERO;

    @Column(name = "impuesto_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal impuestoTotal = BigDecimal.ZERO;

    @Column(name = "total_a_pagar", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAPagar;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false, length = 20)
    private MedioPago medioPago;

    @Column(name = "monto_recibido", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoRecibido;

    @Column(name = "cambio_entregado", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cambioEntregado = BigDecimal.ZERO;

    @Column(name = "referencia_pago", length = 100)
    private String referenciaPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoVenta estado = EstadoVenta.CONFIRMADA;

    @Column(name = "sincronizada", nullable = false)
    @Builder.Default
    private Boolean sincronizada = true;

    @Column(name = "fecha_venta", nullable = false)
    @Builder.Default
    private LocalDateTime fechaVenta = LocalDateTime.now();

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DetalleVenta> detalles = new ArrayList<>();

    @OneToOne(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Factura factura;

    public Venta() {
    }

    public Venta(Long idVenta, TurnoCaja turno, Operario operario, String uuidOffline,
                 BigDecimal subtotal, BigDecimal descuentoGlobal, BigDecimal impuestoTotal,
                 BigDecimal totalAPagar, MedioPago medioPago, BigDecimal montoRecibido,
                 BigDecimal cambioEntregado, String referenciaPago, EstadoVenta estado,
                 Boolean sincronizada, LocalDateTime fechaVenta, List<DetalleVenta> detalles,
                 Factura factura) {
        this.idVenta = idVenta;
        this.turno = turno;
        this.operario = operario;
        this.uuidOffline = uuidOffline;
        this.subtotal = subtotal;
        this.descuentoGlobal = descuentoGlobal;
        this.impuestoTotal = impuestoTotal;
        this.totalAPagar = totalAPagar;
        this.medioPago = medioPago;
        this.montoRecibido = montoRecibido;
        this.cambioEntregado = cambioEntregado;
        this.referenciaPago = referenciaPago;
        this.estado = estado;
        this.sincronizada = sincronizada;
        this.fechaVenta = fechaVenta;
        this.detalles = detalles != null ? detalles : new ArrayList<>();
        this.factura = factura;
    }

    public void addDetalle(DetalleVenta detalle) {
        detalles.add(detalle);
        detalle.setVenta(this);
    }
}
