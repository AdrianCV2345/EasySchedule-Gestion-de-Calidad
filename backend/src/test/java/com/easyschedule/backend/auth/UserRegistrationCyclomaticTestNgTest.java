package com.easyschedule.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.easyschedule.backend.auth.controllers.AuthController;
import com.easyschedule.backend.auth.dto.request.SignupRequest;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.repositories.UserRepository;
import com.easyschedule.backend.auth.service.AuthService;
import com.easyschedule.backend.auth.service.GoogleTokenVerifierService;
import com.easyschedule.backend.auth.service.SessionTokenService;
import com.easyschedule.backend.shared.exception.UserAlreadyExistsException;

/**
 * Suite de Pruebas Unitarias con TestNG (equivalente a la suite JUnit 5).
 * 
 * Implementa exactamente los mismos 7 caminos independientes de Complejidad Ciclomática:
 * - AuthController.registerUser: 4 caminos
 * - AuthService.registerUser: 3 caminos
 * 
 * Permite una comparación directa de sintaxis, ciclo de vida (@BeforeMethod),
 * aserciones e inicialización explícita de colaboradores con respecto a JUnit 5.
 */
public class UserRegistrationCyclomaticTestNgTest {

    private AuthService authServiceMock;
    private AuthController authController;

    private UserRepository userRepositoryMock;
    private PasswordEncoder passwordEncoderMock;
    private SessionTokenService sessionTokenServiceMock;
    private GoogleTokenVerifierService googleTokenVerifierServiceMock;
    private AuthService authServiceUnderTest;

    @BeforeMethod
    public void setUp() {
        authServiceMock = mock(AuthService.class);
        authController = new AuthController(authServiceMock);

        userRepositoryMock = mock(UserRepository.class);
        passwordEncoderMock = mock(PasswordEncoder.class);
        sessionTokenServiceMock = mock(SessionTokenService.class);
        googleTokenVerifierServiceMock = mock(GoogleTokenVerifierService.class);

        authServiceUnderTest = new AuthService(
                userRepositoryMock,
                passwordEncoderMock,
                sessionTokenServiceMock,
                googleTokenVerifierServiceMock
        );
    }

    // =========================================================================
    // 1. AuthController.registerUser(...) -> V(G) = 4 (3 decisiones)
    // =========================================================================

    @Test(description = "Camino C1 (TestNG): Username no nulo (trim), Email no nulo (trim+lower), Servicio exitoso -> 201 CREATED")
    public void camino1_datosCompletos_registroExitoso() {
        SignupRequest request = new SignupRequest();
        request.setUsername("  juanperez  ");
        request.setEmail("  JUAN@example.com  ");
        request.setPassword("SecurePass123!");

        ResponseEntity<Void> response = authController.registerUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(authServiceMock).registerUser(request);
    }

    @Test(description = "Camino C2 (TestNG): Username nulo (fallback \"\"), Email válido, Servicio exitoso -> 201 CREATED")
    public void camino2_usernameNulo_registroExitoso() {
        SignupRequest request = new SignupRequest();
        request.setUsername(null);
        request.setEmail("test@example.com");
        request.setPassword("SecurePass123!");

        ResponseEntity<Void> response = authController.registerUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(authServiceMock).registerUser(request);
    }

    @Test(description = "Camino C3 (TestNG): Username válido, Email nulo (fallback \"\"), Servicio exitoso -> 201 CREATED")
    public void camino3_emailNulo_registroExitoso() {
        SignupRequest request = new SignupRequest();
        request.setUsername("juanperez");
        request.setEmail(null);
        request.setPassword("SecurePass123!");

        ResponseEntity<Void> response = authController.registerUser(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(authServiceMock).registerUser(request);
    }

    @Test(description = "Camino C4 (TestNG): Flujo con excepción en AuthService -> Captura en catch y relanza la excepción")
    public void camino4_servicioLanzaExcepcion_capturaYRelanza() {
        SignupRequest request = new SignupRequest();
        request.setUsername("juanperez");
        request.setEmail("juan@example.com");
        request.setPassword("SecurePass123!");

        doThrow(new UserAlreadyExistsException("Error: El nombre de usuario ya está en uso"))
                .when(authServiceMock).registerUser(request);

        assertThatThrownBy(() -> authController.registerUser(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("Error: El nombre de usuario ya está en uso");

        verify(authServiceMock).registerUser(request);
    }

    // =========================================================================
    // 2. AuthService.registerUser(...) -> V(G) = 3 (2 decisiones)
    // =========================================================================

    @Test(description = "Camino S1 (TestNG): Username ya existe (Decision 1 = True) -> Lanza UserAlreadyExistsException")
    public void camino1_usernameYaExiste_lanzaExcepcion() {
        SignupRequest request = new SignupRequest();
        request.setUsername("  CarlosM  ");
        request.setEmail("  Carlos@Example.COM  ");
        request.setPassword("Password2026!");

        when(userRepositoryMock.existsByUsernameIgnoreCase("CarlosM")).thenReturn(true);

        assertThatThrownBy(() -> authServiceUnderTest.registerUser(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("Error: El nombre de usuario ya está en uso");

        verify(userRepositoryMock).existsByUsernameIgnoreCase("CarlosM");
        verify(userRepositoryMock, never()).existsByEmailIgnoreCase(any());
        verify(userRepositoryMock, never()).save(any());
    }

    @Test(description = "Camino S2 (TestNG): Username disponible, Email ya existe (Decision 1 = False, Decision 2 = True) -> Lanza UserAlreadyExistsException")
    public void camino2_emailYaExiste_lanzaExcepcion() {
        SignupRequest request = new SignupRequest();
        request.setUsername("  CarlosM  ");
        request.setEmail("  Carlos@Example.COM  ");
        request.setPassword("Password2026!");

        when(userRepositoryMock.existsByUsernameIgnoreCase("CarlosM")).thenReturn(false);
        when(userRepositoryMock.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authServiceUnderTest.registerUser(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("Error: El correo electrónico ya está registrado");

        verify(userRepositoryMock).existsByUsernameIgnoreCase("CarlosM");
        verify(userRepositoryMock).existsByEmailIgnoreCase("carlos@example.com");
        verify(userRepositoryMock, never()).save(any());
    }

    @Test(description = "Camino S3 (TestNG): Username y Email disponibles (Decision 1 = False, Decision 2 = False) -> Cifra contraseña y guarda usuario")
    public void camino3_registroExitoso_guardaUsuario() {
        SignupRequest request = new SignupRequest();
        request.setUsername("  CarlosM  ");
        request.setEmail("  Carlos@Example.COM  ");
        request.setPassword("Password2026!");

        when(userRepositoryMock.existsByUsernameIgnoreCase("CarlosM")).thenReturn(false);
        when(userRepositoryMock.existsByEmailIgnoreCase("carlos@example.com")).thenReturn(false);
        when(passwordEncoderMock.encode("Password2026!")).thenReturn("$2a$10$hashedPassword123");

        authServiceUnderTest.registerUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepositoryMock).save(userCaptor.capture());

        User userGuardado = userCaptor.getValue();
        assertThat(userGuardado.getUsername()).isEqualTo("CarlosM");
        assertThat(userGuardado.getEmail()).isEqualTo("carlos@example.com");
        assertThat(userGuardado.getPasswordHash()).isEqualTo("$2a$10$hashedPassword123");
    }
}
