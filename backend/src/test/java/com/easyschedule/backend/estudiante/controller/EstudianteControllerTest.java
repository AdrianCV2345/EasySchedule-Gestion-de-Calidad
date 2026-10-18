package com.easyschedule.backend.estudiante.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.estudiante.dto.EstudianteResponse;
import com.easyschedule.backend.estudiante.dto.PerfilUpdateRequest;
import com.easyschedule.backend.estudiante.service.EstudianteMallaExportService;
import com.easyschedule.backend.estudiante.service.EstudianteService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstudianteController - pruebas unitarias (HU15: consulta y control de acceso; HU16: edicion de perfil)")
class EstudianteControllerTest {

    @Mock
    private EstudianteService estudianteService;

    @Mock
    private EstudianteMallaExportService exportService;

    @InjectMocks
    private EstudianteController controller;

    private static Principal principalConNombre(String name) {
        return () -> name;
    }

    private static EstudianteResponse perfilJuan() {
        return new EstudianteResponse(1L, "juan", "Juan", "Pérez", "juan@correo.com", "CI123", null,
            OffsetDateTime.parse("2024-01-01T00:00:00Z"), (short) 1, 2L, 3L, null, false, false);
    }

    @Test
    @DisplayName("EC-01 principal numerico propietario: consulta autorizada y respuesta del servicio")
    void findProfileByUsernameConPrincipalPropietarioAutoriza() {
        Principal principal = principalConNombre("1");
        EstudianteResponse esperado = perfilJuan();
        when(estudianteService.canAccessProfile("juan", 1L)).thenReturn(true);
        when(estudianteService.findByUsername("juan")).thenReturn(esperado);

        EstudianteResponse resultado = controller.findProfileByUsername("juan", principal);

        assertThat(resultado).isSameAs(esperado);
        verify(estudianteService).canAccessProfile("juan", 1L);
        verify(estudianteService).findByUsername("juan");
    }

    @Test
    @DisplayName("EC-02 principal null: responde UNAUTHORIZED y no invoca al servicio")
    void findProfileByUsernameConPrincipalNullRechaza() {
        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> controller.findProfileByUsername("juan", null));

        assertThat(excepcion.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(estudianteService);
    }

    @Test
    @DisplayName("EC-03 principal con getName null: responde UNAUTHORIZED")
    void findProfileByUsernameConNombreNullRechaza() {
        Principal principal = principalConNombre(null);

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> controller.findProfileByUsername("juan", principal));

        assertThat(excepcion.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(estudianteService);
    }

    @Test
    @DisplayName("EC-04 principal con getName no numerico: responde UNAUTHORIZED")
    void findProfileByUsernameConNombreNoNumericoRechaza() {
        Principal principal = principalConNombre("abc");

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> controller.findProfileByUsername("juan", principal));

        assertThat(excepcion.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(estudianteService);
    }

    @Test
    @DisplayName("EC-05 id autenticado distinto al propietario: responde FORBIDDEN y no consulta el perfil")
    void findProfileByUsernameConPropietarioDistintoRechaza() {
        Principal principal = principalConNombre("1");
        when(estudianteService.canAccessProfile("juan", 1L)).thenReturn(false);

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> controller.findProfileByUsername("juan", principal));

        assertThat(excepcion.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(estudianteService).canAccessProfile("juan", 1L);
        verify(estudianteService, never()).findByUsername(anyString());
    }

    private static PerfilUpdateRequest perfilUpdateRequest() {
        return new PerfilUpdateRequest("juan", "Juan", "Pérez", "juan@correo.com", "CI123",
            LocalDate.of(2000, 1, 1), null, null);
    }

    @Test
    @DisplayName("HU16-01 propietario autorizado: actualiza el perfil y devuelve la respuesta del servicio")
    void updateProfileConPropietarioAutorizado() {
        Principal principal = principalConNombre("1");
        PerfilUpdateRequest request = perfilUpdateRequest();
        EstudianteResponse esperado = new EstudianteResponse(1L, "juan", "Juan Carlos", "Pérez López",
            "juan@correo.com", "CI123", LocalDate.of(2000, 1, 1), OffsetDateTime.parse("2024-01-01T00:00:00Z"),
            (short) 1, 2L, 3L, null, true, false);
        when(estudianteService.canAccessProfile("juan", 1L)).thenReturn(true);
        when(estudianteService.updateProfile("juan", request)).thenReturn(esperado);

        EstudianteResponse resultado = controller.updateProfile("juan", request, principal);

        assertThat(resultado).isSameAs(esperado);
        verify(estudianteService).canAccessProfile("juan", 1L);
        verify(estudianteService).updateProfile("juan", request);
    }

    @Test
    @DisplayName("HU16-02 sin propiedad: responde FORBIDDEN y no invoca updateProfile")
    void updateProfileSinPropietarioRechaza() {
        Principal principal = principalConNombre("1");
        PerfilUpdateRequest request = perfilUpdateRequest();
        when(estudianteService.canAccessProfile("juan", 1L)).thenReturn(false);

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> controller.updateProfile("juan", request, principal));

        assertThat(excepcion.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(estudianteService).canAccessProfile("juan", 1L);
        verify(estudianteService, never()).updateProfile(anyString(), any());
    }
}
