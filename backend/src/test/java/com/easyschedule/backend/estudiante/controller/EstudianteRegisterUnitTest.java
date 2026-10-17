package com.easyschedule.backend.estudiante.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.auth.dto.RegistroRequest;
import com.easyschedule.backend.estudiante.dto.EstudianteResponse;
import com.easyschedule.backend.estudiante.service.EstudianteMallaExportService;
import com.easyschedule.backend.estudiante.service.EstudianteService;
import com.easyschedule.backend.shared.exception.UserAlreadyExistsException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class EstudianteRegisterUnitTest {

    @Mock
    private EstudianteService estudianteService;

    @Mock
    private EstudianteMallaExportService exportService;

    @InjectMocks
    private EstudianteController controller;

    @Test
    void givenValidRequest_whenRegister_thenDelegatesAndReturnsCreated() {
        RegistroRequest request = new RegistroRequest("juan", "Password123!", "juan@mail.com");
        EstudianteResponse expected = response();
        when(estudianteService.register(request)).thenReturn(expected);

        ResponseEntity<EstudianteResponse> result = controller.register(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(expected, result.getBody());
        verify(estudianteService).register(request);
    }

    @Test
    void givenServiceException_whenRegister_thenPropagatesException() {
        RegistroRequest request = new RegistroRequest("juan", "Password123!", "juan@mail.com");
        UserAlreadyExistsException exception = new UserAlreadyExistsException("Usuario duplicado");
        doThrow(exception).when(estudianteService).register(request);

        UserAlreadyExistsException thrown = assertThrows(
            UserAlreadyExistsException.class,
            () -> controller.register(request)
        );

        assertSame(exception, thrown);
        verify(estudianteService).register(request);
    }

    @Test
    void givenNullOptionalFields_whenRegister_thenUsesEmptyLogValuesAndDelegates() {
        RegistroRequest request = new RegistroRequest(null, null, null);
        EstudianteResponse expected = response();
        when(estudianteService.register(request)).thenReturn(expected);

        ResponseEntity<EstudianteResponse> result = controller.register(request);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(expected, result.getBody());
        verify(estudianteService).register(request);
    }

    private EstudianteResponse response() {
        return new EstudianteResponse(
            7L,
            "juan",
            null,
            null,
            "juan@mail.com",
            null,
            null,
            OffsetDateTime.now(),
            null,
            null,
            null,
            null,
            false,
            false
        );
    }
}
