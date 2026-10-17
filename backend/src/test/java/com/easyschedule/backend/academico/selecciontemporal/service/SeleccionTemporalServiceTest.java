package com.easyschedule.backend.academico.selecciontemporal.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.selecciontemporal.model.SeleccionTemporal;
import com.easyschedule.backend.academico.selecciontemporal.repository.SeleccionTemporalRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeleccionTemporalService - pruebas unitarias (HU36: limpiar seleccion temporal)")
class SeleccionTemporalServiceTest {

    @Mock
    private SeleccionTemporalRepository seleccionRepository;

    private SeleccionTemporalService seleccionService;

    @BeforeEach
    void setUp() {
        seleccionService = new SeleccionTemporalService(seleccionRepository, null, null, null);
    }

    @Test
    @DisplayName("ST-05 userId 7: invoca deleteByEstudianteId(7L) exactamente una vez sin excepciones")
    void clearSelectionsDelegaAlRepositorio() {
        assertDoesNotThrow(() -> seleccionService.clearSelections(7L));

        verify(seleccionRepository).deleteByEstudianteId(7L);
    }

    @Test
    @DisplayName("ST35-02 seleccion encontrada: busca con (7L,42L) y elimina la seleccion encontrada sin excepciones")
    void removeSelectionEliminaSeleccionEncontrada() {
        SeleccionTemporal seleccion = mock(SeleccionTemporal.class);
        when(seleccionRepository.findByEstudianteIdAndOfertaMateriaId(7L, 42L))
                .thenReturn(Optional.of(seleccion));

        assertDoesNotThrow(() -> seleccionService.removeSelection(7L, 42L));

        verify(seleccionRepository).findByEstudianteIdAndOfertaMateriaId(7L, 42L);
        verify(seleccionRepository, times(1)).delete(seleccion);
    }
}
