package com.easyschedule.backend.auth.controllers;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.auth.dto.request.GoogleLoginRequest;
import com.easyschedule.backend.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * HU#7 — G07-01 a G07-03: loginWithGoogle del AuthController (V(G)=3).
 * Las tres ramas de credentialPresent en el log se ejercitan y se verifica la delegación al servicio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController - Login con Google (G07)")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private GoogleLoginRequest request(String credential) {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setCredential(credential);
        return request;
    }

    @Test
    @DisplayName("G07-01: credential null -> se evalúa la condición y se delega al servicio")
    void g07_01_credentialNull() {
        GoogleLoginRequest req = request(null);
        ResponseEntity<Object> mocked = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login con Google invalido");
        when(authService.loginWithGoogle(req)).thenReturn(mocked);

        ResponseEntity<Object> response = authController.loginWithGoogle(req);

        assertSame(mocked, response);
        verify(authService).loginWithGoogle(req);
    }

    @Test
    @DisplayName("G07-02: credential en blanco -> condición no nula pero blank, delega al servicio")
    void g07_02_credentialEnBlanco() {
        GoogleLoginRequest req = request("   ");
        ResponseEntity<Object> mocked = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Login con Google invalido");
        when(authService.loginWithGoogle(req)).thenReturn(mocked);

        ResponseEntity<Object> response = authController.loginWithGoogle(req);

        assertSame(mocked, response);
        verify(authService).loginWithGoogle(req);
    }

    @Test
    @DisplayName("G07-03: credential válida -> condición presente, delega y devuelve respuesta del servicio")
    void g07_03_credentialValida() {
        GoogleLoginRequest req = request("token-google");
        ResponseEntity<Object> mocked = ResponseEntity.ok("ok");
        when(authService.loginWithGoogle(req)).thenReturn(mocked);

        ResponseEntity<Object> response = authController.loginWithGoogle(req);

        assertSame(mocked, response);
        verify(authService).loginWithGoogle(req);
    }
}
