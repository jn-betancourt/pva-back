package com.pva.app.controller;

import com.pva.app.exception.AppException;
import com.pva.app.dto.request.AbrirTurnoRequest;
import com.pva.app.dto.request.CerrarTurnoRequest;
import com.pva.app.dto.response.TurnoActivoResponse;
import com.pva.app.dto.response.TurnoResponse;
import com.pva.app.service.TurnoCajaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/turnos")
@PreAuthorize("hasAnyRole('CAJERO', 'ADMIN')")
@RequiredArgsConstructor
public class TurnoCajaController {

    private final TurnoCajaService turnoCajaService;

    @GetMapping("/activo")
    public ResponseEntity<TurnoActivoResponse> obtenerTurnoActivo(Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        TurnoActivoResponse response = turnoCajaService.obtenerTurnoActivo(idOperario);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/abrir")
    public ResponseEntity<TurnoResponse> abrirTurno(
            @Valid @RequestBody AbrirTurnoRequest request,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        TurnoResponse response = turnoCajaService.abrirTurno(idOperario, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id_turno}/cerrar")
    public ResponseEntity<TurnoResponse> cerrarTurno(
            @PathVariable("id_turno") Long idTurno,
            @Valid @RequestBody CerrarTurnoRequest request,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        TurnoResponse response = turnoCajaService.cerrarTurno(idTurno, idOperario, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id_turno}")
    public ResponseEntity<TurnoResponse> obtenerPorId(
            @PathVariable("id_turno") Long idTurno,
            Authentication authentication) {
        Long idOperario = extraerIdOperario(authentication);
        TurnoResponse response = turnoCajaService.obtenerPorId(idTurno, idOperario);
        return ResponseEntity.ok(response);
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
