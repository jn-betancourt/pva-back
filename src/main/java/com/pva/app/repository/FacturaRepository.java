package com.pva.app.repository;

import com.pva.app.domain.Factura;
import com.pva.app.domain.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long> {
    Optional<Factura> findByNumeroTicket(String numeroTicket);
    Optional<Factura> findByVenta(Venta venta);
    List<Factura> findByClienteIdentificacion(String clienteIdentificacion);
}
