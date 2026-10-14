package com.easyschedule.backend.academico.malla.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.dto.MallaResponse;
import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MallaService - pruebas unitarias (HU23: consultar mallas activas por carrera)")
class MallaServiceTest {

    @Mock
    private MallaRepository mallaRepository;

    @InjectMocks
    private MallaService mallaService;

    @Test
    @DisplayName("CP-01 [HU23] findActiveByCarrera mapea cada Malla a MallaResponse y delega con el carreraId")
    void findActiveByCarreraMapeaMallas() {
        Malla malla = malla(10L, 1L, "Malla 2024", "1.0", true);
        when(mallaRepository.findByCarreraIdAndActiveTrueOrderByVersionDesc(1L))
            .thenReturn(List.of(malla));

        List<MallaResponse> result = mallaService.findActiveByCarrera(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
            .extracting(MallaResponse::id, MallaResponse::carreraId, MallaResponse::nombre,
                MallaResponse::version, MallaResponse::active)
            .containsExactly(10L, 1L, "Malla 2024", "1.0", true);
        verify(mallaRepository).findByCarreraIdAndActiveTrueOrderByVersionDesc(1L);
    }

    @Test
    @DisplayName("CP-02 [HU23] findActiveByCarrera devuelve lista vacia cuando no hay mallas activas")
    void findActiveByCarreraDevuelveListaVacia() {
        when(mallaRepository.findByCarreraIdAndActiveTrueOrderByVersionDesc(8L)).thenReturn(List.of());

        List<MallaResponse> result = mallaService.findActiveByCarrera(8L);

        assertThat(result).isEmpty();
        verify(mallaRepository).findByCarreraIdAndActiveTrueOrderByVersionDesc(8L);
    }

    private Malla malla(Long id, Long carreraId, String nombre, String version, boolean active) {
        Malla malla = new Malla();
        malla.setId(id);
        malla.setCarreraId(carreraId);
        malla.setNombre(nombre);
        malla.setVersion(version);
        malla.setActive(active);
        return malla;
    }
}
