package com.easyschedule.backend.academico.malla.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.dto.MallaMateriaResponse;
import com.easyschedule.backend.academico.malla.service.MallaService;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MallaDisponibilidadServiceTest {

    @Mock
    private MallaService mallaService;

    @Mock
    private OfertaMateriaRepository ofertaMateriaRepository;

    @InjectMocks
    private MallaDisponibilidadService disponibilidadService;

    private static MallaMateriaResponse materia(Long id, Long materiaId, String codigo, String nombre,
                                                Short creditos, Short semestre, String estado, List<Long> prereqs) {
        return new MallaMateriaResponse(id, materiaId, codigo, nombre, creditos, semestre, estado, prereqs);
    }

    // ------------------------------------------------------------------
    // TC1: Lista vacía → ciclo exterior de inicializarGrafo y
    //      propagarMateriasAprobadas no se ejecutan.
    // ------------------------------------------------------------------
    @Test
    void getMateriasDisponiblesConListaVaciaDevuelveVacio() {
        when(mallaService.findMateriasByMalla(1L, 1L)).thenReturn(List.of());

        List<MallaMateriaResponse> result =
            disponibilidadService.getMateriasDisponibles(1L, 1L);

        assertEquals(0, result.size());
    }

    // ------------------------------------------------------------------
    // TC2: Materia pendiente sin prerrequisitos (prerequisitosIds == null)
    //      → inicializarGrafo P2, estaCompletadaOCursando P3,
    //      getMateriasDisponibles P2 (incluida).
    // ------------------------------------------------------------------
    @Test
    void getMateriasDisponiblesMateriaPendienteSinPrerrequisitosEstaDisponible() {
        when(mallaService.findMateriasByMalla(1L, 1L)).thenReturn(
            List.of(materia(1L, 101L, "INF-101", "Programación I", (short) 4, (short) 1,
                          "pendiente", null))
        );

        List<MallaMateriaResponse> result =
            disponibilidadService.getMateriasDisponibles(1L, 1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("INF-101", result.get(0).codigoMateria());
    }

    // ------------------------------------------------------------------
    // TC3: Materia aprobada → está completada, se excluye.
    //      inicializarGrafo P3 (lista vacía), estaCompletadaOCursando P1 ("aprobada"),
    //      propagarMateriasAprobadas P4 (aprobada, adj vacío),
    //      getMateriasDisponibles P1 (excluida).
    // ------------------------------------------------------------------
    @Test
    void getMateriasDisponiblesMateriaAprobadaNoEstaDisponible() {
        when(mallaService.findMateriasByMalla(1L, 1L)).thenReturn(
            List.of(materia(1L, 101L, "INF-101", "Programación I", (short) 4, (short) 1,
                          "aprobada", List.of()))
        );

        List<MallaMateriaResponse> result =
            disponibilidadService.getMateriasDisponibles(1L, 1L);

        assertEquals(0, result.size());
    }

    // ------------------------------------------------------------------
    // TC4: Materia cursando → está en curso, se excluye.
    //      estaCompletadaOCursando P2 ("cursando"),
    //      getMateriasDisponibles P1 (excluida).
    // ------------------------------------------------------------------
    @Test
    void getMateriasDisponiblesMateriaCursandoNoEstaDisponible() {
        when(mallaService.findMateriasByMalla(1L, 1L)).thenReturn(
            List.of(materia(1L, 101L, "INF-101", "Programación I", (short) 4, (short) 1,
                          "cursando", List.of()))
        );

        List<MallaMateriaResponse> result =
            disponibilidadService.getMateriasDisponibles(1L, 1L);

        assertEquals(0, result.size());
    }

    // ------------------------------------------------------------------
    // TC5: Estado null → estaCompletadaOCursando devuelve false
    //      (ambas condiciones del OR son falsas), materia incluida.
    // ------------------------------------------------------------------
    @Test
    void getMateriasDisponiblesEstadoNullSeInterpretaComoDisponible() {
        when(mallaService.findMateriasByMalla(1L, 1L)).thenReturn(
            List.of(materia(1L, 101L, "INF-101", "Programación I", (short) 4, (short) 1,
                          null, List.of()))
        );

        List<MallaMateriaResponse> result =
            disponibilidadService.getMateriasDisponibles(1L, 1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
    }

    // ------------------------------------------------------------------
    // TC6: Materia pendiente con prerrequisito aprobado → se libera y está disponible.
    //      inicializarGrafo P4 (un prerrequisito), P5 (varias materias),
    //      propagarMateriasAprobadas P5 (aprobada con dependientes),
    //      getMateriasDisponibles P2 (incluida).
    // ------------------------------------------------------------------
    @Test
    void getMateriasDisponiblesPrerrequisitoAprobadoLiberaDependiente() {
        when(mallaService.findMateriasByMalla(1L, 1L)).thenReturn(
            List.of(
                materia(1L, 101L, "INF-101", "Programación I", (short) 4, (short) 1,
                        "pendiente", List.of(2L)),
                materia(2L, 102L, "INF-102", "Programación II", (short) 4, (short) 2,
                        "aprobada", List.of())
            )
        );

        List<MallaMateriaResponse> result =
            disponibilidadService.getMateriasDisponibles(1L, 1L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals("INF-101", result.get(0).codigoMateria());
    }

    // ------------------------------------------------------------------
    // TC7: Materia pendiente con prerrequisito pendiente → no se libera, no disponible.
    //      getMateriasDisponibles P3 (primera condición verdadera, segunda falsa).
    // ------------------------------------------------------------------
    @Test
    void getMateriasDisponiblesPrerrequisitoPendienteBloqueaMateria() {
        when(mallaService.findMateriasByMalla(1L, 1L)).thenReturn(
            List.of(
                materia(1L, 101L, "INF-101", "Programación I", (short) 4, (short) 1,
                        "pendiente", List.of(2L)),
                materia(2L, 102L, "INF-102", "Programación II", (short) 4, (short) 2,
                        "pendiente", List.of())
            )
        );

        List<MallaMateriaResponse> result =
            disponibilidadService.getMateriasDisponibles(1L, 1L);

        // Solo M2 (sin prerrequisitos, pendiente) aparece; M1 permanece bloqueada.
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).id());
        assertEquals("INF-102", result.get(0).codigoMateria());
    }
}
