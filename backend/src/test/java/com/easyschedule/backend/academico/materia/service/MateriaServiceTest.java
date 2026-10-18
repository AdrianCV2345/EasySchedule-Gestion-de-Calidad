package com.easyschedule.backend.academico.materia.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.model.MallaMateria;
import com.easyschedule.backend.academico.malla.repository.MallaMateriaRepository;
import com.easyschedule.backend.academico.materia.dto.PrerequisitoRequest;
import com.easyschedule.backend.academico.materia.dto.PrerequisitoResponse;
import com.easyschedule.backend.academico.materia.model.Materia;
import com.easyschedule.backend.academico.materia.model.Prerequisito;
import com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MateriaServiceTest {

    @Mock
    private PrerequisitoRepository prerequisitoRepository;

    @Mock
    private MallaMateriaRepository mallaMateriaRepository;

    @InjectMocks
    private MateriaService materiaService;

    // ==================== HU#28 — Eliminar Prerrequisito ====================

    @Test
    void eliminarPrerequisitoLanzaExcepcionCuandoRelacionNoExiste() {
        Long mallaMateriaId = 1L;
        Long prerequisitoMallaMateriaId = 2L;

        when(prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId))
            .thenReturn(false);

        assertThrows(EntityNotFoundException.class,
            () -> materiaService.eliminarPrerequisito(mallaMateriaId, prerequisitoMallaMateriaId));

        verify(prerequisitoRepository).existsByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId);
        verify(prerequisitoRepository, never()).deleteByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId);
    }

    @Test
    void eliminarPrerequisitoBorraRelacionCuandoExiste() {
        Long mallaMateriaId = 1L;
        Long prerequisitoMallaMateriaId = 2L;

        when(prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId))
            .thenReturn(true);

        assertDoesNotThrow(() -> materiaService.eliminarPrerequisito(mallaMateriaId, prerequisitoMallaMateriaId));

        verify(prerequisitoRepository).existsByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId);
        verify(prerequisitoRepository, times(1)).deleteByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId);
    }

    // ==================== HU#27 — Crear Prerrequisito ====================

    @Test
    void crearPrerequisitoConMateriaIdNuloLanzaExcepcion() {
        PrerequisitoRequest request = new PrerequisitoRequest(null, 2L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> materiaService.crearPrerequisito(request));

        assertEquals("Los IDs de materia y prerrequisito son obligatorios.", ex.getMessage());
        verifyNoInteractions(mallaMateriaRepository, prerequisitoRepository);
    }

    @Test
    void crearPrerequisitoConPrerequisitoIdNuloLanzaExcepcion() {
        PrerequisitoRequest request = new PrerequisitoRequest(1L, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> materiaService.crearPrerequisito(request));

        assertEquals("Los IDs de materia y prerrequisito son obligatorios.", ex.getMessage());
        verifyNoInteractions(mallaMateriaRepository, prerequisitoRepository);
    }

    @Test
    void crearPrerequisitoAutoreferenciaLanzaExcepcion() {
        PrerequisitoRequest request = new PrerequisitoRequest(1L, 1L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> materiaService.crearPrerequisito(request));

        assertEquals("Una materia no puede ser prerrequisito de sí misma.", ex.getMessage());
        verifyNoInteractions(mallaMateriaRepository, prerequisitoRepository);
    }

    @Test
    void crearPrerequisitoMateriaPrincipalInexistenteLanzaExcepcion() {
        PrerequisitoRequest request = new PrerequisitoRequest(1L, 2L);

        when(mallaMateriaRepository.findById(1L)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
            () -> materiaService.crearPrerequisito(request));

        assertTrue(ex.getMessage().contains("no existe"));
        verify(mallaMateriaRepository).findById(1L);
        verify(mallaMateriaRepository, never()).findById(2L);
        verify(prerequisitoRepository, never()).save(any());
    }

    @Test
    void crearPrerequisitoPrerrequisitoInexistenteLanzaExcepcion() {
        PrerequisitoRequest request = new PrerequisitoRequest(1L, 2L);

        MallaMateria materia = crearMallaMateria(1L, 10L, "INF-101", "Programacion I", (short) 3);
        when(mallaMateriaRepository.findById(1L)).thenReturn(Optional.of(materia));
        when(mallaMateriaRepository.findById(2L)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
            () -> materiaService.crearPrerequisito(request));

        assertTrue(ex.getMessage().contains("no existe"));
        verify(mallaMateriaRepository).findById(1L);
        verify(mallaMateriaRepository).findById(2L);
        verify(prerequisitoRepository, never()).save(any());
    }

    @Test
    void crearPrerequisitoMateriasDeDiferentesMallasLanzaExcepcion() {
        PrerequisitoRequest request = new PrerequisitoRequest(1L, 2L);

        MallaMateria materia = crearMallaMateria(1L, 10L, "INF-101", "Programacion I", (short) 3);
        MallaMateria prerequisito = crearMallaMateria(2L, 20L, "INF-100", "Logica", (short) 1);
        when(mallaMateriaRepository.findById(1L)).thenReturn(Optional.of(materia));
        when(mallaMateriaRepository.findById(2L)).thenReturn(Optional.of(prerequisito));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> materiaService.crearPrerequisito(request));

        assertTrue(ex.getMessage().contains("no pertenece a la misma malla"));
        verify(prerequisitoRepository, never()).save(any());
    }

    @Test
    void crearPrerequisitoSemestreInvalidoLanzaExcepcion() {
        PrerequisitoRequest request = new PrerequisitoRequest(1L, 2L);

        MallaMateria materia = crearMallaMateria(1L, 10L, "INF-101", "Programacion I", (short) 3);
        MallaMateria prerequisito = crearMallaMateria(2L, 10L, "INF-100", "Logica", (short) 3);
        when(mallaMateriaRepository.findById(1L)).thenReturn(Optional.of(materia));
        when(mallaMateriaRepository.findById(2L)).thenReturn(Optional.of(prerequisito));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> materiaService.crearPrerequisito(request));

        assertTrue(ex.getMessage().contains("semestre anterior"));
        verify(prerequisitoRepository, never()).save(any());
    }

    @Test
    void crearPrerequisitoRelacionDuplicadaLanzaExcepcion() {
        PrerequisitoRequest request = new PrerequisitoRequest(1L, 2L);

        MallaMateria materia = crearMallaMateria(1L, 10L, "INF-101", "Programacion I", (short) 3);
        MallaMateria prerequisito = crearMallaMateria(2L, 10L, "INF-100", "Logica", (short) 2);
        when(mallaMateriaRepository.findById(1L)).thenReturn(Optional.of(materia));
        when(mallaMateriaRepository.findById(2L)).thenReturn(Optional.of(prerequisito));
        when(prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(1L, 2L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> materiaService.crearPrerequisito(request));

        assertTrue(ex.getMessage().contains("ya está registrada"));
        verify(prerequisitoRepository, never()).save(any());
    }

    @Test
    void crearPrerequisitoRelacionInversaExistenteLanzaExcepcion() {
        PrerequisitoRequest request = new PrerequisitoRequest(1L, 2L);

        MallaMateria materia = crearMallaMateria(1L, 10L, "INF-101", "Programacion I", (short) 3);
        MallaMateria prerequisito = crearMallaMateria(2L, 10L, "INF-100", "Logica", (short) 2);
        when(mallaMateriaRepository.findById(1L)).thenReturn(Optional.of(materia));
        when(mallaMateriaRepository.findById(2L)).thenReturn(Optional.of(prerequisito));
        when(prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(1L, 2L)).thenReturn(false);
        when(prerequisitoRepository.findInversePair(1L, 2L)).thenReturn(List.of(new Prerequisito()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> materiaService.crearPrerequisito(request));

        assertTrue(ex.getMessage().contains("relación inversa"));
        verify(prerequisitoRepository, never()).save(any());
    }

    @Test
    void crearPrerequisitoExitosoRetornaResponse() {
        PrerequisitoRequest request = new PrerequisitoRequest(1L, 2L);

        MallaMateria materia = crearMallaMateria(1L, 10L, "INF-101", "Programacion I", (short) 3);
        MallaMateria prerequisito = crearMallaMateria(2L, 10L, "INF-100", "Logica", (short) 2);
        when(mallaMateriaRepository.findById(1L)).thenReturn(Optional.of(materia));
        when(mallaMateriaRepository.findById(2L)).thenReturn(Optional.of(prerequisito));
        when(prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(1L, 2L)).thenReturn(false);
        when(prerequisitoRepository.findInversePair(1L, 2L)).thenReturn(List.of());

        Prerequisito saved = new Prerequisito();
        saved.setId(100L);
        saved.setMallaMateria(materia);
        saved.setPrerequisito(prerequisito);
        when(prerequisitoRepository.save(any(Prerequisito.class))).thenReturn(saved);

        PrerequisitoResponse response = materiaService.crearPrerequisito(request);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals(1L, response.mallaMateriaId());
        assertEquals(2L, response.prerequisitoMallaMateriaId());
        assertEquals("INF-101", response.codigoMateria());
        assertEquals("Programacion I", response.nombreMateria());
        assertEquals("INF-100", response.codigoPrerequisito());
        assertEquals("Logica", response.nombrePrerequisito());
        assertEquals((short) 3, response.semestreSugeridoMateria());
        assertEquals((short) 2, response.semestreSugeridoPrerequisito());

        verify(prerequisitoRepository, times(1)).save(any(Prerequisito.class));
    }

    private MallaMateria crearMallaMateria(Long id, Long mallaId, String codigo, String nombre, Short semestre) {
        Malla malla = new Malla(mallaId, 1L, "Malla " + mallaId, "v1");
        Materia materia = new Materia(id, codigo, nombre, (short) 4);
        MallaMateria mm = new MallaMateria(id, malla, materia);
        mm.setSemestreSugerido(semestre);
        return mm;
    }
}
