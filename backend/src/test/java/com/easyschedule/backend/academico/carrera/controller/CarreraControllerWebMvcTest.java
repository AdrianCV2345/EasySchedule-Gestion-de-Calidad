package com.easyschedule.backend.academico.carrera.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.easyschedule.backend.academico.carrera.dto.CarreraResponse;
import com.easyschedule.backend.academico.carrera.service.CarreraService;
import com.easyschedule.backend.auth.service.SessionTokenService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("CarreraController - pruebas HTTP con MockMvc (configuración real de seguridad)")
class CarreraControllerWebMvcTest {

    private static final String BEARER_TOKEN = "test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CarreraService carreraService;

    @MockitoBean
    private SessionTokenService sessionTokenService;

    @BeforeEach
    void setUp() {
        when(sessionTokenService.validateAndGetUserId(BEARER_TOKEN)).thenReturn(Optional.of(1L));
    }

    @Test
    @DisplayName("CP-05 [P2] GET con universidadId válido devuelve 200 con la lista del servicio")
    void getCarrerasPorUniversidadDevuelve200() throws Exception {
        List<CarreraResponse> carreras = List.of(
            new CarreraResponse(1L, 1L, "Ingeniería de Sistemas", "IS")
        );
        when(carreraService.findActiveByUniversidad(1L)).thenReturn(carreras);

        mockMvc.perform(get("/api/academico/carreras")
                .param("universidadId", "1")
                .header("Authorization", "Bearer " + BEARER_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].codigo").value("IS"));

        verify(carreraService).findActiveByUniversidad(1L);
    }

    @Test
    @DisplayName("CP-06 [P2] GET sin universidadId no llega al servicio (500 real: missing param lo captura GlobalExceptionHandler genérico)")
    void getCarrerasSinParametroNoLlamaServicio() throws Exception {
        mockMvc.perform(get("/api/academico/carreras")
                .header("Authorization", "Bearer " + BEARER_TOKEN))
            .andExpect(status().is5xxServerError());

        verify(carreraService, never()).findActiveByUniversidad(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("CP-07 [P2] GET con universidadId no numérico no llega al servicio (500 real: tipo inválido lo captura handler de RuntimeException)")
    void getCarrerasConParametroInvalidoNoLlamaServicio() throws Exception {
        mockMvc.perform(get("/api/academico/carreras")
                .param("universidadId", "abc")
                .header("Authorization", "Bearer " + BEARER_TOKEN))
            .andExpect(status().is5xxServerError());

        verify(carreraService, never()).findActiveByUniversidad(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("CP-08 [P3] POST con JSON válido devuelve 201 con la respuesta del servicio")
    void postCarreraValidaDevuelve201() throws Exception {
        CarreraResponse creada = new CarreraResponse(1L, 1L, "Ingeniería de Sistemas", "IS");
        when(carreraService.createCarrera(org.mockito.ArgumentMatchers.any())).thenReturn(creada);

        String json = """
            {
              "universidadId": 1,
              "nombre": "Ingeniería de Sistemas",
              "codigo": "IS",
              "active": true
            }
            """;

        mockMvc.perform(post("/api/academico/carreras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .header("Authorization", "Bearer " + BEARER_TOKEN))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.codigo").value("IS"));

        verify(carreraService).createCarrera(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("CP-09 [P3] POST con nombre vacío falla validación @NotBlank y no llama al servicio")
    void postCarreraConNombreVacioDevuelve400() throws Exception {
        String json = """
            {
              "universidadId": 1,
              "nombre": "",
              "codigo": "IS"
            }
            """;

        mockMvc.perform(post("/api/academico/carreras")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .header("Authorization", "Bearer " + BEARER_TOKEN))
            .andExpect(status().isBadRequest());

        verify(carreraService, never()).createCarrera(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("CP-10 [P3] POST con JSON malformado no llega al servicio (500 real: body ilegible lo captura handler de RuntimeException)")
    void postCarreraConJsonMalformadoNoLlamaServicio() throws Exception {
        mockMvc.perform(post("/api/academico/carreras")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ nombre: ")
                .header("Authorization", "Bearer " + BEARER_TOKEN))
            .andExpect(status().is5xxServerError());

        verify(carreraService, never()).createCarrera(org.mockito.ArgumentMatchers.any());
    }
} 