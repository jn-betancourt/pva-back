package com.pva.app.service;

import com.pva.app.exception.ConflictException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.exception.PinInvalidoException;
import com.pva.app.domain.Operario;
import com.pva.app.dto.request.*;
import com.pva.app.dto.response.*;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.repository.TurnoCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OperarioService {

    private final OperarioRepository operarioRepository;
    private final TurnoCajaRepository turnoCajaRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<OperarioResponse> listar(Boolean activo) {
        List<Operario> operarios = operarioRepository.findAll();
        return operarios.stream()
                .filter(o -> activo == null || activo.equals(o.getActivo()))
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OperarioResponse obtenerPorId(Long idOperario) {
        Operario operario = buscarPorIdOError(idOperario);
        return mapToResponse(operario);
    }

    @Transactional
    public OperarioCreadoResponse crear(CrearOperarioRequest request) {
        validarPin(request.pin());

        if (operarioRepository.findByNombreUsuario(request.nombreUsuario()).isPresent()) {
            throw new ConflictException(
                    "El nombre de usuario o documento ya existe",
                    "OPE-DUPLICADO",
                    "Campo: nombre_usuario"
            );
        }

        if (operarioRepository.findByNumeroDocumento(request.numeroDocumento()).isPresent()) {
            throw new ConflictException(
                    "El nombre de usuario o documento ya existe",
                    "OPE-DUPLICADO",
                    "Campo: numero_documento"
            );
        }

        Operario operario = Operario.builder()
                .nombreCompleto(request.nombreCompleto())
                .numeroDocumento(request.numeroDocumento())
                .telefono(request.telefono())
                .nombreUsuario(request.nombreUsuario())
                .pinHash(passwordEncoder.encode(request.pin()))
                .rol(request.rol())
                .activo(true)
                .intentosFallidos(0)
                .build();

        Operario guardado = operarioRepository.save(operario);

        return new OperarioCreadoResponse(
                guardado.getIdOperario(),
                guardado.getNombreCompleto(),
                guardado.getNombreUsuario(),
                guardado.getRol(),
                guardado.getActivo(),
                guardado.getCreadoEn()
        );
    }

    @Transactional
    public OperarioResponse actualizar(Long idOperario, ActualizarOperarioRequest request) {
        Operario operario = buscarPorIdOError(idOperario);

        if (request.nombreUsuario() != null && !request.nombreUsuario().isBlank()) {
            operarioRepository.findByNombreUsuario(request.nombreUsuario()).ifPresent(otro -> {
                if (!otro.getIdOperario().equals(idOperario)) {
                    throw new ConflictException(
                            "El nombre de usuario o documento ya existe",
                            "OPE-DUPLICADO",
                            "Campo: nombre_usuario"
                    );
                }
            });
            operario.setNombreUsuario(request.nombreUsuario());
        }

        if (request.nombreCompleto() != null && !request.nombreCompleto().isBlank()) {
            operario.setNombreCompleto(request.nombreCompleto());
        }

        if (request.telefono() != null) {
            operario.setTelefono(request.telefono());
        }

        if (request.rol() != null) {
            operario.setRol(request.rol());
        }

        Operario actualizado = operarioRepository.save(operario);
        return mapToResponse(actualizado);
    }

    @Transactional
    public OperarioPinResponse actualizarPin(Long idOperario, ActualizarPinRequest request) {
        validarPin(request.pinNuevo());
        Operario operario = buscarPorIdOError(idOperario);

        operario.setPinHash(passwordEncoder.encode(request.pinNuevo()));
        operarioRepository.save(operario);

        return new OperarioPinResponse("PIN actualizado correctamente", idOperario);
    }

    @Transactional
    public OperarioDesbloqueadoResponse desbloquear(Long idOperario) {
        Operario operario = buscarPorIdOError(idOperario);

        operario.setIntentosFallidos(0);
        operarioRepository.save(operario);

        return new OperarioDesbloqueadoResponse(
                "Operario desbloqueado correctamente",
                idOperario,
                0
        );
    }

    @Transactional
    public OperarioInactivadoResponse inactivar(Long idOperario) {
        Operario operario = buscarPorIdOError(idOperario);

        Optional<TurnoCaja> turnoAbierto = turnoCajaRepository.findByOperarioAndEstado(operario, EstadoTurno.ABIERTO);
        if (turnoAbierto.isPresent()) {
            throw new ConflictException(
                    "No se puede inactivar un operario con turno abierto",
                    "OPE-TURNO-ACTIVO",
                    "Cierre el turno antes de inactivar el operario"
            );
        }

        operario.setActivo(false);
        operarioRepository.save(operario);

        return new OperarioInactivadoResponse(
                "Operario inactivado correctamente",
                idOperario,
                false
        );
    }

    private Operario buscarPorIdOError(Long idOperario) {
        return operarioRepository.findById(idOperario)
                .orElseThrow(() -> new EntityNotFoundException("Operario no encontrado", "OPE-NO-ENCONTRADO"));
    }

    private void validarPin(String pin) {
        if (pin == null || !pin.matches("^\\d{4,6}$")) {
            throw new PinInvalidoException();
        }
    }

    private OperarioResponse mapToResponse(Operario operario) {
        return new OperarioResponse(
                operario.getIdOperario(),
                operario.getNombreCompleto(),
                operario.getNumeroDocumento(),
                operario.getTelefono(),
                operario.getNombreUsuario(),
                operario.getRol(),
                operario.getActivo(),
                operario.getIntentosFallidos(),
                operario.getCreadoEn(),
                operario.getActualizadoEn()
        );
    }
}
