package com.easyschedule.backend.estudiante.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.repositories.UserRepository;
import com.easyschedule.backend.auth.service.AuthService;
import com.easyschedule.backend.estudiante.dto.EstudianteResponse;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import com.easyschedule.backend.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstudianteService - pruebas unitarias (HU15: consulta y control de acceso al perfil)")
class EstudianteServiceTest {

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private MallaRepository mallaRepository;

    @Mock
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EstudianteService estudianteService;

    private static User usuario(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }

    private static Estudiante estudiante(Long id, User user) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(id);
        estudiante.setUsername(user.getUsername());
        estudiante.setCorreo(user.getEmail());
        estudiante.setUser(user);
        return estudiante;
    }

    @Test
    @DisplayName("ES-01 usuario localizado por username y propietario correcto: acceso permitido")
    void canAccessProfilePorUsernamePropietarioPermite() {
        User user = usuario(1L, "juan", "juan@correo.com");
        when(userRepository.findByUsernameIgnoreCase("juan")).thenReturn(Optional.of(user));

        boolean permitido = estudianteService.canAccessProfile("juan", 1L);

        assertThat(permitido).isTrue();
        verify(userRepository).findByUsernameIgnoreCase("juan");
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    @DisplayName("ES-02 username no encontrado pero localizado por email: se usa la busqueda alternativa")
    void canAccessProfilePorEmailPropietarioPermite() {
        User user = usuario(1L, "juan", "juan@correo.com");
        when(userRepository.findByUsernameIgnoreCase("juan@correo.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("juan@correo.com")).thenReturn(Optional.of(user));

        boolean permitido = estudianteService.canAccessProfile("juan@correo.com", 1L);

        assertThat(permitido).isTrue();
        verify(userRepository).findByUsernameIgnoreCase("juan@correo.com");
        verify(userRepository).findByEmailIgnoreCase("juan@correo.com");
    }

    @Test
    @DisplayName("ES-03 usuario localizado con id distinto al autenticado: acceso denegado")
    void canAccessProfileConIdDistintoDeniega() {
        User user = usuario(2L, "juan", "juan@correo.com");
        when(userRepository.findByUsernameIgnoreCase("juan")).thenReturn(Optional.of(user));

        boolean permitido = estudianteService.canAccessProfile("juan", 1L);

        assertThat(permitido).isFalse();
    }

    @Test
    @DisplayName("ES-04 perfil existente: devuelve el perfil sin ejecutar save")
    void findByUsernameConPerfilExistenteNoGuarda() {
        User user = usuario(1L, "juan", "juan@correo.com");
        Estudiante existente = estudiante(1L, user);
        when(userRepository.findByUsernameIgnoreCase("juan")).thenReturn(Optional.of(user));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(existente));

        EstudianteResponse response = estudianteService.findByUsername("juan");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("juan");
        assertThat(response.email()).isEqualTo("juan@correo.com");
        verify(estudianteRepository).findById(1L);
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    @DisplayName("ES-05 perfil inexistente y usuario existente: crea y persiste el estudiante")
    void findByUsernameSinPerfilCreaYGuarda() {
        User user = usuario(1L, "juan", "juan@correo.com");
        when(userRepository.findByUsernameIgnoreCase("juan")).thenReturn(Optional.of(user));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.empty());
        when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> {
            Estudiante creado = invocation.getArgument(0);
            creado.setId(1L);
            return creado;
        });

        EstudianteResponse response = estudianteService.findByUsername("juan");

        ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
        verify(estudianteRepository).save(captor.capture());
        Estudiante guardado = captor.getValue();
        assertThat(guardado.getUsername()).isEqualTo("juan");
        assertThat(guardado.getCorreo()).isEqualTo("juan@correo.com");
        assertThat(guardado.getUser()).isSameAs(user);
        assertThat(guardado.isProfileCompleted()).isFalse();
        assertThat(guardado.isTourCompleted()).isFalse();
        assertThat(guardado.getFechaRegistro()).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("juan");
        verify(userRepository).findByUsernameIgnoreCase("juan");
        verify(estudianteRepository).findById(1L);
    }

    @Test
    @DisplayName("ES-06 findByUsername con perfil valido: retorna EstudianteResponse con los datos correctos")
    void findByUsernameMapeaPerfilCompleto() {
        User user = usuario(1L, "juan", "juan@correo.com");
        Estudiante existente = estudiante(1L, user);
        existente.setNombre("Juan");
        existente.setApellido("Pérez");
        existente.setCarnetIdentidad("CI123");
        existente.setFechaNacimiento(LocalDate.of(2000, 1, 1));
        existente.setFechaRegistro(OffsetDateTime.parse("2024-01-01T00:00:00Z"));
        existente.setSemestreActual((short) 3);
        existente.setUniversidadId(10L);
        existente.setCarreraId(20L);
        Malla malla = new Malla();
        malla.setId(30L);
        existente.setMalla(malla);
        existente.setProfileCompleted(true);
        existente.setTourCompleted(true);
        when(userRepository.findByUsernameIgnoreCase("juan")).thenReturn(Optional.of(user));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(existente));

        EstudianteResponse response = estudianteService.findByUsername("juan");

        assertThat(response).isEqualTo(new EstudianteResponse(
            1L, "juan", "Juan", "Pérez", "juan@correo.com", "CI123",
            LocalDate.of(2000, 1, 1), OffsetDateTime.parse("2024-01-01T00:00:00Z"),
            (short) 3, 10L, 20L, 30L, true, true));
    }

    @Test
    @DisplayName("ES-07 findByUsername sin resultado: lanza ResourceNotFoundException")
    void findByUsernameSinUsuarioLanzaExcepcion() {
        when(userRepository.findByUsernameIgnoreCase("nadie")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("nadie")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> estudianteService.findByUsername("nadie"));

        verify(userRepository).findByUsernameIgnoreCase("nadie");
        verify(userRepository).findByEmailIgnoreCase("nadie");
    }

    @Test
    @DisplayName("ES-08 [cobertura] perfil existente sin usuario ni datos de contacto: se sincroniza y persiste")
    void findByUsernameSincronizaPerfilIncompleto() {
        User user = usuario(1L, "juan", "juan@correo.com");
        Estudiante existente = new Estudiante();
        existente.setId(1L);
        when(userRepository.findByUsernameIgnoreCase("juan")).thenReturn(Optional.of(user));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(estudianteRepository.save(existente)).thenReturn(existente);

        EstudianteResponse response = estudianteService.findByUsername("juan");

        verify(estudianteRepository).save(existente);
        assertThat(existente.getUser()).isSameAs(user);
        assertThat(existente.getUsername()).isEqualTo("juan");
        assertThat(existente.getCorreo()).isEqualTo("juan@correo.com");
        assertThat(response.username()).isEqualTo("juan");
        assertThat(response.email()).isEqualTo("juan@correo.com");
    }

    @Test
    @DisplayName("ES-09 [cobertura] perfil existente con username y correo distintos: se actualizan y persisten")
    void findByUsernameActualizaDatosDesincronizados() {
        User user = usuario(1L, "juan", "juan@correo.com");
        Estudiante existente = new Estudiante();
        existente.setId(1L);
        existente.setUser(user);
        existente.setUsername("juanviejo");
        existente.setCorreo("viejo@correo.com");
        when(userRepository.findByUsernameIgnoreCase("juan")).thenReturn(Optional.of(user));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(estudianteRepository.save(existente)).thenReturn(existente);

        EstudianteResponse response = estudianteService.findByUsername("juan");

        verify(estudianteRepository).save(existente);
        assertThat(existente.getUsername()).isEqualTo("juan");
        assertThat(existente.getCorreo()).isEqualTo("juan@correo.com");
        assertThat(response.username()).isEqualTo("juan");
        assertThat(response.email()).isEqualTo("juan@correo.com");
    }

    @Test
    @DisplayName("ES-10 [cobertura] fallo al crear el perfil: recupera el registro existente")
    void findByUsernameRecuperaPerfilTrasFalloDeGuardado() {
        User user = usuario(1L, "juan", "juan@correo.com");
        Estudiante recuperado = estudiante(1L, user);
        when(userRepository.findByUsernameIgnoreCase("juan")).thenReturn(Optional.of(user));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.empty());
        when(estudianteRepository.save(any(Estudiante.class))).thenThrow(new RuntimeException("conflicto"));
        when(estudianteRepository.findByUsernameIgnoreCase("juan")).thenReturn(Optional.of(recuperado));

        EstudianteResponse response = estudianteService.findByUsername("juan");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("juan");
        verify(estudianteRepository).findByUsernameIgnoreCase("juan");
    }
}
