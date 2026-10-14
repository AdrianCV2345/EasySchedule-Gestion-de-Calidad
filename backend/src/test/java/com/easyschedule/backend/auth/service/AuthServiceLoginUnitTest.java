package com.easyschedule.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.auth.dto.request.LoginRequest;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.repositories.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionTokenService sessionTokenService;

    @Mock
    private GoogleTokenVerifierService googleTokenVerifierService;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginByUsernameReturnsSessionAndIssuesToken() {
        LoginRequest request = request(" testuser ", "Password123!");
        User user = user(7L, "testuser", "testuser@mail.com", "bcrypt-hash");

        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "bcrypt-hash")).thenReturn(true);
        when(sessionTokenService.issueToken(7L)).thenReturn("session-token");
        when(sessionTokenService.getTokenTtlSeconds()).thenReturn(3600L);

        ResponseEntity<Object> response = authService.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("session-token", body.get("token"));
        assertEquals("testuser", body.get("username"));
        assertEquals(3600L, body.get("expiresInSeconds"));
        verify(sessionTokenService).issueToken(7L);
    }

    @Test
    void loginByEmailReturnsSessionAndIssuesToken() {
        LoginRequest request = request("user@mail.com", "Password123!");
        User user = user(7L, "testuser", "user@mail.com", "bcrypt-hash");

        when(userRepository.findByUsernameIgnoreCase("user@mail.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("user@mail.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "bcrypt-hash")).thenReturn(true);
        when(sessionTokenService.issueToken(7L)).thenReturn("session-token");
        when(sessionTokenService.getTokenTtlSeconds()).thenReturn(3600L);

        ResponseEntity<Object> response = authService.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(sessionTokenService).issueToken(7L);
    }

    @Test
    void loginRejectsUnknownUserWithoutIssuingToken() {
        LoginRequest request = request("unknown", "Password123!");

        when(userRepository.findByUsernameIgnoreCase("unknown")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("unknown")).thenReturn(Optional.empty());

        ResponseEntity<Object> response = authService.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(sessionTokenService, never()).issueToken(any());
    }

    @Test
    void loginRejectsUserWithBlankHashWithoutCheckingPassword() {
        LoginRequest request = request("testuser", "Password123!");
        User user = user(7L, "testuser", "testuser@mail.com", "   ");

        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(user));

        ResponseEntity<Object> response = authService.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(sessionTokenService, never()).issueToken(any());
    }

    @Test
    void loginRejectsUserWithNullHashWithoutCheckingPassword() {
        LoginRequest request = request("testuser", "Password123!");
        User user = user(7L, "testuser", "testuser@mail.com", null);

        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(user));

        ResponseEntity<Object> response = authService.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(passwordEncoder, never()).matches(any(), any());
        verify(sessionTokenService, never()).issueToken(any());
    }

    @Test
    void loginRejectsIncorrectPasswordWithoutIssuingToken() {
        LoginRequest request = request("testuser", "WrongPassword!");
        User user = user(7L, "testuser", "testuser@mail.com", "bcrypt-hash");

        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword!", "bcrypt-hash")).thenReturn(false);

        ResponseEntity<Object> response = authService.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(sessionTokenService, never()).issueToken(any());
    }

    @Test
    void loginRejectsInvalidHashWithoutIssuingToken() {
        LoginRequest request = request("testuser", "Password123!");
        User user = user(7L, "testuser", "testuser@mail.com", "invalid-hash");

        when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123!", "invalid-hash"))
            .thenThrow(new IllegalArgumentException("Invalid BCrypt hash"));

        ResponseEntity<Object> response = authService.login(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(sessionTokenService, never()).issueToken(any());
    }

    private LoginRequest request(String identifier, String password) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(identifier);
        request.setPassword(password);
        return request;
    }

    private User user(Long id, String username, String email, String passwordHash) {
        User user = new User(username, email, passwordHash);
        user.setId(id);
        return user;
    }
}
