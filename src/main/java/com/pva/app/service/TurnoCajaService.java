package com.pva.app.service;

import com.pva.app.exception.AppException;
import com.pva.app.exception.ConflictException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.dto.request.AbrirTurnoRequest;
import com.pva.app.dto.request.CerrarTurnoRequest;
import com.pva.app.dto.response.TurnoActivoResponse;
import com.pva.app.dto.response.TurnoResponse;
import com.pva.app.repository.TurnoCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TurnoCajaService {

    private final TurnoCajaRepository turnoCajaRepository;
    private final OperarioRepository operarioRepository;

    @Transactional(readOnly = true)
    public TurnoActivoResponse obtenerTurnoActivo(Operario operario) {
        if (operario == null) {
            throw new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO");
        }

        return turnoCajaRepository.findByOperarioAndEstado(operario, EstadoTurno.ABIERTO)
                .map(TurnoActivoResponse::activo)
                .orElseGet(TurnoActivoResponse::sinTurno);
    }

    @Transactional(readOnly = true)
    public TurnoActivoResponse obtenerTurnoActivo(Long idOperario) {
        Operario operario = buscarOperarioOError(idOperario);
        return obtenerTurnoActivo(operario);
    }

    @Transactional
    public TurnoResponse abrirTurno(Operario operario, AbrirTurnoRequest request) {
        if (operario == null) {
            throw new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO");
        }

        // RN-OPE-04: Validar que el operario no tenga ya un turno con estado = ABIERTO
        Optional<TurnoCaja> turnoAbierto = turnoCajaRepository.findByOperarioAndEstado(operario, EstadoTurno.ABIERTO);
        if (turnoAbierto.isPresent()) {
            throw new ConflictException(
                    "El operario ya tiene un turno abierto",
                    "TURNO-YA-ABIERTO",
                    "Cierre el turno actual antes de abrir uno nuevo"
            );
        }

        BigDecimal baseInicial = request.baseEfectivoInicial().setScale(2, RoundingMode.HALF_UP);
        BigDecimal cero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        TurnoCaja nuevoTurno = TurnoCaja.builder()
                .operario(operario)
                .baseEfectivoInicial(baseInicial)
                .totalEfectivoVentas(cero)
                .totalDigitalVentas(cero)
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build();

        TurnoCaja guardado = turnoCajaRepository.save(nuevoTurno);
        return TurnoResponse.fromEntity(guardado);
    }

    @Transactional
    public TurnoResponse abrirTurno(Long idOperario, AbrirTurnoRequest request) {
        Operario operario = buscarOperarioOError(idOperario);
        return abrirTurno(operario, request);
    }

    @Transactional
    public TurnoResponse cerrarTurno(Long idTurno, Operario operarioActual, CerrarTurnoRequest request) {
        if (operarioActual == null) {
            throw new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO");
        }

        TurnoCaja turno = turnoCajaRepository.findById(idTurno)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado", "TURNO-NO-ENCONTRADO"));

        // Verifica que pertenezca al operario (o ADMIN)
        boolean esDueno = turno.getOperario().getIdOperario().equals(operarioActual.getIdOperario());
        boolean esAdmin = operarioActual.getRol() == RolOperario.ADMIN;
        if (!esDueno && !esAdmin) {
            throw new AppException(
                    "Acceso denegado",
                    HttpStatus.FORBIDDEN,
                    "AUTH-NO-AUTORIZADO",
                    "No tiene permisos suficientes para cerrar este turno"
            );
        }

        // Verifica que esté ABIERTO (si ya está cerrado -> 409)
        if (turno.getEstado() == EstadoTurno.CERRADO) {
            throw new ConflictException(
                    "El turno ya se encuentra cerrado",
                    "TURNO-YA-CERRADO",
                    "No se puede cerrar un turno que ya ha sido finalizado"
            );
        }

        // RN-OPE-05: Arqueo ciego - calcula discrepancia = monto_fisico_arqueo - (base_efectivo_inicial + total_efectivo_ventas)
        BigDecimal base = turno.getBaseEfectivoInicial().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalEfectivo = turno.getTotalEfectivoVentas() != null
                ? turno.getTotalEfectivoVentas().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalEsperado = base.add(totalEfectivo);
        BigDecimal montoFisico = request.montoFisicoArqueo().setScale(2, RoundingMode.HALF_UP);
        BigDecimal discrepancia = montoFisico.subtract(totalEsperado);

        // BDD US-05 Escenario 2: Si discrepancia != 0.00 y observaciones_cierre está vacío/en blanco, exige observaciones
        if (discrepancia.compareTo(BigDecimal.ZERO) != 0) {
            if (request.observacionesCierre() == null || request.observacionesCierre().trim().isEmpty()) {
                throw new AppException(
                        "Se requieren observaciones de cierre debido a una discrepancia en el arqueo",
                        HttpStatus.BAD_REQUEST,
                        "TURNO-OBSERVACIONES-REQUERIDAS",
                        "Discrepancia detectada: " + discrepancia
                );
            }
        }

        turno.setMontoFisicoArqueo(montoFisico);
        turno.setDiscrepancia(discrepancia);
        turno.setObservacionesCierre(request.observacionesCierre());
        turno.setEstado(EstadoTurno.CERRADO);
        turno.setFechaCierre(LocalDateTime.now());

        TurnoCaja guardado = turnoCajaRepository.save(turno);
        return TurnoResponse.fromEntity(guardado);
    }

    @Transactional
    public TurnoResponse cerrarTurno(Long idTurno, Long idOperarioActual, CerrarTurnoRequest request) {
        Operario operario = buscarOperarioOError(idOperarioActual);
        return cerrarTurno(idTurno, operario, request);
    }

    @Transactional(readOnly = true)
    public TurnoResponse obtenerPorId(Long idTurno, Operario operarioActual) {
        if (operarioActual == null) {
            throw new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO");
        }

        TurnoCaja turno = turnoCajaRepository.findById(idTurno)
                .orElseThrow(() -> new EntityNotFoundException("Turno no encontrado", "TURNO-NO-ENCONTRADO"));

        // Si el operario es CAJERO y el turno no es suyo, deniega con 403 AUTH-NO-AUTORIZADO. Solo ADMIN puede consultar turnos ajenos.
        boolean esDueno = turno.getOperario().getIdOperario().equals(operarioActual.getIdOperario());
        boolean esAdmin = operarioActual.getRol() == RolOperario.ADMIN;
        if (!esDueno && !esAdmin) {
            throw new AppException(
                    "Acceso denegado",
                    HttpStatus.FORBIDDEN,
                    "AUTH-NO-AUTORIZADO",
                    "No tiene permisos para consultar turnos de otros operarios"
            );
        }

        return TurnoResponse.fromEntity(turno);
    }

    @Transactional(readOnly = true)
    public TurnoResponse obtenerPorId(Long idTurno, Long idOperarioActual) {
        Operario operario = buscarOperarioOError(idOperarioActual);
        return obtenerPorId(idTurno, operario);
    }

    private Operario buscarOperarioOError(Long idOperario) {
        return operarioRepository.findById(idOperario)
                .orElseThrow(() -> new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO"));
    }
}
