package com.easyschedule.backend.academico.estado_materia.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.estado_materia.dto.EstadoMateriaResponse;
import com.easyschedule.backend.academico.estado_materia.service.EstadoMateriaService;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EstadoMateriaQueryUnitTest {

    @Mock
    private EstadoMateriaService estadoMateriaService;

    @InjectMocks
    private EstadoMateriaController controller;

    @Test
    void givenValidPrincipal_whenGetEstados_thenDelegatesByUserId() {
        Principal principal = () -> "7";
        List<EstadoMateriaResponse> expected = List.of(response());
        when(estadoMateriaService.getEstadosByUserId(7L)).thenReturn(expected);

        List<EstadoMateriaResponse> result = controller.getEstados(principal);

        assertSame(expected, result);
        verify(estadoMateriaService).getEstadosByUserId(7L);
    }

    @Test
    void givenValidPrincipal_whenGetEstadosByMalla_thenDelegatesByUserAndMalla() {
        Principal principal = () -> "7";
        List<EstadoMateriaResponse> expected = List.of(response());
        when(estadoMateriaService.getEstadosByMalla(7L, 3L)).thenReturn(expected);

        List<EstadoMateriaResponse> result = controller.getEstadosByMalla(3L, principal);

        assertSame(expected, result);
        verify(estadoMateriaService).getEstadosByMalla(7L, 3L);
    }

    @Test
    void givenInvalidPrincipal_whenGetEstados_thenReturnsUnauthorized() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.getEstados(null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    private EstadoMateriaResponse response() {
        return new EstadoMateriaResponse(20L, 7L, 10L, "aprobada", OffsetDateTime.now());
    }
}
