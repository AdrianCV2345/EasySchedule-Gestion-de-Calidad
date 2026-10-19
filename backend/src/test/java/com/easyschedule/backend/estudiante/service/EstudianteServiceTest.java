package com.easyschedule.backend.estudiante.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.repositories.UserRepository;
import com.easyschedule.backend.auth.service.AuthService;
import com.easyschedule.backend.estudiante.dto.EstudianteResponse;
import com.easyschedule.backend.estudiante.dto.PerfilUpdateRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

    private User user7() {
        return usuario(7L, "estudiante7", "est7@correo.com");
    }

    private Estudiante estudiante7Completo(User user) {
        Estudiante estudiante = estudiante(7L, user);
        estudiante.setNombre("Carlos");
        estudiante.setApellido("Quispe");
        estudiante.setCarnetIdentidad("7654321");
        estudiante.setFechaNacimiento(LocalDate.of(2000, 1, 1));
        return estudiante;
    }

    private void stubGetOrCreateSincronizado(User user, Estudiante estudiante) {
        when(userRepository.findByUsernameIgnoreCase("estudiante7")).thenReturn(Optional.of(user));
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
    }

    private PerfilUpdateRequest perfil(String username, String nombre, String apellido,
            String email, String carnet, LocalDate fecha) {
        return new PerfilUpdateRequest(username, nombre, apellido, email, carnet, fecha, null, null);
    }

    private void stubSaveEstudiante() {
        when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("HU16-03 datos validos con normalizacion: perfil completo y guarda User y Estudiante")
    void updateProfileNormalizaDatosYMarcaPerfilCompleto() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        when(estudianteRepository.existsByCarnetIdentidadIgnoreCase("7654321 LP")).thenReturn(false);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("estudiante7", "  mARIA   lOS ANGELES ",
                " qUISPE  mamani ", " EST7@CORREO.COM ", " 7654321 lp ", LocalDate.of(1999, 5, 20));

        EstudianteResponse response = estudianteService.updateProfile("estudiante7", request);

        assertEquals("Maria Los Angeles", estudiante.getNombre());
        assertEquals("Quispe Mamani", estudiante.getApellido());
        assertEquals("est7@correo.com", user.getEmail());
        assertEquals("est7@correo.com", estudiante.getCorreo());
        assertEquals("7654321 LP", estudiante.getCarnetIdentidad());
        assertEquals(LocalDate.of(1999, 5, 20), estudiante.getFechaNacimiento());
        assertThat(estudiante.isProfileCompleted()).isTrue();
        verify(userRepository).save(user);
        verify(estudianteRepository).save(estudiante);
        assertEquals("Maria Los Angeles", response.nombre());
        assertEquals("7654321 LP", response.carnetIdentidad());
        assertThat(response.profileCompleted()).isTrue();
    }

    @Test
    @DisplayName("HU16-04 username correo y CI sin cambios: omite consultas de duplicados y guarda")
    void updateProfileSinCambiosDeIdentidadOmiteConsultasDeDuplicados() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("estudiante7", "carlos", "quispe",
                "est7@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        EstudianteResponse response = estudianteService.updateProfile("estudiante7", request);

        assertEquals("Carlos", estudiante.getNombre());
        assertEquals("Quispe", estudiante.getApellido());
        assertThat(estudiante.isProfileCompleted()).isTrue();
        verify(userRepository, never()).existsByUsernameIgnoreCase(anyString());
        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
        verify(estudianteRepository, never()).existsByUsernameIgnoreCase(anyString());
        verify(estudianteRepository, never()).existsByCorreoIgnoreCase(anyString());
        verify(estudianteRepository, never()).existsByCarnetIdentidadIgnoreCase(anyString());
        verify(userRepository).save(user);
        verify(estudianteRepository).save(estudiante);
        assertEquals("estudiante7", response.username());
    }

    @Test
    @DisplayName("HU16-05 nuevo username existente en UserRepository: HTTP 409 sin guardado final")
    void updateProfileUsernameDuplicadoEnUserRepoConflict() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        when(userRepository.existsByUsernameIgnoreCase("nuevoUsuario")).thenReturn(true);

        PerfilUpdateRequest request = perfil("nuevoUsuario", "Carlos", "Quispe",
                "est7@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> estudianteService.updateProfile("estudiante7", request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Error: El nombre de usuario ya está en uso", ex.getReason());
        verify(userRepository, never()).save(any(User.class));
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    @DisplayName("HU16-06 username disponible en UserRepo pero existente en EstudianteRepository: HTTP 409")
    void updateProfileUsernameDuplicadoEnEstudianteRepoConflict() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        when(userRepository.existsByUsernameIgnoreCase("nuevoUsuario")).thenReturn(false);
        when(estudianteRepository.existsByUsernameIgnoreCase("nuevoUsuario")).thenReturn(true);

        PerfilUpdateRequest request = perfil("nuevoUsuario", "Carlos", "Quispe",
                "est7@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> estudianteService.updateProfile("estudiante7", request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    @DisplayName("HU16-07 nuevo correo existente en UserRepository: HTTP 409 sin guardado final")
    void updateProfileEmailDuplicadoEnUserRepoConflict() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        when(userRepository.existsByEmailIgnoreCase("nuevo@correo.com")).thenReturn(true);

        PerfilUpdateRequest request = perfil("estudiante7", "Carlos", "Quispe",
                "nuevo@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> estudianteService.updateProfile("estudiante7", request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Error: El correo electrónico ya está registrado", ex.getReason());
        verify(userRepository, never()).save(any(User.class));
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    @DisplayName("HU16-08 correo disponible en UserRepo pero existente en EstudianteRepository: HTTP 409")
    void updateProfileEmailDuplicadoEnEstudianteRepoConflict() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        when(userRepository.existsByEmailIgnoreCase("nuevo@correo.com")).thenReturn(false);
        when(estudianteRepository.existsByCorreoIgnoreCase("nuevo@correo.com")).thenReturn(true);

        PerfilUpdateRequest request = perfil("estudiante7", "Carlos", "Quispe",
                "nuevo@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> estudianteService.updateProfile("estudiante7", request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    @DisplayName("HU16-09 CI anterior null y nuevo CI registrado en otro estudiante: HTTP 409")
    void updateProfileCarnetDuplicadoConAnteriorNullConflict() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        estudiante.setCarnetIdentidad(null);
        stubGetOrCreateSincronizado(user, estudiante);
        when(estudianteRepository.existsByCarnetIdentidadIgnoreCase("7654321 LP")).thenReturn(true);

        PerfilUpdateRequest request = perfil("estudiante7", "Carlos", "Quispe",
                "est7@correo.com", "7654321 LP", LocalDate.of(2000, 1, 1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> estudianteService.updateProfile("estudiante7", request));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Error: El carnet de identidad ya está en uso", ex.getReason());
        verify(userRepository, never()).save(any(User.class));
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    @DisplayName("HU16-10 identificador de usuario inexistente: ResourceNotFoundException sin guardado")
    void updateProfileUsuarioInexistenteLanzaResourceNotFound() {
        when(userRepository.findByUsernameIgnoreCase("fantasma")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("fantasma")).thenReturn(Optional.empty());

        PerfilUpdateRequest request = perfil("fantasma", "Carlos", "Quispe",
                "nuevo@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        assertThrows(ResourceNotFoundException.class,
                () -> estudianteService.updateProfile("fantasma", request));

        verify(userRepository, never()).save(any(User.class));
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    @DisplayName("HU16-11 nombre null: perfil incompleto y nombre null")
    void updateProfileNombreNullPerfilIncompleto() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("estudiante7", null, "Quispe",
                "est7@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        estudianteService.updateProfile("estudiante7", request);

        assertNull(estudiante.getNombre());
        assertFalse(estudiante.isProfileCompleted());
    }

    @Test
    @DisplayName("HU16-12 nombre en blanco: normalizado a vacio y perfil incompleto")
    void updateProfileNombreEnBlancoPerfilIncompleto() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("estudiante7", "   ", "Quispe",
                "est7@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        estudianteService.updateProfile("estudiante7", request);

        assertEquals("", estudiante.getNombre());
        assertFalse(estudiante.isProfileCompleted());
    }

    @Test
    @DisplayName("HU16-13 apellido null: perfil incompleto")
    void updateProfileApellidoNullPerfilIncompleto() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("estudiante7", "Carlos", null,
                "est7@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        estudianteService.updateProfile("estudiante7", request);

        assertNull(estudiante.getApellido());
        assertFalse(estudiante.isProfileCompleted());
    }

    @Test
    @DisplayName("HU16-14 apellido en blanco: normalizado a vacio y perfil incompleto")
    void updateProfileApellidoEnBlancoPerfilIncompleto() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("estudiante7", "Carlos", "   ",
                "est7@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        estudianteService.updateProfile("estudiante7", request);

        assertEquals("", estudiante.getApellido());
        assertFalse(estudiante.isProfileCompleted());
    }

    @Test
    @DisplayName("HU16-15 username en blanco: normalizado a vacio y perfil incompleto")
    void updateProfileUsernameEnBlancoPerfilIncompleto() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        when(userRepository.existsByUsernameIgnoreCase("")).thenReturn(false);
        when(estudianteRepository.existsByUsernameIgnoreCase("")).thenReturn(false);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("   ", "Carlos", "Quispe",
                "est7@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        estudianteService.updateProfile("estudiante7", request);

        assertEquals("", user.getUsername());
        assertEquals("", estudiante.getUsername());
        assertFalse(estudiante.isProfileCompleted());
    }

    @Test
    @DisplayName("HU16-16 correo en blanco: normalizado a vacio y perfil incompleto")
    void updateProfileEmailEnBlancoPerfilIncompleto() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        when(userRepository.existsByEmailIgnoreCase("")).thenReturn(false);
        when(estudianteRepository.existsByCorreoIgnoreCase("")).thenReturn(false);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("estudiante7", "Carlos", "Quispe",
                "   ", "7654321", LocalDate.of(2000, 1, 1));

        estudianteService.updateProfile("estudiante7", request);

        assertEquals("", user.getEmail());
        assertEquals("", estudiante.getCorreo());
        assertFalse(estudiante.isProfileCompleted());
    }

    @Test
    @DisplayName("HU16-17 carnet null: CI null y perfil incompleto")
    void updateProfileCarnetNullPerfilIncompleto() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        when(estudianteRepository.existsByCarnetIdentidadIgnoreCase(null)).thenReturn(false);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("estudiante7", "Carlos", "Quispe",
                "est7@correo.com", null, LocalDate.of(2000, 1, 1));

        estudianteService.updateProfile("estudiante7", request);

        assertNull(estudiante.getCarnetIdentidad());
        assertFalse(estudiante.isProfileCompleted());
    }

    @Test
    @DisplayName("HU16-18 carnet en blanco: normalizado a vacio y perfil incompleto")
    void updateProfileCarnetEnBlancoPerfilIncompleto() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        when(estudianteRepository.existsByCarnetIdentidadIgnoreCase("")).thenReturn(false);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("estudiante7", "Carlos", "Quispe",
                "est7@correo.com", "   ", LocalDate.of(2000, 1, 1));

        estudianteService.updateProfile("estudiante7", request);

        assertEquals("", estudiante.getCarnetIdentidad());
        assertFalse(estudiante.isProfileCompleted());
    }

    @Test
    @DisplayName("HU16-19 fechaNacimiento null: perfil incompleto")
    void updateProfileFechaNacimientoNullPerfilIncompleto() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        stubSaveEstudiante();

        PerfilUpdateRequest request = perfil("estudiante7", "Carlos", "Quispe",
                "est7@correo.com", "7654321", null);

        estudianteService.updateProfile("estudiante7", request);

        assertNull(estudiante.getFechaNacimiento());
        assertFalse(estudiante.isProfileCompleted());
    }

    @Test
    @DisplayName("HU16-20 userRepository.save lanza IllegalStateException: se propaga y no guarda Estudiante")
    void updateProfileFalloDePersistenciaUserSePropaga() {
        User user = user7();
        Estudiante estudiante = estudiante7Completo(user);
        stubGetOrCreateSincronizado(user, estudiante);
        when(userRepository.save(any(User.class))).thenThrow(new IllegalStateException("fallo de persistencia"));

        PerfilUpdateRequest request = perfil("estudiante7", "Carlos", "Quispe",
                "est7@correo.com", "7654321", LocalDate.of(2000, 1, 1));

        assertThrows(IllegalStateException.class,
                () -> estudianteService.updateProfile("estudiante7", request));

        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    // ==================================================================
    // HU#18 — Marcar tour de bienvenida como completado
    // ==================================================================

    @Test
    @DisplayName("HU18-01 estudiante existente: marca tour como completado y lo persiste")
    void completeTourEstudianteExistenteMarcaTourCompletado() {
        User user = usuario(1L, "juan", "juan@correo.com");
        Estudiante existente = estudiante(1L, user);
        existente.setTourCompleted(false);
        when(userRepository.findByUsernameIgnoreCase("juan")).thenReturn(Optional.of(user));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> {
            Estudiante guardado = invocation.getArgument(0);
            guardado.setId(1L);
            return guardado;
        });

        EstudianteResponse response = estudianteService.completeTour("juan");

        assertThat(response.tourCompleted()).isTrue();
        assertThat(response.id()).isEqualTo(1L);

        ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
        verify(estudianteRepository).save(captor.capture());
        Estudiante guardado = captor.getValue();
        assertThat(guardado.isTourCompleted()).isTrue();
    }

    @Test
    @DisplayName("HU18-02 usuario inexistente: lanza ResourceNotFoundException")
    void completeTourUsuarioInexistenteLanzaExcepcion() {
        when(userRepository.findByUsernameIgnoreCase("fantasma")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("fantasma")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
            () -> estudianteService.completeTour("fantasma"));

        verify(userRepository).findByUsernameIgnoreCase("fantasma");
        verify(userRepository).findByEmailIgnoreCase("fantasma");
        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    @DisplayName("HU18-03 perfil inexistente: crea nuevo estudiante y marca tour completado")
    void completeTourPerfilInexistenteCreaYMarcaTour() {
        User user = usuario(1L, "nuevo", "nuevo@correo.com");
        when(userRepository.findByUsernameIgnoreCase("nuevo")).thenReturn(Optional.of(user));
        when(estudianteRepository.findById(1L)).thenReturn(Optional.empty());
        when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> {
            Estudiante creado = invocation.getArgument(0);
            creado.setId(1L);
            return creado;
        });

        EstudianteResponse response = estudianteService.completeTour("nuevo");

        assertThat(response.tourCompleted()).isTrue();
        assertThat(response.id()).isEqualTo(1L);

        ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
        verify(estudianteRepository, times(2)).save(captor.capture());
        Estudiante guardado = captor.getValue();
        assertThat(guardado.isTourCompleted()).isTrue();
        assertThat(guardado.getUsername()).isEqualTo("nuevo");
        assertThat(guardado.getCorreo()).isEqualTo("nuevo@correo.com");
    }
}
