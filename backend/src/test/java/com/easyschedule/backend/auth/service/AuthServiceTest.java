package com.easyschedule.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.auth.dto.request.GoogleLoginRequest;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.repositories.UserRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * HU#7 — G07-04 a G07-16: loginWithGoogle de AuthService (V(G)=4,6,1,3,3).
 * Métodos privados cubiertos indirectamente sin reflexión.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService - Login con Google (G07)")
class AuthServiceTest {

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

    private static final GoogleUserInfo GOOGLE_INFO =
            new GoogleUserInfo("gid-123", "user@example.com", "Google User");

    private GoogleLoginRequest request(String credential) {
        GoogleLoginRequest request = new GoogleLoginRequest();
        request.setCredential(credential);
        return request;
    }

    private User user(String username, String googleId, String authProvider) {
        User user = new User();
        user.setId(7L);
        user.setUsername(username);
        user.setEmail("user@example.com");
        user.setGoogleId(googleId);
        user.setAuthProvider(authProvider);
        return user;
    }

    private void stubExitoso(User user) {
        when(sessionTokenService.issueToken(user.getId())).thenReturn("google-token");
        when(sessionTokenService.getTokenTtlSeconds()).thenReturn(900L);
    }

    private void assertRespuestaExitosa(ResponseEntity<Object> response, String expectedUsername) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("google-token", body.get("token"));
        assertEquals(expectedUsername, body.get("username"));
        assertEquals(900L, body.get("expiresInSeconds"));
        assertEquals("Login con Google exitoso", body.get("message"));
    }

    @Test
    @DisplayName("G07-04: credential null -> 401, sin verify() ni emisión de token")
    void g07_04_credentialNull() {
        ResponseEntity<Object> response = authService.loginWithGoogle(request(null));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Login con Google invalido", response.getBody());
        verify(googleTokenVerifierService, never()).verify(any());
        verify(sessionTokenService, never()).issueToken(any());
    }

    @Test
    @DisplayName("G07-05: credential en blanco -> 401, sin invocar verify()")
    void g07_05_credentialEnBlanco() {
        ResponseEntity<Object> response = authService.loginWithGoogle(request("   "));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Login con Google invalido", response.getBody());
        verify(googleTokenVerifierService, never()).verify(any());
        verify(sessionTokenService, never()).issueToken(any());
    }

    @Test
    @DisplayName("G07-06: verify() devuelve null -> 401, sin buscar usuario ni generar token")
    void g07_06_verifyDevuelveNull() {
        when(googleTokenVerifierService.verify("token-invalido")).thenReturn(null);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("token-invalido"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Login con Google invalido", response.getBody());
        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(sessionTokenService, never()).issueToken(any());
    }

    @Test
    @DisplayName("G07-07: usuario existente googleId=null authProvider=null -> actualiza ambos y guarda")
    void g07_07_asociaAmbosDesdeNull() {
        User existing = user("ana", null, null);
        when(googleTokenVerifierService.verify("cred")).thenReturn(GOOGLE_INFO);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        stubExitoso(existing);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("cred"));

        assertEquals("gid-123", existing.getGoogleId());
        assertEquals("GOOGLE", existing.getAuthProvider());
        assertNotNull(existing.getUpdatedAt());
        verify(userRepository, times(1)).save(existing);
        verify(sessionTokenService).issueToken(7L);
        assertRespuestaExitosa(response, "ana");
    }

    @Test
    @DisplayName("G07-08: googleId=null authProvider=GOOGLE -> actualiza solamente Google ID")
    void g07_08_completaSoloGoogleId() {
        User existing = user("luis", null, "GOOGLE");
        when(googleTokenVerifierService.verify("cred")).thenReturn(GOOGLE_INFO);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        stubExitoso(existing);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("cred"));

        assertEquals("gid-123", existing.getGoogleId());
        assertEquals("GOOGLE", existing.getAuthProvider());
        assertNotNull(existing.getUpdatedAt());
        verify(userRepository, times(1)).save(existing);
        verify(sessionTokenService).issueToken(7L);
        assertRespuestaExitosa(response, "luis");
    }

    @Test
    @DisplayName("G07-09: googleId vacio authProvider=GOOGLE -> completa Google ID")
    void g07_09_googleIdVacio() {
        User existing = user("maria", "", "GOOGLE");
        when(googleTokenVerifierService.verify("cred")).thenReturn(GOOGLE_INFO);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        stubExitoso(existing);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("cred"));

        assertEquals("gid-123", existing.getGoogleId());
        assertNotNull(existing.getUpdatedAt());
        verify(userRepository, times(1)).save(existing);
        verify(sessionTokenService).issueToken(7L);
        assertRespuestaExitosa(response, "maria");
    }

    @Test
    @DisplayName("G07-10: googleId configurado authProvider=null -> completa proveedor")
    void g07_10_authProviderNull() {
        User existing = user("pedro", "gid-otro", null);
        when(googleTokenVerifierService.verify("cred")).thenReturn(GOOGLE_INFO);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        stubExitoso(existing);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("cred"));

        assertEquals("gid-otro", existing.getGoogleId());
        assertEquals("GOOGLE", existing.getAuthProvider());
        assertNotNull(existing.getUpdatedAt());
        verify(userRepository, times(1)).save(existing);
        verify(sessionTokenService).issueToken(7L);
        assertRespuestaExitosa(response, "pedro");
    }

    @Test
    @DisplayName("G07-11: googleId configurado authProvider vacio -> completa proveedor")
    void g07_11_authProviderVacio() {
        User existing = user("sara", "gid-otro", "");
        when(googleTokenVerifierService.verify("cred")).thenReturn(GOOGLE_INFO);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        stubExitoso(existing);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("cred"));

        assertEquals("GOOGLE", existing.getAuthProvider());
        assertNotNull(existing.getUpdatedAt());
        verify(userRepository, times(1)).save(existing);
        verify(sessionTokenService).issueToken(7L);
        assertRespuestaExitosa(response, "sara");
    }

    @Test
    @DisplayName("G07-12: googleId configurado authProvider=GOOGLE -> sin save(), info conservada, token emitido")
    void g07_12_asociacionCompletaSinSave() {
        User existing = user("carlos", "gid-otro", "GOOGLE");
        when(googleTokenVerifierService.verify("cred")).thenReturn(GOOGLE_INFO);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(existing));
        stubExitoso(existing);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("cred"));

        assertEquals("gid-otro", existing.getGoogleId());
        assertEquals("GOOGLE", existing.getAuthProvider());
        assertNull(existing.getUpdatedAt());
        verify(userRepository, never()).save(any());
        verify(sessionTokenService).issueToken(7L);
        assertRespuestaExitosa(response, "carlos");
    }

    private User capturarUsuarioGuardado() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("G07-13: email a@example.com sin colision -> username 'user'")
    void g07_13_usernameFallbackUser() {
        when(googleTokenVerifierService.verify("cred")).thenReturn(
                new GoogleUserInfo("gid-1", "a@example.com", "A"));
        when(userRepository.findByEmailIgnoreCase("a@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase("user")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(91L);
            return saved;
        });
        when(sessionTokenService.issueToken(91L)).thenReturn("google-token");
        when(sessionTokenService.getTokenTtlSeconds()).thenReturn(900L);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("cred"));

        User guardado = capturarUsuarioGuardado();
        assertEquals("user", guardado.getUsername());
        assertEquals("a@example.com", guardado.getEmail());
        assertNull(guardado.getPasswordHash());
        assertEquals("gid-1", guardado.getGoogleId());
        assertEquals("GOOGLE", guardado.getAuthProvider());
        assertTrue(guardado.isActive());
        assertEquals(0, guardado.getTokenVersion());
        assertNotNull(guardado.getCreatedAt());
        assertNotNull(guardado.getUpdatedAt());
        verify(userRepository, times(1)).save(any(User.class));
        verify(sessionTokenService).issueToken(91L);
        assertRespuestaExitosa(response, "user");
    }

    @Test
    @DisplayName("G07-14: email largo sin colision -> username truncado a 20 caracteres")
    void g07_14_usernameTruncado20() {
        when(googleTokenVerifierService.verify("cred")).thenReturn(
                new GoogleUserInfo("gid-2", "abcdefghijklmnopqrstuvwxy@example.com", "Long"));
        when(userRepository.findByEmailIgnoreCase("abcdefghijklmnopqrstuvwxy@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase("abcdefghijklmnopqrst")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(92L);
            return saved;
        });
        when(sessionTokenService.issueToken(92L)).thenReturn("google-token");
        when(sessionTokenService.getTokenTtlSeconds()).thenReturn(900L);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("cred"));

        User guardado = capturarUsuarioGuardado();
        assertEquals("abcdefghijklmnopqrst", guardado.getUsername());
        assertEquals(20, guardado.getUsername().length());
        verify(userRepository, times(1)).save(any(User.class));
        verify(sessionTokenService).issueToken(92L);
        assertRespuestaExitosa(response, "abcdefghijklmnopqrst");
    }

    @Test
    @DisplayName("G07-15: una colision -> username con sufijo 1 y truncacion a 20")
    void g07_15_usernameConUnaColision() {
        when(googleTokenVerifierService.verify("cred")).thenReturn(
                new GoogleUserInfo("gid-3", "abcdefghijklmnopqrst@example.com", "Collision"));
        when(userRepository.findByEmailIgnoreCase("abcdefghijklmnopqrst@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase("abcdefghijklmnopqrst")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("abcdefghijklmnopqrs1")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(93L);
            return saved;
        });
        when(sessionTokenService.issueToken(93L)).thenReturn("google-token");
        when(sessionTokenService.getTokenTtlSeconds()).thenReturn(900L);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("cred"));

        User guardado = capturarUsuarioGuardado();
        assertEquals("abcdefghijklmnopqrs1", guardado.getUsername());
        assertEquals(20, guardado.getUsername().length());
        verify(userRepository, times(1)).save(any(User.class));
        verify(sessionTokenService).issueToken(93L);
        assertRespuestaExitosa(response, "abcdefghijklmnopqrs1");
    }

    @Test
    @DisplayName("G07-16: dos colisiones -> username 'normaluser2'")
    void g07_16_usernameDosColisiones() {
        when(googleTokenVerifierService.verify("cred")).thenReturn(
                new GoogleUserInfo("gid-4", "normal.user@example.com", "Normal"));
        when(userRepository.findByEmailIgnoreCase("normal.user@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase("normaluser")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("normaluser1")).thenReturn(true);
        when(userRepository.existsByUsernameIgnoreCase("normaluser2")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(94L);
            return saved;
        });
        when(sessionTokenService.issueToken(94L)).thenReturn("google-token");
        when(sessionTokenService.getTokenTtlSeconds()).thenReturn(900L);

        ResponseEntity<Object> response = authService.loginWithGoogle(request("cred"));

        User guardado = capturarUsuarioGuardado();
        assertEquals("normaluser2", guardado.getUsername());
        assertEquals("normal.user@example.com", guardado.getEmail());
        assertNull(guardado.getPasswordHash());
        assertEquals("gid-4", guardado.getGoogleId());
        assertEquals("GOOGLE", guardado.getAuthProvider());
        assertTrue(guardado.isActive());
        assertEquals(0, guardado.getTokenVersion());
        assertNotNull(guardado.getCreatedAt());
        assertNotNull(guardado.getUpdatedAt());
        verify(userRepository, times(1)).save(any(User.class));
        verify(sessionTokenService).issueToken(94L);
        assertRespuestaExitosa(response, "normaluser2");
    }
}
