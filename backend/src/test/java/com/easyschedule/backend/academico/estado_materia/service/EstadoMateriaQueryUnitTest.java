package com.easyschedule.backend.academico.estado_materia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.estado_materia.dto.EstadoMateriaResponse;
import com.easyschedule.backend.academico.estado_materia.model.EstadoMateria;
import com.easyschedule.backend.academico.estado_materia.repository.EstadoMateriaRepository;
import com.easyschedule.backend.academico.malla.model.MallaMateria;
import com.easyschedule.backend.auth.models.User;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EstadoMateriaQueryUnitTest {

    @Mock
    private EstadoMateriaRepository estadoMateriaRepository;

    @Mock
    private com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository prerequisitoRepository;

    @Mock
    private EstadoMateriaService selfService;

    @InjectMocks
    private EstadoMateriaService service;

    private EstadoMateria estado;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(7L);
        MallaMateria mallaMateria = new MallaMateria();
        mallaMateria.setId(10L);

        estado = new EstadoMateria();
        estado.setId(20L);
        estado.setUser(user);
        estado.setMallaMateria(mallaMateria);
        estado.setEstado("aprobada");
        estado.setFechaActualizacion(OffsetDateTime.now());
    }

    @Test
    void givenStoredStates_whenGetByUserId_thenMapsAndReturnsThem() {
        when(estadoMateriaRepository.findByUser_Id(7L)).thenReturn(List.of(estado));

        List<EstadoMateriaResponse> result = service.getEstadosByUserId(7L);

        assertEquals(1, result.size());
        assertEquals(20L, result.get(0).id());
        assertEquals(7L, result.get(0).userId());
        assertEquals(10L, result.get(0).mallaMateriaId());
        assertEquals("aprobada", result.get(0).estado());
        verify(estadoMateriaRepository).findByUser_Id(7L);
    }

    @Test
    void givenNoStoredStates_whenGetByUserId_thenReturnsEmptyList() {
        when(estadoMateriaRepository.findByUser_Id(7L)).thenReturn(List.of());

        List<EstadoMateriaResponse> result = service.getEstadosByUserId(7L);

        assertTrue(result.isEmpty());
        verify(estadoMateriaRepository).findByUser_Id(7L);
    }

    @Test
    void givenStatesForMalla_whenGetByMalla_thenDelegatesWithBothIdentifiers() {
        when(estadoMateriaRepository.findByUserIdAndMallaId(7L, 3L)).thenReturn(List.of(estado));

        List<EstadoMateriaResponse> result = service.getEstadosByMalla(7L, 3L);

        assertEquals(1, result.size());
        assertEquals("aprobada", result.get(0).estado());
        verify(estadoMateriaRepository).findByUserIdAndMallaId(7L, 3L);
    }

    @Test
    void givenNoStatesForMalla_whenGetByMalla_thenReturnsEmptyList() {
        when(estadoMateriaRepository.findByUserIdAndMallaId(7L, 3L)).thenReturn(List.of());

        List<EstadoMateriaResponse> result = service.getEstadosByMalla(7L, 3L);

        assertTrue(result.isEmpty());
        verify(estadoMateriaRepository).findByUserIdAndMallaId(7L, 3L);
    }
}
