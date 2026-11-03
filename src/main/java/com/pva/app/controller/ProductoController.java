package com.pva.app.controller;

import com.pva.app.dto.request.ActualizarProductoRequest;
import com.pva.app.dto.request.CrearProductoRequest;
import com.pva.app.dto.response.CrearProductoResponse;
import com.pva.app.dto.response.InactivarProductoResponse;
import com.pva.app.dto.response.ProductoBarcodeResponse;
import com.pva.app.dto.response.ProductoResponse;
import com.pva.app.service.ProductoService;
import com.pva.app.util.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/productos")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CrearProductoResponse> crear(
            @Valid @RequestBody CrearProductoRequest request,
            Authentication authentication) {
        Long idOperario = SecurityUtils.extraerIdOperario(authentication);
        CrearProductoResponse response = productoService.crear(request, idOperario);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id_producto}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductoResponse> actualizar(
            @PathVariable("id_producto") Long idProducto,
            @Valid @RequestBody ActualizarProductoRequest request) {
        ProductoResponse response = productoService.actualizar(idProducto, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id_producto}/inactivar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InactivarProductoResponse> inactivar(
            @PathVariable("id_producto") Long idProducto) {
        InactivarProductoResponse response = productoService.inactivar(idProducto);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id_producto}")
    @PreAuthorize("hasAnyRole('CAJERO', 'ADMIN')")
    public ResponseEntity<ProductoResponse> obtenerPorId(
            @PathVariable("id_producto") Long idProducto) {
        ProductoResponse response = productoService.obtenerPorId(idProducto);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('CAJERO', 'ADMIN')")
    public ResponseEntity<List<ProductoResponse>> listar(
            @RequestParam(name = "id_categoria", required = false) Long idCategoria,
            @RequestParam(name = "activo", required = false) Boolean activo,
            @RequestParam(name = "stock_bajo", required = false) Boolean stockBajo,
            @RequestParam(name = "q", required = false) String q) {
        List<ProductoResponse> response = productoService.listar(idCategoria, activo, stockBajo, q);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/barcode/{codigo_barras}")
    @PreAuthorize("hasAnyRole('CAJERO', 'ADMIN')")
    public ResponseEntity<ProductoBarcodeResponse> buscarPorBarcode(
            @PathVariable("codigo_barras") String codigoBarras) {
        ProductoBarcodeResponse response = productoService.buscarPorBarcode(codigoBarras);
        return ResponseEntity.ok(response);
    }
}
