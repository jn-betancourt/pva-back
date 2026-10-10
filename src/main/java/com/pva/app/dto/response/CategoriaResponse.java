package com.pva.app.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pva.app.domain.Categoria;

public record CategoriaResponse(
        @JsonProperty("id_categoria")
        Long idCategoria,

        @JsonProperty("nombre")
        String nombre,

        @JsonProperty("descripcion")
        String descripcion,

        @JsonProperty("activo")
        Boolean activo
) {
    public static CategoriaResponse fromEntity(Categoria categoria) {
        return new CategoriaResponse(
                categoria.getIdCategoria(),
                categoria.getNombre(),
                categoria.getDescripcion(),
                categoria.getActivo()
        );
    }
}
