package com.easyschedule.backend.estudiante.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.repositories.UserRepository;
import com.easyschedule.backend.auth.service.AuthService;
import com.easyschedule.backend.estudiante.dto.EstudianteResponse;
import com.easyschedule.backend.estudiante.dto.EstudianteUpdateRequest;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import com.easyschedule.backend.shared.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas unitarias para la funcionalidad de actualización administrativa de estudiantes
 * en EstudianteService.
 *
 * Mapeo exhaustivo de caminos basados en Complejidad Ciclomática V(G):
 * Método bajo prueba: EstudianteService.update(Long id, EstudianteUpdateRequest request)
 *
 * Caminos evaluados:
 * 1. Existencia del Estudiante:
 *    - Camino E1: Estudiante encontrado por ID -> Continúa el flujo de actualización.
 *    - Camino E2: Estudiante inexistente -> Lanza ResourceNotFoundException("Estudiante no encontrado con id: ...").
 *
 * 2. Resolución de Malla Curricular:
 *    - Camino M1: request.mallaId() != null y existe en MallaRepository -> Asocia la Malla recuperada.
 *    - Camino M2: request.mallaId() != null pero NO existe en BD -> Lanza ResourceNotFoundException("Malla no encontrada con id: ...").
 *    - Camino M3: request.mallaId() == null -> Asigna null a la malla sin consultar el repositorio.
 *
 * 3. Normalización y Formateo de Datos:
 *    - Nombre y apellido formateados con Capital Case (ej. "  juan   carlos  " -> "Juan Carlos").
 *    - Carnet normalizado con espacios compactados y en mayúsculas (" 123456 lp " -> "123456 LP").
 *
 * 4. Integridad de Datos e Inmutabilidad de Campos No Involucrados:
 *    - Garantiza que id, fechaRegistro, tourCompleted y user permanezcan inalterados.
 *
 * 5. Sincronización y Fallback con Entidad User:
 *    - Camino S1: Estudiante con User asociado y campos username/correo en blanco/nulos
 *                 -> Se sincronizan con los valores de User.
 *    - Camino S2: Estudiante con User asociado y campos username/correo ya definidos
 *                 -> No se sobrescriben.
 *    - Camino S3: Estudiante sin User asociado (user == null)
 *                 -> No se produce NullPointerException ni alteración errónea.
 *
 * 6. Cálculo del estado del perfil (isProfileCompleted):
 *    - Camino P1: Todos los campos obligatorios completos -> profileCompleted = true.
 *    - Camino P2: Algún campo obligatorio faltante o en blanco -> profileCompleted = false.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EstudianteService - Pruebas Unitarias de Actualización Administrativa y Complejidad Ciclomática")
class EstudianteUpdateServiceTest {

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

    private User sampleUser;
    private Estudiante existingEstudiante;
    private Malla sampleMalla;
    private OffsetDateTime fixedFechaRegistro;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(10L);
        sampleUser.setUsername("user_sistema");
        sampleUser.setEmail("user_sistema@mail.com");

        sampleMalla = new Malla();
        sampleMalla.setId(100L);

        fixedFechaRegistro = OffsetDateTime.parse("2024-01-10T09:00:00Z");

        existingEstudiante = new Estudiante();
        existingEstudiante.setId(10L);
        existingEstudiante.setUsername("carlos.rojas");
        existingEstudiante.setCorreo("carlos.rojas@mail.com");
        existingEstudiante.setNombre("Carlos");
        existingEstudiante.setApellido("Rojas");
        existingEstudiante.setCarnetIdentidad("555444 LP");
        existingEstudiante.setFechaNacimiento(LocalDate.of(2001, 8, 20));
        existingEstudiante.setFechaRegistro(fixedFechaRegistro);
        existingEstudiante.setSemestreActual((short) 2);
        existingEstudiante.setUniversidadId(1L);
        existingEstudiante.setCarreraId(2L);
        existingEstudiante.setMalla(null);
        existingEstudiante.setProfileCompleted(true);
        existingEstudiante.setTourCompleted(true);
        existingEstudiante.setUser(sampleUser);
    }

    // =========================================================================
    // 1. Caminos de Búsqueda y Manejo de Errores por Inexistencia
    // =========================================================================
    @Nested
    @DisplayName("Control de Inexistencia y Excepciones")
    class InexistenciaYExcepcionesTests {

        @Test
        @DisplayName("Camino E2: Estudiante a actualizar no existe -> Lanza ResourceNotFoundException")
        void update_cuandoEstudianteNoExiste_lanzaResourceNotFoundException() {
            when(estudianteRepository.findById(99L)).thenReturn(Optional.empty());

            EstudianteUpdateRequest request = new EstudianteUpdateRequest(
                    "Carlos", "Rojas", "123456 LP",
                    LocalDate.of(2000, 1, 1), (short) 3,
                    1L, 2L, null
            );

            assertThatThrownBy(() -> estudianteService.update(99L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Estudiante no encontrado con id: 99");

            verify(estudianteRepository).findById(99L);
            verify(estudianteRepository, never()).save(any());
            verify(mallaRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Camino M2: Malla especificada en request no existe -> Lanza ResourceNotFoundException")
        void update_cuandoMallaNoExiste_lanzaResourceNotFoundException() {
            when(estudianteRepository.findById(10L)).thenReturn(Optional.of(existingEstudiante));
            when(mallaRepository.findById(888L)).thenReturn(Optional.empty());

            EstudianteUpdateRequest request = new EstudianteUpdateRequest(
                    "Carlos", "Rojas", "123456 LP",
                    LocalDate.of(2000, 1, 1), (short) 3,
                    1L, 2L, 888L
            );

            assertThatThrownBy(() -> estudianteService.update(10L, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Malla no encontrada con id: 888");

            verify(estudianteRepository).findById(10L);
            verify(mallaRepository).findById(888L);
            verify(estudianteRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 2. Actualización Exitosa y Asociación de Malla
    // =========================================================================
    @Nested
    @DisplayName("Actualización Exitosa con Malla")
    class ActualizacionExitosaTests {

        @Test
        @DisplayName("Camino M1: Actualización con mallaId válido -> Asocia malla y persiste los cambios")
        void update_conMallaExistente_actualizaYPersisteCorrectamente() {
            when(estudianteRepository.findById(10L)).thenReturn(Optional.of(existingEstudiante));
            when(mallaRepository.findById(100L)).thenReturn(Optional.of(sampleMalla));
            when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EstudianteUpdateRequest request = new EstudianteUpdateRequest(
                    "  mario   alberto  ",
                    "  gomez   torres  ",
                    " 8765432-1a cb ",
                    LocalDate.of(2002, 5, 14),
                    (short) 5,
                    3L,
                    4L,
                    100L
            );

            EstudianteResponse response = estudianteService.update(10L, request);

            ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
            verify(estudianteRepository).save(captor.capture());
            Estudiante actualizado = captor.getValue();

            // Verificación de datos actualizados y formateados
            assertThat(actualizado.getNombre()).isEqualTo("Mario Alberto");
            assertThat(actualizado.getApellido()).isEqualTo("Gomez Torres");
            assertThat(actualizado.getCarnetIdentidad()).isEqualTo("8765432-1A CB");
            assertThat(actualizado.getFechaNacimiento()).isEqualTo(LocalDate.of(2002, 5, 14));
            assertThat(actualizado.getSemestreActual()).isEqualTo((short) 5);
            assertThat(actualizado.getUniversidadId()).isEqualTo(3L);
            assertThat(actualizado.getCarreraId()).isEqualTo(4L);
            assertThat(actualizado.getMalla()).isSameAs(sampleMalla);
            assertThat(actualizado.isProfileCompleted()).isTrue();

            // Verificación de respuesta DTO
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.nombre()).isEqualTo("Mario Alberto");
            assertThat(response.mallaId()).isEqualTo(100L);
            assertThat(response.profileCompleted()).isTrue();
        }

        @Test
        @DisplayName("Camino M3: Actualización con mallaId null -> Asigna malla null sin invocar MallaRepository")
        void update_conMallaIdNull_asignaMallaNull() {
            existingEstudiante.setMalla(sampleMalla); // previamente tenía malla
            when(estudianteRepository.findById(10L)).thenReturn(Optional.of(existingEstudiante));
            when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EstudianteUpdateRequest request = new EstudianteUpdateRequest(
                    "Carlos", "Rojas", "555444 LP",
                    LocalDate.of(2001, 8, 20), (short) 3,
                    1L, 2L, null
            );

            EstudianteResponse response = estudianteService.update(10L, request);

            verify(mallaRepository, never()).findById(any());
            verify(estudianteRepository).save(existingEstudiante);
            assertThat(existingEstudiante.getMalla()).isNull();
            assertThat(response.mallaId()).isNull();
        }
    }

    // =========================================================================
    // 3. Integridad de Datos e Inmutabilidad de Campos No Involucrados
    // =========================================================================
    @Nested
    @DisplayName("Integridad e Inmutabilidad de Campos")
    class IntegridadDatosTests {

        @Test
        @DisplayName("Comprueba que id, fechaRegistro, tourCompleted y user permanezcan inalterados")
        void update_conservaCamposNoInvolucradosEnActualizacion() {
            when(estudianteRepository.findById(10L)).thenReturn(Optional.of(existingEstudiante));
            when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EstudianteUpdateRequest request = new EstudianteUpdateRequest(
                    "NuevoNombre", "NuevoApellido", "999888 SC",
                    LocalDate.of(1999, 12, 31), (short) 6,
                    5L, 6L, null
            );

            estudianteService.update(10L, request);

            ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
            verify(estudianteRepository).save(captor.capture());
            Estudiante actualizado = captor.getValue();

            // Los campos inmutables deben mantenerse idénticos a los originales
            assertThat(actualizado.getId()).isEqualTo(10L);
            assertThat(actualizado.getFechaRegistro()).isEqualTo(fixedFechaRegistro);
            assertThat(actualizado.isTourCompleted()).isTrue();
            assertThat(actualizado.getUser()).isSameAs(sampleUser);
        }
    }

    // =========================================================================
    // 4. Sincronización y Fallback con Entidad User
    // =========================================================================
    @Nested
    @DisplayName("Sincronización de Identidad con Entidad User")
    class SincronizacionUserTests {

        @Test
        @DisplayName("Camino S1: Estudiante con username y correo nulos/en blanco -> Fallback a datos del User")
        void update_cuandoUsernameYCorreoEnBlanco_fallbackADatosDeUser() {
            existingEstudiante.setUsername(null);
            existingEstudiante.setCorreo("   ");
            when(estudianteRepository.findById(10L)).thenReturn(Optional.of(existingEstudiante));
            when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EstudianteUpdateRequest request = new EstudianteUpdateRequest(
                    "Carlos", "Rojas", "555444 LP",
                    LocalDate.of(2001, 8, 20), (short) 2,
                    1L, 2L, null
            );

            estudianteService.update(10L, request);

            assertThat(existingEstudiante.getUsername()).isEqualTo("user_sistema");
            assertThat(existingEstudiante.getCorreo()).isEqualTo("user_sistema@mail.com");
        }

        @Test
        @DisplayName("Camino S2: Estudiante con username y correo ya poblados -> No los sobrescribe con User")
        void update_cuandoUsernameYCorreoYaExisten_mantieneValoresPropios() {
            existingEstudiante.setUsername("propio.username");
            existingEstudiante.setCorreo("propio@correo.com");
            when(estudianteRepository.findById(10L)).thenReturn(Optional.of(existingEstudiante));
            when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EstudianteUpdateRequest request = new EstudianteUpdateRequest(
                    "Carlos", "Rojas", "555444 LP",
                    LocalDate.of(2001, 8, 20), (short) 2,
                    1L, 2L, null
            );

            estudianteService.update(10L, request);

            assertThat(existingEstudiante.getUsername()).isEqualTo("propio.username");
            assertThat(existingEstudiante.getCorreo()).isEqualTo("propio@correo.com");
        }

        @Test
        @DisplayName("Camino S3: Estudiante sin User asociado -> No genera error ni sobrescritura")
        void update_cuandoUserEsNull_noGeneraError() {
            existingEstudiante.setUser(null);
            existingEstudiante.setUsername("sin_user");
            existingEstudiante.setCorreo("sin_user@mail.com");
            when(estudianteRepository.findById(10L)).thenReturn(Optional.of(existingEstudiante));
            when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EstudianteUpdateRequest request = new EstudianteUpdateRequest(
                    "Carlos", "Rojas", "555444 LP",
                    LocalDate.of(2001, 8, 20), (short) 2,
                    1L, 2L, null
            );

            estudianteService.update(10L, request);

            assertThat(existingEstudiante.getUsername()).isEqualTo("sin_user");
            assertThat(existingEstudiante.getCorreo()).isEqualTo("sin_user@mail.com");
        }
    }

    // =========================================================================
    // 5. Verificación de Estado del Perfil (isProfileCompleted)
    // =========================================================================
    @Nested
    @DisplayName("Cálculo de Completitud de Perfil")
    class ProfileCompletedTests {

        @Test
        @DisplayName("Camino P2: Si algún dato obligatorio queda nulo o en blanco -> profileCompleted es false")
        void update_cuandoFaltanDatosObligatorios_profileCompletedEsFalse() {
            existingEstudiante.setFechaNacimiento(null);
            when(estudianteRepository.findById(10L)).thenReturn(Optional.of(existingEstudiante));
            when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Solicitud sin fecha de nacimiento
            EstudianteUpdateRequest request = new EstudianteUpdateRequest(
                    "Carlos", "Rojas", "555444 LP",
                    null, (short) 2,
                    1L, 2L, null
            );

            estudianteService.update(10L, request);

            assertThat(existingEstudiante.isProfileCompleted()).isFalse();
        }
    }
}
