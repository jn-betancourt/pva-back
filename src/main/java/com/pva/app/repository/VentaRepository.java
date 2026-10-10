package com.pva.app.repository;

import com.pva.app.domain.TurnoCaja;
import com.pva.app.domain.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {
    Optional<Venta> findByUuidOffline(String uuidOffline);
    List<Venta> findByTurno(TurnoCaja turno);
    List<Venta> findByFechaVentaBetween(LocalDateTime desde, LocalDateTime hasta);
}
