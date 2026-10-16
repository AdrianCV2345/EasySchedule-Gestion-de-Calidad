package com.easyschedule.backend.auth.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerLogoutUnitTest {

    @Mock
    private AuthService authService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AuthController authController;

    @Test
    void givenAuthorizationHeader_whenLogout_thenDelegatesToService() {
        ResponseEntity<Object> expectedResponse = ResponseEntity.ok().build();
        when(request.getHeader("Authorization")).thenReturn("Bearer abc123");
        when(authService.logout("Bearer abc123")).thenReturn(expectedResponse);

        ResponseEntity<Object> response = authController.logout(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).logout("Bearer abc123");
    }

    @Test
    void givenMissingAuthorizationHeader_whenLogout_thenDelegatesNullAndSucceeds() {
        ResponseEntity<Object> expectedResponse = ResponseEntity.ok().build();
        when(request.getHeader("Authorization")).thenReturn(null);
        when(authService.logout(null)).thenReturn(expectedResponse);

        ResponseEntity<Object> response = authController.logout(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(authService).logout(null);
    }
}
