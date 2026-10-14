package com.easyschedule.backend.academico.universidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.universidad.dto.UniversidadResponse;
import com.easyschedule.backend.academico.universidad.model.Universidad;
import com.easyschedule.backend.academico.universidad.repository.UniversidadRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UniversidadService - pruebas unitarias (HU19: consultar universidades activas)")
class UniversidadServiceTest {

    @Mock
    private UniversidadRepository universidadRepository;

    @InjectMocks
    private UniversidadService universidadService;

    @Test
    @DisplayName("CP-01 [HU19] findAllActive mapea cada Universidad a UniversidadResponse conservando los datos")
    void findAllActiveMapeaUniversidades() {
        Universidad universidad = universidad(1L, "Universidad Central", "UC");
        when(universidadRepository.findByActiveTrueOrderByNombreAsc())
            .thenReturn(List.of(universidad));

        List<UniversidadResponse> result = universidadService.findAllActive();

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
            .extracting(UniversidadResponse::id, UniversidadResponse::nombre, UniversidadResponse::codigo)
            .containsExactly(1L, "Universidad Central", "UC");
        verify(universidadRepository).findByActiveTrueOrderByNombreAsc();
    }

    @Test
    @DisplayName("CP-02 [HU19] findAllActive devuelve lista vacia cuando no hay universidades activas")
    void findAllActiveDevuelveListaVacia() {
        when(universidadRepository.findByActiveTrueOrderByNombreAsc()).thenReturn(List.of());

        List<UniversidadResponse> result = universidadService.findAllActive();

        assertThat(result).isEmpty();
        verify(universidadRepository).findByActiveTrueOrderByNombreAsc();
    }

    private Universidad universidad(Long id, String nombre, String codigo) {
        Universidad universidad = new Universidad();
        universidad.setId(id);
        universidad.setNombre(nombre);
        universidad.setCodigo(codigo);
        universidad.setActive(true);
        return universidad;
    }
}
