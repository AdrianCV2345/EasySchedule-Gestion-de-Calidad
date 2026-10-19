package com.easyschedule.backend.academico.horario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.horario.dto.HorarioGeneradoResponse;
import com.easyschedule.backend.academico.horario.dto.HorarioGeneradorRequest;
import com.easyschedule.backend.academico.horario.service.HorarioGeneradorService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class HorarioGeneradorControllerTest {

    @Mock
    private HorarioGeneradorService horarioGeneradorService;

    @InjectMocks
    private HorarioGeneradorController controller;

    @Test
    void generarHorariosDebeDelegarEnServicioYRetornar200() {
        HorarioGeneradorRequest request = new HorarioGeneradorRequest(1L, 1L, List.of(), List.of());
        HorarioGeneradoResponse response = new HorarioGeneradoResponse(0.0, List.of());
        List<HorarioGeneradoResponse> esperado = List.of(response);
        when(horarioGeneradorService.generarHorarios(request)).thenReturn(esperado);

        ResponseEntity<List<HorarioGeneradoResponse>> resultado = controller.generarHorarios(request);

        assertEquals(200, resultado.getStatusCodeValue());
        assertEquals(esperado, resultado.getBody());
        verify(horarioGeneradorService).generarHorarios(request);
    }
}
