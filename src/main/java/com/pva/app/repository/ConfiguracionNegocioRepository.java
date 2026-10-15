package com.pva.app.repository;

import com.pva.app.domain.ConfiguracionNegocio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionNegocioRepository extends JpaRepository<ConfiguracionNegocio, Long> {
}
