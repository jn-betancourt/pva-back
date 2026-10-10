package com.pva.app.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "configuracion_negocio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionNegocio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_configuracion")
    private Long idConfiguracion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_administrador", nullable = false)
    private Operario administrador;

    @Column(name = "nombre_establecimiento", nullable = false, length = 255)
    private String nombreEstablecimiento;

    @Column(name = "nit_rut", nullable = false, length = 20)
    private String nitRut;

    @Column(name = "direccion", nullable = false, length = 255)
    private String direccion;

    @Column(name = "telefono_contacto", nullable = false, length = 20)
    private String telefonoContacto;

    @Column(name = "porcentaje_iva_defecto", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal porcentajeIvaDefecto = new BigDecimal("19.00");

    @Column(name = "descuento_maximo_cajero", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal descuentoMaximoCajero = new BigDecimal("10.00");

    @Column(name = "pie_ticket", length = 255)
    private String pieTicket;
}
