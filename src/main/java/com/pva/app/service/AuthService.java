package com.pva.app.service;

import com.pva.app.dto.request.LoginRequest;
import com.pva.app.dto.response.LoginResponse;
import com.pva.app.dto.response.LoginTurnoActivoResponse;
import com.pva.app.exception.AppException;
import com.pva.app.exception.BadCredentialsException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.exception.LockedException;
import com.pva.app.domain.Operario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.repository.TurnoCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final OperarioRepository operarioRepository;
    private final TurnoCajaRepository turnoCajaRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
    public LoginResponse login(LoginRequest request) {
        Operario operario = operarioRepository.findByNombreUsuario(request.nombreUsuario())
                .orElseThrow(() -> new EntityNotFoundException("Operario no existe", "AUTH-USUARIO-NO-ENCONTRADO"));

        if (Boolean.FALSE.equals(operario.getActivo())) {
            throw new AppException(
                    "Operario inactivo",
                    HttpStatus.FORBIDDEN,
                    "AUTH-USUARIO-INACTIVO",
                    "El usuario se encuentra inactivo en el sistema"
            );
        }

        int intentosActuales = operario.getIntentosFallidos() == null ? 0 : operario.getIntentosFallidos();
        if (intentosActuales >= 3) {
            throw new LockedException();
        }

        if (!passwordEncoder.matches(request.pin(), operario.getPinHash())) {
            int nuevosIntentos = intentosActuales + 1;
            operario.setIntentosFallidos(nuevosIntentos);
            operarioRepository.save(operario);

            if (nuevosIntentos >= 3) {
                throw new LockedException();
            } else {
                int restantes = 3 - nuevosIntentos;
                throw new BadCredentialsException("Intentos fallidos restantes: " + restantes);
            }
        }

        if (intentosActuales > 0) {
            operario.setIntentosFallidos(0);
            operarioRepository.save(operario);
        }

        String token = jwtService.generateToken(
                operario.getIdOperario(),
                operario.getNombreUsuario(),
                operario.getRol().name()
        );

        Optional<TurnoCaja> turnoOpt = turnoCajaRepository.findByOperarioAndEstado(operario, EstadoTurno.ABIERTO);
        LoginTurnoActivoResponse turnoResponse = turnoOpt
                .map(t -> new LoginTurnoActivoResponse(t.getIdTurno(), t.getEstado().name(), t.getFechaApertura()))
                .orElse(new LoginTurnoActivoResponse(null, null, null));

        return new LoginResponse(
                token,
                operario.getIdOperario(),
                operario.getNombreCompleto(),
                operario.getNombreUsuario(),
                operario.getRol(),
                turnoResponse
        );
    }

    public void logout(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            tokenBlacklistService.revokeToken(token);
        }
    }
}
