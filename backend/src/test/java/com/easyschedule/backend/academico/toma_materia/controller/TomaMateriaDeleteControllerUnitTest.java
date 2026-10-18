package com.easyschedule.backend.academico.toma_materia.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.easyschedule.backend.academico.toma_materia.service.TomaMateriaService;
import java.security.Principal;
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
 * Pruebas unitarias puras para TomaMateriaController.deleteByOferta.
 *
 * Mapeo de caminos:
 * 1. Flujo Exitoso: Principal válido con ID numérico delega a tomaMateriaService.deleteByUserIdAndOfertaId(userId, ofertaId).
 * 2. Principal Nulo: Lanza ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida").
 * 3. Principal con Nombre Nulo: Lanza ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida").
 * 4. Principal con Nombre No Numérico: Lanza ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida").
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TomaMateriaController - Pruebas Unitarias Puras de Eliminación (deleteByOferta)")
class TomaMateriaDeleteControllerUnitTest {

    @Mock
    private TomaMateriaService tomaMateriaService;

    @InjectMocks
    private TomaMateriaController tomaMateriaController;

    private Long userId;
    private Long ofertaId;
    private Principal principalValido;

    @BeforeEach
    void setUp() {
        userId = 15L;
        ofertaId = 200L;
        principalValido = () -> String.valueOf(userId);
    }

    @Nested
    @DisplayName("deleteByOferta(Long ofertaId, Principal principal) - Delegación y Seguridad")
    class DeleteByOfertaTests {

        @Test
        @DisplayName("Flujo de éxito: delega la eliminación al servicio con el userId del Principal y el ofertaId")
        void deleteByOferta_cuandoPrincipalEsValido_delegaCorrectamenteEnServicio() {
            doNothing().when(tomaMateriaService).deleteByUserIdAndOfertaId(userId, ofertaId);

            assertThatCode(() -> tomaMateriaController.deleteByOferta(ofertaId, principalValido))
                    .doesNotThrowAnyException();

            verify(tomaMateriaService, times(1)).deleteByUserIdAndOfertaId(userId, ofertaId);
        }

        @Test
        @DisplayName("Rechazo por sesión nula: Principal == null lanza ResponseStatusException 401 UNAUTHORIZED")
        void deleteByOferta_cuandoPrincipalEsNull_lanzaUnauthorized() {
            assertThatThrownBy(() -> tomaMateriaController.deleteByOferta(ofertaId, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(rse.getReason()).isEqualTo("Sesion invalida");
                    });

            verify(tomaMateriaService, never()).deleteByUserIdAndOfertaId(any(), any());
        }

        @Test
        @DisplayName("Rechazo por credencial nula: Principal.getName() == null lanza ResponseStatusException 401 UNAUTHORIZED")
        void deleteByOferta_cuandoNombrePrincipalEsNull_lanzaUnauthorized() {
            Principal principalConNombreNull = () -> null;

            assertThatThrownBy(() -> tomaMateriaController.deleteByOferta(ofertaId, principalConNombreNull))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(rse.getReason()).isEqualTo("Sesion invalida");
                    });

            verify(tomaMateriaService, never()).deleteByUserIdAndOfertaId(any(), any());
        }

        @Test
        @DisplayName("Rechazo por formato inválido: Principal.getName() no numérico lanza ResponseStatusException 401 UNAUTHORIZED")
        void deleteByOferta_cuandoNombrePrincipalNoEsNumerico_lanzaUnauthorized() {
            Principal principalNoNumerico = () -> "id_alfanumerico";

            assertThatThrownBy(() -> tomaMateriaController.deleteByOferta(ofertaId, principalNoNumerico))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(ex -> {
                        ResponseStatusException rse = (ResponseStatusException) ex;
                        assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                        assertThat(rse.getReason()).isEqualTo("Sesion invalida");
                    });

            verify(tomaMateriaService, never()).deleteByUserIdAndOfertaId(any(), any());
        }
    }
}
