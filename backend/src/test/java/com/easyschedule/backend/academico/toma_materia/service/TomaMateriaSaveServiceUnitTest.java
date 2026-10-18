package com.easyschedule.backend.academico.toma_materia.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.estado_materia.service.EstadoMateriaService;
import com.easyschedule.backend.academico.malla.model.MallaMateria;
import com.easyschedule.backend.academico.malla.repository.MallaMateriaRepository;
import com.easyschedule.backend.academico.materia.model.Materia;
import com.easyschedule.backend.academico.materia.model.Prerequisito;
import com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository;
import com.easyschedule.backend.academico.oferta_materia.model.OfertaMateria;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import com.easyschedule.backend.academico.toma_materia.dto.TomaMateriaRequest;
import com.easyschedule.backend.academico.toma_materia.dto.TomaMateriaResponse;
import com.easyschedule.backend.academico.toma_materia.model.TomaMateriaEstudiante;
import com.easyschedule.backend.academico.toma_materia.repository.TomaMateriaEstudianteRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Suite de Pruebas Unitarias para TomaMateriaService.saveByUserId.
 *
 * Cobertura de todas las decisiones lógicas y restricciones de negocio:
 * 1. Validación de Inscripción Repetida (409 CONFLICT)
 * 2. Validación de Existencia de MallaMateria (404 NOT FOUND)
 * 3. Validación de Estado de la Materia:
 *    - Ya aprobada (409 CONFLICT)
 *    - En curso / cursando (409 CONFLICT)
 *    - Estado permitido (null, reprobada, pendiente)
 * 4. Validación de Prerrequisitos:
 *    - Prerrequisito no aprobado o inexistente (400 BAD REQUEST)
 *    - Prerrequisito aprobado (permite avanzar)
 * 5. Límite de Créditos Permitidos:
 *    - Suma acumulada > 30 créditos (400 BAD REQUEST)
 *    - Suma acumulada <= 30 créditos (permite inscripción)
 * 6. Flujo Exitoso Integral y Sincronización:
 *    - Persistencia en TomaMateriaEstudianteRepository con estado "inscrita"
 *    - Sincronización con estadoMateriaService.syncEstadoFromToma(userId, mallaMateriaId, "inscrita")
 *    - Retorno de lista de TomaMateriaResponse con datos y fechas asignadas
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TomaMateriaService - Pruebas Unitarias de Guardado/Inscripción de Materias (saveByUserId)")
class TomaMateriaSaveServiceUnitTest {

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
        userId = 1L;
    }

    // =========================================================================
    // 1. Inscripción Repetida (Conflicto de Duplicidad)
    // =========================================================================
    @Nested
    @DisplayName("1. Validación de Inscripción Repetida")
    class InscripcionRepetidaTests {

        @Test
        @DisplayName("Rechaza con 409 CONFLICT si el estudiante ya registró la oferta/materia previamente")
        void saveByUserId_materiaYaRegistrada_lanzaConflict() {
            Long ofertaId = 10L;
            OfertaMateria oferta = new OfertaMateria();
            oferta.setId(ofertaId);

            when(ofertaMateriaRepository.findAllById(List.of(ofertaId))).thenReturn(List.of(oferta));
            when(tomaMateriaEstudianteRepository.existsByUserIdAndOfertaId(userId, ofertaId)).thenReturn(true);

            TomaMateriaRequest request = new TomaMateriaRequest(List.of(ofertaId));

            assertThatThrownBy(() -> tomaMateriaService.saveByUserId(userId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(rse.getReason()).isEqualTo("Ya registraste esta materia/paralelo.");
                    });

            verify(mallaMateriaRepository, never()).findById(any());
            verify(tomaMateriaEstudianteRepository, never()).save(any());
            verify(estadoMateriaService, never()).syncEstadoFromToma(any(), any(), any());
        }
    }

    // =========================================================================
    // 2. Existencia de Malla Curricular Asociada a la Oferta
    // =========================================================================
    @Nested
    @DisplayName("2. Validación de Existencia de Malla Curricular")
    class ExistenciaMallaTests {

        @Test
        @DisplayName("Rechaza con 404 NOT FOUND si la relación MallaMateria no existe")
        void saveByUserId_mallaMateriaNoExiste_lanzaNotFound() {
            Long ofertaId = 10L;
            Long mallaMateriaId = 100L;
            OfertaMateria oferta = new OfertaMateria();
            oferta.setId(ofertaId);
            oferta.setMallaMateriaId(mallaMateriaId);

            when(ofertaMateriaRepository.findAllById(List.of(ofertaId))).thenReturn(List.of(oferta));
            when(tomaMateriaEstudianteRepository.existsByUserIdAndOfertaId(userId, ofertaId)).thenReturn(false);
            when(mallaMateriaRepository.findById(mallaMateriaId)).thenReturn(Optional.empty());

            TomaMateriaRequest request = new TomaMateriaRequest(List.of(ofertaId));

            assertThatThrownBy(() -> tomaMateriaService.saveByUserId(userId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(rse.getReason()).isEqualTo("Malla no encontrada");
                    });

            verify(estadoMateriaService, never()).getEstadoMateria(any(), any());
            verify(tomaMateriaEstudianteRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 3. Estado Actual de la Materia (Aprobada o Cursando)
    // =========================================================================
    @Nested
    @DisplayName("3. Validación de Estado de la Materia")
    class EstadoMateriaTests {

        private OfertaMateria oferta;
        private MallaMateria mallaMateria;

        @BeforeEach
        void setupOfertaYMalla() {
            oferta = new OfertaMateria();
            oferta.setId(10L);
            oferta.setMallaMateriaId(100L);

            Materia materia = new Materia(1L, "SIS101", "Programación I", (short) 5);
            mallaMateria = new MallaMateria(100L, null, materia);

            when(ofertaMateriaRepository.findAllById(List.of(10L))).thenReturn(List.of(oferta));
            when(tomaMateriaEstudianteRepository.existsByUserIdAndOfertaId(userId, 10L)).thenReturn(false);
            when(mallaMateriaRepository.findById(100L)).thenReturn(Optional.of(mallaMateria));
        }

        @Test
        @DisplayName("Rechaza con 409 CONFLICT si la materia ya figura en estado 'aprobada'")
        void saveByUserId_materiaYaAprobada_lanzaConflict() {
            when(estadoMateriaService.getEstadoMateria(userId, 100L)).thenReturn("aprobada");

            TomaMateriaRequest request = new TomaMateriaRequest(List.of(10L));

            assertThatThrownBy(() -> tomaMateriaService.saveByUserId(userId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(rse.getReason()).isEqualTo("Ya tienes aprobada o en curso: Programación I");
                    });

            verify(prerequisitoRepository, never()).findByMallaMateria_Id(any());
            verify(tomaMateriaEstudianteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rechaza con 409 CONFLICT si la materia ya figura en estado 'cursando'")
        void saveByUserId_materiaEnCurso_lanzaConflict() {
            when(estadoMateriaService.getEstadoMateria(userId, 100L)).thenReturn("cursando");

            TomaMateriaRequest request = new TomaMateriaRequest(List.of(10L));

            assertThatThrownBy(() -> tomaMateriaService.saveByUserId(userId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(rse.getReason()).isEqualTo("Ya tienes aprobada o en curso: Programación I");
                    });

            verify(prerequisitoRepository, never()).findByMallaMateria_Id(any());
            verify(tomaMateriaEstudianteRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 4. Cumplimiento de Prerrequisitos
    // =========================================================================
    @Nested
    @DisplayName("4. Validación de Prerrequisitos")
    class PrerequisitosTests {

        private OfertaMateria oferta;
        private MallaMateria mallaMateria;
        private MallaMateria prereqMallaMateria;
        private Prerequisito prerequisito;

        @BeforeEach
        void setupPrerequisitos() {
            oferta = new OfertaMateria();
            oferta.setId(20L);
            oferta.setMallaMateriaId(200L);

            Materia materia = new Materia(2L, "SIS102", "Estructura de Datos", (short) 5);
            mallaMateria = new MallaMateria(200L, null, materia);

            Materia matPre = new Materia(1L, "SIS101", "Programación I", (short) 5);
            prereqMallaMateria = new MallaMateria(100L, null, matPre);

            prerequisito = new Prerequisito(1L, mallaMateria, prereqMallaMateria);

            when(ofertaMateriaRepository.findAllById(List.of(20L))).thenReturn(List.of(oferta));
            when(tomaMateriaEstudianteRepository.existsByUserIdAndOfertaId(userId, 20L)).thenReturn(false);
            when(mallaMateriaRepository.findById(200L)).thenReturn(Optional.of(mallaMateria));
            when(estadoMateriaService.getEstadoMateria(userId, 200L)).thenReturn(null); // No cursada ni aprobada
            when(prerequisitoRepository.findByMallaMateria_Id(200L)).thenReturn(List.of(prerequisito));
        }

        @Test
        @DisplayName("Rechaza con 400 BAD REQUEST si el prerrequisito no está aprobado (ej. nulo o reprobada)")
        void saveByUserId_prerrequisitoNoAprobado_lanzaBadRequest() {
            when(estadoMateriaService.getEstadoMateria(userId, 100L)).thenReturn("reprobada");

            TomaMateriaRequest request = new TomaMateriaRequest(List.of(20L));

            assertThatThrownBy(() -> tomaMateriaService.saveByUserId(userId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("Falta prerrequisito: Programación I");
                    });

            verify(tomaMateriaEstudianteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Permite avanzar si el prerrequisito requerido figura como 'aprobada'")
        void saveByUserId_prerrequisitoAprobado_permiteInscripcion() {
            when(estadoMateriaService.getEstadoMateria(userId, 100L)).thenReturn("aprobada");
            when(tomaMateriaEstudianteRepository.save(any(TomaMateriaEstudiante.class))).thenAnswer(inv -> {
                TomaMateriaEstudiante t = inv.getArgument(0);
                t.setId(1L);
                return t;
            });

            TomaMateriaRequest request = new TomaMateriaRequest(List.of(20L));

            List<TomaMateriaResponse> result = tomaMateriaService.saveByUserId(userId, request);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).ofertaId()).isEqualTo(20L);
            verify(tomaMateriaEstudianteRepository, times(1)).save(any(TomaMateriaEstudiante.class));
            verify(estadoMateriaService).syncEstadoFromToma(userId, 200L, "inscrita");
        }
    }

    // =========================================================================
    // 5. Límite de Créditos Permitidos por Periodo
    // =========================================================================
    @Nested
    @DisplayName("5. Validación del Límite Máximo de Créditos (30)")
    class LimiteCreditosTests {

        @Test
        @DisplayName("Rechaza con 400 BAD REQUEST si el total acumulado de créditos supera el máximo (30)")
        void saveByUserId_superaLimiteCreditos_lanzaBadRequest() {
            // Materia 1 de 20 créditos
            OfertaMateria o1 = new OfertaMateria();
            o1.setId(31L);
            o1.setMallaMateriaId(301L);
            MallaMateria mm1 = new MallaMateria(301L, null, new Materia(1L, "M1", "Materia Pesada 1", (short) 20));

            // Materia 2 de 15 créditos (Total = 35 > 30)
            OfertaMateria o2 = new OfertaMateria();
            o2.setId(32L);
            o2.setMallaMateriaId(302L);
            MallaMateria mm2 = new MallaMateria(302L, null, new Materia(2L, "M2", "Materia Pesada 2", (short) 15));

            when(ofertaMateriaRepository.findAllById(List.of(31L, 32L))).thenReturn(List.of(o1, o2));
            when(tomaMateriaEstudianteRepository.existsByUserIdAndOfertaId(userId, 31L)).thenReturn(false);
            when(tomaMateriaEstudianteRepository.existsByUserIdAndOfertaId(userId, 32L)).thenReturn(false);
            when(mallaMateriaRepository.findById(301L)).thenReturn(Optional.of(mm1));
            when(mallaMateriaRepository.findById(302L)).thenReturn(Optional.of(mm2));
            when(estadoMateriaService.getEstadoMateria(userId, 301L)).thenReturn(null);
            when(estadoMateriaService.getEstadoMateria(userId, 302L)).thenReturn(null);
            when(prerequisitoRepository.findByMallaMateria_Id(301L)).thenReturn(Collections.emptyList());
            when(prerequisitoRepository.findByMallaMateria_Id(302L)).thenReturn(Collections.emptyList());

            TomaMateriaRequest request = new TomaMateriaRequest(List.of(31L, 32L));

            assertThatThrownBy(() -> tomaMateriaService.saveByUserId(userId, request))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(rse.getReason()).isEqualTo("Superas el limite de 30 creditos");
                    });

            verify(tomaMateriaEstudianteRepository, never()).save(any());
            verify(estadoMateriaService, never()).syncEstadoFromToma(any(), any(), any());
        }

        @Test
        @DisplayName("Permite inscripción exactamente en el límite de créditos (30)")
        void saveByUserId_creditosExactamenteEnLimite_permiteInscripcion() {
            // Dos materias de 15 créditos cada una = 30 exactos
            OfertaMateria o1 = new OfertaMateria();
            o1.setId(31L);
            o1.setMallaMateriaId(301L);
            MallaMateria mm1 = new MallaMateria(301L, null, new Materia(1L, "M1", "Materia 1", (short) 15));

            OfertaMateria o2 = new OfertaMateria();
            o2.setId(32L);
            o2.setMallaMateriaId(302L);
            MallaMateria mm2 = new MallaMateria(302L, null, new Materia(2L, "M2", "Materia 2", (short) 15));

            when(ofertaMateriaRepository.findAllById(List.of(31L, 32L))).thenReturn(List.of(o1, o2));
            when(tomaMateriaEstudianteRepository.existsByUserIdAndOfertaId(userId, 31L)).thenReturn(false);
            when(tomaMateriaEstudianteRepository.existsByUserIdAndOfertaId(userId, 32L)).thenReturn(false);
            when(mallaMateriaRepository.findById(301L)).thenReturn(Optional.of(mm1));
            when(mallaMateriaRepository.findById(302L)).thenReturn(Optional.of(mm2));
            when(estadoMateriaService.getEstadoMateria(userId, 301L)).thenReturn(null);
            when(estadoMateriaService.getEstadoMateria(userId, 302L)).thenReturn(null);
            when(prerequisitoRepository.findByMallaMateria_Id(301L)).thenReturn(Collections.emptyList());
            when(prerequisitoRepository.findByMallaMateria_Id(302L)).thenReturn(Collections.emptyList());

            when(tomaMateriaEstudianteRepository.save(any(TomaMateriaEstudiante.class))).thenAnswer(inv -> {
                TomaMateriaEstudiante t = inv.getArgument(0);
                t.setId(10L);
                return t;
            });

            TomaMateriaRequest request = new TomaMateriaRequest(List.of(31L, 32L));

            List<TomaMateriaResponse> result = tomaMateriaService.saveByUserId(userId, request);

            assertThat(result).hasSize(2);
            verify(tomaMateriaEstudianteRepository, times(2)).save(any(TomaMateriaEstudiante.class));
            verify(estadoMateriaService).syncEstadoFromToma(userId, 301L, "inscrita");
            verify(estadoMateriaService).syncEstadoFromToma(userId, 302L, "inscrita");
        }
    }

    // =========================================================================
    // 6. Flujo Exitoso Completo y Sincronización
    // =========================================================================
    @Nested
    @DisplayName("6. Flujo Exitoso Completo y Sincronización de Estados")
    class FlujoExitosoTests {

        @Test
        @DisplayName("Inscripción exitosa: persiste registros con estado 'inscrita', sincroniza estados y retorna DTOs")
        void saveByUserId_flujoCompletoValido_persisteYSincronizaCorrectamente() {
            OfertaMateria oferta1 = new OfertaMateria();
            oferta1.setId(101L);
            oferta1.setMallaMateriaId(201L);

            OfertaMateria oferta2 = new OfertaMateria();
            oferta2.setId(102L);
            oferta2.setMallaMateriaId(202L);

            MallaMateria mm1 = new MallaMateria(201L, null, new Materia(1L, "FIS101", "Física I", (short) 5));
            MallaMateria mm2 = new MallaMateria(202L, null, new Materia(2L, "QMC101", "Química General", (short) 4));

            when(ofertaMateriaRepository.findAllById(List.of(101L, 102L))).thenReturn(List.of(oferta1, oferta2));
            when(tomaMateriaEstudianteRepository.existsByUserIdAndOfertaId(userId, 101L)).thenReturn(false);
            when(tomaMateriaEstudianteRepository.existsByUserIdAndOfertaId(userId, 102L)).thenReturn(false);
            when(mallaMateriaRepository.findById(201L)).thenReturn(Optional.of(mm1));
            when(mallaMateriaRepository.findById(202L)).thenReturn(Optional.of(mm2));
            when(estadoMateriaService.getEstadoMateria(userId, 201L)).thenReturn("pendiente");
            when(estadoMateriaService.getEstadoMateria(userId, 202L)).thenReturn(null);
            when(prerequisitoRepository.findByMallaMateria_Id(201L)).thenReturn(Collections.emptyList());
            when(prerequisitoRepository.findByMallaMateria_Id(202L)).thenReturn(Collections.emptyList());

            when(tomaMateriaEstudianteRepository.save(any(TomaMateriaEstudiante.class))).thenAnswer(inv -> {
                TomaMateriaEstudiante t = inv.getArgument(0);
                t.setId(t.getOfertaId() + 1000L);
                return t;
            });

            TomaMateriaRequest request = new TomaMateriaRequest(List.of(101L, 102L));

            List<TomaMateriaResponse> responses = tomaMateriaService.saveByUserId(userId, request);

            assertThat(responses).isNotNull().hasSize(2);

            // Verificación del primer registro guardado
            assertThat(responses.get(0).id()).isEqualTo(1101L);
            assertThat(responses.get(0).userId()).isEqualTo(userId);
            assertThat(responses.get(0).ofertaId()).isEqualTo(101L);
            assertThat(responses.get(0).estado()).isEqualTo("inscrita");
            assertThat(responses.get(0).fechaInscripcion()).isNotNull();

            // Verificación del segundo registro guardado
            assertThat(responses.get(1).id()).isEqualTo(1102L);
            assertThat(responses.get(1).userId()).isEqualTo(userId);
            assertThat(responses.get(1).ofertaId()).isEqualTo(102L);
            assertThat(responses.get(1).estado()).isEqualTo("inscrita");

            // Capturar argumentos guardados en el repositorio
            ArgumentCaptor<TomaMateriaEstudiante> captor = ArgumentCaptor.forClass(TomaMateriaEstudiante.class);
            verify(tomaMateriaEstudianteRepository, times(2)).save(captor.capture());
            List<TomaMateriaEstudiante> guardados = captor.getAllValues();
            assertThat(guardados.get(0).getEstado()).isEqualTo("inscrita");
            assertThat(guardados.get(1).getEstado()).isEqualTo("inscrita");

            // Verificación de sincronización con el colaborador externo
            verify(estadoMateriaService, times(1)).syncEstadoFromToma(userId, 201L, "inscrita");
            verify(estadoMateriaService, times(1)).syncEstadoFromToma(userId, 202L, "inscrita");
        }
    }
}
