package com.pva.app.service;

import com.pva.app.domain.Categoria;
import com.pva.app.dto.request.ActualizarCategoriaRequest;
import com.pva.app.dto.response.CategoriaResponse;
import com.pva.app.dto.request.CrearCategoriaRequest;
import com.pva.app.dto.response.InactivarCategoriaResponse;
import com.pva.app.repository.CategoriaRepository;
import com.pva.app.exception.ConflictException;
import com.pva.app.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaService categoriaService;

    private Categoria categoria1;
    private Categoria categoria2;

    @BeforeEach
    void setUp() {
        categoria1 = Categoria.builder()
                .idCategoria(1L)
                .nombre("Lácteos")
                .descripcion("Leches y quesos")
                .activo(true)
                .build();

        categoria2 = Categoria.builder()
                .idCategoria(2L)
                .nombre("Bebidas")
                .descripcion("Gaseosas y jugos")
                .activo(false)
                .build();
    }

    @Test
    @DisplayName("Listar categorías con filtro activo = true")
    void testListarCategoriasActivas() {
        when(categoriaRepository.findByActivo(true)).thenReturn(List.of(categoria1));

        List<CategoriaResponse> resultado = categoriaService.listar(true);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).nombre()).isEqualTo("Lácteos");
        verify(categoriaRepository).findByActivo(true);
    }

    @Test
    @DisplayName("Listar todas las categorías cuando activo es null")
    void testListarTodasCategorias() {
        when(categoriaRepository.findAll()).thenReturn(List.of(categoria1, categoria2));

        List<CategoriaResponse> resultado = categoriaService.listar(null);

        assertThat(resultado).hasSize(2);
        verify(categoriaRepository).findAll();
    }

    @Test
    @DisplayName("Crear categoría exitosa con nombre único")
    void testCrearCategoriaExitosa() {
        CrearCategoriaRequest request = new CrearCategoriaRequest("Snacks", "Papas y galletas");
        when(categoriaRepository.findByNombre("Snacks")).thenReturn(Optional.empty());
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocation -> {
            Categoria c = invocation.getArgument(0);
            c.setIdCategoria(3L);
            return c;
        });

        CategoriaResponse response = categoriaService.crear(request);

        assertThat(response.idCategoria()).isEqualTo(3L);
        assertThat(response.nombre()).isEqualTo("Snacks");
        assertThat(response.activo()).isTrue();
        verify(categoriaRepository).save(any(Categoria.class));
    }

    @Test
    @DisplayName("Crear categoría con nombre duplicado lanza ConflictException CAT-DUPLICADO")
    void testCrearCategoriaDuplicadaLanzaConflicto() {
        CrearCategoriaRequest request = new CrearCategoriaRequest("Lácteos", "Otra descripción");
        when(categoriaRepository.findByNombre("Lácteos")).thenReturn(Optional.of(categoria1));

        assertThatThrownBy(() -> categoriaService.crear(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("El nombre de la categoría ya existe")
                .matches(ex -> ((ConflictException) ex).getCodigo().equals("CAT-DUPLICADO"));

        verify(categoriaRepository, never()).save(any(Categoria.class));
    }

    @Test
    @DisplayName("Actualizar categoría exitosamente")
    void testActualizarCategoriaExitosa() {
        ActualizarCategoriaRequest request = new ActualizarCategoriaRequest("Lácteos y Derivados", "Nueva descripción");
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria1));
        when(categoriaRepository.findByNombre("Lácteos y Derivados")).thenReturn(Optional.empty());
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoriaResponse response = categoriaService.actualizar(1L, request);

        assertThat(response.nombre()).isEqualTo("Lácteos y Derivados");
        assertThat(response.descripcion()).isEqualTo("Nueva descripción");
    }

    @Test
    @DisplayName("Actualizar categoría con nombre existente en otra categoría lanza CAT-DUPLICADO")
    void testActualizarCategoriaNombreExistenteLanzaConflicto() {
        ActualizarCategoriaRequest request = new ActualizarCategoriaRequest("Bebidas", "Intento renombrar");
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria1));
        when(categoriaRepository.findByNombre("Bebidas")).thenReturn(Optional.of(categoria2));

        assertThatThrownBy(() -> categoriaService.actualizar(1L, request))
                .isInstanceOf(ConflictException.class)
                .matches(ex -> ((ConflictException) ex).getCodigo().equals("CAT-DUPLICADO"));
    }

    @Test
    @DisplayName("Inactivar categoría exitosamente")
    void testInactivarCategoriaExitosa() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria1));
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InactivarCategoriaResponse response = categoriaService.inactivar(1L);

        assertThat(response.idCategoria()).isEqualTo(1L);
        assertThat(response.activo()).isFalse();
        assertThat(categoria1.getActivo()).isFalse();
    }

    @Test
    @DisplayName("Buscar categoría inexistente lanza CAT-NO-ENCONTRADA")
    void testBuscarInexistenteLanzaNotFound() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.inactivar(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .matches(ex -> ((EntityNotFoundException) ex).getCodigo().equals("CAT-NO-ENCONTRADA"));
    }
}
