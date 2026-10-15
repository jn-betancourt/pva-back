package com.pva.app.service;

import com.pva.app.dto.request.LoginRequest;
import com.pva.app.dto.response.LoginResponse;
import com.pva.app.exception.AppException;
import com.pva.app.exception.BadCredentialsException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.exception.LockedException;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.repository.OperarioRepository;
import com.pva.app.domain.EstadoTurno;
import com.pva.app.domain.TurnoCaja;
import com.pva.app.repository.TurnoCajaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private OperarioRepository operarioRepository;

    @Mock
    private TurnoCajaRepository turnoCajaRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    private AuthService authService;

    private Operario operarioActivo;

    @BeforeEach
    void setUp() {
        operarioActivo = Operario.builder()
                .idOperario(1L)
                .nombreCompleto("Cajero Principal")
                .numeroDocumento("1000000001")
                .nombreUsuario("cajero")
                .pinHash("$2a$10$hashedpin")
                .rol(RolOperario.CAJERO)
                .activo(true)
                .intentosFallidos(0)
                .build();
    }

    @Test
    @DisplayName("US-01 BDD Escenario 1: Acceso exitoso con PIN correcto reinicia intentos y genera token")
    void testLoginExitoso() {
        LoginRequest request = new LoginRequest("cajero", "1234");
        when(operarioRepository.findByNombreUsuario("cajero")).thenReturn(Optional.of(operarioActivo));
        when(passwordEncoder.matches("1234", "$2a$10$hashedpin")).thenReturn(true);
        when(jwtService.generateToken(1L, "cajero", "CAJERO")).thenReturn("jwt.token.valido");

        TurnoCaja turno = TurnoCaja.builder()
                .idTurno(10L)
                .operario(operarioActivo)
                .baseEfectivoInicial(new BigDecimal("50000.00"))
                .estado(EstadoTurno.ABIERTO)
                .fechaApertura(LocalDateTime.now())
                .build();
        when(turnoCajaRepository.findByOperarioAndEstado(operarioActivo, EstadoTurno.ABIERTO))
                .thenReturn(Optional.of(turno));

        LoginResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt.token.valido");
        assertThat(response.idOperario()).isEqualTo(1L);
        assertThat(response.nombreUsuario()).isEqualTo("cajero");
        assertThat(response.rol()).isEqualTo(RolOperario.CAJERO);
        assertThat(response.turnoActivo()).isNotNull();
        assertThat(response.turnoActivo().idTurno()).isEqualTo(10L);
        assertThat(response.turnoActivo().estado()).isEqualTo("ABIERTO");
    }

    @Test
    @DisplayName("US-01: Usuario inexistente debe lanzar EntityNotFoundException con AUTH-USUARIO-NO-ENCONTRADO")
    void testLoginUsuarioInexistente() {
        LoginRequest request = new LoginRequest("no_existe", "1234");
        when(operarioRepository.findByNombreUsuario("no_existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Operario no existe")
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("AUTH-USUARIO-NO-ENCONTRADO"));
    }

    @Test
    @DisplayName("US-01: Operario inactivo debe rechazar la autenticación")
    void testLoginOperarioInactivo() {
        operarioActivo.setActivo(false);
        LoginRequest request = new LoginRequest("cajero", "1234");
        when(operarioRepository.findByNombreUsuario("cajero")).thenReturn(Optional.of(operarioActivo));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AppException.class)
                .hasMessage("Operario inactivo")
                .matches(e -> ((AppException) e).getCodigo().equals("AUTH-USUARIO-INACTIVO"));
    }

    @Test
    @DisplayName("US-01 BDD Escenario 2: PIN incorrecto incrementa intentos fallidos y muestra advertencia con restantes")
    void testLoginPinIncorrectoPrimerIntento() {
        LoginRequest request = new LoginRequest("cajero", "9999");
        when(operarioRepository.findByNombreUsuario("cajero")).thenReturn(Optional.of(operarioActivo));
        when(passwordEncoder.matches("9999", "$2a$10$hashedpin")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Credenciales inválidas")
                .matches(e -> ((BadCredentialsException) e).getCodigo().equals("AUTH-PIN-INCORRECTO"))
                .matches(e -> ((BadCredentialsException) e).getDetalle().equals("Intentos fallidos restantes: 2"));

        assertThat(operarioActivo.getIntentosFallidos()).isEqualTo(1);
        verify(operarioRepository).save(operarioActivo);
    }

    @Test
    @DisplayName("US-02 BDD Escenario 1: Bloqueo automático tras tercer intento fallido")
    void testLoginTercerIntentoBloquea() {
        operarioActivo.setIntentosFallidos(2);
        LoginRequest request = new LoginRequest("cajero", "9999");
        when(operarioRepository.findByNombreUsuario("cajero")).thenReturn(Optional.of(operarioActivo));
        when(passwordEncoder.matches("9999", "$2a$10$hashedpin")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(LockedException.class)
                .hasMessage("Usuario bloqueado por intentos fallidos")
                .matches(e -> ((LockedException) e).getCodigo().equals("AUTH-OPERARIO-BLOQUEADO"))
                .matches(e -> ((LockedException) e).getDetalle().equals("Contacte al administrador para desbloquear el acceso"));

        assertThat(operarioActivo.getIntentosFallidos()).isEqualTo(3);
        verify(operarioRepository).save(operarioActivo);
    }

    @Test
    @DisplayName("US-02: Usuario ya bloqueado (intentos_fallidos >= 3) no permite login")
    void testLoginUsuarioYaBloqueado() {
        operarioActivo.setIntentosFallidos(3);
        LoginRequest request = new LoginRequest("cajero", "1234");
        when(operarioRepository.findByNombreUsuario("cajero")).thenReturn(Optional.of(operarioActivo));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(LockedException.class)
                .matches(e -> ((LockedException) e).getCodigo().equals("AUTH-OPERARIO-BLOQUEADO"));

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("US-01: Logout invalida token en TokenBlacklistService")
    void testLogout() {
        String authHeader = "Bearer token.jwt.a.invalidar";
        authService.logout(authHeader);

        verify(tokenBlacklistService).revokeToken("token.jwt.a.invalidar");
    }

    @Test
    @DisplayName("US-01: Logout con header nulo o no Bearer no invoca blacklist")
    void testLogoutHeaderInvalido() {
        authService.logout(null);
        authService.logout("Basic dXNlcjpwYXNz");

        verify(tokenBlacklistService, never()).revokeToken(any());
    }

    @Test
    @DisplayName("US-01 Escenario 1: Acceso exitoso con intentos fallidos previos resetea el contador")
    void testLoginExitosoConIntentosFallidosPrevios() {
        operarioActivo.setIntentosFallidos(2);
        LoginRequest request = new LoginRequest("cajero", "1234");
        when(operarioRepository.findByNombreUsuario("cajero")).thenReturn(Optional.of(operarioActivo));
        when(passwordEncoder.matches("1234", "$2a$10$hashedpin")).thenReturn(true);
        when(jwtService.generateToken(1L, "cajero", "CAJERO")).thenReturn("jwt.token.valido");
        when(turnoCajaRepository.findByOperarioAndEstado(operarioActivo, EstadoTurno.ABIERTO)).thenReturn(Optional.empty());

        LoginResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt.token.valido");
        assertThat(operarioActivo.getIntentosFallidos()).isEqualTo(0);
        assertThat(response.turnoActivo().idTurno()).isNull();
        verify(operarioRepository).save(operarioActivo);
    }
}

