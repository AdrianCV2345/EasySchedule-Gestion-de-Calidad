package com.easyschedule.backend.academico.seleccion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.seleccion.dto.SeleccionRequest;
import com.easyschedule.backend.academico.seleccion.dto.SeleccionResponse;
import com.easyschedule.backend.academico.seleccion.service.SeleccionService;
import java.security.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeleccionController - pruebas unitarias (HU31: consulta de seleccion academica)")
class SeleccionControllerTest {

    @Mock
    private SeleccionService seleccionService;

    @InjectMocks
    private SeleccionController seleccionController;

    private static Principal principalConNombre(String name) {
        return () -> name;
    }

    @Test
    @DisplayName("SC31-01 delegacion del controller: invoca getSeleccionByUserId(7L) y retorna la respuesta del servicio")
    void getSeleccionDelegaConIdAutenticadoYRetornaRespuesta() {
        SeleccionResponse esperado = new SeleccionResponse(1L, "Universidad Mayor", 2L, "Ingenieria de Sistemas", 3L, "Ingenieria de Sistemas");
        when(seleccionService.getSeleccionByUserId(7L)).thenReturn(esperado);

        SeleccionResponse resultado = seleccionController.getSeleccion(principalConNombre("7"));

        assertThat(resultado).isSameAs(esperado);
        verify(seleccionService, times(1)).getSeleccionByUserId(7L);
    }

    @Test
    @DisplayName("SC32-01 delegacion del controller: invoca saveSeleccionByUserId(7L, request) y retorna la respuesta del servicio")
    void saveSeleccionDelegaConIdAutenticadoYRetornaRespuesta() {
        SeleccionRequest request = new SeleccionRequest(1L, 2L, 3L);
        SeleccionResponse esperado = new SeleccionResponse(1L, "Universidad Mayor", 2L, "Ingenieria de Sistemas", 3L, "Ingenieria de Sistemas");
        when(seleccionService.saveSeleccionByUserId(7L, request)).thenReturn(esperado);

        SeleccionResponse resultado = seleccionController.saveSeleccion(request, principalConNombre("7"));

        assertThat(resultado).isSameAs(esperado);
        verify(seleccionService, times(1)).saveSeleccionByUserId(7L, request);
    }
}
