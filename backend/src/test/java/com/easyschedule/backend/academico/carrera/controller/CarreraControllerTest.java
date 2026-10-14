package com.easyschedule.backend.academico.carrera.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.carrera.dto.CarreraRequest;
import com.easyschedule.backend.academico.carrera.dto.CarreraResponse;
import com.easyschedule.backend.academico.carrera.service.CarreraService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CarreraController - pruebas unitarias (delegacion de consulta y creacion)")
class CarreraControllerTest {

    @Mock
    private CarreraService carreraService;

    @InjectMocks
    private CarreraController carreraController;

    @Test
    @DisplayName("CP-01 [P2] findByUniversidad devuelve la lista configurada y delega con el id recibido")
    void findByUniversidadReturnsServiceResult() {
        List<CarreraResponse> expected = List.of(
            new CarreraResponse(1L, 1L, "Ingeniería de Sistemas", "IS"),
            new CarreraResponse(2L, 1L, "Medicina", "MED")
        );
        when(carreraService.findActiveByUniversidad(1L)).thenReturn(expected);

        List<CarreraResponse> result = carreraController.findByUniversidad(1L);

        assertThat(result).isSameAs(expected);
        verify(carreraService).findActiveByUniversidad(1L);
    }

    @Test
    @DisplayName("CP-02 [P3] create devuelve la respuesta del servicio y delega con el request recibido")
    void createReturnsServiceResult() {
        CarreraRequest request = new CarreraRequest(1L, "Ingeniería de Sistemas", "IS", true);
        CarreraResponse expected = new CarreraResponse(10L, 1L, "Ingeniería de Sistemas", "IS");

        when(carreraService.createCarrera(request)).thenReturn(expected);

        CarreraResponse result = carreraController.create(request);

        assertThat(result).isSameAs(expected);
        verify(carreraService).createCarrera(request);
    }
} 