package com.pva.app.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends AppException {

    public ConflictException(String message, String codigo, String detalle) {
        super(message, HttpStatus.CONFLICT, codigo, detalle);
    }

    public ConflictException(String message, String codigo) {
        super(message, HttpStatus.CONFLICT, codigo, null);
    }
}
