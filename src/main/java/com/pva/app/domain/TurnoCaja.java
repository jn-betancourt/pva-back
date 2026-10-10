package com.pva.app.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "turno_caja")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_turno")
    private Long idTurno;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_operario", nullable = false)
    private Operario operario;

    @Column(name = "base_efectivo_inicial", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseEfectivoInicial;

    @Column(name = "total_efectivo_ventas", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalEfectivoVentas = BigDecimal.ZERO;

    @Column(name = "total_digital_ventas", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalDigitalVentas = BigDecimal.ZERO;

    @Column(name = "monto_fisico_arqueo", precision = 12, scale = 2)
    private BigDecimal montoFisicoArqueo;

    @Column(name = "discrepancia", precision = 12, scale = 2)
    private BigDecimal discrepancia;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoTurno estado = EstadoTurno.ABIERTO;

    @Column(name = "observaciones_cierre", columnDefinition = "TEXT")
    private String observacionesCierre;

    @Column(name = "fecha_apertura", nullable = false)
    @Builder.Default
    private LocalDateTime fechaApertura = LocalDateTime.now();

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
}
