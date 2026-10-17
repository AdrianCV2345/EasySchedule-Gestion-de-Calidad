package com.easyschedule.backend.estudiante.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easyschedule.backend.estudiante.dto.EstudianteResponse;
import com.easyschedule.backend.estudiante.service.EstudianteMallaExportService;
import com.easyschedule.backend.estudiante.service.EstudianteService;
import com.easyschedule.backend.shared.exception.GlobalExceptionHandler;
import com.easyschedule.backend.shared.exception.ResourceNotFoundException;
import java.security.Principal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Pruebas unitarias de capa Web (MockMvc en contexto web aislado) para los endpoints
 * de consulta de estudiantes en EstudianteController.
 *
 * Mapeo de caminos y comportamiento HTTP:
 * 1. GET /api/estudiantes (findAll)
 *    - Camino 1: Lista con elementos -> 200 OK y JSON Array
 *    - Camino 2: Lista vacía -> 200 OK y JSON Array []
 * 2. GET /api/estudiantes/{id} (findById)
 *    - Camino 1: Estudiante encontrado -> 200 OK con DTO EstudianteResponse
 *    - Camino 2: Estudiante inexistente -> 404 NOT FOUND manejado por GlobalExceptionHandler
 * 3. GET /api/estudiantes/perfil/{username} (findProfileByUsername)
 *    - Camino 1: Autenticado y propietario autorizado -> 200 OK
 *    - Camino 2: Sin credenciales (Principal null) -> 401 UNAUTHORIZED
 *    - Camino 3: Principal no numérico -> 401 UNAUTHORIZED
 *    - Camino 4: Autenticado pero no es propietario -> 403 FORBIDDEN
 *    - Camino 5: Propietario autorizado pero perfil no existe en BD -> 404 NOT FOUND
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EstudianteController - Pruebas Unitarias de Consulta con MockMvc")
class EstudianteConsultaWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private EstudianteService estudianteService;

    @Mock
    private EstudianteMallaExportService exportService;

    @InjectMocks
    private EstudianteController estudianteController;

    private EstudianteResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(estudianteController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleResponse = new EstudianteResponse(
                1L,
                "juanperez",
                "Juan",
                "Perez",
                "juan.perez@universidad.edu",
                "12345678",
                LocalDate.of(2001, 5, 20),
                OffsetDateTime.parse("2024-01-15T10:30:00Z"),
                (short) 5,
                10L,
                20L,
                30L,
                true,
                true
        );
    }

    // =========================================================================
    // 1. GET /api/estudiantes (findAll)
    // =========================================================================
    @Nested
    @DisplayName("GET /api/estudiantes - findAll()")
    class FindAllEndpointTests {

        @Test
        @DisplayName("Debe responder 200 OK y lista JSON con estudiantes cuando existen registros")
        void findAll_cuandoExistenEstudiantes_retorna200YLista() throws Exception {
            when(estudianteService.findAll()).thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/estudiantes")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].username").value("juanperez"))
                    .andExpect(jsonPath("$[0].nombre").value("Juan"))
                    .andExpect(jsonPath("$[0].apellido").value("Perez"))
                    .andExpect(jsonPath("$[0].email").value("juan.perez@universidad.edu"))
                    .andExpect(jsonPath("$[0].carnetIdentidad").value("12345678"))
                    .andExpect(jsonPath("$[0].semestreActual").value(5))
                    .andExpect(jsonPath("$[0].universidadId").value(10))
                    .andExpect(jsonPath("$[0].carreraId").value(20))
                    .andExpect(jsonPath("$[0].mallaId").value(30))
                    .andExpect(jsonPath("$[0].profileCompleted").value(true))
                    .andExpect(jsonPath("$[0].tourCompleted").value(true));

            verify(estudianteService).findAll();
        }

        @Test
        @DisplayName("Debe responder 200 OK y arreglo vacío cuando no hay estudiantes registrados")
        void findAll_cuandoNoHayEstudiantes_retorna200YListaVacia() throws Exception {
            when(estudianteService.findAll()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/estudiantes")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.length()").value(0));

            verify(estudianteService).findAll();
        }
    }

    // =========================================================================
    // 2. GET /api/estudiantes/{id} (findById)
    // =========================================================================
    @Nested
    @DisplayName("GET /api/estudiantes/{id} - findById(id)")
    class FindByIdEndpointTests {

        @Test
        @DisplayName("Debe responder 200 OK y datos del estudiante cuando el ID existe")
        void findById_cuandoEstudianteExiste_retorna200YDetalle() throws Exception {
            when(estudianteService.findById(1L)).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/estudiantes/{id}", 1L)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("juanperez"))
                    .andExpect(jsonPath("$.nombre").value("Juan"))
                    .andExpect(jsonPath("$.apellido").value("Perez"))
                    .andExpect(jsonPath("$.email").value("juan.perez@universidad.edu"))
                    .andExpect(jsonPath("$.carnetIdentidad").value("12345678"))
                    .andExpect(jsonPath("$.profileCompleted").value(true));

            verify(estudianteService).findById(1L);
        }

        @Test
        @DisplayName("Debe responder 404 NOT FOUND con payload de error cuando el estudiante no existe")
        void findById_cuandoEstudianteNoExiste_retorna404() throws Exception {
            when(estudianteService.findById(99L))
                    .thenThrow(new ResourceNotFoundException("Estudiante no encontrado con id: 99"));

            mockMvc.perform(get("/api/estudiantes/{id}", 99L)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Estudiante no encontrado con id: 99"))
                    .andExpect(jsonPath("$.timestamp").exists());

            verify(estudianteService).findById(99L);
        }
    }

    // =========================================================================
    // 3. GET /api/estudiantes/perfil/{username} (findProfileByUsername)
    // =========================================================================
    @Nested
    @DisplayName("GET /api/estudiantes/perfil/{username} - findProfileByUsername(...)")
    class FindProfileByUsernameEndpointTests {

        @Test
        @DisplayName("Debe responder 200 OK cuando el usuario autenticado es el propietario del perfil")
        void findProfile_cuandoAutenticadoEsPropietario_retorna200() throws Exception {
            Principal principalMock = () -> "1";
            when(estudianteService.canAccessProfile("juanperez", 1L)).thenReturn(true);
            when(estudianteService.findByUsername("juanperez")).thenReturn(sampleResponse);

            mockMvc.perform(get("/api/estudiantes/perfil/{username}", "juanperez")
                            .principal(principalMock)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("juanperez"))
                    .andExpect(jsonPath("$.email").value("juan.perez@universidad.edu"));

            verify(estudianteService).canAccessProfile("juanperez", 1L);
            verify(estudianteService).findByUsername("juanperez");
        }

        @Test
        @DisplayName("Debe responder 401 UNAUTHORIZED cuando no se provee Principal autenticado")
        void findProfile_sinPrincipal_retorna401() throws Exception {
            mockMvc.perform(get("/api/estudiantes/perfil/{username}", "juanperez")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verify(estudianteService, never()).canAccessProfile(any(), any());
            verify(estudianteService, never()).findByUsername(any());
        }

        @Test
        @DisplayName("Debe responder 401 UNAUTHORIZED cuando el nombre del Principal no es numérico")
        void findProfile_principalNoNumerico_retorna401() throws Exception {
            Principal principalMock = () -> "usuario_no_numerico";

            mockMvc.perform(get("/api/estudiantes/perfil/{username}", "juanperez")
                            .principal(principalMock)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());

            verify(estudianteService, never()).canAccessProfile(any(), any());
            verify(estudianteService, never()).findByUsername(any());
        }

        @Test
        @DisplayName("Debe responder 403 FORBIDDEN cuando el usuario autenticado no es el propietario del perfil")
        void findProfile_usuarioNoPropietario_retorna403() throws Exception {
            Principal principalMock = () -> "2";
            when(estudianteService.canAccessProfile("juanperez", 2L)).thenReturn(false);

            mockMvc.perform(get("/api/estudiantes/perfil/{username}", "juanperez")
                            .principal(principalMock)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden());

            verify(estudianteService).canAccessProfile("juanperez", 2L);
            verify(estudianteService, never()).findByUsername(any());
        }

        @Test
        @DisplayName("Debe responder 404 NOT FOUND cuando el perfil solicitado no existe en el sistema")
        void findProfile_cuandoPerfilNoExiste_retorna404() throws Exception {
            Principal principalMock = () -> "1";
            when(estudianteService.canAccessProfile("desconocido", 1L)).thenReturn(true);
            when(estudianteService.findByUsername("desconocido"))
                    .thenThrow(new ResourceNotFoundException("Usuario no encontrado con username o correo: desconocido"));

            mockMvc.perform(get("/api/estudiantes/perfil/{username}", "desconocido")
                            .principal(principalMock)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Usuario no encontrado con username o correo: desconocido"));

            verify(estudianteService).canAccessProfile("desconocido", 1L);
            verify(estudianteService).findByUsername("desconocido");
        }
    }
}
