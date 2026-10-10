package com.pva.app.controller;

import com.pva.app.dto.request.ActualizarCategoriaRequest;
import com.pva.app.dto.response.CategoriaResponse;
import com.pva.app.dto.request.CrearCategoriaRequest;
import com.pva.app.dto.response.InactivarCategoriaResponse;
import com.pva.app.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CAJERO', 'ADMIN')")
    public ResponseEntity<List<CategoriaResponse>> listar(
            @RequestParam(name = "activo", required = false, defaultValue = "true") Boolean activo) {
        List<CategoriaResponse> response = categoriaService.listar(activo);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CrearCategoriaRequest request) {
        CategoriaResponse response = categoriaService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id_categoria}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoriaResponse> actualizar(
            @PathVariable("id_categoria") Long idCategoria,
            @Valid @RequestBody ActualizarCategoriaRequest request) {
        CategoriaResponse response = categoriaService.actualizar(idCategoria, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id_categoria}/inactivar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InactivarCategoriaResponse> inactivar(
            @PathVariable("id_categoria") Long idCategoria) {
        InactivarCategoriaResponse response = categoriaService.inactivar(idCategoria);
        return ResponseEntity.ok(response);
    }
}
