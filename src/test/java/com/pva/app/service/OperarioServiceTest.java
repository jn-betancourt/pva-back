package com.pva.app.service;

import com.pva.app.exception.ConflictException;
import com.pva.app.exception.EntityNotFoundException;
import com.pva.app.exception.PinInvalidoException;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.dto.request.*;
import com.pva.app.dto.response.*;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperarioServiceTest {

    @Mock
    private OperarioRepository operarioRepository;

    @Mock
    private TurnoCajaRepository turnoCajaRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private OperarioService operarioService;

    private Operario operario;

    @BeforeEach
    void setUp() {
        operario = Operario.builder()
                .idOperario(1L)
                .nombreCompleto("Carlos Gómez")
                .numeroDocumento("1020304050")
                .telefono("3101234567")
                .nombreUsuario("cgomez")
                .pinHash("$2a$10$hashedpin")
                .rol(RolOperario.CAJERO)
                .activo(true)
                .intentosFallidos(0)
                .creadoEn(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Listar operarios sin filtro y con filtro de activo")
    void testListarOperarios() {
        Operario inactivo = Operario.builder()
                .idOperario(2L)
                .nombreCompleto("Laura Paz")
                .numeroDocumento("1020304051")
                .nombreUsuario("lpaz")
                .rol(RolOperario.CAJERO)
                .activo(false)
                .build();

        when(operarioRepository.findAll()).thenReturn(List.of(operario, inactivo));

        List<OperarioResponse> todos = operarioService.listar(null);
        assertThat(todos).hasSize(2);

        List<OperarioResponse> activos = operarioService.listar(true);
        assertThat(activos).hasSize(1);
        assertThat(activos.get(0).nombreUsuario()).isEqualTo("cgomez");

        List<OperarioResponse> inactivos = operarioService.listar(false);
        assertThat(inactivos).hasSize(1);
        assertThat(inactivos.get(0).nombreUsuario()).isEqualTo("lpaz");
    }

    @Test
    @DisplayName("Obtener por ID existente y no existente")
    void testObtenerPorId() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario));
        when(operarioRepository.findById(99L)).thenReturn(Optional.empty());

        OperarioResponse encontrado = operarioService.obtenerPorId(1L);
        assertThat(encontrado.idOperario()).isEqualTo(1L);
        assertThat(encontrado.nombreUsuario()).isEqualTo("cgomez");

        assertThatThrownBy(() -> operarioService.obtenerPorId(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("OPE-NO-ENCONTRADO"));
    }

    @Test
    @DisplayName("US-03 BDD Escenario 1: Creación exitosa de cajero con activo=true y PIN hasheado")
    void testCrearOperarioExitoso() {
        CrearOperarioRequest request = new CrearOperarioRequest(
                "Carlos Gómez",
                "1020304050",
                "3101234567",
                "cgomez",
                "5678",
                RolOperario.CAJERO
        );

        when(operarioRepository.findByNombreUsuario("cgomez")).thenReturn(Optional.empty());
        when(operarioRepository.findByNumeroDocumento("1020304050")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("5678")).thenReturn("$2a$10$encoded5678");
        when(operarioRepository.save(any(Operario.class))).thenAnswer(invocation -> {
            Operario op = invocation.getArgument(0);
            op.setIdOperario(10L);
            return op;
        });

        OperarioCreadoResponse response = operarioService.crear(request);

        assertThat(response.idOperario()).isEqualTo(10L);
        assertThat(response.nombreUsuario()).isEqualTo("cgomez");
        assertThat(response.rol()).isEqualTo(RolOperario.CAJERO);
        assertThat(response.activo()).isTrue();
    }

    @Test
    @DisplayName("Crear operario con PIN inválido lanza PinInvalidoException (OPE-PIN-INVALIDO)")
    void testCrearOperarioPinInvalido() {
        CrearOperarioRequest requestCorto = new CrearOperarioRequest(
                "Carlos Gómez", "1020304050", null, "cgomez", "12", RolOperario.CAJERO
        );
        assertThatThrownBy(() -> operarioService.crear(requestCorto))
                .isInstanceOf(PinInvalidoException.class)
                .matches(e -> ((PinInvalidoException) e).getCodigo().equals("OPE-PIN-INVALIDO"));

        CrearOperarioRequest requestLetras = new CrearOperarioRequest(
                "Carlos Gómez", "1020304050", null, "cgomez", "abcd", RolOperario.CAJERO
        );
        assertThatThrownBy(() -> operarioService.crear(requestLetras))
                .isInstanceOf(PinInvalidoException.class);
    }

    @Test
    @DisplayName("Crear operario con nombre de usuario duplicado lanza ConflictException (OPE-DUPLICADO)")
    void testCrearOperarioUsuarioDuplicado() {
        CrearOperarioRequest request = new CrearOperarioRequest(
                "Carlos Gómez", "1020304050", null, "cgomez", "1234", RolOperario.CAJERO
        );
        when(operarioRepository.findByNombreUsuario("cgomez")).thenReturn(Optional.of(operario));

        assertThatThrownBy(() -> operarioService.crear(request))
                .isInstanceOf(ConflictException.class)
                .matches(e -> ((ConflictException) e).getCodigo().equals("OPE-DUPLICADO"))
                .matches(e -> ((ConflictException) e).getDetalle().equals("Campo: nombre_usuario"));
    }

    @Test
    @DisplayName("Crear operario con documento duplicado lanza ConflictException (OPE-DUPLICADO)")
    void testCrearOperarioDocumentoDuplicado() {
        CrearOperarioRequest request = new CrearOperarioRequest(
                "Carlos Gómez", "1020304050", null, "cgomez2", "1234", RolOperario.CAJERO
        );
        when(operarioRepository.findByNombreUsuario("cgomez2")).thenReturn(Optional.empty());
        when(operarioRepository.findByNumeroDocumento("1020304050")).thenReturn(Optional.of(operario));

        assertThatThrownBy(() -> operarioService.crear(request))
                .isInstanceOf(ConflictException.class)
                .matches(e -> ((ConflictException) e).getCodigo().equals("OPE-DUPLICADO"))
                .matches(e -> ((ConflictException) e).getDetalle().equals("Campo: numero_documento"));
    }

    @Test
    @DisplayName("Actualizar operario modifica datos y valida unicidad de nombre de usuario")
    void testActualizarOperario() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario));
        when(operarioRepository.save(any(Operario.class))).thenReturn(operario);

        ActualizarOperarioRequest request = new ActualizarOperarioRequest(
                "Carlos Gómez Editado", "3009998877", null, RolOperario.ADMIN
        );

        OperarioResponse response = operarioService.actualizar(1L, request);
        assertThat(response.nombreCompleto()).isEqualTo("Carlos Gómez Editado");
        assertThat(response.telefono()).isEqualTo("3009998877");
        assertThat(response.rol()).isEqualTo(RolOperario.ADMIN);
    }

    @Test
    @DisplayName("Actualizar PIN valida formato y guarda nuevo hash")
    void testActualizarPin() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario));
        when(passwordEncoder.encode("4321")).thenReturn("$2a$10$newhash");

        ActualizarPinRequest request = new ActualizarPinRequest("4321");
        OperarioPinResponse response = operarioService.actualizarPin(1L, request);

        assertThat(response.idOperario()).isEqualTo(1L);
        assertThat(response.mensaje()).isEqualTo("PIN actualizado correctamente");
        assertThat(operario.getPinHash()).isEqualTo("$2a$10$newhash");
        verify(operarioRepository).save(operario);
    }

    @Test
    @DisplayName("US-02 BDD Escenario 2: Desbloqueo por administrador restablece intentos_fallidos a 0")
    void testDesbloquearOperario() {
        operario.setIntentosFallidos(3);
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario));

        OperarioDesbloqueadoResponse response = operarioService.desbloquear(1L);

        assertThat(response.idOperario()).isEqualTo(1L);
        assertThat(response.intentosFallidos()).isEqualTo(0);
        assertThat(response.mensaje()).isEqualTo("Operario desbloqueado correctamente");
        assertThat(operario.getIntentosFallidos()).isEqualTo(0);
        verify(operarioRepository).save(operario);
    }

    @Test
    @DisplayName("US-03 BDD Escenario 2: Intento de baja con turno abierto rechaza con OPE-TURNO-ACTIVO (RN-OPE-06)")
    void testInactivarConTurnoAbierto() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario));

        TurnoCaja turnoAbierto = TurnoCaja.builder()
                .idTurno(5L)
                .operario(operario)
                .baseEfectivoInicial(new BigDecimal("10000.00"))
                .estado(EstadoTurno.ABIERTO)
                .build();
        when(turnoCajaRepository.findByOperarioAndEstado(operario, EstadoTurno.ABIERTO))
                .thenReturn(Optional.of(turnoAbierto));

        assertThatThrownBy(() -> operarioService.inactivar(1L))
                .isInstanceOf(ConflictException.class)
                .matches(e -> ((ConflictException) e).getCodigo().equals("OPE-TURNO-ACTIVO"))
                .matches(e -> ((ConflictException) e).getDetalle().equals("Cierre el turno antes de inactivar el operario"));

        assertThat(operario.getActivo()).isTrue();
        verify(operarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Inactivar operario sin turno abierto cambia activo a false")
    void testInactivarSinTurnoAbierto() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario));
        when(turnoCajaRepository.findByOperarioAndEstado(operario, EstadoTurno.ABIERTO))
                .thenReturn(Optional.empty());

        OperarioInactivadoResponse response = operarioService.inactivar(1L);

        assertThat(response.idOperario()).isEqualTo(1L);
        assertThat(response.activo()).isFalse();
        assertThat(response.mensaje()).isEqualTo("Operario inactivado correctamente");
        assertThat(operario.getActivo()).isFalse();
        verify(operarioRepository).save(operario);
    }

    @Test
    @DisplayName("Actualizar operario con nombre de usuario duplicado de otro operario lanza ConflictException")
    void testActualizarOperarioUsuarioDuplicadoOtro() {
        Operario otro = Operario.builder().idOperario(2L).nombreUsuario("otro_user").build();
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario));
        when(operarioRepository.findByNombreUsuario("otro_user")).thenReturn(Optional.of(otro));

        ActualizarOperarioRequest request = new ActualizarOperarioRequest(
                "Carlos Edit", "3101234567", "otro_user", RolOperario.CAJERO
        );

        assertThatThrownBy(() -> operarioService.actualizar(1L, request))
                .isInstanceOf(ConflictException.class)
                .matches(e -> ((ConflictException) e).getCodigo().equals("OPE-DUPLICADO"));
    }

    @Test
    @DisplayName("Actualizar operario con su propio nombre de usuario no lanza conflicto")
    void testActualizarOperarioMismoUsuario() {
        when(operarioRepository.findById(1L)).thenReturn(Optional.of(operario));
        when(operarioRepository.findByNombreUsuario("cgomez")).thenReturn(Optional.of(operario));
        when(operarioRepository.save(any(Operario.class))).thenReturn(operario);

        ActualizarOperarioRequest request = new ActualizarOperarioRequest(
                "Carlos Gómez", "3101234567", "cgomez", RolOperario.CAJERO
        );

        OperarioResponse response = operarioService.actualizar(1L, request);
        assertThat(response.nombreUsuario()).isEqualTo("cgomez");
    }

    @Test
    @DisplayName("Operaciones sobre operario inexistente lanzan EntityNotFoundException")
    void testOperacionesOperarioInexistente() {
        when(operarioRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> operarioService.actualizarPin(999L, new ActualizarPinRequest("1234")))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("OPE-NO-ENCONTRADO"));

        assertThatThrownBy(() -> operarioService.desbloquear(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("OPE-NO-ENCONTRADO"));

        assertThatThrownBy(() -> operarioService.inactivar(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(e -> ((EntityNotFoundException) e).getCodigo().equals("OPE-NO-ENCONTRADO"));
    }
}

