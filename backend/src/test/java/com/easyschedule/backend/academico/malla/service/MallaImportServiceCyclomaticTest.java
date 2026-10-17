package com.easyschedule.backend.academico.malla.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.carrera.repository.CarreraRepository;
import com.easyschedule.backend.academico.malla.dto.MallaImportRequest;
import com.easyschedule.backend.academico.malla.dto.MallaImportResponse;
import com.easyschedule.backend.academico.malla.dto.MateriaImportRequest;
import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.model.MallaMateria;
import com.easyschedule.backend.academico.malla.repository.MallaMateriaRepository;
import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import com.easyschedule.backend.academico.materia.model.Materia;
import com.easyschedule.backend.academico.materia.model.Prerequisito;
import com.easyschedule.backend.academico.materia.repository.MateriaRepository;
import com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository;
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
 * Matriz exhaustiva de pruebas unitarias para MallaImportService y métodos asociados.
 *
 * Cobertura de las 22 decisiones y caminos independientes acumulados (V(G)=35):
 * 1. Validaciones de Request (nombre null/blank, carreraId null, materias null/empty).
 * 2. Validación de Carrera (existe vs no existe).
 * 3. Obtención de Versión (provista explícita vs fallback default "1.0").
 * 4. Validación de Malla Existente (duplicada activa vs no existe).
 * 5. Creación de Malla y persistencia en repositorio.
 * 6. Iteración y Validación de Materias (código null/blank, nombre null/blank, semestre null/menor a 1).
 * 7. Reutilización vs Creación de Materias (findByCodigo presente vs nueva creación con creditos provistos/default 0).
 * 8. Procesamiento de Prerrequisitos (lista null/empty, prerrequisito inexistente en mapa,
 *    autoreferencia materia consigo misma, creación de nuevo prerrequisito vs ya existente en BD).
 * 9. Construcción de MallaImportResponse con conteos y mensaje de éxito.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MallaImportService - Suite de Pruebas Unitarias de Complejidad Ciclomática (V(G)=35)")
class MallaImportServiceCyclomaticTest {

    @Mock
    private MallaRepository mallaRepository;

    @Mock
    private MateriaRepository materiaRepository;

    @Mock
    private MallaMateriaRepository mallaMateriaRepository;

    @Mock
    private PrerequisitoRepository prerequisitoRepository;

    @Mock
    private CarreraRepository carreraRepository;

    @InjectMocks
    private MallaImportService mallaImportService;

    private Long defaultCarreraId;
    private String defaultNombre;
    private String defaultVersion;

    @BeforeEach
    void setUp() {
        defaultCarreraId = 1L;
        defaultNombre = "Ingeniería de Sistemas Plan 2026";
        defaultVersion = "2.0";
    }

    // =========================================================================
    // 1. Validaciones de MallaImportRequest (validarRequest)
    // =========================================================================
    @Nested
    @DisplayName("1. Validaciones de Request Inicial (validarRequest)")
    class ValidarRequestTests {

        @Test
        @DisplayName("Camino R1: Nombre es null -> Lanza IllegalArgumentException('El nombre de la malla es requerido')")
        void validarRequest_cuandoNombreEsNull_lanzaExcepcion() {
            MallaImportRequest request = new MallaImportRequest(null, "1.0", 1L, List.of(crearMateriaValida("MAT101")));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El nombre de la malla es requerido");

            verify(carreraRepository, never()).existsById(any());
        }

        @Test
        @DisplayName("Camino R2: Nombre está en blanco -> Lanza IllegalArgumentException('El nombre de la malla es requerido')")
        void validarRequest_cuandoNombreEstaEnBlanco_lanzaExcepcion() {
            MallaImportRequest request = new MallaImportRequest("   ", "1.0", 1L, List.of(crearMateriaValida("MAT101")));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El nombre de la malla es requerido");
        }

        @Test
        @DisplayName("Camino R3: CarreraId es null -> Lanza IllegalArgumentException('El ID de la carrera es requerido')")
        void validarRequest_cuandoCarreraIdEsNull_lanzaExcepcion() {
            MallaImportRequest request = new MallaImportRequest("Plan A", "1.0", null, List.of(crearMateriaValida("MAT101")));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El ID de la carrera es requerido");
        }

        @Test
        @DisplayName("Camino R4: Lista de materias es null -> Lanza IllegalArgumentException('Debe proporcionar al menos una materia')")
        void validarRequest_cuandoMateriasEsNull_lanzaExcepcion() {
            MallaImportRequest request = new MallaImportRequest("Plan A", "1.0", 1L, null);

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Debe proporcionar al menos una materia");
        }

        @Test
        @DisplayName("Camino R5: Lista de materias está vacía -> Lanza IllegalArgumentException('Debe proporcionar al menos una materia')")
        void validarRequest_cuandoMateriasEstaVacia_lanzaExcepcion() {
            MallaImportRequest request = new MallaImportRequest("Plan A", "1.0", 1L, Collections.emptyList());

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Debe proporcionar al menos una materia");
        }
    }

    // =========================================================================
    // 2. Validación de Carrera y Duplicidad de Malla
    // =========================================================================
    @Nested
    @DisplayName("2. Validación de Carrera y Existencia de Malla")
    class ValidarCarreraYMallaTests {

        @Test
        @DisplayName("Camino C1: Carrera no existe en el sistema -> Lanza IllegalArgumentException")
        void validarCarrera_cuandoCarreraNoExiste_lanzaExcepcion() {
            when(carreraRepository.existsById(99L)).thenReturn(false);

            MallaImportRequest request = new MallaImportRequest("Plan A", "1.0", 99L, List.of(crearMateriaValida("MAT101")));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La carrera con ID 99 no existe");

            verify(mallaRepository, never()).existsByCarreraIdAndVersionAndActiveTrue(any(), any());
        }

        @Test
        @DisplayName("Camino M1: Ya existe una malla activa para la carrera con la misma versión -> Lanza IllegalArgumentException")
        void validarMallaExistente_cuandoYaExisteMallaActiva_lanzaExcepcion() {
            when(carreraRepository.existsById(1L)).thenReturn(true);
            when(mallaRepository.existsByCarreraIdAndVersionAndActiveTrue(1L, "2.0")).thenReturn(true);

            MallaImportRequest request = new MallaImportRequest("Plan A", "2.0", 1L, List.of(crearMateriaValida("MAT101")));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ya existe una malla activa para esta carrera con versión 2.0. Use una versión diferente.");

            verify(mallaRepository, never()).save(any());
        }

        @Test
        @DisplayName("Camino M2: Versión es null -> Usa versión por defecto '1.0' y valida contra la base de datos")
        void obtenerVersion_cuandoVersionEsNull_aplicaVersionDefault() {
            when(carreraRepository.existsById(1L)).thenReturn(true);
            when(mallaRepository.existsByCarreraIdAndVersionAndActiveTrue(1L, "1.0")).thenReturn(true);

            // Version null en request
            MallaImportRequest request = new MallaImportRequest("Plan A", null, 1L, List.of(crearMateriaValida("MAT101")));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ya existe una malla activa para esta carrera con versión 1.0. Use una versión diferente.");

            verify(mallaRepository).existsByCarreraIdAndVersionAndActiveTrue(1L, "1.0");
        }
    }

    // =========================================================================
    // 3. Validación de Materias (validarMateria)
    // =========================================================================
    @Nested
    @DisplayName("3. Validación de Atributos de Materias (validarMateria)")
    class ValidarMateriaTests {

        @BeforeEach
        void mockCarreraYMallaOk() {
            when(carreraRepository.existsById(defaultCarreraId)).thenReturn(true);
            when(mallaRepository.existsByCarreraIdAndVersionAndActiveTrue(defaultCarreraId, defaultVersion)).thenReturn(false);
            when(mallaRepository.save(any(Malla.class))).thenAnswer(invocation -> {
                Malla m = invocation.getArgument(0);
                m.setId(10L);
                return m;
            });
        }

        @Test
        @DisplayName("Camino MAT1: Código de materia es null -> Lanza IllegalArgumentException('El código de la materia es requerido')")
        void validarMateria_codigoNull_lanzaExcepcion() {
            MateriaImportRequest mat = new MateriaImportRequest(null, "Calculo I", 1, 5, null);
            MallaImportRequest request = new MallaImportRequest(defaultNombre, defaultVersion, defaultCarreraId, List.of(mat));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El código de la materia es requerido");
        }

        @Test
        @DisplayName("Camino MAT2: Código de materia está en blanco -> Lanza IllegalArgumentException('El código de la materia es requerido')")
        void validarMateria_codigoEnBlanco_lanzaExcepcion() {
            MateriaImportRequest mat = new MateriaImportRequest("   ", "Calculo I", 1, 5, null);
            MallaImportRequest request = new MallaImportRequest(defaultNombre, defaultVersion, defaultCarreraId, List.of(mat));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El código de la materia es requerido");
        }

        @Test
        @DisplayName("Camino MAT3: Nombre de materia es null -> Lanza IllegalArgumentException('El nombre de la materia es requerido')")
        void validarMateria_nombreNull_lanzaExcepcion() {
            MateriaImportRequest mat = new MateriaImportRequest("MAT101", null, 1, 5, null);
            MallaImportRequest request = new MallaImportRequest(defaultNombre, defaultVersion, defaultCarreraId, List.of(mat));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El nombre de la materia es requerido");
        }

        @Test
        @DisplayName("Camino MAT4: Nombre de materia está en blanco -> Lanza IllegalArgumentException('El nombre de la materia es requerido')")
        void validarMateria_nombreEnBlanco_lanzaExcepcion() {
            MateriaImportRequest mat = new MateriaImportRequest("MAT101", "   ", 1, 5, null);
            MallaImportRequest request = new MallaImportRequest(defaultNombre, defaultVersion, defaultCarreraId, List.of(mat));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El nombre de la materia es requerido");
        }

        @Test
        @DisplayName("Camino MAT5: Semestre sugerido es null -> Lanza IllegalArgumentException('El semestre sugerido debe ser mayor a 0')")
        void validarMateria_semestreNull_lanzaExcepcion() {
            MateriaImportRequest mat = new MateriaImportRequest("MAT101", "Calculo I", null, 5, null);
            MallaImportRequest request = new MallaImportRequest(defaultNombre, defaultVersion, defaultCarreraId, List.of(mat));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El semestre sugerido debe ser mayor a 0");
        }

        @Test
        @DisplayName("Camino MAT6: Semestre sugerido es menor a 1 (ej. 0 o negativo) -> Lanza IllegalArgumentException")
        void validarMateria_semestreMenorAUno_lanzaExcepcion() {
            MateriaImportRequest mat = new MateriaImportRequest("MAT101", "Calculo I", 0, 5, null);
            MallaImportRequest request = new MallaImportRequest(defaultNombre, defaultVersion, defaultCarreraId, List.of(mat));

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("El semestre sugerido debe ser mayor a 0");
        }
    }

    // =========================================================================
    // 4. Reutilización vs Creación de Materias (obtenerOCrearMateria)
    // =========================================================================
    @Nested
    @DisplayName("4. Reutilización vs Creación de Materias y Manejo de Créditos")
    class ObtenerOCrearMateriaTests {

        @BeforeEach
        void mockGeneralSetup() {
            when(carreraRepository.existsById(defaultCarreraId)).thenReturn(true);
            when(mallaRepository.existsByCarreraIdAndVersionAndActiveTrue(defaultCarreraId, defaultVersion)).thenReturn(false);
            when(mallaRepository.save(any(Malla.class))).thenAnswer(invocation -> {
                Malla m = invocation.getArgument(0);
                m.setId(10L);
                return m;
            });
            when(mallaMateriaRepository.save(any(MallaMateria.class))).thenAnswer(invocation -> {
                MallaMateria mm = invocation.getArgument(0);
                mm.setId(100L);
                return mm;
            });
        }

        @Test
        @DisplayName("Camino REUT1: Materia ya existe en el catálogo -> Se reutiliza y NO se invoca materiaRepository.save()")
        void obtenerOCrearMateria_materiaExistente_seReutiliza() {
            Materia existente = new Materia();
            existente.setId(50L);
            existente.setCodigo("MAT101");
            existente.setNombre("Cálculo I");
            existente.setCreditos((short) 5);

            when(materiaRepository.findByCodigo("MAT101")).thenReturn(Optional.of(existente));

            MateriaImportRequest mat = new MateriaImportRequest("MAT101", "Cálculo I", 1, 5, null);
            MallaImportRequest request = new MallaImportRequest(defaultNombre, defaultVersion, defaultCarreraId, List.of(mat));

            MallaImportResponse response = mallaImportService.importarMalla(request);

            assertThat(response).isNotNull();
            assertThat(response.materiasImportadas()).isEqualTo(1);
            verify(materiaRepository).findByCodigo("MAT101");
            verify(materiaRepository, never()).save(any(Materia.class));
        }

        @Test
        @DisplayName("Camino NUEV1: Materia no existe con créditos informados -> Se crea y persiste con sus créditos")
        void obtenerOCrearMateria_materiaNuevaConCreditos_seCreaYPersiste() {
            when(materiaRepository.findByCodigo("PROG101")).thenReturn(Optional.empty());
            when(materiaRepository.save(any(Materia.class))).thenAnswer(invocation -> {
                Materia m = invocation.getArgument(0);
                m.setId(51L);
                return m;
            });

            MateriaImportRequest mat = new MateriaImportRequest("PROG101", "Programación I", 1, 6, null);
            MallaImportRequest request = new MallaImportRequest(defaultNombre, defaultVersion, defaultCarreraId, List.of(mat));

            mallaImportService.importarMalla(request);

            ArgumentCaptor<Materia> captor = ArgumentCaptor.forClass(Materia.class);
            verify(materiaRepository).save(captor.capture());
            Materia guardada = captor.getValue();
            assertThat(guardada.getCodigo()).isEqualTo("PROG101");
            assertThat(guardada.getNombre()).isEqualTo("Programación I");
            assertThat(guardada.getCreditos()).isEqualTo((short) 6);
            assertThat(guardada.isActive()).isTrue();
        }

        @Test
        @DisplayName("Camino NUEV2: Materia no existe y créditos es null -> Se crea y persiste con créditos por defecto (0)")
        void obtenerOCrearMateria_materiaNuevaSinCreditos_asignaCreditosDefaultCero() {
            when(materiaRepository.findByCodigo("TALL101")).thenReturn(Optional.empty());
            when(materiaRepository.save(any(Materia.class))).thenAnswer(invocation -> {
                Materia m = invocation.getArgument(0);
                m.setId(52L);
                return m;
            });

            MateriaImportRequest mat = new MateriaImportRequest("TALL101", "Taller Inicial", 1, null, null);
            MallaImportRequest request = new MallaImportRequest(defaultNombre, defaultVersion, defaultCarreraId, List.of(mat));

            mallaImportService.importarMalla(request);

            ArgumentCaptor<Materia> captor = ArgumentCaptor.forClass(Materia.class);
            verify(materiaRepository).save(captor.capture());
            Materia guardada = captor.getValue();
            assertThat(guardada.getCreditos()).isEqualTo((short) 0);
        }
    }

    // =========================================================================
    // 5. Integridad de Prerrequisitos (crearPrerequisitos)
    // =========================================================================
    @Nested
    @DisplayName("5. Integridad y Reglas de Prerrequisitos (crearPrerequisitos)")
    class PrerequisitosTests {

        private MateriaImportRequest materia1;
        private MateriaImportRequest materia2;

        @BeforeEach
        void setupMaterias() {
            when(carreraRepository.existsById(defaultCarreraId)).thenReturn(true);
            when(mallaRepository.existsByCarreraIdAndVersionAndActiveTrue(defaultCarreraId, defaultVersion)).thenReturn(false);
            when(mallaRepository.save(any(Malla.class))).thenAnswer(invocation -> {
                Malla m = invocation.getArgument(0);
                m.setId(10L);
                return m;
            });

            // Mock de repositorio de materias
            when(materiaRepository.findByCodigo(any())).thenAnswer(invocation -> {
                Materia m = new Materia();
                m.setCodigo(invocation.getArgument(0));
                return Optional.of(m);
            });

            // Mock asignación de MallaMateria con IDs únicos por materia
            when(mallaMateriaRepository.save(any(MallaMateria.class))).thenAnswer(invocation -> {
                MallaMateria mm = invocation.getArgument(0);
                if ("MAT101".equals(mm.getMateria().getCodigo())) {
                    mm.setId(101L);
                } else if ("MAT102".equals(mm.getMateria().getCodigo())) {
                    mm.setId(102L);
                } else {
                    mm.setId(999L);
                }
                return mm;
            });
        }

        @Test
        @DisplayName("Camino PR1: Prerrequisito no existe en la malla actual -> Lanza IllegalArgumentException")
        void crearPrerequisitos_prerequisitoInexistenteEnMalla_lanzaExcepcion() {
            // MAT102 exige FIS101 pero FIS101 no está en la lista de materias de la malla
            materia1 = new MateriaImportRequest("MAT101", "Cálculo I", 1, 5, null);
            materia2 = new MateriaImportRequest("MAT102", "Cálculo II", 2, 5, List.of("FIS101"));

            MallaImportRequest request = new MallaImportRequest(
                    defaultNombre, defaultVersion, defaultCarreraId, List.of(materia1, materia2)
            );

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La materia con codigo 'FIS101' no existe en la malla actual");

            verify(prerequisitoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Camino PR2: Autoreferencia (materia es prerrequisito de sí misma) -> Lanza IllegalArgumentException")
        void crearPrerequisitos_autoreferencia_lanzaExcepcion() {
            // MAT101 tiene como prerrequisito a sí misma
            materia1 = new MateriaImportRequest("MAT101", "Cálculo I", 1, 5, List.of("MAT101"));

            MallaImportRequest request = new MallaImportRequest(
                    defaultNombre, defaultVersion, defaultCarreraId, List.of(materia1)
            );

            assertThatThrownBy(() -> mallaImportService.importarMalla(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("La materia 'MAT101' no puede ser prerequisito de si misma");

            verify(prerequisitoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Camino PR3: Prerrequisito nuevo no registrado -> Se crea y persiste en PrerequisitoRepository")
        void crearPrerequisitos_nuevoPrerequisito_seCreaYPersiste() {
            materia1 = new MateriaImportRequest("MAT101", "Cálculo I", 1, 5, null);
            materia2 = new MateriaImportRequest("MAT102", "Cálculo II", 2, 5, List.of("MAT101"));

            // No existe previamente en la base de datos
            when(prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(102L, 101L))
                    .thenReturn(false);

            MallaImportRequest request = new MallaImportRequest(
                    defaultNombre, defaultVersion, defaultCarreraId, List.of(materia1, materia2)
            );

            MallaImportResponse response = mallaImportService.importarMalla(request);

            assertThat(response.prerequisitosImportados()).isEqualTo(1);

            ArgumentCaptor<Prerequisito> prereqCaptor = ArgumentCaptor.forClass(Prerequisito.class);
            verify(prerequisitoRepository, times(1)).save(prereqCaptor.capture());
            Prerequisito guardado = prereqCaptor.getValue();
            assertThat(guardado.getMallaMateria().getId()).isEqualTo(102L);
            assertThat(guardado.getPrerequisito().getId()).isEqualTo(101L);
        }

        @Test
        @DisplayName("Camino PR4: Prerrequisito ya existente en BD -> Se omite la duplicación (no invoca save)")
        void crearPrerequisitos_prerequisitoYaExiste_noLoDuplica() {
            materia1 = new MateriaImportRequest("MAT101", "Cálculo I", 1, 5, null);
            materia2 = new MateriaImportRequest("MAT102", "Cálculo II", 2, 5, List.of("MAT101"));

            // Ya existe en la base de datos
            when(prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(102L, 101L))
                    .thenReturn(true);

            MallaImportRequest request = new MallaImportRequest(
                    defaultNombre, defaultVersion, defaultCarreraId, List.of(materia1, materia2)
            );

            MallaImportResponse response = mallaImportService.importarMalla(request);

            assertThat(response.prerequisitosImportados()).isEqualTo(1);
            // El contador aumenta porque se procesó la relación, pero no se duplica en BD
            verify(prerequisitoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Camino PR5: Materia con lista de prerrequisitos vacía o null -> No itera prerrequisitos")
        void crearPrerequisitos_listaPrerequisitosVaciaONull_noRegistraPrerequisitos() {
            materia1 = new MateriaImportRequest("MAT101", "Cálculo I", 1, 5, null);
            materia2 = new MateriaImportRequest("MAT102", "Cálculo II", 2, 5, Collections.emptyList());

            MallaImportRequest request = new MallaImportRequest(
                    defaultNombre, defaultVersion, defaultCarreraId, List.of(materia1, materia2)
            );

            MallaImportResponse response = mallaImportService.importarMalla(request);

            assertThat(response.prerequisitosImportados()).isEqualTo(0);
            verify(prerequisitoRepository, never()).existsByMallaMateria_IdAndPrerequisito_Id(any(), any());
            verify(prerequisitoRepository, never()).save(any());
        }
    }

    // =========================================================================
    // 6. Importación Exitosa Completa (Camino Principal de Éxito)
    // =========================================================================
    @Nested
    @DisplayName("6. Escenario Integral de Éxito (Importación Masiva)")
    class ImportacionExitosaCompletaTests {

        @Test
        @DisplayName("Importación completa exitosa: crea malla, materias, relaciones y prerrequisitos con conteos correctos")
        void importarMalla_flujoCompletoValido_retornaRespuestaConConteosCorrectos() {
            // Setup
            when(carreraRepository.existsById(1L)).thenReturn(true);
            when(mallaRepository.existsByCarreraIdAndVersionAndActiveTrue(1L, "2026-1")).thenReturn(false);

            when(mallaRepository.save(any(Malla.class))).thenAnswer(invocation -> {
                Malla m = invocation.getArgument(0);
                m.setId(500L);
                return m;
            });

            // Materia 1 ya existe en catálogo
            Materia matExistente = new Materia();
            matExistente.setId(10L);
            matExistente.setCodigo("MAT101");
            matExistente.setNombre("Matemática I");
            matExistente.setCreditos((short) 5);
            when(materiaRepository.findByCodigo("MAT101")).thenReturn(Optional.of(matExistente));

            // Materia 2 es nueva en catálogo
            when(materiaRepository.findByCodigo("FIS101")).thenReturn(Optional.empty());
            when(materiaRepository.save(any(Materia.class))).thenAnswer(invocation -> {
                Materia m = invocation.getArgument(0);
                m.setId(11L);
                return m;
            });

            // MallaMateria saves
            when(mallaMateriaRepository.save(any(MallaMateria.class))).thenAnswer(invocation -> {
                MallaMateria mm = invocation.getArgument(0);
                if ("MAT101".equals(mm.getMateria().getCodigo())) {
                    mm.setId(1001L);
                } else {
                    mm.setId(1002L);
                }
                return mm;
            });

            // Prerrequisito no existe previamente
            when(prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(1002L, 1001L)).thenReturn(false);

            MateriaImportRequest m1 = new MateriaImportRequest("MAT101", "Matemática I", 1, 5, null);
            MateriaImportRequest m2 = new MateriaImportRequest("FIS101", "Física I", 2, 4, List.of("MAT101"));

            MallaImportRequest request = new MallaImportRequest(
                    "Plan de Estudios 2026", "2026-1", 1L, List.of(m1, m2)
            );

            // Ejecución
            MallaImportResponse response = mallaImportService.importarMalla(request);

            // Aserciones
            assertThat(response).isNotNull();
            assertThat(response.mallaId()).isEqualTo(500L);
            assertThat(response.nombre()).isEqualTo("Plan de Estudios 2026");
            assertThat(response.materiasImportadas()).isEqualTo(2);
            assertThat(response.prerequisitosImportados()).isEqualTo(1);
            assertThat(response.mensaje()).isEqualTo("Malla importada exitosamente");

            // Verificación de llamadas
            verify(carreraRepository).existsById(1L);
            verify(mallaRepository).existsByCarreraIdAndVersionAndActiveTrue(1L, "2026-1");
            verify(mallaRepository).save(any(Malla.class));
            verify(materiaRepository).findByCodigo("MAT101");
            verify(materiaRepository).findByCodigo("FIS101");
            verify(materiaRepository, times(1)).save(any(Materia.class)); // Solo para FIS101
            verify(mallaMateriaRepository, times(2)).save(any(MallaMateria.class));
            verify(prerequisitoRepository, times(1)).save(any(Prerequisito.class));
        }
    }

    private MateriaImportRequest crearMateriaValida(String codigo) {
        return new MateriaImportRequest(codigo, "Materia " + codigo, 1, 4, null);
    }
}
