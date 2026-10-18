package com.easyschedule.backend.academico.toma_materia.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.toma_materia.dto.TomaMateriaResponse;
import com.easyschedule.backend.academico.toma_materia.service.TomaMateriaService;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pruebas unitarias puras (JUnit 5 + Mockito) para TomaMateriaController.listByCurrentUser.
 *
 * Enfoque: Totalmente desacoplado de servidor HTTP y Spring MVC, evaluando las ramas de decisión:
 * - Camino 1: Principal válido con ID numérico -> Delega a tomaMateriaService.listByUserId(id) y retorna resultado.
 * - Camino 2: Principal nulo -> Lanza ResponseStatusException(UNAUTHORIZED, "Sesion invalida").
 * - Camino 3: Principal.getName() nulo -> Lanza ResponseStatusException(UNAUTHORIZED, "Sesion invalida").
 * - Camino 4: Principal.getName() no numérico -> Lanza ResponseStatusException(UNAUTHORIZED, "Sesion invalida").
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TomaMateriaController - Pruebas Unitarias Puras de Consulta (listByCurrentUser)")
class TomaMateriaConsultaControllerUnitTest {

    @Mock
    private TomaMateriaService tomaMateriaService;

    @InjectMocks
    private TomaMateriaController tomaMateriaController;

    private Principal principalValido;
    private Long authenticatedUserId;

    @BeforeEach
    void setUp() {
        authenticatedUserId = 100L;
        principalValido = () -> String.valueOf(authenticatedUserId);
    }

    @Nested
    @DisplayName("listByCurrentUser(Principal principal) - Delegación y Control de Sesión")
    class ListByCurrentUserTests {

        @Test
        @DisplayName("Camino 1: Principal numérico válido -> Delega al servicio con el userId y retorna la lista")
        void listByCurrentUser_cuandoPrincipalEsValido_delegaEnServicioYRetornaLista() {
            TomaMateriaResponse item = new TomaMateriaResponse(
                    1L,
                    authenticatedUserId,
                    10L,
                    "inscrita",
                    OffsetDateTime.parse("2026-03-01T10:00:00Z"),
                    OffsetDateTime.parse("2026-03-01T10:00:00Z")
            );

            when(tomaMateriaService.listByUserId(authenticatedUserId))
                    .thenReturn(List.of(item));

            List<TomaMateriaResponse> result = tomaMateriaController.listByCurrentUser(principalValido);

            assertThat(result).isNotNull().hasSize(1);
            assertThat(result.get(0).ofertaId()).isEqualTo(10L);
            assertThat(result.get(0).userId()).isEqualTo(authenticatedUserId);

            verify(tomaMateriaService, times(1)).listByUserId(authenticatedUserId);
        }

        @Test
        @DisplayName("Camino 2: Principal numérico válido y estudiante sin inscripciones -> Retorna lista vacía")
        void listByCurrentUser_cuandoNoTieneInscripciones_retornaListaVacia() {
            when(tomaMateriaService.listByUserId(authenticatedUserId))
                    .thenReturn(Collections.emptyList());

            List<TomaMateriaResponse> result = tomaMateriaController.listByCurrentUser(principalValido);

            assertThat(result).isNotNull().isEmpty();

            verify(tomaMateriaService, times(1)).listByUserId(authenticatedUserId);
        }

        @Test
        @DisplayName("Camino 3: Principal es null -> Lanza ResponseStatusException con HTTP 401 UNAUTHORIZED")
        void listByCurrentUser_cuandoPrincipalEsNull_lanzaUnauthorized() {
            assertThatThrownBy(() -> tomaMateriaController.listByCurrentUser(null))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(rse.getReason()).isEqualTo("Sesion invalida");
                    });

            verify(tomaMateriaService, never()).listByUserId(any());
        }

        @Test
        @DisplayName("Camino 4: Principal con getName null -> Lanza ResponseStatusException con HTTP 401 UNAUTHORIZED")
        void listByCurrentUser_cuandoNombreDePrincipalEsNull_lanzaUnauthorized() {
            Principal principalConNombreNull = () -> null;

            assertThatThrownBy(() -> tomaMateriaController.listByCurrentUser(principalConNombreNull))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(rse.getReason()).isEqualTo("Sesion invalida");
                    });

            verify(tomaMateriaService, never()).listByUserId(any());
        }

        @Test
        @DisplayName("Camino 5: Principal con nombre no numérico -> Lanza ResponseStatusException con HTTP 401 UNAUTHORIZED")
        void listByCurrentUser_cuandoNombreNoEsNumerico_lanzaUnauthorized() {
            Principal principalNoNumerico = () -> "usuario_no_id";

            assertThatThrownBy(() -> tomaMateriaController.listByCurrentUser(principalNoNumerico))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(rse.getReason()).isEqualTo("Sesion invalida");
                    });

            verify(tomaMateriaService, never()).listByUserId(any());
        }
    }
}
