package com.easyschedule.backend.auth.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.auth.dto.request.ChangePasswordRequest;
import com.easyschedule.backend.auth.service.AuthService;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthControllerChangePasswordUnitTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void cpC01ChangePasswordRejectsNullPrincipal() {
        ChangePasswordRequest request = new ChangePasswordRequest();

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authController.changePassword(request, null)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(authService);
    }

    @Test
    void cpC02ChangePasswordRejectsPrincipalWithNullName() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        Principal principal = () -> null;

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authController.changePassword(request, principal)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(authService);
    }

    @Test
    void cpC03ChangePasswordRejectsNonNumericPrincipalName() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        Principal principal = () -> "abc";

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authController.changePassword(request, principal)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(authService);
    }

    @Test
    void cpC04ChangePasswordDelegatesValidPrincipalToService() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        Principal principal = () -> "1";
        ResponseEntity<Object> expectedResponse = ResponseEntity.ok()
            .body(java.util.Map.of("message", "Contrasenia actualizada correctamente"));
        when(authService.changePassword(1L, request)).thenReturn(expectedResponse);

        ResponseEntity<Object> response = authController.changePassword(request, principal);

        assertSame(expectedResponse, response);
        verify(authService).changePassword(1L, request);
    }
}
