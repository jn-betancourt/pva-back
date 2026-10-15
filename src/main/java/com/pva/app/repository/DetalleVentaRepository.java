package com.pva.app.repository;

import com.pva.app.domain.DetalleVenta;
import com.pva.app.domain.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {
    List<DetalleVenta> findByVenta(Venta venta);
}
