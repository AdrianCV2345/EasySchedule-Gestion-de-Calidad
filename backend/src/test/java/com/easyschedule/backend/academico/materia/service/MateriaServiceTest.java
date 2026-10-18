package com.easyschedule.backend.academico.materia.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.repository.MallaMateriaRepository;
import com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository;
import jakarta.persistence.EntityNotFoundException;
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

    @Test
    void eliminarPrerequisitoLanzaExcepcionCuandoRelacionNoExiste() {
        // given
        Long mallaMateriaId = 1L;
        Long prerequisitoMallaMateriaId = 2L;

        when(prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId))
            .thenReturn(false);

        // when / then
        EntityNotFoundException exception = assertThrows(
            EntityNotFoundException.class,
            () -> materiaService.eliminarPrerequisito(mallaMateriaId, prerequisitoMallaMateriaId)
        );

        assertThrows(EntityNotFoundException.class, () -> {
            throw exception;
        });

        verify(prerequisitoRepository).existsByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId);
        verify(prerequisitoRepository, never()).deleteByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId);
    }

    @Test
    void eliminarPrerequisitoBorraRelacionCuandoExiste() {
        // given
        Long mallaMateriaId = 1L;
        Long prerequisitoMallaMateriaId = 2L;

        when(prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId))
            .thenReturn(true);

        // when
        assertDoesNotThrow(() -> materiaService.eliminarPrerequisito(mallaMateriaId, prerequisitoMallaMateriaId));

        // then
        verify(prerequisitoRepository).existsByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId);
        verify(prerequisitoRepository, times(1)).deleteByMallaMateria_IdAndPrerequisito_Id(mallaMateriaId, prerequisitoMallaMateriaId);
    }
}
