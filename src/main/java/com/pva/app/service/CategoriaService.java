package com.pva.app.service;

import com.pva.app.domain.Categoria;
import com.pva.app.dto.request.ActualizarCategoriaRequest;
import com.pva.app.dto.response.CategoriaResponse;
import com.pva.app.dto.request.CrearCategoriaRequest;
import com.pva.app.dto.response.InactivarCategoriaResponse;
import com.pva.app.repository.CategoriaRepository;
import com.pva.app.exception.ConflictException;
import com.pva.app.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar(Boolean activo) {
        List<Categoria> categorias;
        if (activo != null) {
            categorias = categoriaRepository.findByActivo(activo);
        } else {
            categorias = categoriaRepository.findAll();
        }
        return categorias.stream()
                .map(CategoriaResponse::fromEntity)
                .toList();
    }

    @Transactional
    public CategoriaResponse crear(CrearCategoriaRequest request) {
        String nombreNormalizado = request.nombre().trim();

        if (categoriaRepository.findByNombre(nombreNormalizado).isPresent()) {
            throw new ConflictException(
                    "El nombre de la categoría ya existe",
                    "CAT-DUPLICADO",
                    "Campo: nombre"
            );
        }

        Categoria categoria = Categoria.builder()
                .nombre(nombreNormalizado)
                .descripcion(request.descripcion())
                .activo(true)
                .build();

        Categoria guardada = categoriaRepository.save(categoria);
        return CategoriaResponse.fromEntity(guardada);
    }

    @Transactional
    public CategoriaResponse actualizar(Long idCategoria, ActualizarCategoriaRequest request) {
        Categoria categoria = buscarPorIdOError(idCategoria);
        String nuevoNombre = request.nombre().trim();

        Optional<Categoria> existente = categoriaRepository.findByNombre(nuevoNombre);
        if (existente.isPresent() && !existente.get().getIdCategoria().equals(idCategoria)) {
            throw new ConflictException(
                    "El nombre de la categoría ya existe",
                    "CAT-DUPLICADO",
                    "Campo: nombre"
            );
        }

        categoria.setNombre(nuevoNombre);
        categoria.setDescripcion(request.descripcion());

        Categoria guardada = categoriaRepository.save(categoria);
        return CategoriaResponse.fromEntity(guardada);
    }

    @Transactional
    public InactivarCategoriaResponse inactivar(Long idCategoria) {
        Categoria categoria = buscarPorIdOError(idCategoria);
        categoria.setActivo(false);
        categoriaRepository.save(categoria);

        return new InactivarCategoriaResponse(
                "Categoría inactivada correctamente",
                idCategoria,
                false
        );
    }

    private Categoria buscarPorIdOError(Long idCategoria) {
        return categoriaRepository.findById(idCategoria)
                .orElseThrow(() -> new EntityNotFoundException("Categoría no encontrada", "CAT-NO-ENCONTRADA"));
    }
}
