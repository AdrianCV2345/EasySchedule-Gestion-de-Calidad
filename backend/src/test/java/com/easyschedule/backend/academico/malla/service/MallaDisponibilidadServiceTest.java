package com.easyschedule.backend.academico.malla.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.dto.MallaMateriaResponse;
import com.easyschedule.backend.academico.malla.dto.MateriaDisponibleConOfertasResponse;
import com.easyschedule.backend.academico.oferta_materia.model.OfertaMateria;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MallaDisponibilidadServiceTest {

    @Mock
    private MallaService mallaService;

    @Mock
    private OfertaMateriaRepository ofertaMateriaRepository;

    @Spy
    @InjectMocks
    private MallaDisponibilidadService disponibilidadService;

    @Test
    void debeDevolverMateriasConOfertasCuandoExistenOfertas() {
        // given
        Long mallaId = 1L;
        Long userId = 10L;

        MallaMateriaResponse materia = new MallaMateriaResponse(
            1L, 100L, "INF-101", "Programación I", (short) 4, (short) 1, "pendiente", List.of()
        );
        List<MallaMateriaResponse> disponibles = List.of(materia);

        OfertaMateria oferta1 = new OfertaMateria();
        oferta1.setId(10L);
        oferta1.setSemestre("2025-1");
        oferta1.setParalelo("A");
        oferta1.setDocente("Docente A");
        oferta1.setAula("Aula 101");

        OfertaMateria oferta2 = new OfertaMateria();
        oferta2.setId(11L);
        oferta2.setSemestre("2025-1");
        oferta2.setParalelo("B");
        oferta2.setDocente("Docente B");
        oferta2.setAula("Aula 102");

        List<OfertaMateria> ofertasEntity = List.of(oferta1, oferta2);

        when(disponibilidadService.getMateriasDisponibles(mallaId, userId)).thenReturn(disponibles);
        when(ofertaMateriaRepository.findByMateriaId(100L)).thenReturn(ofertasEntity);

        // when
        List<MateriaDisponibleConOfertasResponse> result =
            disponibilidadService.getMateriasDisponiblesConOfertas(mallaId, userId);

        // then
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        assertEquals(100L, result.get(0).materiaId());
        assertEquals("INF-101", result.get(0).codigoMateria());
        assertEquals("Programación I", result.get(0).nombreMateria());
        assertEquals(2, result.get(0).ofertas().size());
        assertEquals(10L, result.get(0).ofertas().get(0).id());
        assertEquals("2025-1", result.get(0).ofertas().get(0).semestre());
        assertEquals("A", result.get(0).ofertas().get(0).paralelo());
        assertEquals(11L, result.get(0).ofertas().get(1).id());

        verify(disponibilidadService).getMateriasDisponibles(mallaId, userId);
        verify(ofertaMateriaRepository).findByMateriaId(100L);
    }

    @Test
    void debeDevolverListaDeOfertasVaciaCuandoMateriaNoTieneOfertas() {
        // given
        Long mallaId = 1L;
        Long userId = 10L;

        MallaMateriaResponse materia = new MallaMateriaResponse(
            2L, 101L, "INF-102", "Programación II", (short) 4, (short) 2, "pendiente", List.of()
        );
        List<MallaMateriaResponse> disponibles = List.of(materia);

        when(disponibilidadService.getMateriasDisponibles(mallaId, userId)).thenReturn(disponibles);
        when(ofertaMateriaRepository.findByMateriaId(101L)).thenReturn(List.of());

        // when
        List<MateriaDisponibleConOfertasResponse> result =
            disponibilidadService.getMateriasDisponiblesConOfertas(mallaId, userId);

        // then
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).id());
        assertEquals(101L, result.get(0).materiaId());
        assertTrue(result.get(0).ofertas().isEmpty());

        verify(disponibilidadService).getMateriasDisponibles(mallaId, userId);
        verify(ofertaMateriaRepository).findByMateriaId(101L);
    }

    @Test
    void noDebeDevolverMateriasCuandoGetMateriasDisponiblesDevuelveVacio() {
        // given
        Long mallaId = 1L;
        Long userId = 10L;

        when(disponibilidadService.getMateriasDisponibles(mallaId, userId)).thenReturn(List.of());

        // when
        List<MateriaDisponibleConOfertasResponse> result =
            disponibilidadService.getMateriasDisponiblesConOfertas(mallaId, userId);

        // then
        assertTrue(result.isEmpty());

        verify(disponibilidadService).getMateriasDisponibles(mallaId, userId);
        verify(ofertaMateriaRepository, never()).findByMateriaId(anyLong());
    }
}
