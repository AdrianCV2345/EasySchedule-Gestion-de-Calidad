package com.easyschedule.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.auth.dto.request.ChangePasswordRequest;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.repositories.UserRepository;
import java.time.OffsetDateTime;
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
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceChangePasswordUnitTest {

    private static final String OLD_HASH = "old-bcrypt-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private SessionTokenService sessionTokenService;

    @Mock
    private GoogleTokenVerifierService googleTokenVerifierService;

    @InjectMocks
    private AuthService authService;

    @Test
    void cpS01ChangePasswordThrowsNotFoundWhenUserDoesNotExist() {
        ChangePasswordRequest request = changePasswordRequest(
            "ActualPass123!", "NuevaPass123!", "NuevaPass123!"
        );
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.changePassword(7L, request)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
        verify(encoder, never()).encode(any());
    }

    @Test
    void cpS02ChangePasswordRejectsIncorrectCurrentPassword() {
        User user = user(7L, OLD_HASH);
        ChangePasswordRequest request = changePasswordRequest(
            "WrongPass123!", "NuevaPass123!", "NuevaPass123!"
        );
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("WrongPass123!", OLD_HASH)).thenReturn(false);

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.changePassword(7L, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
        verify(encoder, never()).encode(any());
    }

    @Test
    void cpS03ChangePasswordRejectsConfirmationMismatch() {
        User user = user(7L, OLD_HASH);
        ChangePasswordRequest request = changePasswordRequest(
            "ActualPass123!", "NuevaPass123!", "ConfirmDistinta123!"
        );
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("ActualPass123!", OLD_HASH)).thenReturn(true);

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.changePassword(7L, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
        verify(encoder, never()).encode(any());
    }

    @Test
    void cpS04ChangePasswordRejectsNewPasswordEqualToCurrent() {
        String sharedPassword = "SamePass123!";
        User user = user(7L, OLD_HASH);
        ChangePasswordRequest request = changePasswordRequest(
            sharedPassword, sharedPassword, sharedPassword
        );
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches(sharedPassword, OLD_HASH)).thenReturn(true);

        ResponseStatusException ex = assertThrows(
            ResponseStatusException.class,
            () -> authService.changePassword(7L, request)
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
        verify(encoder, never()).encode(any());
    }

    @Test
    void cpS05ChangePasswordPersistsEncodedPasswordAndUpdatesTimestamp() {
        User user = user(7L, OLD_HASH);
        ChangePasswordRequest request = changePasswordRequest(
            "ActualPass123!", "NuevaPass123!", "NuevaPass123!"
        );
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("ActualPass123!", OLD_HASH)).thenReturn(true);
        when(encoder.matches("NuevaPass123!", OLD_HASH)).thenReturn(false);
        when(encoder.encode("NuevaPass123!")).thenReturn("encoded-new-hash");
        when(userRepository.save(user)).thenReturn(user);

        OffsetDateTime antes = OffsetDateTime.now();
        ResponseEntity<Object> response = authService.changePassword(7L, request);
        OffsetDateTime despues = OffsetDateTime.now();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertNotNull(body);
        assertEquals("Contrasenia actualizada correctamente", body.get("message"));

        verify(encoder).encode("NuevaPass123!");
        assertEquals("encoded-new-hash", user.getPasswordHash());
        assertNotEquals("NuevaPass123!", user.getPasswordHash());

        assertNotNull(user.getUpdatedAt());
        assertTrue(user.getUpdatedAt().isAfter(antes.minusSeconds(1)),
            "updatedAt debe ser reciente (no anterior a la llamada)");
        assertTrue(user.getUpdatedAt().isBefore(despues.plusSeconds(1)),
            "updatedAt debe ser reciente (no posterior a la finalizacion)");

        verify(userRepository, times(1)).save(user);
    }

    private ChangePasswordRequest changePasswordRequest(
        String currentPassword,
        String newPassword,
        String confirmNewPassword
    ) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        request.setConfirmNewPassword(confirmNewPassword);
        return request;
    }

    private User user(Long id, String passwordHash) {
        User user = new User("testuser", "testuser@mail.com", passwordHash);
        user.setId(id);
        return user;
    }
}
