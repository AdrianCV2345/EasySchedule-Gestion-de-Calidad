package com.easyschedule.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.easyschedule.backend.auth.controllers.AuthController;
import com.easyschedule.backend.auth.dto.request.SignupRequest;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.repositories.UserRepository;
import com.easyschedule.backend.auth.service.AuthService;
import com.easyschedule.backend.shared.exception.UserAlreadyExistsException;

/**
 * Suite de Pruebas Unitarias con JUnit 5, Mockito y AssertJ.
 * 
 * Basada estrictamente en el análisis de Complejidad Ciclomática V(G) para la Historia de Usuario:
 * "Registro de Usuarios".
 * Total de caminos independientes: 7 (AuthController: 4 caminos, AuthService: 3 caminos).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PoC Registro de Usuarios - JUnit 5")
class UserRegistrationCyclomaticJUnit5Test {

    // =========================================================================
    // 1. AuthController.registerUser(...) -> V(G) = 4 (3 decisiones: 2 ternarias + 1 try-catch)
    // =========================================================================
    @Nested
    @DisplayName("AuthController.registerUser - Pruebas de Caminos Independientes (V(G) = 4)")
    class AuthControllerPathsTest {

        @Mock
        private AuthService authService;

        @InjectMocks
        private AuthController authController;

        private SignupRequest request;

        @BeforeEach
        void setUp() {
            request = new SignupRequest();
        }

        @Test
        @DisplayName("Camino C1: Username no nulo (trim), Email no nulo (trim+lower), Servicio exitoso -> 201 CREATED")
        void camino1_datosCompletos_registroExitoso() {
            request.setUsername("  juanperez  ");
            request.setEmail("  JUAN@example.com  ");
            request.setPassword("SecurePass123!");

            ResponseEntity<Void> response = authController.registerUser(request);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(authService).registerUser(request);
        }

        @Test
        @DisplayName("Camino C2: Username nulo (fallback \"\"), Email válido, Servicio exitoso -> 201 CREATED")
        void camino2_usernameNulo_registroExitoso() {
            request.setUsername(null);
            request.setEmail("test@example.com");
            request.setPassword("SecurePass123!");

            ResponseEntity<Void> response = authController.registerUser(request);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(authService).registerUser(request);
        }

        @Test
        @DisplayName("Camino C3: Username válido, Email nulo (fallback \"\"), Servicio exitoso -> 201 CREATED")
        void camino3_emailNulo_registroExitoso() {
            request.setUsername("juanperez");
            request.setEmail(null);
            request.setPassword("SecurePass123!");

            ResponseEntity<Void> response = authController.registerUser(request);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(authService).registerUser(request);
        }

        @Test
        @DisplayName("Camino C4: Flujo con excepción en AuthService -> Captura en catch y relanza la excepción")
        void camino4_servicioLanzaExcepcion_capturaYRelanza() {
            request.setUsername("juanperez");
            request.setEmail("juan@example.com");
            request.setPassword("SecurePass123!");

            doThrow(new UserAlreadyExistsException("Error: El nombre de usuario ya está en uso"))
                    .when(authService).registerUser(request);

            assertThatThrownBy(() -> authController.registerUser(request))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessage("Error: El nombre de usuario ya está en uso");

            verify(authService).registerUser(request);
        }
    }

    // =========================================================================
    // 2. AuthService.registerUser(...) -> V(G) = 3 (2 decisiones: if username exists, if email exists)
    // =========================================================================
    @Nested
    @DisplayName("AuthService.registerUser - Pruebas de Caminos Independientes (V(G) = 3)")
    class AuthServicePathsTest {

        @Mock
        private UserRepository userRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        @InjectMocks
        private AuthService authService;

        private SignupRequest request;

        @BeforeEach
        void setUp() {
            request = new SignupRequest();
            request.setUsername("  CarlosM  ");
            request.setEmail("  Carlos@Example.COM  ");
            request.setPassword("Password2026!");
        }

        @Test
        @DisplayName("Camino S1: Username ya existe (Decision 1 = True) -> Lanza UserAlreadyExistsException")
        void camino1_usernameYaExiste_lanzaExcepcion() {
            when(userRepository.existsByUsernameIgnoreCase("CarlosM")).thenReturn(true);

            assertThatThrownBy(() -> authService.registerUser(request))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessage("Error: El nombre de usuario ya está en uso");

            verify(userRepository).existsByUsernameIgnoreCase("CarlosM");
            verify(userRepository, never()).existsByEmailIgnoreCase(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Camino S2: Username disponible, Email ya existe (Decision 1 = False, Decision 2 = True) -> Lanza UserAlreadyExistsException")
        void camino2_emailYaExiste_lanzaExcepcion() {
            when(userRepository.existsByUsernameIgnoreCase("CarlosM")).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.registerUser(request))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessage("Error: El correo electrónico ya está registrado");

            verify(userRepository).existsByUsernameIgnoreCase("CarlosM");
            verify(userRepository).existsByEmailIgnoreCase("carlos@example.com");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Camino S3: Username y Email disponibles (Decision 1 = False, Decision 2 = False) -> Cifra contraseña y guarda usuario")
        void camino3_registroExitoso_guardaUsuario() {
            when(userRepository.existsByUsernameIgnoreCase("CarlosM")).thenReturn(false);
            when(userRepository.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Password2026!")).thenReturn("$2a$10$hashedPassword123");

            authService.registerUser(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User userGuardado = userCaptor.getValue();
            assertThat(userGuardado.getUsername()).isEqualTo("CarlosM");
            assertThat(userGuardado.getEmail()).isEqualTo("carlos@example.com");
            assertThat(userGuardado.getPasswordHash()).isEqualTo("$2a$10$hashedPassword123");
        }
    }
}
