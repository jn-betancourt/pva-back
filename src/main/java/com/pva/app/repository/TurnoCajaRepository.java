package com.pva.app.repository;

import com.pva.app.domain.Operario;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.TurnoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TurnoCajaRepository extends JpaRepository<TurnoCaja, Long> {
    Optional<TurnoCaja> findByOperarioAndEstado(Operario operario, EstadoTurno estado);
}
