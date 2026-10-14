package com.easyschedule.backend.academico.universidad.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.universidad.dto.UniversidadRequest;
import com.easyschedule.backend.academico.universidad.dto.UniversidadResponse;
import com.easyschedule.backend.academico.universidad.model.Universidad;
import com.easyschedule.backend.academico.universidad.repository.UniversidadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Suite de Pruebas Unitarias con JUnit 5, Mockito y AssertJ para la Historia de Usuario:
 * "HU#20 - Crear Universidad".
 *
 * Valida UniversidadService.createUniversidad() cubriendo los caminos independientes
 * identificados (validaciones de nombre/codigo, normalizacion y estado activo por defecto).
 * La persistencia se simula con Mockito; la validacion declarativa de @Valid y el manejo
 * HTTP quedan fuera de esta prueba unitaria.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HU#20 - Crear Universidad: UniversidadService.createUniversidad()")
class UniversidadServiceCreateUniversidadUnitTest {

    @Mock
    private UniversidadRepository universidadRepository;

    @InjectMocks
    private UniversidadService universidadService;

    @Test
    @DisplayName("CP01: nombre null -> rechaza con 'El nombre de la universidad es requerido'")
    void cp01_nombreNull_lanzaExcepcion() {
        UniversidadRequest request = new UniversidadRequest(null, "UCB", true);

        assertThatThrownBy(() -> universidadService.createUniversidad(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("El nombre de la universidad es requerido");

        verify(universidadRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP02: nombre en blanco -> rechaza con 'El nombre de la universidad es requerido'")
    void cp02_nombreBlanco_lanzaExcepcion() {
        UniversidadRequest request = new UniversidadRequest("   ", "UCB", true);

        assertThatThrownBy(() -> universidadService.createUniversidad(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("El nombre de la universidad es requerido");

        verify(universidadRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP03: codigo null -> rechaza con 'El codigo de la universidad es requerido'")
    void cp03_codigoNull_lanzaExcepcion() {
        UniversidadRequest request =
            new UniversidadRequest("Universidad Católica Boliviana", null, true);

        assertThatThrownBy(() -> universidadService.createUniversidad(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("El codigo de la universidad es requerido");

        verify(universidadRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP04: codigo en blanco -> rechaza con 'El codigo de la universidad es requerido'")
    void cp04_codigoBlanco_lanzaExcepcion() {
        UniversidadRequest request =
            new UniversidadRequest("Universidad Católica Boliviana", "   ", true);

        assertThatThrownBy(() -> universidadService.createUniversidad(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("El codigo de la universidad es requerido");

        verify(universidadRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP05: active=false explicito -> normaliza datos y conserva active=false")
    void cp05_activeFalseExplicito_normalizaYConservaEstado() {
        UniversidadRequest request =
            new UniversidadRequest(" Universidad Católica Boliviana ", " ucb ", false);
        stubSaveAssigningId(10L);

        UniversidadResponse response = universidadService.createUniversidad(request);

        assertThat(response).isEqualTo(
            new UniversidadResponse(10L, "Universidad Católica Boliviana", "UCB"));

        ArgumentCaptor<Universidad> captor = captureSaved();
        Universidad entidad = captor.getValue();
        assertThat(entidad.getNombre()).isEqualTo("Universidad Católica Boliviana");
        assertThat(entidad.getCodigo()).isEqualTo("UCB");
        assertThat(entidad.isActive()).isFalse();
    }

    @Test
    @DisplayName("CP06: active=null -> normaliza datos y activa por defecto active=true")
    void cp06_activeNull_activaPorDefecto() {
        UniversidadRequest request =
            new UniversidadRequest(" Universidad Católica Boliviana ", " ucb ", null);
        stubSaveAssigningId(10L);

        UniversidadResponse response = universidadService.createUniversidad(request);

        assertThat(response).isEqualTo(
            new UniversidadResponse(10L, "Universidad Católica Boliviana", "UCB"));

        ArgumentCaptor<Universidad> captor = captureSaved();
        Universidad entidad = captor.getValue();
        assertThat(entidad.getNombre()).isEqualTo("Universidad Católica Boliviana");
        assertThat(entidad.getCodigo()).isEqualTo("UCB");
        assertThat(entidad.isActive()).isTrue();
    }

    private void stubSaveAssigningId(Long id) {
        when(universidadRepository.save(any(Universidad.class))).thenAnswer(invocation -> {
            Universidad universidad = invocation.getArgument(0);
            universidad.setId(id);
            return universidad;
        });
    }

    private ArgumentCaptor<Universidad> captureSaved() {
        ArgumentCaptor<Universidad> captor = ArgumentCaptor.forClass(Universidad.class);
        verify(universidadRepository).save(captor.capture());
        return captor;
    }
}
