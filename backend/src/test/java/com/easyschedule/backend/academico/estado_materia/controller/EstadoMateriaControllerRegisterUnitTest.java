package com.easyschedule.backend.academico.estado_materia.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.estado_materia.dto.EstadoMateriaRequest;
import com.easyschedule.backend.academico.estado_materia.dto.EstadoMateriaResponse;
import com.easyschedule.backend.academico.estado_materia.service.EstadoMateriaService;
import com.easyschedule.backend.shared.exception.UserAlreadyExistsException;
import java.security.Principal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EstadoMateriaControllerRegisterUnitTest {

    @Mock
    private EstadoMateriaService estadoMateriaService;

    @InjectMocks
    private EstadoMateriaController controller;

    @Test
    void givenValidPrincipalAndRequest_whenSaveEstado_thenDelegatesToService() {
        Principal principal = () -> "7";
        EstadoMateriaRequest request = new EstadoMateriaRequest(10L, "pendiente");
        EstadoMateriaResponse expected = response();
        when(estadoMateriaService.saveEstado(7L, request)).thenReturn(expected);

        EstadoMateriaResponse result = controller.saveEstado(request, principal);

        assertSame(expected, result);
        verify(estadoMateriaService).saveEstado(7L, request);
    }

    @Test
    void givenServiceException_whenSaveEstado_thenPropagatesException() {
        Principal principal = () -> "7";
        EstadoMateriaRequest request = new EstadoMateriaRequest(10L, "cursando");
        UserAlreadyExistsException exception = new UserAlreadyExistsException("Estado invalido");
        doThrow(exception).when(estadoMateriaService).saveEstado(7L, request);

        UserAlreadyExistsException thrown = assertThrows(
            UserAlreadyExistsException.class,
            () -> controller.saveEstado(request, principal)
        );

        assertSame(exception, thrown);
        verify(estadoMateriaService).saveEstado(7L, request);
    }

    @Test
    void givenInvalidPrincipal_whenSaveEstado_thenReturnsUnauthorized() {
        EstadoMateriaRequest request = new EstadoMateriaRequest(10L, "pendiente");

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> controller.saveEstado(request, () -> "not-a-number")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
    }

    private EstadoMateriaResponse response() {
        return new EstadoMateriaResponse(20L, 7L, 10L, "pendiente", OffsetDateTime.now());
    }
}
