package com.pva.app.exception;

import org.springframework.http.HttpStatus;

public class BadCredentialsException extends AppException {

    public BadCredentialsException(String message, String codigo, String detalle) {
        super(message, HttpStatus.UNAUTHORIZED, codigo, detalle);
    }

    public BadCredentialsException(String detalle) {
        super("Credenciales inválidas", HttpStatus.UNAUTHORIZED, "AUTH-PIN-INCORRECTO", detalle);
    }
}
