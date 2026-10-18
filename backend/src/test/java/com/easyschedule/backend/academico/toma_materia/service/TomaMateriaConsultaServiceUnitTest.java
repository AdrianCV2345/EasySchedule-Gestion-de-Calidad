package com.easyschedule.backend.academico.toma_materia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.estado_materia.service.EstadoMateriaService;
import com.easyschedule.backend.academico.malla.repository.MallaMateriaRepository;
import com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import com.easyschedule.backend.academico.toma_materia.dto.TomaMateriaResponse;
import com.easyschedule.backend.academico.toma_materia.model.TomaMateriaEstudiante;
import com.easyschedule.backend.academico.toma_materia.repository.TomaMateriaEstudianteRepository;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pruebas unitarias puras (JUnit 5 + Mockito) para la consulta y transformación
 * de materias inscritas en TomaMateriaService.listByUserId.
 *
 * Cumplimiento de requisitos:
 * 1. Flujo exitoso: recupera entidades y las transforma fielmente a TomaMateriaResponse (todos los atributos).
 * 2. Aislamiento estricto: Mockito sobre TomaMateriaEstudianteRepository sin base de datos ni SQL real.
 * 3. Cobertura de caminos lógicos:
 *    - Camino 1: Estudiante con múltiples inscripciones registradas.
 *    - Camino 2: Estudiante sin materias inscritas (lista vacía).
 * 4. Integridad de ordenamiento y no afectación de servicios de modificación/validación.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TomaMateriaService - Pruebas Unitarias Puras de Consulta de Materias Inscritas")
class TomaMateriaConsultaServiceUnitTest {

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

    @BeforeEach
    void setUp() {
        userId = 42L;
    }

    @Nested
    @DisplayName("listByUserId(Long userId) - Recuperación y Transformación a DTO")
    class ListByUserIdTests {

        @Test
        @DisplayName("Camino 1: Estudiante con materias inscritas -> Transforma coleccion completa a TomaMateriaResponse")
        void listByUserId_cuandoExistenInscripciones_retornaListaTransformadaCorrectamente() {
            OffsetDateTime fecha1 = OffsetDateTime.parse("2026-03-01T10:00:00Z");
            OffsetDateTime fecha2 = OffsetDateTime.parse("2026-03-02T15:30:00Z");

            TomaMateriaEstudiante toma1 = new TomaMateriaEstudiante();
            toma1.setId(101L);
            toma1.setUserId(userId);
            toma1.setOfertaId(501L);
            toma1.setEstado("inscrita");
            toma1.setFechaInscripcion(fecha1);
            toma1.setFechaActualizacion(fecha1);

            TomaMateriaEstudiante toma2 = new TomaMateriaEstudiante();
            toma2.setId(102L);
            toma2.setUserId(userId);
            toma2.setOfertaId(502L);
            toma2.setEstado("inscrita");
            toma2.setFechaInscripcion(fecha2);
            toma2.setFechaActualizacion(fecha2);

            when(tomaMateriaEstudianteRepository.findByUserIdOrderByFechaInscripcionDesc(userId))
                    .thenReturn(List.of(toma2, toma1));

            List<TomaMateriaResponse> result = tomaMateriaService.listByUserId(userId);

            assertThat(result).isNotNull().hasSize(2);

            // Verificación del primer elemento (más reciente)
            TomaMateriaResponse item1 = result.get(0);
            assertThat(item1.id()).isEqualTo(102L);
            assertThat(item1.userId()).isEqualTo(userId);
            assertThat(item1.ofertaId()).isEqualTo(502L);
            assertThat(item1.estado()).isEqualTo("inscrita");
            assertThat(item1.fechaInscripcion()).isEqualTo(fecha2);
            assertThat(item1.fechaActualizacion()).isEqualTo(fecha2);

            // Verificación del segundo elemento
            TomaMateriaResponse item2 = result.get(1);
            assertThat(item2.id()).isEqualTo(101L);
            assertThat(item2.userId()).isEqualTo(userId);
            assertThat(item2.ofertaId()).isEqualTo(501L);
            assertThat(item2.estado()).isEqualTo("inscrita");
            assertThat(item2.fechaInscripcion()).isEqualTo(fecha1);
            assertThat(item2.fechaActualizacion()).isEqualTo(fecha1);

            verify(tomaMateriaEstudianteRepository, times(1))
                    .findByUserIdOrderByFechaInscripcionDesc(userId);
            verify(ofertaMateriaRepository, never()).findAllById(any());
            verify(estadoMateriaService, never()).getEstadoMateria(any(), any());
        }

        @Test
        @DisplayName("Camino 2: Estudiante sin materias inscritas -> Retorna lista vacía sin errores")
        void listByUserId_cuandoNoExistenInscripciones_retornaListaVacia() {
            when(tomaMateriaEstudianteRepository.findByUserIdOrderByFechaInscripcionDesc(userId))
                    .thenReturn(Collections.emptyList());

            List<TomaMateriaResponse> result = tomaMateriaService.listByUserId(userId);

            assertThat(result).isNotNull().isEmpty();

            verify(tomaMateriaEstudianteRepository, times(1))
                    .findByUserIdOrderByFechaInscripcionDesc(userId);
        }
    }
}
