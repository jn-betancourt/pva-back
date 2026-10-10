package com.pva.app.exception;

import org.springframework.http.HttpStatus;

public class LockedException extends AppException {

    public LockedException(String message, String codigo, String detalle) {
        super(message, HttpStatus.LOCKED, codigo, detalle);
    }

    public LockedException() {
        super(
                "Usuario bloqueado por intentos fallidos",
                HttpStatus.LOCKED,
                "AUTH-OPERARIO-BLOQUEADO",
                "Contacte al administrador para desbloquear el acceso"
        );
    }
}
