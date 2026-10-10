package com.pva.app.repository;

import com.pva.app.domain.Operario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OperarioRepository extends JpaRepository<Operario, Long> {
    Optional<Operario> findByNombreUsuario(String nombreUsuario);
    Optional<Operario> findByNumeroDocumento(String numeroDocumento);
}
