package com.easyschedule.backend.estudiante.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.Collections;
import java.util.List;
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
 * Pruebas unitarias de capa de Servicio para la consulta de información de estudiantes
 * en EstudianteService.
 *
 * Mapeo de caminos basados en Complejidad Ciclomática V(G):
 * 
 * 1. findById(id) -> V(G) = 2 (getEstudianteOrThrow: findById present vs empty)
 *    - Camino 1: Estudiante existe -> Retorna EstudianteResponse mapeado con éxito.
 *    - Camino 2: Estudiante no existe -> Lanza ResourceNotFoundException("Estudiante no encontrado con id: ...").
 *
 * 2. findAll() -> V(G) = 1 (flujo directo de stream/map)
 *    - Camino 1: Repositorio contiene entidades -> Lista de DTOs transformados correctamente.
 *    - Camino 2: Repositorio vacío -> Lista vacía.
 *
 * 3. toResponse(estudiante) -> Variantes de Mapeo de datos (username/email fallback desde User y Malla):
 *    - Camino M1: Estudiante con datos directos completos y malla asociada.
 *    - Camino M2: Estudiante con username/email vacíos -> toma fallback desde entidad User asociada.
 *    - Camino M3: Estudiante sin Malla asociada -> mallaId resulta en null.
 *
 * 4. findByUsername(identifier) -> V(G) = 6 (getOrCreateByIdentifier: resolución por username/email,
 *    existencia previa, sincronización de campos y captura de conflicto en guardado):
 *    - Camino P1: Usuario no encontrado ni por username ni por email -> Lanza ResourceNotFoundException.
 *    - Camino P2: Perfil ya existente y sincronizado -> Retorna DTO sin invocar save().
 *    - Camino P3: Perfil ya existente pero desincronizado (user null / username distinto / email distinto)
 *                 -> Sincroniza y persiste cambios en estudianteRepository.save().
 *    - Camino P4: Perfil no existe en student_profiles -> Crea y persiste nueva entidad Estudiante asociada al User.
 *    - Camino P5: Error concurrente al guardar nuevo perfil -> Recupera registro existente alternativo por username/correo.
 *
 * 5. canAccessProfile(identifier, userId) -> V(G) = 3 (username vs email, match de id vs no match):
 *    - Camino A1: Usuario encontrado por username y su ID coincide con el autenticado -> true.
 *    - Camino A2: Usuario no encontrado por username pero sí por email y su ID coincide -> true.
 *    - Camino A3: Usuario no encontrado o con ID diferente -> false.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EstudianteService - Pruebas Unitarias de Consulta y Complejidad Ciclomática")
class EstudianteConsultaServiceTest {

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
    private Estudiante sampleEstudiante;
    private Malla sampleMalla;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(100L);
        sampleUser.setUsername("juanperez");
        sampleUser.setEmail("juan.perez@universidad.edu");

        sampleMalla = new Malla();
        sampleMalla.setId(500L);

        sampleEstudiante = new Estudiante();
        sampleEstudiante.setId(100L);
        sampleEstudiante.setUsername("juanperez");
        sampleEstudiante.setCorreo("juan.perez@universidad.edu");
        sampleEstudiante.setNombre("Juan");
        sampleEstudiante.setApellido("Perez");
        sampleEstudiante.setCarnetIdentidad("CI87654321");
        sampleEstudiante.setFechaNacimiento(LocalDate.of(2002, 3, 15));
        sampleEstudiante.setFechaRegistro(OffsetDateTime.parse("2024-02-01T08:00:00Z"));
        sampleEstudiante.setSemestreActual((short) 4);
        sampleEstudiante.setUniversidadId(10L);
        sampleEstudiante.setCarreraId(20L);
        sampleEstudiante.setMalla(sampleMalla);
        sampleEstudiante.setProfileCompleted(true);
        sampleEstudiante.setTourCompleted(true);
        sampleEstudiante.setUser(sampleUser);
    }

    // =========================================================================
    // 1. findById(id)
    // =========================================================================
    @Nested
    @DisplayName("findById(Long id) - Complejidad V(G) = 2")
    class FindByIdTests {

        @Test
        @DisplayName("Camino 1: Estudiante existente -> Retorna DTO con todos los atributos mapeados")
        void findById_cuandoEstudianteExiste_retornaDto() {
            when(estudianteRepository.findById(100L)).thenReturn(Optional.of(sampleEstudiante));

            EstudianteResponse response = estudianteService.findById(100L);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.username()).isEqualTo("juanperez");
            assertThat(response.nombre()).isEqualTo("Juan");
            assertThat(response.apellido()).isEqualTo("Perez");
            assertThat(response.email()).isEqualTo("juan.perez@universidad.edu");
            assertThat(response.carnetIdentidad()).isEqualTo("CI87654321");
            assertThat(response.fechaNacimiento()).isEqualTo(LocalDate.of(2002, 3, 15));
            assertThat(response.semestreActual()).isEqualTo((short) 4);
            assertThat(response.universidadId()).isEqualTo(10L);
            assertThat(response.carreraId()).isEqualTo(20L);
            assertThat(response.mallaId()).isEqualTo(500L);
            assertThat(response.profileCompleted()).isTrue();
            assertThat(response.tourCompleted()).isTrue();

            verify(estudianteRepository).findById(100L);
        }

        @Test
        @DisplayName("Camino 2: Estudiante inexistente -> Lanza ResourceNotFoundException")
        void findById_cuandoEstudianteNoExiste_lanzaResourceNotFoundException() {
            when(estudianteRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> estudianteService.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Estudiante no encontrado con id: 999");

            verify(estudianteRepository).findById(999L);
        }
    }

    // =========================================================================
    // 2. findAll()
    // =========================================================================
    @Nested
    @DisplayName("findAll() - Consulta y transformación masiva")
    class FindAllTests {

        @Test
        @DisplayName("Camino 1: Repositorio contiene estudiantes -> Transforma colección a EstudianteResponse")
        void findAll_conEstudiantes_retornaListaTransformada() {
            when(estudianteRepository.findAll()).thenReturn(List.of(sampleEstudiante));

            List<EstudianteResponse> list = estudianteService.findAll();

            assertThat(list).hasSize(1);
            assertThat(list.get(0).id()).isEqualTo(100L);
            assertThat(list.get(0).username()).isEqualTo("juanperez");
            assertThat(list.get(0).email()).isEqualTo("juan.perez@universidad.edu");

            verify(estudianteRepository).findAll();
        }

        @Test
        @DisplayName("Camino 2: Repositorio vacío -> Retorna lista vacía sin errores")
        void findAll_sinEstudiantes_retornaListaVacia() {
            when(estudianteRepository.findAll()).thenReturn(Collections.emptyList());

            List<EstudianteResponse> list = estudianteService.findAll();

            assertThat(list).isEmpty();
            verify(estudianteRepository).findAll();
        }
    }

    // =========================================================================
    // 3. toResponse - Variantes de Mapeo de Datos
    // =========================================================================
    @Nested
    @DisplayName("Mapeo de Datos en toResponse")
    class DataMappingTests {

        @Test
        @DisplayName("Mapeo con fallback: username y correo en blanco toman los datos del User asociado")
        void toResponse_conCamposEnBlanco_utilizaFallbackDeUser() {
            Estudiante estudianteConCamposVacios = new Estudiante();
            estudianteConCamposVacios.setId(100L);
            estudianteConCamposVacios.setUsername("   ");
            estudianteConCamposVacios.setCorreo(null);
            estudianteConCamposVacios.setUser(sampleUser);
            estudianteConCamposVacios.setMalla(null);

            when(estudianteRepository.findById(100L)).thenReturn(Optional.of(estudianteConCamposVacios));

            EstudianteResponse response = estudianteService.findById(100L);

            assertThat(response.username()).isEqualTo("juanperez");
            assertThat(response.email()).isEqualTo("juan.perez@universidad.edu");
            assertThat(response.mallaId()).isNull();
        }

        @Test
        @DisplayName("Mapeo sin Malla: mallaId es null cuando la entidad estudiante no tiene malla asociada")
        void toResponse_sinMalla_retornaMallaIdNull() {
            sampleEstudiante.setMalla(null);
            when(estudianteRepository.findById(100L)).thenReturn(Optional.of(sampleEstudiante));

            EstudianteResponse response = estudianteService.findById(100L);

            assertThat(response.mallaId()).isNull();
        }
    }

    // =========================================================================
    // 4. findByUsername(identifier) - Complejidad V(G) = 6
    // =========================================================================
    @Nested
    @DisplayName("findByUsername(identifier) - Resolución, creación y sincronización")
    class FindByUsernameTests {

        @Test
        @DisplayName("Camino P1: Identificador no encontrado en repositorio de usuarios -> ResourceNotFoundException")
        void findByUsername_usuarioNoExiste_lanzaExcepcion() {
            when(userRepository.findByUsernameIgnoreCase("desconocido")).thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase("desconocido")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> estudianteService.findByUsername("desconocido"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Usuario no encontrado con username o correo: desconocido");

            verify(estudianteRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Camino P2: Perfil ya existente y sincronizado -> Retorna DTO sin llamar a save()")
        void findByUsername_perfilSincronizado_noGuarda() {
            when(userRepository.findByUsernameIgnoreCase("juanperez")).thenReturn(Optional.of(sampleUser));
            when(estudianteRepository.findById(100L)).thenReturn(Optional.of(sampleEstudiante));

            EstudianteResponse response = estudianteService.findByUsername("juanperez");

            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.username()).isEqualTo("juanperez");
            verify(estudianteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Camino P3: Perfil existente con datos desincronizados -> Actualiza campos y persiste")
        void findByUsername_perfilDesincronizado_actualizaYPersiste() {
            Estudiante desincronizado = new Estudiante();
            desincronizado.setId(100L);
            desincronizado.setUser(null);
            desincronizado.setUsername("antiguo_username");
            desincronizado.setCorreo("antiguo@correo.com");

            when(userRepository.findByUsernameIgnoreCase("juanperez")).thenReturn(Optional.of(sampleUser));
            when(estudianteRepository.findById(100L)).thenReturn(Optional.of(desincronizado));
            when(estudianteRepository.save(desincronizado)).thenReturn(desincronizado);

            EstudianteResponse response = estudianteService.findByUsername("juanperez");

            assertThat(desincronizado.getUser()).isSameAs(sampleUser);
            assertThat(desincronizado.getUsername()).isEqualTo("juanperez");
            assertThat(desincronizado.getCorreo()).isEqualTo("juan.perez@universidad.edu");
            verify(estudianteRepository).save(desincronizado);
            assertThat(response.username()).isEqualTo("juanperez");
        }

        @Test
        @DisplayName("Camino P4: Perfil inexistente para usuario existente -> Crea perfil y lo persiste")
        void findByUsername_sinPerfilPrevio_creaYPersiste() {
            when(userRepository.findByUsernameIgnoreCase("juanperez")).thenReturn(Optional.of(sampleUser));
            when(estudianteRepository.findById(100L)).thenReturn(Optional.empty());
            when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(invocation -> {
                Estudiante nuevo = invocation.getArgument(0);
                nuevo.setId(100L);
                return nuevo;
            });

            EstudianteResponse response = estudianteService.findByUsername("juanperez");

            ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
            verify(estudianteRepository).save(captor.capture());
            Estudiante creado = captor.getValue();

            assertThat(creado.getUsername()).isEqualTo("juanperez");
            assertThat(creado.getCorreo()).isEqualTo("juan.perez@universidad.edu");
            assertThat(creado.getUser()).isSameAs(sampleUser);
            assertThat(creado.isProfileCompleted()).isFalse();
            assertThat(response.id()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Camino P5: Fallo de concurrencia al crear perfil -> Recupera registro existente por username/email")
        void findByUsername_falloAlGuardar_recuperaExistente() {
            when(userRepository.findByUsernameIgnoreCase("juanperez")).thenReturn(Optional.of(sampleUser));
            when(estudianteRepository.findById(100L)).thenReturn(Optional.empty());
            when(estudianteRepository.save(any(Estudiante.class)))
                    .thenThrow(new RuntimeException("Violación de clave única por concurrencia"));
            when(estudianteRepository.findByUsernameIgnoreCase("juanperez")).thenReturn(Optional.of(sampleEstudiante));

            EstudianteResponse response = estudianteService.findByUsername("juanperez");

            assertThat(response.id()).isEqualTo(100L);
            assertThat(response.username()).isEqualTo("juanperez");
            verify(estudianteRepository).findByUsernameIgnoreCase("juanperez");
        }
    }

    // =========================================================================
    // 5. canAccessProfile(identifier, userId) - Complejidad V(G) = 3
    // =========================================================================
    @Nested
    @DisplayName("canAccessProfile(identifier, userId) - Verificación de autorización")
    class CanAccessProfileTests {

        @Test
        @DisplayName("Camino A1: Usuario encontrado por username y coincide userId -> true")
        void canAccessProfile_porUsernameCoincide_retornaTrue() {
            when(userRepository.findByUsernameIgnoreCase("juanperez")).thenReturn(Optional.of(sampleUser));

            boolean permitido = estudianteService.canAccessProfile("juanperez", 100L);

            assertThat(permitido).isTrue();
            verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        }

        @Test
        @DisplayName("Camino A2: Usuario encontrado por email y coincide userId -> true")
        void canAccessProfile_porEmailCoincide_retornaTrue() {
            when(userRepository.findByUsernameIgnoreCase("juan.perez@universidad.edu")).thenReturn(Optional.empty());
            when(userRepository.findByEmailIgnoreCase("juan.perez@universidad.edu")).thenReturn(Optional.of(sampleUser));

            boolean permitido = estudianteService.canAccessProfile("juan.perez@universidad.edu", 100L);

            assertThat(permitido).isTrue();
            verify(userRepository).findByEmailIgnoreCase("juan.perez@universidad.edu");
        }

        @Test
        @DisplayName("Camino A3: Usuario con userId distinto al autenticado -> false")
        void canAccessProfile_usuarioDiferente_retornaFalse() {
            when(userRepository.findByUsernameIgnoreCase("juanperez")).thenReturn(Optional.of(sampleUser));

            boolean permitido = estudianteService.canAccessProfile("juanperez", 999L);

            assertThat(permitido).isFalse();
        }
    }
}
