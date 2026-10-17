package com.easyschedule.backend.academico.selecciontemporal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.easyschedule.backend.academico.selecciontemporal.service.SeleccionTemporalService;
import java.security.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeleccionTemporalController - pruebas unitarias (HU36: limpiar seleccion temporal)")
class SeleccionTemporalControllerTest {

    @Mock
    private SeleccionTemporalService seleccionService;

    @InjectMocks
    private SeleccionTemporalController controller;

    private static Principal principalConNombre(String name) {
        return () -> name;
    }

    @Test
    @DisplayName("ST-01 principal con nombre 7: delega clearSelections(7L) una vez sin excepciones")
    void clearSelectionsDelegaUserId() {
        Principal principal = principalConNombre("7");

        assertDoesNotThrow(() -> controller.clearSelections(principal));

        verify(seleccionService).clearSelections(7L);
    }

    @Test
    @DisplayName("ST-02 principal null: lanza ResponseStatusException 401 y no invoca al servicio")
    void clearSelectionsConPrincipalNullRechaza() {
        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> controller.clearSelections(null));

        assertThat(excepcion.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(excepcion.getReason()).isEqualTo("Sesión inválida");
        verify(seleccionService, never()).clearSelections(any());
    }

    @Test
    @DisplayName("ST-03 principal con getName null: lanza ResponseStatusException 401 y no invoca al servicio")
    void clearSelectionsConNombreNullRechaza() {
        Principal principal = principalConNombre(null);

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> controller.clearSelections(principal));

        assertThat(excepcion.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(excepcion.getReason()).isEqualTo("Sesión inválida");
        verify(seleccionService, never()).clearSelections(any());
    }

    @Test
    @DisplayName("ST-04 principal con nombre no numerico: lanza ResponseStatusException 401 y no invoca al servicio")
    void clearSelectionsConNombreNoNumericoRechaza() {
        Principal principal = principalConNombre("abc");

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> controller.clearSelections(principal));

        assertThat(excepcion.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(excepcion.getReason()).isEqualTo("Sesión inválida");
        verify(seleccionService, never()).clearSelections(any());
    }

    @Test
    @DisplayName("ST35-01 principal con nombre 7 y ofertaMateriaId 42: delega removeSelection(7L, 42L) una vez sin excepciones")
    void removeSelectionDelegaUserIdYOferta() {
        Principal principal = principalConNombre("7");

        assertDoesNotThrow(() -> controller.removeSelection(principal, 42L));

        verify(seleccionService, times(1)).removeSelection(7L, 42L);
    }
}
