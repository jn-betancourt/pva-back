package com.pva.app.service;

import com.pva.app.domain.ConfiguracionNegocio;
import com.pva.app.domain.EstadoFactura;
import com.pva.app.domain.Factura;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.dto.response.FacturaDetalleResponse;
import com.pva.app.dto.response.FacturaResumenResponse;
import com.pva.app.exception.AppException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.repository.ConfiguracionNegocioRepository;
import com.pva.app.repository.FacturaRepository;
import com.pva.app.repository.OperarioRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class FacturaService {

    private final FacturaRepository facturaRepository;
    private final OperarioRepository operarioRepository;
    private final ConfiguracionNegocioRepository configuracionNegocioRepository;

    public FacturaService(FacturaRepository facturaRepository,
                          OperarioRepository operarioRepository,
                          ConfiguracionNegocioRepository configuracionNegocioRepository) {
        this.facturaRepository = facturaRepository;
        this.operarioRepository = operarioRepository;
        this.configuracionNegocioRepository = configuracionNegocioRepository;
    }

    /** Lista comprobantes con filtros opcionales. El CAJERO solo ve los de sus propias ventas. */
    @Transactional(readOnly = true)
    public List<FacturaResumenResponse> listar(Long idOperario, String numeroTicket, String clienteIdentificacion,
                                               LocalDateTime desde, LocalDateTime hasta, EstadoFactura estado) {
        Operario operario = buscarOperario(idOperario);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new AppException(
                    "El rango de fechas es invalido: 'desde' es posterior a 'hasta'",
                    HttpStatus.BAD_REQUEST,
                    "FAC-RANGO-INVALIDO");
        }

        Specification<Factura> spec = (root, query, cb) -> {
            List<Predicate> predicados = new ArrayList<>();
            if (operario.getRol() != RolOperario.ADMIN) {
                predicados.add(cb.equal(root.get("venta").get("operario"), operario));
            }
            if (numeroTicket != null && !numeroTicket.isBlank()) {
                predicados.add(cb.equal(root.get("numeroTicket"), numeroTicket.trim()));
            }
            if (clienteIdentificacion != null && !clienteIdentificacion.isBlank()) {
                predicados.add(cb.equal(root.get("clienteIdentificacion"), clienteIdentificacion.trim()));
            }
            if (desde != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("fechaEmision"), desde));
            }
            if (hasta != null) {
                predicados.add(cb.lessThanOrEqualTo(root.get("fechaEmision"), hasta));
            }
            if (estado != null) {
                predicados.add(cb.equal(root.get("estado"), estado));
            }
            return cb.and(predicados.toArray(new Predicate[0]));
        };

        return facturaRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "fechaEmision", "idFactura")).stream()
                .map(FacturaResumenResponse::fromEntity)
                .toList();
    }

    /** Detalle completo para reimpresion del ticket (datos del negocio e items). */
    @Transactional(readOnly = true)
    public FacturaDetalleResponse obtenerPorId(Long idFactura, Long idOperario) {
        Operario operario = buscarOperario(idOperario);
        Factura factura = facturaRepository.findById(idFactura)
                .orElseThrow(() -> new EntityNotFoundException("Factura no encontrada", "FAC-NO-ENCONTRADA"));
        if (operario.getRol() != RolOperario.ADMIN
                && !factura.getVenta().getOperario().getIdOperario().equals(operario.getIdOperario())) {
            throw new AppException(
                    "Acceso denegado",
                    HttpStatus.FORBIDDEN,
                    "AUTH-NO-AUTORIZADO",
                    "No tiene permisos suficientes para realizar esta accion");
        }
        ConfiguracionNegocio config = configuracionNegocioRepository.findAll().stream().findFirst().orElse(null);
        return FacturaDetalleResponse.fromEntity(factura, config);
    }

    private Operario buscarOperario(Long idOperario) {
        return operarioRepository.findById(idOperario)
                .orElseThrow(() -> new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO"));
    }
}
