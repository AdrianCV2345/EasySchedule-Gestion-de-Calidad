package com.easyschedule.backend.academico.carrera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.carrera.dto.CarreraRequest;
import com.easyschedule.backend.academico.carrera.dto.CarreraResponse;
import com.easyschedule.backend.academico.carrera.model.Carrera;
import com.easyschedule.backend.academico.carrera.repository.CarreraRepository;
import com.easyschedule.backend.academico.universidad.repository.UniversidadRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CarreraService - pruebas unitarias (mapeo y reglas de negocio)")
class CarreraServiceTest {

    @Mock
    private CarreraRepository carreraRepository;

    @Mock
    private UniversidadRepository universidadRepository;

    @InjectMocks
    private CarreraService carreraService;

    @Test
    @DisplayName("CR-01 [P1] findActiveByUniversidad mapea cada Carrera a CarreraResponse en el orden recibido")
    void findActiveByUniversidadMapeaCarreras() {
        Carrera sistemas = carrera(1L, 2L, "Ingeniería de Sistemas", "IS", true);
        Carrera medicina = carrera(2L, 2L, "Medicina", "MED", true);
        when(carreraRepository.findByUniversidadIdAndActiveTrueOrderByNombreAsc(2L))
            .thenReturn(List.of(sistemas, medicina));

        List<CarreraResponse> result = carreraService.findActiveByUniversidad(2L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0))
            .extracting(CarreraResponse::id, CarreraResponse::universidadId, CarreraResponse::nombre, CarreraResponse::codigo)
            .containsExactly(1L, 2L, "Ingeniería de Sistemas", "IS");
        assertThat(result.get(1))
            .extracting(CarreraResponse::id, CarreraResponse::universidadId, CarreraResponse::nombre, CarreraResponse::codigo)
            .containsExactly(2L, 2L, "Medicina", "MED");
        verify(carreraRepository).findByUniversidadIdAndActiveTrueOrderByNombreAsc(2L);
    }

    @Test
    @DisplayName("CR-02 [P1] findActiveByUniversidad devuelve lista vacía cuando no existen carreras")
    void findActiveByUniversidadDevuelveListaVacia() {
        when(carreraRepository.findByUniversidadIdAndActiveTrueOrderByNombreAsc(8L)).thenReturn(List.of());

        List<CarreraResponse> result = carreraService.findActiveByUniversidad(8L);

        assertThat(result).isEmpty();
        verify(carreraRepository).findByUniversidadIdAndActiveTrueOrderByNombreAsc(8L);
    }

    @Test
    @DisplayName("CR-03 [P3] createCarrera guarda con trim, codigo en mayusculas y active=true por defecto, y devuelve la respuesta")
    void createCarreraGuardaYDevuelveMapeado() {
        CarreraRequest request = new CarreraRequest(3L, "  Ingeniería de Sistemas  ", " is ", null);
        when(universidadRepository.existsById(3L)).thenReturn(true);
        Carrera guardada = carrera(10L, 3L, "Ingeniería de Sistemas", "IS", true);
        when(carreraRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(guardada);

        CarreraResponse result = carreraService.createCarrera(request);

        ArgumentCaptor<Carrera> captor = ArgumentCaptor.forClass(Carrera.class);
        verify(carreraRepository).save(captor.capture());
        Carrera toSave = captor.getValue();
        assertThat(toSave.getNombre()).isEqualTo("Ingeniería de Sistemas");
        assertThat(toSave.getCodigo()).isEqualTo("IS");
        assertThat(toSave.isActive()).isTrue();

        assertThat(result)
            .extracting(CarreraResponse::id, CarreraResponse::universidadId, CarreraResponse::nombre, CarreraResponse::codigo)
            .containsExactly(10L, 3L, "Ingeniería de Sistemas", "IS");
        verify(universidadRepository).existsById(3L);
    }

    @Test
    @DisplayName("CR-04 [P3] createCarrera respeta active=false cuando viene explisito")
    void createCarreraRespetaActiveFalse() {
        CarreraRequest request = new CarreraRequest(3L, "Derecho", "DER", false);
        when(universidadRepository.existsById(3L)).thenReturn(true);
        Carrera guardada = carrera(11L, 3L, "Derecho", "DER", false);
        when(carreraRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(guardada);

        CarreraResponse result = carreraService.createCarrera(request);

        ArgumentCaptor<Carrera> captor = ArgumentCaptor.forClass(Carrera.class);
        verify(carreraRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(result.codigo()).isEqualTo("DER");
    }

    @Test
    @DisplayName("CR-05 [P3] createCarrera lanza excepcion si el nombre esta en blanco y no toca la BD")
    void createCarreraNombreEnBlancoLanzaExcepcion() {
        CarreraRequest request = new CarreraRequest(3L, "  ", "IS", true);

        assertThatThrownBy(() -> carreraService.createCarrera(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("El nombre de la carrera es requerido");

        verify(universidadRepository, never()).existsById(org.mockito.ArgumentMatchers.any());
        verify(carreraRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("CR-06 [P3] createCarrera lanza excepcion si el codigo esta en blanco")
    void createCarreraCodigoEnBlancoLanzaExcepcion() {
        CarreraRequest request = new CarreraRequest(3L, "Ingeniería", "  ", true);

        assertThatThrownBy(() -> carreraService.createCarrera(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("El codigo de la carrera es requerido");

        verify(carreraRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("CR-07 [P3] createCarrera lanza excepcion si universidadId es nulo")
    void createCarreraUniversidadIdNuloLanzaExcepcion() {
        CarreraRequest request = new CarreraRequest(null, "Ingeniería", "IS", true);

        assertThatThrownBy(() -> carreraService.createCarrera(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("El ID de la universidad es requerido");

        verify(carreraRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("CR-08 [P3] createCarrera lanza excepcion si la universidad no existe")
    void createCarreraUniversidadNoExisteLanzaExcepcion() {
        CarreraRequest request = new CarreraRequest(99L, "Ingeniería", "IS", true);
        when(universidadRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> carreraService.createCarrera(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("La universidad con ID 99 no existe");

        verify(carreraRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("CR-09 [P3] createCarrera lanza excepcion si el nombre es nulo")
    void createCarreraNombreNuloLanzaExcepcion() {
        CarreraRequest request = new CarreraRequest(3L, null, "IS", true);

        assertThatThrownBy(() -> carreraService.createCarrera(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("El nombre de la carrera es requerido");

        verify(carreraRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("CR-10 [P3] createCarrera lanza excepcion si el codigo es nulo")
    void createCarreraCodigoNuloLanzaExcepcion() {
        CarreraRequest request = new CarreraRequest(3L, "Ingeniería", null, true);

        assertThatThrownBy(() -> carreraService.createCarrera(request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("El codigo de la carrera es requerido");

        verify(carreraRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Carrera carrera(Long id, Long universidadId, String nombre, String codigo, boolean active) {
        Carrera carrera = new Carrera();
        carrera.setId(id);
        carrera.setUniversidadId(universidadId);
        carrera.setNombre(nombre);
        carrera.setCodigo(codigo);
        carrera.setActive(active);
        return carrera;
    }
}