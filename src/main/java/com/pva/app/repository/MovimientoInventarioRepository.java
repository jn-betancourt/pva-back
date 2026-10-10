package com.pva.app.repository;

import com.pva.app.domain.MovimientoInventario;
import com.pva.app.domain.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {
    List<MovimientoInventario> findByProductoOrderByFechaMovimientoDesc(Producto producto);
    List<MovimientoInventario> findByProductoAndFechaMovimientoBetween(Producto producto, LocalDateTime desde, LocalDateTime hasta);
}
