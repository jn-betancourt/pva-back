package com.pva.app.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;
    private final String detalle;

    public AppException(String message, HttpStatus status, String codigo, String detalle) {
        super(message);
        this.status = status;
        this.codigo = codigo;
        this.detalle = detalle;
    }

    public AppException(String message, HttpStatus status, String codigo) {
        this(message, status, codigo, null);
    }
}
