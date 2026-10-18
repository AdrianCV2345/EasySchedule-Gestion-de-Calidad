package com.easyschedule.backend.academico.estado_materia.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.estado_materia.dto.EstadoMateriaRequest;
import com.easyschedule.backend.academico.estado_materia.dto.EstadoMateriaResponse;
import com.easyschedule.backend.academico.estado_materia.model.EstadoMateria;
import com.easyschedule.backend.academico.estado_materia.repository.EstadoMateriaRepository;
import com.easyschedule.backend.academico.materia.model.Materia;
import com.easyschedule.backend.academico.materia.model.Prerequisito;
import com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository;
import com.easyschedule.backend.academico.malla.model.MallaMateria;
import com.easyschedule.backend.auth.models.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EstadoMateriaRegisterUnitTest {

    @Mock
    private EstadoMateriaRepository estadoMateriaRepository;

    @Mock
    private PrerequisitoRepository prerequisitoRepository;

    @Mock
    private EstadoMateriaService selfService;

    @InjectMocks
    private EstadoMateriaService service;

    private User user;
    private MallaMateria targetMatter;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(7L);
        user.setUsername("juan");
        targetMatter = new MallaMateria();
        targetMatter.setId(10L);
    }

    @Test
    void givenCursandoState_whenSaveEstado_thenRejectsWithoutUpsert() {
        EstadoMateriaRequest request = new EstadoMateriaRequest(10L, " CURSANDO ");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.saveEstado(7L, request)
        );

        assertEquals(
            "El estado 'cursando' se asigna automaticamente al tomar la materia. Solo se permite cambiar a 'aprobada' o 'pendiente'.",
            exception.getMessage()
        );
        verify(estadoMateriaRepository, never()).upsertEstado(any(), any(), any());
    }

    @Test
    void givenNonApprovedState_whenSaveEstado_thenUpsertsAndReturnsState() {
        EstadoMateriaRequest request = new EstadoMateriaRequest(10L, "pendiente");
        EstadoMateria saved = savedState("pendiente", targetMatter);
        when(estadoMateriaRepository.findByUserIdAndMallaMateria_Id(7L, 10L))
            .thenReturn(Optional.of(saved));

        EstadoMateriaResponse response = service.saveEstado(7L, request);

        verify(estadoMateriaRepository).upsertEstado(7L, 10L, "pendiente");
        assertEquals("pendiente", response.estado());
        assertEquals(7L, response.userId());
        assertEquals(10L, response.mallaMateriaId());
    }

    @Test
    void givenNullState_whenSaveEstado_thenUsesEmptyNormalizedStateAndUpserts() {
        EstadoMateriaRequest request = new EstadoMateriaRequest(10L, null);
        EstadoMateria saved = savedState(null, targetMatter);
        when(estadoMateriaRepository.findByUserIdAndMallaMateria_Id(7L, 10L))
            .thenReturn(Optional.of(saved));

        service.saveEstado(7L, request);

        verify(estadoMateriaRepository).upsertEstado(7L, 10L, null);
    }

    @Test
    void givenApprovedStateWithoutPrerequisites_whenSaveEstado_thenUpsertsState() {
        EstadoMateriaRequest request = new EstadoMateriaRequest(10L, " aprobada ");
        EstadoMateria saved = savedState("aprobada", targetMatter);
        when(prerequisitoRepository.findByMallaMateria_Id(10L)).thenReturn(List.of());
        when(estadoMateriaRepository.findByUserIdAndMallaMateria_Id(7L, 10L))
            .thenReturn(Optional.of(saved));

        EstadoMateriaResponse response = service.saveEstado(7L, request);

        verify(prerequisitoRepository).findByMallaMateria_Id(10L);
        verify(estadoMateriaRepository).upsertEstado(7L, 10L, " aprobada ");
        assertEquals("aprobada", response.estado());
    }

    @Test
    void givenApprovedStateWithApprovedPrerequisites_whenSaveEstado_thenUpsertsState() {
        MallaMateria prerequisiteMatter = prerequisiteMatter(4L, "Algebra");
        Prerequisito prerequisite = new Prerequisito(1L, targetMatter, prerequisiteMatter);
        EstadoMateria prerequisiteState = savedState("aprobada", prerequisiteMatter);
        EstadoMateria saved = savedState("aprobada", targetMatter);

        when(prerequisitoRepository.findByMallaMateria_Id(10L)).thenReturn(List.of(prerequisite));
        when(estadoMateriaRepository.findByUserIdAndMallaMateria_Id(7L, 4L))
            .thenReturn(Optional.of(prerequisiteState));
        when(estadoMateriaRepository.findByUserIdAndMallaMateria_Id(7L, 10L))
            .thenReturn(Optional.of(saved));

        service.saveEstado(7L, new EstadoMateriaRequest(10L, "aprobada"));

        verify(estadoMateriaRepository).upsertEstado(7L, 10L, "aprobada");
    }

    @Test
    void givenApprovedStateWithMissingPrerequisite_whenSaveEstado_thenRejectsWithoutUpsert() {
        MallaMateria prerequisiteMatter = prerequisiteMatter(4L, "Algebra");
        Prerequisito prerequisite = new Prerequisito(1L, targetMatter, prerequisiteMatter);
        when(prerequisitoRepository.findByMallaMateria_Id(10L)).thenReturn(List.of(prerequisite));
        when(estadoMateriaRepository.findByUserIdAndMallaMateria_Id(7L, 4L))
            .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> service.saveEstado(7L, new EstadoMateriaRequest(10L, "aprobada"))
        );

        assertEquals(
            "No se puede cambiar a completado. Primero debe completar el prerequisito: Algebra",
            exception.getMessage()
        );
        verify(estadoMateriaRepository, never()).upsertEstado(any(), any(), any());
    }

    @Test
    void givenNonApprovedPrerequisiteState_whenSaveEstado_thenRejectsWithoutUpsert() {
        MallaMateria prerequisiteMatter = prerequisiteMatter(4L, "Algebra");
        Prerequisito prerequisite = new Prerequisito(1L, targetMatter, prerequisiteMatter);
        when(prerequisitoRepository.findByMallaMateria_Id(10L)).thenReturn(List.of(prerequisite));
        when(estadoMateriaRepository.findByUserIdAndMallaMateria_Id(7L, 4L))
            .thenReturn(Optional.of(savedState("pendiente", prerequisiteMatter)));

        assertThrows(
            IllegalArgumentException.class,
            () -> service.saveEstado(7L, new EstadoMateriaRequest(10L, "aprobada"))
        );

        verify(estadoMateriaRepository, never()).upsertEstado(any(), any(), any());
    }

    @Test
    void givenEmptyBatch_whenSaveEstados_thenReturnsEmptyListAndDoesNotInvokeSaveEstado() {
        List<EstadoMateriaResponse> result = service.saveEstados(7L, List.of());

        assertTrue(result.isEmpty());
        verify(selfService, never()).saveEstado(any(), any());
    }

    @Test
    void givenNullStateInBatch_whenSaveEstados_thenInvokesSaveEstadoForNullState() {
        EstadoMateriaRequest nullState = new EstadoMateriaRequest(10L, null);
        EstadoMateriaResponse expected = new EstadoMateriaResponse(21L, 7L, 10L, null, OffsetDateTime.now());
        when(selfService.saveEstado(7L, nullState)).thenReturn(expected);

        List<EstadoMateriaResponse> result = service.saveEstados(7L, List.of(nullState));

        assertEquals(List.of(expected), result);
        verify(selfService, times(1)).saveEstado(7L, nullState);
    }

    @Test
    void givenBatchWithCursandoAndAllowedStates_whenSaveEstados_thenFiltersCursandoAndUsesProxy() {
        EstadoMateriaRequest cursando = new EstadoMateriaRequest(10L, "cursando");
        EstadoMateriaRequest pendiente = new EstadoMateriaRequest(11L, "pendiente");
        EstadoMateriaResponse expected = new EstadoMateriaResponse(21L, 7L, 11L, "pendiente", OffsetDateTime.now());
        when(selfService.saveEstado(7L, pendiente)).thenReturn(expected);

        List<EstadoMateriaResponse> result = service.saveEstados(7L, List.of(cursando, pendiente));

        assertEquals(List.of(expected), result);
        verify(selfService).saveEstado(7L, pendiente);
        verify(selfService, never()).saveEstado(7L, cursando);
    }

    @Test
    void givenSavedStateNotFound_whenSaveEstado_thenThrowsRuntimeException() {
        EstadoMateriaRequest request = new EstadoMateriaRequest(10L, "pendiente");
        when(estadoMateriaRepository.findByUserIdAndMallaMateria_Id(7L, 10L))
            .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
            RuntimeException.class,
            () -> service.saveEstado(7L, request)
        );

        assertEquals("Estado no guardado", exception.getMessage());
        verify(estadoMateriaRepository).upsertEstado(7L, 10L, "pendiente");
        verify(estadoMateriaRepository).findByUserIdAndMallaMateria_Id(7L, 10L);
    }

    private EstadoMateria savedState(String state, MallaMateria matter) {
        EstadoMateria saved = new EstadoMateria();
        saved.setId(20L);
        saved.setUser(user);
        saved.setMallaMateria(matter);
        saved.setEstado(state);
        saved.setFechaActualizacion(OffsetDateTime.now());
        return saved;
    }

    private MallaMateria prerequisiteMatter(Long id, String name) {
        MallaMateria matter = new MallaMateria();
        matter.setId(id);
        Materia materia = new Materia();
        materia.setNombre(name);
        matter.setMateria(materia);
        return matter;
    }
}
