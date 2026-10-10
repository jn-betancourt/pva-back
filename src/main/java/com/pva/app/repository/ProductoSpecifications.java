package com.pva.app.repository;

import com.pva.app.domain.Producto;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductoSpecifications {

    private ProductoSpecifications() {
    }

    public static Specification<Producto> conFiltros(Long idCategoria, Boolean activo, Boolean stockBajo, String q) {
        return (root, query, cb) -> {
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("categoria", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (idCategoria != null) {
                predicates.add(cb.equal(root.get("categoria").get("idCategoria"), idCategoria));
            }

            if (activo != null) {
                predicates.add(cb.equal(root.get("activo"), activo));
            }

            if (Boolean.TRUE.equals(stockBajo)) {
                predicates.add(cb.lessThanOrEqualTo(root.get("stockActual"), root.get("stockMinimo")));
            }

            if (q != null && !q.trim().isEmpty()) {
                String searchPattern = "%" + q.trim().toLowerCase() + "%";
                Predicate nombreMatch = cb.like(cb.lower(root.get("nombre")), searchPattern);
                Predicate descMatch = cb.like(cb.lower(root.get("descripcion")), searchPattern);
                predicates.add(cb.or(nombreMatch, descMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
