package com.pva.app.controller;

import com.pva.app.domain.EstadoVenta;
import com.pva.app.domain.MedioPago;
import com.pva.app.dto.request.AnularVentaRequest;
import com.pva.app.dto.request.CrearVentaRequest;
import com.pva.app.dto.response.AnulacionVentaResponse;
import com.pva.app.dto.response.VentaResponse;
import com.pva.app.dto.response.VentaResumenResponse;
import com.pva.app.exception.AppException;
import com.pva.app.service.VentaService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ventas")
@PreAuthorize("hasAnyRole('CAJERO', 'ADMIN')")
public class VentaController {

    private final VentaService ventaService;

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    @PostMapping
    public ResponseEntity<VentaResponse> crearVenta(
            @Valid @RequestBody CrearVentaRequest request,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(ventaService.crearVenta(request, idOperario));
    }

    @GetMapping
    public ResponseEntity<List<VentaResumenResponse>> listar(
            @RequestParam(value = "id_turno", required = false) Long idTurno,
            @RequestParam(value = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(value = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(value = "estado", required = false) EstadoVenta estado,
            @RequestParam(value = "medio_pago", required = false) MedioPago medioPago,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        return ResponseEntity.ok(ventaService.listar(idOperario, idTurno, desde, hasta, estado, medioPago));
    }

    @GetMapping("/{id_venta}")
    public ResponseEntity<VentaResponse> obtenerPorId(
            @PathVariable("id_venta") Long idVenta,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        return ResponseEntity.ok(ventaService.obtenerPorId(idVenta, idOperario));
    }

    @PatchMapping("/{id_venta}/anular")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AnulacionVentaResponse> anular(
            @PathVariable("id_venta") Long idVenta,
            @Valid @RequestBody AnularVentaRequest request,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        return ResponseEntity.ok(ventaService.anularVenta(idVenta, request, idOperario));
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
