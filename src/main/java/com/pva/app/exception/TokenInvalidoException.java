package com.pva.app.exception;

import org.springframework.http.HttpStatus;

public class TokenInvalidoException extends AppException {

    public TokenInvalidoException(String message, String codigo, String detalle) {
        super(message, HttpStatus.UNAUTHORIZED, codigo, detalle);
    }

    public TokenInvalidoException() {
        super("Token inválido o expirado", HttpStatus.UNAUTHORIZED, "AUTH-TOKEN-INVALIDO", null);
    }
}
