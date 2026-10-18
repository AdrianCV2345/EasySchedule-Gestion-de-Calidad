package com.easyschedule.backend.academico.toma_materia.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.estado_materia.service.EstadoMateriaService;
import com.easyschedule.backend.academico.malla.repository.MallaMateriaRepository;
import com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import com.easyschedule.backend.academico.toma_materia.model.TomaMateriaEstudiante;
import com.easyschedule.backend.academico.toma_materia.repository.TomaMateriaEstudianteRepository;
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
 * Pruebas unitarias puras para TomaMateriaService.deleteByUserIdAndOfertaId.
 *
 * Cumplimiento de principios:
 * 1. Flujo Exitoso: Si existe una inscripción para (userId, ofertaId), tomaMateriaEstudianteRepository.delete(entidad)
 *    se ejecuta exactamente una vez con la entidad recuperada.
 * 2. Efectos Acotados (Principio de Realismo de Código): El método implementado únicamente consulta y elimina en
 *    TomaMateriaEstudianteRepository; NO altera EstadoMateriaService ni otros repositorios (se verifica mediante verifyNoInteractions).
 * 3. Manejo de Inexistencia / Idempotencia Silenciosa: Si no existe inscripción para la tupla (userId, ofertaId)
 *    (por ofertaId inexistente, ajena a otro usuario o ya eliminada), el método completa normalmente sin excepciones
 *    y nunca invoca tomaMateriaEstudianteRepository.delete(...).
 * 4. Aislamiento Estricto: Mockito sobre repositorios sin contexto de Spring ni base de datos real.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TomaMateriaService - Pruebas Unitarias de Eliminación/Retiro de Inscripción por Oferta ID")
class TomaMateriaDeleteServiceUnitTest {

    @Mock
    private TomaMateriaEstudianteRepository tomaMateriaEstudianteRepository;

    @Mock
    private OfertaMateriaRepository ofertaMateriaRepository;

    @Mock
    private EstadoMateriaService estadoMateriaService;

    @Mock
    private PrerequisitoRepository prerequisitoRepository;

    @Mock
    private MallaMateriaRepository mallaMateriaRepository;

    @InjectMocks
    private TomaMateriaService tomaMateriaService;

    private Long userId;
    private Long ofertaId;

    @BeforeEach
    void setUp() {
        userId = 15L;
        ofertaId = 200L;
    }

    @Nested
    @DisplayName("deleteByUserIdAndOfertaId(Long userId, Long ofertaId)")
    class DeleteByUserIdAndOfertaIdTests {

        @Test
        @DisplayName("Flujo de éxito: inscripción existente es recuperada y eliminada exactamente una vez")
        void deleteByUserIdAndOfertaId_cuandoExisteInscripcion_eliminaEntidadEnRepositorio() {
            TomaMateriaEstudiante registroExistente = new TomaMateriaEstudiante();
            registroExistente.setId(1001L);
            registroExistente.setUserId(userId);
            registroExistente.setOfertaId(ofertaId);
            registroExistente.setEstado("inscrita");

            when(tomaMateriaEstudianteRepository.findByUserIdAndOfertaId(userId, ofertaId))
                    .thenReturn(Optional.of(registroExistente));

            tomaMateriaService.deleteByUserIdAndOfertaId(userId, ofertaId);

            // Verificación del efecto real: invocación a delete con la entidad encontrada
            verify(tomaMateriaEstudianteRepository, times(1)).findByUserIdAndOfertaId(userId, ofertaId);
            verify(tomaMateriaEstudianteRepository, times(1)).delete(registroExistente);

            // Verificación de efectos acotados (principio de realismo de código):
            // No asume efectos secundarios que no están en el código fuente
            verifyNoInteractions(estadoMateriaService);
            verifyNoInteractions(ofertaMateriaRepository);
            verifyNoInteractions(prerequisitoRepository);
            verifyNoInteractions(mallaMateriaRepository);
        }

        @Test
        @DisplayName("Recurso inexistente / ya eliminado: completa normalmente (idempotencia) y nunca ejecuta delete(...)")
        void deleteByUserIdAndOfertaId_cuandoNoExisteInscripcion_noEjecutaDelete() {
            when(tomaMateriaEstudianteRepository.findByUserIdAndOfertaId(userId, ofertaId))
                    .thenReturn(Optional.empty());

            assertThatCode(() -> tomaMateriaService.deleteByUserIdAndOfertaId(userId, ofertaId))
                    .doesNotThrowAnyException();

            verify(tomaMateriaEstudianteRepository, times(1)).findByUserIdAndOfertaId(userId, ofertaId);
            verify(tomaMateriaEstudianteRepository, never()).delete(any());
            verify(tomaMateriaEstudianteRepository, never()).deleteById(any());

            verifyNoInteractions(estadoMateriaService);
        }

        @Test
        @DisplayName("Inscripción perteneciente a otro usuario: findByUserIdAndOfertaId retorna vacío y no elimina")
        void deleteByUserIdAndOfertaId_ofertaPerteneceAOtroUsuario_noEjecutaDelete() {
            Long otroUserId = 99L;
            when(tomaMateriaEstudianteRepository.findByUserIdAndOfertaId(otroUserId, ofertaId))
                    .thenReturn(Optional.empty());

            tomaMateriaService.deleteByUserIdAndOfertaId(otroUserId, ofertaId);

            verify(tomaMateriaEstudianteRepository).findByUserIdAndOfertaId(otroUserId, ofertaId);
            verify(tomaMateriaEstudianteRepository, never()).delete(any());
        }
    }
}
