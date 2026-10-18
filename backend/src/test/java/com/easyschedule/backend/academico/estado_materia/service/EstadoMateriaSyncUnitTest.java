package com.easyschedule.backend.academico.estado_materia.service;

import static org.mockito.Mockito.verify;

import com.easyschedule.backend.academico.estado_materia.repository.EstadoMateriaRepository;
import com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EstadoMateriaSyncUnitTest {

    @Mock
    private EstadoMateriaRepository estadoMateriaRepository;

    @Mock
    private PrerequisitoRepository prerequisitoRepository;

    @Mock
    private EstadoMateriaService selfService;

    @InjectMocks
    private EstadoMateriaService service;

    @Test
    void givenNullTomaState_whenSync_thenStoresCursando() {
        service.syncEstadoFromToma(7L, 10L, null);

        verify(estadoMateriaRepository).upsertEstado(7L, 10L, "cursando");
    }

    @Test
    void givenInscritaTomaState_whenSync_thenStoresCursando() {
        service.syncEstadoFromToma(7L, 10L, "inscrita");

        verify(estadoMateriaRepository).upsertEstado(7L, 10L, "cursando");
    }

    @Test
    void givenAprobadaTomaState_whenSync_thenStoresAprobada() {
        service.syncEstadoFromToma(7L, 10L, " aprobada ");

        verify(estadoMateriaRepository).upsertEstado(7L, 10L, "aprobada");
    }

    @Test
    void givenRetiradaTomaState_whenSync_thenStoresPendiente() {
        service.syncEstadoFromToma(7L, 10L, "retirada");

        verify(estadoMateriaRepository).upsertEstado(7L, 10L, "pendiente");
    }

    @Test
    void givenUnknownTomaState_whenSync_thenStoresCursandoByDefault() {
        service.syncEstadoFromToma(7L, 10L, "estado-desconocido");

        verify(estadoMateriaRepository).upsertEstado(7L, 10L, "cursando");
    }
}
