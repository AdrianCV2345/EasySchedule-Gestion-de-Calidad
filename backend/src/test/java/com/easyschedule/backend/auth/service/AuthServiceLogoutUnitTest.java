package com.easyschedule.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.easyschedule.backend.auth.repositories.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceLogoutUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionTokenService sessionTokenService;

    @Mock
    private GoogleTokenVerifierService googleTokenVerifierService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            passwordEncoder,
            sessionTokenService,
            googleTokenVerifierService
        );
    }

    @Test
    void givenBearerHeader_whenLogout_thenExtractsAndRevokesToken() {
        ResponseEntity<Object> response = authService.logout("Bearer abc123");

        assertLogoutSucceeded(response);
        verify(sessionTokenService).revokeToken("abc123");
    }

    @Test
    void givenMissingHeader_whenLogout_thenRevokesEmptyTokenAndSucceeds() {
        ResponseEntity<Object> response = authService.logout(null);

        assertLogoutSucceeded(response);
        verify(sessionTokenService).revokeToken("");
    }

    @Test
    void givenNonBearerHeader_whenLogout_thenRevokesEmptyTokenAndSucceeds() {
        ResponseEntity<Object> response = authService.logout("Basic abc123");

        assertLogoutSucceeded(response);
        verify(sessionTokenService).revokeToken("");
    }

    @Test
    void givenUnknownBearerToken_whenLogout_thenRemainsSuccessful() {
        ResponseEntity<Object> response = authService.logout("Bearer unknown-token");

        assertLogoutSucceeded(response);
        verify(sessionTokenService).revokeToken("unknown-token");
    }

    @Test
    void givenValidToken_whenLogout_thenRevokesItAndItCannotBeValidated() {
        SessionTokenService realTokenService = new SessionTokenService();
        authService = new AuthService(
            userRepository,
            passwordEncoder,
            realTokenService,
            googleTokenVerifierService
        );
        String token = realTokenService.issueToken(42L);

        ResponseEntity<Object> response = authService.logout("Bearer " + token);

        assertLogoutSucceeded(response);
        assertEquals(Optional.empty(), realTokenService.validateAndGetUserId(token));
    }

    @Test
    void givenBearerHeaderWithWhitespace_whenLogout_thenTrimsExtractedToken() {
        ResponseEntity<Object> response = authService.logout("Bearer   abc123  ");

        assertLogoutSucceeded(response);
        verify(sessionTokenService).revokeToken("abc123");
    }

    private void assertLogoutSucceeded(ResponseEntity<Object> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(
            "Sesion cerrada correctamente",
            ((Map<?, ?>) response.getBody()).get("message")
        );
    }
}
