package com.pva.app.controller;

import com.pva.app.service.JwtService;
import com.pva.app.domain.Categoria;
import com.pva.app.repository.CategoriaRepository;
import com.pva.app.domain.Operario;
import com.pva.app.domain.RolOperario;
import com.pva.app.repository.OperarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoriaControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private OperarioRepository operarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String adminToken;
    private String cajeroToken;
    private Categoria categoria1;

    @BeforeEach
    void setUp() {
        Operario admin = operarioRepository.findByNombreUsuario("admin_cat_test")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Admin Cat Test")
                        .numeroDocumento("9200000001")
                        .nombreUsuario("admin_cat_test")
                        .pinHash(passwordEncoder.encode("1234"))
                        .rol(RolOperario.ADMIN)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        Operario cajero = operarioRepository.findByNombreUsuario("cajero_cat_test")
                .orElseGet(() -> operarioRepository.save(Operario.builder()
                        .nombreCompleto("Cajero Cat Test")
                        .numeroDocumento("9200000002")
                        .nombreUsuario("cajero_cat_test")
                        .pinHash(passwordEncoder.encode("1234"))
                        .rol(RolOperario.CAJERO)
                        .activo(true)
                        .intentosFallidos(0)
                        .build()));

        adminToken = jwtService.generateToken(admin.getIdOperario(), "admin_cat_test", "ADMIN");
        cajeroToken = jwtService.generateToken(cajero.getIdOperario(), "cajero_cat_test", "CAJERO");

        categoria1 = categoriaRepository.findByNombre("Granos y Cereales")
                .orElseGet(() -> categoriaRepository.save(Categoria.builder()
                        .nombre("Granos y Cereales")
                        .descripcion("Arroz, frijol, lentejas")
                        .activo(true)
                        .build()));
    }

    @Test
    @DisplayName("GET /api/v1/categorias sin token retorna 401")
    void testListarSinTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("AUTH-TOKEN-INVALIDO"));
    }

    @Test
    @DisplayName("GET /api/v1/categorias con CAJERO o ADMIN retorna 200")
    void testListarConCajeroRetorna200() throws Exception {
        mockMvc.perform(get("/api/v1/categorias")
                        .header("Authorization", "Bearer " + cajeroToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())))
                .andExpect(jsonPath("$[?(@.nombre == 'Granos y Cereales')].activo").value(hasItem(true)));
    }

    @Test
    @DisplayName("POST /api/v1/categorias por CAJERO retorna 403 AUTH-NO-AUTORIZADO")
    void testCrearConCajeroRetorna403() throws Exception {
        mockMvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + cajeroToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Lácteos\", \"descripcion\": \"Leche y derivados\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("AUTH-NO-AUTORIZADO"));
    }

    @Test
    @DisplayName("POST /api/v1/categorias por ADMIN crea exitosamente 201 Created")
    void testCrearConAdminRetorna201() throws Exception {
        mockMvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Bebidas Alcohólicas\", \"descripcion\": \"Cervezas y licores\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id_categoria").isNumber())
                .andExpect(jsonPath("$.nombre").value("Bebidas Alcohólicas"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/categorias duplicada retorna 409 CAT-DUPLICADO")
    void testCrearDuplicadaRetorna409() throws Exception {
        mockMvc.perform(post("/api/v1/categorias")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Granos y Cereales\", \"descripcion\": \"Repetida\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CAT-DUPLICADO"));
    }

    @Test
    @DisplayName("PUT /api/v1/categorias/{id} por ADMIN actualiza correctamente")
    void testActualizarCategoriaRetorna200() throws Exception {
        mockMvc.perform(put("/api/v1/categorias/" + categoria1.getIdCategoria())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Granos y Legumbres\", \"descripcion\": \"Actualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Granos y Legumbres"))
                .andExpect(jsonPath("$.descripcion").value("Actualizado"));
    }

    @Test
    @DisplayName("PATCH /api/v1/categorias/{id}/inactivar por ADMIN realiza baja lógica")
    void testInactivarCategoriaRetorna200() throws Exception {
        mockMvc.perform(patch("/api/v1/categorias/" + categoria1.getIdCategoria() + "/inactivar")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_categoria").value(categoria1.getIdCategoria()))
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.mensaje").value("Categoría inactivada correctamente"));
    }
}
