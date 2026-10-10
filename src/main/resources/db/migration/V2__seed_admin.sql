-- V2__seed_admin.sql
-- Usuario administrador inicial para arranque del sistema y pruebas
INSERT INTO operario (nombre_completo, numero_documento, telefono, nombre_usuario, pin_hash, rol, activo, intentos_fallidos)
VALUES ('Administrador Inicial', '1000000000', '3000000000', 'admin', '$2a$10$aZ1vZA8WxGAj5BeM.e9bmOP2VaBvD4W/u//jr3E9IX/b0O5TlI0qq', 'ADMIN', TRUE, 0);
