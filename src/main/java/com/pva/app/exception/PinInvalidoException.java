package com.pva.app.exception;

import org.springframework.http.HttpStatus;

public class PinInvalidoException extends AppException {

    public PinInvalidoException(String message, String codigo, String detalle) {
        super(message, HttpStatus.BAD_REQUEST, codigo, detalle);
    }

    public PinInvalidoException() {
        super(
                "El PIN debe contener entre 4 y 6 dígitos numéricos",
                HttpStatus.BAD_REQUEST,
                "OPE-PIN-INVALIDO",
                null
        );
    }
}
