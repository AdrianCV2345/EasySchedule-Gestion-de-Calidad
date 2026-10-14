package com.easyschedule.backend.auth.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.auth.dto.request.LoginRequest;
import com.easyschedule.backend.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerLoginUnitTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void loginHandlesNullIdentifierAndDelegatesToService() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(null);

        ResponseEntity<Object> expectedResponse = ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .build();
        when(authService.login(request)).thenReturn(expectedResponse);

        ResponseEntity<Object> response = authController.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(authService).login(request);
    }
}
