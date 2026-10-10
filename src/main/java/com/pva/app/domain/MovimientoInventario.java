package com.pva.app.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimiento_inventario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Long idMovimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_producto", nullable = false, updatable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_movimiento", nullable = false, length = 20, updatable = false)
    private TipoMovimientoInventario tipoMovimiento;

    @Column(name = "cantidad", nullable = false, updatable = false)
    private Integer cantidad;

    @Column(name = "costo_unitario", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal costoUnitario;

    @Column(name = "costo_total", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal costoTotal;

    @Column(name = "saldo_resultante", nullable = false, updatable = false)
    private Integer saldoResultante;

    @Column(name = "id_referencia", length = 50, updatable = false)
    private String idReferencia;

    @Column(name = "motivo_ajuste", length = 255, updatable = false)
    private String motivoAjuste;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_operario", nullable = false, updatable = false)
    private Operario operario;

    @Column(name = "fecha_movimiento", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime fechaMovimiento = LocalDateTime.now();
}
