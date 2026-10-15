-- V1__init_schema.sql
-- PuntoVenta Ágil (PVA) - MySQL 8.0 / InnoDB
-- Technical Enabler EN-01: Esquema inicial de base de datos

-- 1. Tabla: operario
CREATE TABLE operario (
    id_operario BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_completo VARCHAR(255) NOT NULL,
    numero_documento VARCHAR(20) NOT NULL,
    telefono VARCHAR(15) NULL,
    nombre_usuario VARCHAR(50) NOT NULL,
    pin_hash VARCHAR(255) NOT NULL,
    rol VARCHAR(20) NOT NULL DEFAULT 'CAJERO',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    intentos_fallidos INT NOT NULL DEFAULT 0,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_operario_numero_documento UNIQUE (numero_documento),
    CONSTRAINT uk_operario_nombre_usuario UNIQUE (nombre_usuario),
    CONSTRAINT chk_operario_rol CHECK (rol IN ('CAJERO', 'ADMIN')),
    CONSTRAINT chk_operario_intentos_fallidos CHECK (intentos_fallidos >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Tabla: configuracion_negocio
CREATE TABLE configuracion_negocio (
    id_configuracion BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_administrador BIGINT NOT NULL,
    nombre_establecimiento VARCHAR(255) NOT NULL,
    nit_rut VARCHAR(20) NOT NULL,
    direccion VARCHAR(255) NOT NULL,
    telefono_contacto VARCHAR(20) NOT NULL,
    porcentaje_iva_defecto DECIMAL(5,2) NOT NULL DEFAULT 19.00,
    descuento_maximo_cajero DECIMAL(5,2) NOT NULL DEFAULT 10.00,
    pie_ticket VARCHAR(255) NULL,
    CONSTRAINT fk_config_administrador FOREIGN KEY (id_administrador) REFERENCES operario(id_operario) ON DELETE RESTRICT,
    CONSTRAINT chk_config_iva CHECK (porcentaje_iva_defecto >= 0.00),
    CONSTRAINT chk_config_descuento CHECK (descuento_maximo_cajero >= 0.00 AND descuento_maximo_cajero <= 100.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Tabla: turno_caja
CREATE TABLE turno_caja (
    id_turno BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_operario BIGINT NOT NULL,
    base_efectivo_inicial DECIMAL(12,2) NOT NULL,
    total_efectivo_ventas DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_digital_ventas DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    monto_fisico_arqueo DECIMAL(12,2) NULL,
    discrepancia DECIMAL(12,2) NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ABIERTO',
    observaciones_cierre TEXT NULL,
    fecha_apertura TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre TIMESTAMP NULL,
    CONSTRAINT fk_turno_operario FOREIGN KEY (id_operario) REFERENCES operario(id_operario) ON DELETE RESTRICT,
    CONSTRAINT chk_turno_base_efectivo CHECK (base_efectivo_inicial >= 0.00),
    CONSTRAINT chk_turno_estado CHECK (estado IN ('ABIERTO', 'CERRADO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Tabla: categoria
CREATE TABLE categoria (
    id_categoria BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255) NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_categoria_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Tabla: producto
CREATE TABLE producto (
    id_producto BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo_barras VARCHAR(50) NULL,
    nombre VARCHAR(255) NOT NULL,
    descripcion TEXT NULL,
    id_categoria BIGINT NOT NULL,
    precio_venta DECIMAL(12,2) NOT NULL,
    costo_promedio DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    aplica_iva BOOLEAN NOT NULL DEFAULT FALSE,
    porcentaje_iva DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    stock_actual INT NOT NULL DEFAULT 0,
    stock_minimo INT NOT NULL DEFAULT 5,
    unidad_medida VARCHAR(20) NOT NULL DEFAULT 'UNIDAD',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_producto_codigo_barras UNIQUE (codigo_barras),
    CONSTRAINT uk_producto_nombre UNIQUE (nombre),
    CONSTRAINT fk_producto_categoria FOREIGN KEY (id_categoria) REFERENCES categoria(id_categoria) ON DELETE RESTRICT,
    CONSTRAINT chk_producto_precio_venta CHECK (precio_venta >= 0.00),
    CONSTRAINT chk_producto_costo_promedio CHECK (costo_promedio >= 0.00),
    CONSTRAINT chk_producto_porcentaje_iva CHECK (porcentaje_iva >= 0.00),
    CONSTRAINT chk_producto_stock_minimo CHECK (stock_minimo >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_producto_codigo_barras ON producto(codigo_barras);
CREATE INDEX idx_producto_categoria_activo ON producto(id_categoria, activo);

-- 6. Tabla: movimiento_inventario (Kardex Append-Only)
CREATE TABLE movimiento_inventario (
    id_movimiento BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_producto BIGINT NOT NULL,
    tipo_movimiento VARCHAR(20) NOT NULL,
    cantidad INT NOT NULL,
    costo_unitario DECIMAL(12,2) NOT NULL,
    costo_total DECIMAL(12,2) NOT NULL,
    saldo_resultante INT NOT NULL,
    id_referencia VARCHAR(50) NULL,
    motivo_ajuste VARCHAR(255) NULL,
    id_operario BIGINT NOT NULL,
    fecha_movimiento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_movimiento_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto) ON DELETE RESTRICT,
    CONSTRAINT fk_movimiento_operario FOREIGN KEY (id_operario) REFERENCES operario(id_operario) ON DELETE RESTRICT,
    CONSTRAINT chk_movimiento_tipo CHECK (tipo_movimiento IN ('COMPRA', 'VENTA', 'AJUSTE_ENTRADA', 'AJUSTE_SALIDA')),
    CONSTRAINT chk_movimiento_costo_unitario CHECK (costo_unitario >= 0.00),
    CONSTRAINT chk_movimiento_costo_total CHECK (costo_total >= 0.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_kardex_producto_fecha ON movimiento_inventario(id_producto, fecha_movimiento);

-- 7. Tabla: venta
CREATE TABLE venta (
    id_venta BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_turno BIGINT NOT NULL,
    id_operario BIGINT NOT NULL,
    uuid_offline VARCHAR(36) NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    descuento_global DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    impuesto_total DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_a_pagar DECIMAL(12,2) NOT NULL,
    medio_pago VARCHAR(20) NOT NULL,
    monto_recibido DECIMAL(12,2) NOT NULL,
    cambio_entregado DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    referencia_pago VARCHAR(100) NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
    sincronizada BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_venta TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_venta_uuid_offline UNIQUE (uuid_offline),
    CONSTRAINT fk_venta_turno FOREIGN KEY (id_turno) REFERENCES turno_caja(id_turno) ON DELETE RESTRICT,
    CONSTRAINT fk_venta_operario FOREIGN KEY (id_operario) REFERENCES operario(id_operario) ON DELETE RESTRICT,
    CONSTRAINT chk_venta_subtotal CHECK (subtotal >= 0.00),
    CONSTRAINT chk_venta_descuento_global CHECK (descuento_global >= 0.00),
    CONSTRAINT chk_venta_impuesto_total CHECK (impuesto_total >= 0.00),
    CONSTRAINT chk_venta_total_a_pagar CHECK (total_a_pagar >= 0.00),
    CONSTRAINT chk_venta_monto_recibido CHECK (monto_recibido >= 0.00),
    CONSTRAINT chk_venta_cambio_entregado CHECK (cambio_entregado >= 0.00),
    CONSTRAINT chk_venta_medio_pago CHECK (medio_pago IN ('EFECTIVO', 'TRANSFERENCIA', 'TARJETA')),
    CONSTRAINT chk_venta_estado CHECK (estado IN ('CONFIRMADA', 'ANULADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_venta_turno ON venta(id_turno);
CREATE INDEX idx_venta_fecha ON venta(fecha_venta);

-- 8. Tabla: detalle_venta
CREATE TABLE detalle_venta (
    id_detalle BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_venta BIGINT NOT NULL,
    id_producto BIGINT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(12,2) NOT NULL,
    costo_unitario_historico DECIMAL(12,2) NOT NULL,
    descuento_linea DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    porcentaje_iva DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    impuesto_linea DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    subtotal_linea DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_detalle_venta FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE RESTRICT,
    CONSTRAINT fk_detalle_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto) ON DELETE RESTRICT,
    CONSTRAINT chk_detalle_cantidad CHECK (cantidad > 0),
    CONSTRAINT chk_detalle_precio_unitario CHECK (precio_unitario >= 0.00),
    CONSTRAINT chk_detalle_costo_historico CHECK (costo_unitario_historico >= 0.00),
    CONSTRAINT chk_detalle_descuento_linea CHECK (descuento_linea >= 0.00),
    CONSTRAINT chk_detalle_porcentaje_iva CHECK (porcentaje_iva >= 0.00),
    CONSTRAINT chk_detalle_impuesto_linea CHECK (impuesto_linea >= 0.00),
    CONSTRAINT chk_detalle_subtotal_linea CHECK (subtotal_linea >= 0.00)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Tabla: factura (Ticket / Comprobante Local)
CREATE TABLE factura (
    id_factura BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_venta BIGINT NOT NULL,
    numero_ticket VARCHAR(30) NOT NULL,
    cliente_identificacion VARCHAR(20) NOT NULL DEFAULT '222222222222',
    cliente_nombre VARCHAR(255) NOT NULL DEFAULT 'CONSUMIDOR FINAL',
    cliente_direccion VARCHAR(255) NULL,
    cliente_telefono VARCHAR(20) NULL,
    monto_total DECIMAL(12,2) NOT NULL,
    total_impuestos DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    estado VARCHAR(20) NOT NULL DEFAULT 'EMITIDA',
    motivo_anulacion VARCHAR(255) NULL,
    anulada_por BIGINT NULL,
    fecha_emision TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_factura_id_venta UNIQUE (id_venta),
    CONSTRAINT uk_factura_numero_ticket UNIQUE (numero_ticket),
    CONSTRAINT fk_factura_venta FOREIGN KEY (id_venta) REFERENCES venta(id_venta) ON DELETE RESTRICT,
    CONSTRAINT fk_factura_anulada_por FOREIGN KEY (anulada_por) REFERENCES operario(id_operario) ON DELETE RESTRICT,
    CONSTRAINT chk_factura_monto_total CHECK (monto_total >= 0.00),
    CONSTRAINT chk_factura_total_impuestos CHECK (total_impuestos >= 0.00),
    CONSTRAINT chk_factura_estado CHECK (estado IN ('EMITIDA', 'ANULADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_factura_numero ON factura(numero_ticket);
CREATE INDEX idx_factura_cliente_cedula ON factura(cliente_identificacion);
