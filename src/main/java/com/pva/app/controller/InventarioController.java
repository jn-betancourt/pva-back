package com.pva.app.controller;

import com.pva.app.exception.AppException;
import com.pva.app.dto.response.MovimientoInventarioResponse;
import com.pva.app.dto.request.RegistrarCompraRequest;
import com.pva.app.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventario")
public class InventarioController {

    private final InventarioService inventarioService;

    public InventarioController(InventarioService inventarioService) {
        this.inventarioService = inventarioService;
    }

    @PostMapping("/compra")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MovimientoInventarioResponse> registrarCompra(
            @Valid @RequestBody RegistrarCompraRequest request,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        MovimientoInventarioResponse response = inventarioService.registrarCompra(request, idOperario);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/ajuste")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.pva.app.dto.response.AjusteInventarioResponse> registrarAjuste(
            @Valid @RequestBody com.pva.app.dto.request.RegistrarAjusteRequest request,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(inventarioService.registrarAjuste(request, idOperario));
    }

    @org.springframework.web.bind.annotation.GetMapping("/kardex/{id_producto}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.pva.app.dto.response.KardexResponse> obtenerKardex(
            @org.springframework.web.bind.annotation.PathVariable("id_producto") Long idProducto,
            @org.springframework.web.bind.annotation.RequestParam(value = "desde", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime desde,
            @org.springframework.web.bind.annotation.RequestParam(value = "hasta", required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime hasta,
            @org.springframework.web.bind.annotation.RequestParam(value = "tipo", required = false)
            com.pva.app.domain.TipoMovimientoInventario tipo) {
        return ResponseEntity.ok(inventarioService.obtenerKardex(idProducto, desde, hasta, tipo));
    }

    @org.springframework.web.bind.annotation.GetMapping("/alertas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<com.pva.app.dto.response.AlertaStockResponse>> listarAlertas() {
        return ResponseEntity.ok(inventarioService.listarAlertas());
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
