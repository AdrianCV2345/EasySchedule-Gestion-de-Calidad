package com.easyschedule.backend.estudiante.controller;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.easyschedule.backend.estudiante.service.EstudianteMallaExportService;
import com.easyschedule.backend.estudiante.service.EstudianteService;
import com.easyschedule.backend.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas unitarias puras (JUnit 5 + Mockito) para la funcionalidad de eliminación en EstudianteController.
 *
 * Enfoque: Desacoplado, sin levantar contexto web ni contenedor Spring, validando la delegación directa
 * hacia EstudianteService y la propagación de excepciones.
 *
 * Caminos evaluados:
 * 1. Delegación exitosa: El controlador invoca a estudianteService.delete(id) exactamente una vez.
 * 2. Propagación de excepción: Si estudianteService lanza ResourceNotFoundException, el controlador
 *    la propaga fielmente sin alteración.
 * 3. Propagación de error inesperado: Si el servicio arroja cualquier RuntimeException, el controlador
 *    la propaga.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EstudianteController - Pruebas Unitarias Puras de Eliminación")
class EstudianteDeleteControllerUnitTest {

    @Mock
    private EstudianteService estudianteService;

    @Mock
    private EstudianteMallaExportService exportService;

    @InjectMocks
    private EstudianteController estudianteController;

    @Nested
    @DisplayName("delete(Long id) - Delegación a capa de servicio")
    class DeleteControllerTests {

        @Test
        @DisplayName("Flujo de éxito: delega la eliminación a estudianteService.delete(...) exactamente una vez")
        void delete_cuandoIdEsValido_delegaEnServicioExitosamente() {
            Long estudianteId = 15L;
            doNothing().when(estudianteService).delete(estudianteId);

            assertThatCode(() -> estudianteController.delete(estudianteId))
                    .doesNotThrowAnyException();

            verify(estudianteService, times(1)).delete(estudianteId);
        }

        @Test
        @DisplayName("Manejo de inexistencia: propaga ResourceNotFoundException cuando el servicio la arroja")
        void delete_cuandoEstudianteNoExiste_propagaResourceNotFoundException() {
            Long estudianteInexistenteId = 999L;
            doThrow(new ResourceNotFoundException("Estudiante no encontrado con id: " + estudianteInexistenteId))
                    .when(estudianteService).delete(estudianteInexistenteId);

            assertThatThrownBy(() -> estudianteController.delete(estudianteInexistenteId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Estudiante no encontrado con id: 999");

            verify(estudianteService, times(1)).delete(estudianteInexistenteId);
        }

        @Test
        @DisplayName("Propagación de errores imprevistos: propaga cualquier RuntimeException generada")
        void delete_cuandoServicioFallaInesperadamente_propagaExcepcion() {
            Long estudianteId = 20L;
            doThrow(new IllegalStateException("Error de infraestructura o persistencia"))
                    .when(estudianteService).delete(estudianteId);

            assertThatThrownBy(() -> estudianteController.delete(estudianteId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Error de infraestructura o persistencia");

            verify(estudianteService, times(1)).delete(estudianteId);
        }
    }
}
