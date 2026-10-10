package com.pva.app.controller;

import com.pva.app.dto.request.*;
import com.pva.app.dto.response.*;
import com.pva.app.service.OperarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/operarios")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class OperarioController {

    private final OperarioService operarioService;

    @GetMapping
    public ResponseEntity<List<OperarioResponse>> listar(
            @RequestParam(value = "activo", required = false) Boolean activo) {
        List<OperarioResponse> operarios = operarioService.listar(activo);
        return ResponseEntity.ok(operarios);
    }

    @GetMapping("/{id_operario}")
    public ResponseEntity<OperarioResponse> obtenerPorId(
            @PathVariable("id_operario") Long idOperario) {
        OperarioResponse response = operarioService.obtenerPorId(idOperario);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<OperarioCreadoResponse> crear(
            @Valid @RequestBody CrearOperarioRequest request) {
        OperarioCreadoResponse response = operarioService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id_operario}")
    public ResponseEntity<OperarioResponse> actualizar(
            @PathVariable("id_operario") Long idOperario,
            @RequestBody ActualizarOperarioRequest request) {
        OperarioResponse response = operarioService.actualizar(idOperario, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id_operario}/pin")
    public ResponseEntity<OperarioPinResponse> actualizarPin(
            @PathVariable("id_operario") Long idOperario,
            @Valid @RequestBody ActualizarPinRequest request) {
        OperarioPinResponse response = operarioService.actualizarPin(idOperario, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id_operario}/desbloquear")
    public ResponseEntity<OperarioDesbloqueadoResponse> desbloquear(
            @PathVariable("id_operario") Long idOperario) {
        OperarioDesbloqueadoResponse response = operarioService.desbloquear(idOperario);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id_operario}/inactivar")
    public ResponseEntity<OperarioInactivadoResponse> inactivar(
            @PathVariable("id_operario") Long idOperario) {
        OperarioInactivadoResponse response = operarioService.inactivar(idOperario);
        return ResponseEntity.ok(response);
    }
}
