package com.pva.app.controller;

import com.pva.app.domain.EstadoFactura;
import com.pva.app.dto.response.FacturaDetalleResponse;
import com.pva.app.dto.response.FacturaResumenResponse;
import com.pva.app.exception.AppException;
import com.pva.app.service.FacturaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/facturas")
@PreAuthorize("hasAnyRole('CAJERO', 'ADMIN')")
public class FacturaController {

    private final FacturaService facturaService;

    public FacturaController(FacturaService facturaService) {
        this.facturaService = facturaService;
    }

    @GetMapping
    public ResponseEntity<List<FacturaResumenResponse>> listar(
            @RequestParam(value = "numero_ticket", required = false) String numeroTicket,
            @RequestParam(value = "cliente_identificacion", required = false) String clienteIdentificacion,
            @RequestParam(value = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(value = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(value = "estado", required = false) EstadoFactura estado,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        return ResponseEntity.ok(
                facturaService.listar(idOperario, numeroTicket, clienteIdentificacion, desde, hasta, estado));
    }

    @GetMapping("/{id_factura}")
    public ResponseEntity<FacturaDetalleResponse> obtenerPorId(
            @PathVariable("id_factura") Long idFactura,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        return ResponseEntity.ok(facturaService.obtenerPorId(idFactura, idOperario));
    }

    private Long extraerIdOperario(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException("Token inválido o expirado", HttpStatus.UNAUTHORIZED, "AUTH-TOKEN-INVALIDO");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        if (principal instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new AppException("Token inválido o expirado", HttpStatus.UNAUTHORIZED, "AUTH-TOKEN-INVALIDO");
            }
        }
        throw new AppException("Token inválido o expirado", HttpStatus.UNAUTHORIZED, "AUTH-TOKEN-INVALIDO");
    }
}
