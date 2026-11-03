package com.pva.app.controller;

import com.pva.app.domain.TipoMovimientoInventario;
import com.pva.app.dto.request.RegistrarAjusteRequest;
import com.pva.app.dto.request.RegistrarCompraRequest;
import com.pva.app.dto.response.AjusteInventarioResponse;
import com.pva.app.dto.response.AlertaStockResponse;
import com.pva.app.dto.response.KardexResponse;
import com.pva.app.dto.response.MovimientoInventarioResponse;
import com.pva.app.service.InventarioService;
import com.pva.app.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

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
        Long idOperario = SecurityUtils.extraerIdOperario(authentication);
        MovimientoInventarioResponse response = inventarioService.registrarCompra(request, idOperario);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/ajuste")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AjusteInventarioResponse> registrarAjuste(
            @Valid @RequestBody RegistrarAjusteRequest request,
            Authentication authentication) {
        Long idOperario = SecurityUtils.extraerIdOperario(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(inventarioService.registrarAjuste(request, idOperario));
    }

    @GetMapping("/kardex/{id_producto}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KardexResponse> obtenerKardex(
            @PathVariable("id_producto") Long idProducto,
            @RequestParam(value = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(value = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(value = "tipo", required = false) TipoMovimientoInventario tipo) {
        return ResponseEntity.ok(inventarioService.obtenerKardex(idProducto, desde, hasta, tipo));
    }

    @GetMapping("/alertas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AlertaStockResponse>> listarAlertas() {
        return ResponseEntity.ok(inventarioService.listarAlertas());
    }
}
