package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ErrorResponse(
        String error,
        String codigo,
        String detalle
) {}
