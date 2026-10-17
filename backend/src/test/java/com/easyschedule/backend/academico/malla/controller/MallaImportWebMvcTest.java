package com.easyschedule.backend.academico.malla.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easyschedule.backend.academico.malla.dto.MallaImportRequest;
import com.easyschedule.backend.academico.malla.dto.MallaImportResponse;
import com.easyschedule.backend.academico.malla.dto.MateriaImportRequest;
import com.easyschedule.backend.academico.malla.service.MallaFileParserService;
import com.easyschedule.backend.academico.malla.service.MallaImportService;
import com.easyschedule.backend.academico.malla.service.MallaService;
import com.easyschedule.backend.shared.exception.GlobalExceptionHandler;
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
 * Pruebas unitarias de capa Web con MockMvc para la importación directa por JSON
 * en MallaController (POST /api/academico/mallas).
 *
 * Valida el status HTTP 201 CREATED en éxito y 400 BAD_REQUEST ante errores de validación
 * de negocio lanzados por MallaImportService (IllegalArgumentException).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MallaController - Pruebas Unitarias de Importación JSON con MockMvc")
class MallaImportWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private MallaService mallaService;

    @Mock
    private MallaFileParserService fileParserService;

    @Mock
    private MallaImportService mallaImportService;

    @InjectMocks
    private MallaController mallaController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(mallaController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("POST /api/academico/mallas - Importación Directa por JSON")
    class CrearMallaJsonTests {

        @Test
        @DisplayName("Importación exitosa responde HTTP 201 CREATED con el DTO MallaImportResponse y los conteos")
        void crearMalla_jsonValido_retorna201Created() throws Exception {
            MallaImportResponse responseMock = new MallaImportResponse(
                    100L,
                    "Plan de Estudios 2026",
                    2,
                    1,
                    "Malla importada exitosamente"
            );

            when(mallaImportService.importarMalla(any(MallaImportRequest.class))).thenReturn(responseMock);

            String payloadJson = """
                    {
                      "nombre": "Plan de Estudios 2026",
                      "version": "1.0",
                      "carreraId": 1,
                      "materias": [
                        {
                          "codigo": "MAT101",
                          "nombre": "Matemática I",
                          "semestre": 1,
                          "creditos": 5
                        },
                        {
                          "codigo": "MAT102",
                          "nombre": "Matemática II",
                          "semestre": 2,
                          "creditos": 5,
                          "prerequisitos": ["MAT101"]
                        }
                      ]
                    }
                    """;

            mockMvc.perform(post("/api/academico/mallas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadJson)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.mallaId").value(100))
                    .andExpect(jsonPath("$.nombre").value("Plan de Estudios 2026"))
                    .andExpect(jsonPath("$.materiasImportadas").value(2))
                    .andExpect(jsonPath("$.prerequisitosImportados").value(1))
                    .andExpect(jsonPath("$.mensaje").value("Malla importada exitosamente"));

            verify(mallaImportService).importarMalla(any(MallaImportRequest.class));
        }

        @Test
        @DisplayName("Error de negocio (carrera inexistente o validación fallida) responde HTTP 400 BAD REQUEST")
        void crearMalla_errorValidacion_retorna400BadRequest() throws Exception {
            when(mallaImportService.importarMalla(any(MallaImportRequest.class)))
                    .thenThrow(new IllegalArgumentException("La carrera con ID 99 no existe"));

            String payloadJson = """
                    {
                      "nombre": "Plan Invalido",
                      "version": "1.0",
                      "carreraId": 99,
                      "materias": [
                        {
                          "codigo": "MAT101",
                          "nombre": "Matemática I",
                          "semestre": 1,
                          "creditos": 5
                        }
                      ]
                    }
                    """;

            mockMvc.perform(post("/api/academico/mallas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payloadJson)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verify(mallaImportService).importarMalla(any(MallaImportRequest.class));
        }
    }
}
