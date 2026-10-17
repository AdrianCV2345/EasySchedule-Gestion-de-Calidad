package com.easyschedule.backend.estudiante.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easyschedule.backend.estudiante.dto.EstudianteResponse;
import com.easyschedule.backend.estudiante.dto.EstudianteUpdateRequest;
import com.easyschedule.backend.estudiante.service.EstudianteMallaExportService;
import com.easyschedule.backend.estudiante.service.EstudianteService;
import com.easyschedule.backend.shared.exception.GlobalExceptionHandler;
import com.easyschedule.backend.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * Pruebas unitarias de capa Web con MockMvc (aislamiento con standaloneSetup)
 * para el endpoint de actualización administrativa en EstudianteController.
 *
 * Endpoint objetivo: PUT /api/estudiantes/{id}
 *
 * Caminos evaluados:
 * 1. Actualización exitosa -> 200 OK con DTO EstudianteResponse reflejando los cambios.
 * 2. Estudiante inexistente -> 404 NOT FOUND manejado por GlobalExceptionHandler con payload estándar de error.
 * 3. Malla inexistente referenciada -> 404 NOT FOUND con mensaje de error específico.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EstudianteController - Pruebas Unitarias de Actualización con MockMvc")
class EstudianteUpdateWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private EstudianteService estudianteService;

    @Mock
    private EstudianteMallaExportService exportService;

    @InjectMocks
    private EstudianteController estudianteController;

    private EstudianteResponse responseMock;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(estudianteController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        responseMock = new EstudianteResponse(
                1L,
                "carlos.rojas",
                "Carlos",
                "Rojas",
                "carlos.rojas@mail.com",
                "6543210 LP",
                LocalDate.of(2000, 10, 12),
                OffsetDateTime.parse("2024-01-01T00:00:00Z"),
                (short) 4,
                1L,
                2L,
                10L,
                true,
                false
        );
    }

    @Nested
    @DisplayName("PUT /api/estudiantes/{id} - Actualización Administrativa")
    class UpdateEndpointTests {

        @Test
        @DisplayName("Debe responder 200 OK y retornar el perfil actualizado cuando el estudiante existe")
        void update_cuandoEstudianteExiste_retorna200YPerfilActualizado() throws Exception {
            when(estudianteService.update(eq(1L), any(EstudianteUpdateRequest.class)))
                    .thenReturn(responseMock);

            String requestJson = """
                    {
                      "nombre": "Carlos",
                      "apellido": "Rojas",
                      "carnetIdentidad": "6543210 LP",
                      "fechaNacimiento": "2000-10-12",
                      "semestreActual": 4,
                      "universidadId": 1,
                      "carreraId": 2,
                      "mallaId": 10
                    }
                    """;

            mockMvc.perform(put("/api/estudiantes/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.nombre").value("Carlos"))
                    .andExpect(jsonPath("$.apellido").value("Rojas"))
                    .andExpect(jsonPath("$.carnetIdentidad").value("6543210 LP"))
                    .andExpect(jsonPath("$.semestreActual").value(4))
                    .andExpect(jsonPath("$.mallaId").value(10))
                    .andExpect(jsonPath("$.profileCompleted").value(true));

            verify(estudianteService).update(eq(1L), any(EstudianteUpdateRequest.class));
        }

        @Test
        @DisplayName("Debe responder 404 NOT FOUND cuando el estudiante a actualizar no existe")
        void update_cuandoEstudianteNoExiste_retorna404() throws Exception {
            when(estudianteService.update(eq(999L), any(EstudianteUpdateRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Estudiante no encontrado con id: 999"));

            String requestJson = """
                    {
                      "nombre": "Carlos",
                      "apellido": "Rojas",
                      "carnetIdentidad": "6543210 LP",
                      "fechaNacimiento": "2000-10-12",
                      "semestreActual": 4
                    }
                    """;

            mockMvc.perform(put("/api/estudiantes/{id}", 999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Estudiante no encontrado con id: 999"))
                    .andExpect(jsonPath("$.timestamp").exists());

            verify(estudianteService).update(eq(999L), any(EstudianteUpdateRequest.class));
        }

        @Test
        @DisplayName("Debe responder 404 NOT FOUND cuando la mallaId especificada no existe")
        void update_cuandoMallaNoExiste_retorna404() throws Exception {
            when(estudianteService.update(eq(1L), any(EstudianteUpdateRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Malla no encontrada con id: 888"));

            String requestJson = """
                    {
                      "nombre": "Carlos",
                      "apellido": "Rojas",
                      "carnetIdentidad": "6543210 LP",
                      "fechaNacimiento": "2000-10-12",
                      "semestreActual": 4,
                      "mallaId": 888
                    }
                    """;

            mockMvc.perform(put("/api/estudiantes/{id}", 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Malla no encontrada con id: 888"));

            verify(estudianteService).update(eq(1L), any(EstudianteUpdateRequest.class));
        }
    }
}
