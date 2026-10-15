package com.pva.app.exception;

import org.springframework.http.HttpStatus;

public class EntityNotFoundException extends AppException {

    public EntityNotFoundException(String message, String codigo, String detalle) {
        super(message, HttpStatus.NOT_FOUND, codigo, detalle);
    }

    public EntityNotFoundException(String message, String codigo) {
        super(message, HttpStatus.NOT_FOUND, codigo, null);
    }
}
