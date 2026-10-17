package com.easyschedule.backend.estudiante.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import com.easyschedule.backend.auth.repositories.UserRepository;
import com.easyschedule.backend.auth.service.AuthService;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import com.easyschedule.backend.shared.exception.ResourceNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas unitarias puras (JUnit 5 + Mockito) para la funcionalidad de eliminación en EstudianteService.
 *
 * Enfoque: Totalmente desacoplado de bases de datos o contexto de Spring, validando la lógica
 * de negocio de eliminación y el cumplimiento de las ramas de Complejidad Ciclomática:
 * - V(G) de delete(id) = 2 (estudiante existe vs estudiante no existe en getEstudianteOrThrow).
 *
 * Requisitos evaluados:
 * 1. Flujo de Éxito: Cuando el estudiante existe en el repositorio, se obtiene la entidad
 *    y se invoca estudianteRepository.delete(...) exactamente una vez con la entidad recuperada.
 * 2. Manejo de Recurso Inexistente: Cuando el estudiante no existe, getEstudianteOrThrow()
 *    lanza ResourceNotFoundException y estudianteRepository.delete(...) nunca se ejecuta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EstudianteService - Pruebas Unitarias Puras de Eliminación")
class EstudianteDeleteServiceUnitTest {

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

    private Estudiante sampleEstudiante;

    @BeforeEach
    void setUp() {
        sampleEstudiante = new Estudiante();
        sampleEstudiante.setId(42L);
        sampleEstudiante.setUsername("estudiante.eliminar");
        sampleEstudiante.setCorreo("eliminar@easyschedule.com");
    }

    @Nested
    @DisplayName("delete(Long id) - Reglas de Negocio y Persistencia")
    class DeleteServiceTests {

        @Test
        @DisplayName("Flujo de éxito: estudiante existente es obtenido y eliminado exactamente una vez")
        void delete_cuandoEstudianteExiste_ejecutaDeleteExactamenteUnaVez() {
            Long estudianteId = 42L;
            when(estudianteRepository.findById(estudianteId)).thenReturn(Optional.of(sampleEstudiante));
            doNothing().when(estudianteRepository).delete(sampleEstudiante);

            assertThatCode(() -> estudianteService.delete(estudianteId))
                    .doesNotThrowAnyException();

            verify(estudianteRepository, times(1)).findById(estudianteId);
            verify(estudianteRepository, times(1)).delete(sampleEstudiante);
        }

        @Test
        @DisplayName("Manejo de recurso inexistente: lanza ResourceNotFoundException y nunca ejecuta delete(...)")
        void delete_cuandoEstudianteNoExiste_lanzaExcepcionYNuncaEjecutaDelete() {
            Long estudianteInexistenteId = 999L;
            when(estudianteRepository.findById(estudianteInexistenteId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> estudianteService.delete(estudianteInexistenteId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Estudiante no encontrado con id: 999");

            verify(estudianteRepository, times(1)).findById(estudianteInexistenteId);
            verify(estudianteRepository, never()).delete(any(Estudiante.class));
            verify(estudianteRepository, never()).deleteById(any());
        }
    }
}
