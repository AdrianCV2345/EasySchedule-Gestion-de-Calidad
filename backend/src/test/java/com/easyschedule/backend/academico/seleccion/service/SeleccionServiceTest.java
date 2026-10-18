package com.easyschedule.backend.academico.seleccion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.carrera.model.Carrera;
import com.easyschedule.backend.academico.carrera.repository.CarreraRepository;
import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import com.easyschedule.backend.academico.seleccion.dto.SeleccionRequest;
import com.easyschedule.backend.academico.seleccion.dto.SeleccionResponse;
import com.easyschedule.backend.academico.universidad.model.Universidad;
import com.easyschedule.backend.academico.universidad.repository.UniversidadRepository;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeleccionService - pruebas unitarias (HU31: consulta / HU32: guardado de seleccion academica)")
class SeleccionServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private UniversidadRepository universidadRepository;

    @Mock
    private CarreraRepository carreraRepository;

    @Mock
    private MallaRepository mallaRepository;

    @InjectMocks
    private SeleccionService seleccionService;

    @Test
    @DisplayName("SC31-02 universidad no configurada: retorno anticipado con seis atributos null sin consultar catalogos")
    void universidadNoConfiguradaRetornaSeleccionVacia() {
        Estudiante estudiante = estudiante(null, null, null);
        when(estudianteRepository.findById(USER_ID)).thenReturn(Optional.of(estudiante));

        SeleccionResponse resultado = seleccionService.getSeleccionByUserId(USER_ID);

        assertThat(resultado)
            .extracting(SeleccionResponse::universidadId, SeleccionResponse::universidad,
                SeleccionResponse::carreraId, SeleccionResponse::carrera,
                SeleccionResponse::mallaId, SeleccionResponse::malla)
            .containsExactly(null, null, null, null, null, null);
        verifyNoInteractions(universidadRepository, carreraRepository, mallaRepository);
    }

    @Test
    @DisplayName("SC31-03 carrera no configurada: retorno anticipado con seis atributos null sin consultar catalogos")
    void carreraNoConfiguradaRetornaSeleccionVacia() {
        Estudiante estudiante = estudiante(1L, null, null);
        when(estudianteRepository.findById(USER_ID)).thenReturn(Optional.of(estudiante));

        SeleccionResponse resultado = seleccionService.getSeleccionByUserId(USER_ID);

        assertThat(resultado)
            .extracting(SeleccionResponse::universidadId, SeleccionResponse::universidad,
                SeleccionResponse::carreraId, SeleccionResponse::carrera,
                SeleccionResponse::mallaId, SeleccionResponse::malla)
            .containsExactly(null, null, null, null, null, null);
        verifyNoInteractions(universidadRepository, carreraRepository, mallaRepository);
    }

    @Test
    @DisplayName("SC31-04 malla no configurada: retorno anticipado con seis atributos null sin consultar catalogos")
    void mallaNoConfiguradaRetornaSeleccionVacia() {
        Estudiante estudiante = estudiante(1L, 2L, null);
        when(estudianteRepository.findById(USER_ID)).thenReturn(Optional.of(estudiante));

        SeleccionResponse resultado = seleccionService.getSeleccionByUserId(USER_ID);

        assertThat(resultado)
            .extracting(SeleccionResponse::universidadId, SeleccionResponse::universidad,
                SeleccionResponse::carreraId, SeleccionResponse::carrera,
                SeleccionResponse::mallaId, SeleccionResponse::malla)
            .containsExactly(null, null, null, null, null, null);
        verifyNoInteractions(universidadRepository, carreraRepository, mallaRepository);
    }

    @Test
    @DisplayName("SC31-05 seleccion completa: retorna ids y nombres de universidad, carrera y malla con etiqueta del nombre")
    void seleccionCompletaRetornaDatosYEtiquetaDeMalla() {
        Estudiante estudiante = estudiante(1L, 2L, malla(3L, 2L, "Ingeniería de Sistemas", "2026"));
        when(estudianteRepository.findById(USER_ID)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(universidad(1L, "Universidad Mayor")));
        when(carreraRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(carrera(2L, "Ingeniería de Sistemas")));
        when(mallaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(malla(3L, 2L, "Ingeniería de Sistemas", "2026")));

        SeleccionResponse resultado = seleccionService.getSeleccionByUserId(USER_ID);

        assertThat(resultado)
            .extracting(SeleccionResponse::universidadId, SeleccionResponse::universidad,
                SeleccionResponse::carreraId, SeleccionResponse::carrera,
                SeleccionResponse::mallaId, SeleccionResponse::malla)
            .containsExactly(1L, "Universidad Mayor", 2L, "Ingeniería de Sistemas", 3L, "Ingeniería de Sistemas");
        verify(universidadRepository).findByIdAndActiveTrue(1L);
        verify(carreraRepository).findByIdAndActiveTrue(2L);
        verify(mallaRepository).findByIdAndActiveTrue(3L);
    }

    @Test
    @DisplayName("SC31-06 nombre de malla null: etiqueta 'Malla 2026' y demas valores correctos")
    void nombreDeMallaNullUsaEtiquetaConVersion() {
        Estudiante estudiante = estudiante(1L, 2L, malla(3L, 2L, null, "2026"));
        when(estudianteRepository.findById(USER_ID)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(universidad(1L, "Universidad Mayor")));
        when(carreraRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(carrera(2L, "Ingeniería de Sistemas")));
        when(mallaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(malla(3L, 2L, null, "2026")));

        SeleccionResponse resultado = seleccionService.getSeleccionByUserId(USER_ID);

        assertThat(resultado)
            .extracting(SeleccionResponse::universidadId, SeleccionResponse::universidad,
                SeleccionResponse::carreraId, SeleccionResponse::carrera,
                SeleccionResponse::mallaId, SeleccionResponse::malla)
            .containsExactly(1L, "Universidad Mayor", 2L, "Ingeniería de Sistemas", 3L, "Malla 2026");
    }

    @Test
    @DisplayName("SC31-07 nombre de malla en blanco: etiqueta 'Malla 2026'")
    void nombreDeMallaEnBlancoUsaEtiquetaConVersion() {
        Estudiante estudiante = estudiante(1L, 2L, malla(3L, 2L, "   ", "2026"));
        when(estudianteRepository.findById(USER_ID)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(universidad(1L, "Universidad Mayor")));
        when(carreraRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(carrera(2L, "Ingeniería de Sistemas")));
        when(mallaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(malla(3L, 2L, "   ", "2026")));

        SeleccionResponse resultado = seleccionService.getSeleccionByUserId(USER_ID);

        assertThat(resultado.malla()).isEqualTo("Malla 2026");
        assertThat(resultado.universidadId()).isEqualTo(1L);
        assertThat(resultado.carreraId()).isEqualTo(2L);
        assertThat(resultado.mallaId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("SC32-02 carrera no especificada y primera malla: usa la carrera de la malla, asigna todo y reinicia semestre a 1")
    void carreraNoEspecificadaUsaCarreraDeLaMalla() {
        SeleccionRequest request = new SeleccionRequest(1L, null, 3L);
        Malla malla = malla(3L, 2L, "Ingeniería de Sistemas", "2026");
        Carrera carreraDeMalla = carrera(2L, 1L, "Ingeniería de Sistemas");
        Estudiante estudiante = estudiante(null, null, null);

        when(universidadRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(universidad(1L, "Universidad Mayor")));
        when(mallaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(malla));
        when(carreraRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(carreraDeMalla));
        when(estudianteRepository.findById(USER_ID)).thenReturn(Optional.of(estudiante));

        SeleccionResponse resultado = seleccionService.saveSeleccionByUserId(USER_ID, request);

        ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
        verify(estudianteRepository, times(1)).save(captor.capture());
        Estudiante guardado = captor.getValue();
        assertThat(guardado.getUniversidadId()).isEqualTo(1L);
        assertThat(guardado.getCarreraId()).isEqualTo(2L);
        assertThat(guardado.getMalla()).isSameAs(malla);
        assertThat(guardado.getSemestreActual()).isEqualTo((short) 1);

        assertThat(resultado)
            .extracting(SeleccionResponse::universidadId, SeleccionResponse::universidad,
                SeleccionResponse::carreraId, SeleccionResponse::carrera,
                SeleccionResponse::mallaId, SeleccionResponse::malla)
            .containsExactly(1L, "Universidad Mayor", 2L, "Ingeniería de Sistemas", 3L, "Ingeniería de Sistemas");
    }

    @Test
    @DisplayName("SC32-03 cambio a malla distinta: reemplaza malla, actualiza ids y reinicia semestre a 1")
    void cambioDeMallaReiniciaSemestre() {
        SeleccionRequest request = new SeleccionRequest(1L, null, 3L);
        Malla mallaAnterior = malla(10L, 2L, "Malla antigua", "2025");
        Malla mallaNueva = malla(3L, 2L, "Ingeniería de Sistemas", "2026");
        Estudiante estudiante = estudiante(2L, 9L, mallaAnterior);
        estudiante.setSemestreActual((short) 5);

        when(universidadRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(universidad(1L, "Universidad Mayor")));
        when(mallaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(mallaNueva));
        when(carreraRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(carrera(2L, 1L, "Ingeniería de Sistemas")));
        when(estudianteRepository.findById(USER_ID)).thenReturn(Optional.of(estudiante));

        seleccionService.saveSeleccionByUserId(USER_ID, request);

        ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
        verify(estudianteRepository, times(1)).save(captor.capture());
        Estudiante guardado = captor.getValue();
        assertThat(guardado.getUniversidadId()).isEqualTo(1L);
        assertThat(guardado.getCarreraId()).isEqualTo(2L);
        assertThat(guardado.getMalla()).isSameAs(mallaNueva);
        assertThat(guardado.getSemestreActual()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("SC32-04 misma malla: conserva semestre 5 y guarda igualmente una vez")
    void mismaMallaConservaSemestre() {
        SeleccionRequest request = new SeleccionRequest(1L, null, 10L);
        Malla mallaActual = malla(10L, 2L, "Ingeniería de Sistemas", "2026");
        Estudiante estudiante = estudiante(1L, 2L, mallaActual);
        estudiante.setSemestreActual((short) 5);

        when(universidadRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(universidad(1L, "Universidad Mayor")));
        when(mallaRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(mallaActual));
        when(carreraRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(carrera(2L, 1L, "Ingeniería de Sistemas")));
        when(estudianteRepository.findById(USER_ID)).thenReturn(Optional.of(estudiante));

        SeleccionResponse resultado = seleccionService.saveSeleccionByUserId(USER_ID, request);

        ArgumentCaptor<Estudiante> captor = ArgumentCaptor.forClass(Estudiante.class);
        verify(estudianteRepository, times(1)).save(captor.capture());
        Estudiante guardado = captor.getValue();
        assertThat(guardado.getSemestreActual()).isEqualTo((short) 5);
        assertThat(guardado.getMalla()).isSameAs(mallaActual);
        assertThat(resultado.mallaId()).isEqualTo(10L);
        assertThat(resultado.carreraId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("SC32-05 carrera explicita valida igual a la de la malla: supera validaciones y guarda la seleccion")
    void carreraExplicitaValidaCorrespondienteALaMalla() {
        SeleccionRequest request = new SeleccionRequest(1L, 2L, 3L);
        Malla malla = malla(3L, 2L, "Ingeniería de Sistemas", "2026");
        Carrera carreraDeMalla = carrera(2L, 1L, "Ingeniería de Sistemas");
        Estudiante estudiante = estudiante(null, null, null);

        when(universidadRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(universidad(1L, "Universidad Mayor")));
        when(mallaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(malla));
        when(carreraRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(carreraDeMalla));
        when(estudianteRepository.findById(USER_ID)).thenReturn(Optional.of(estudiante));

        SeleccionResponse resultado = seleccionService.saveSeleccionByUserId(USER_ID, request);

        verify(estudianteRepository, times(1)).save(any(Estudiante.class));
        assertThat(resultado)
            .extracting(SeleccionResponse::universidadId, SeleccionResponse::universidad,
                SeleccionResponse::carreraId, SeleccionResponse::carrera,
                SeleccionResponse::mallaId, SeleccionResponse::malla)
            .containsExactly(1L, "Universidad Mayor", 2L, "Ingeniería de Sistemas", 3L, "Ingeniería de Sistemas");
    }

    @Test
    @DisplayName("SC32-06 carrera de la malla de otra universidad: 400 BAD_REQUEST y no guarda")
    void carreraDeMallaDeOtraUniversidadLanza400() {
        SeleccionRequest request = new SeleccionRequest(1L, null, 3L);
        Malla malla = malla(3L, 2L, "Ingeniería de Sistemas", "2026");

        when(universidadRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(universidad(1L, "Universidad Mayor")));
        when(mallaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(malla));
        when(carreraRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(carrera(2L, 2L, "Otra carrera")));

        assertThatThrownBy(() -> seleccionService.saveSeleccionByUserId(USER_ID, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).isEqualTo("La malla no pertenece a la universidad seleccionada");
            });

        verify(estudianteRepository, never()).save(any(Estudiante.class));
        verify(estudianteRepository, never()).findById(any());
    }

    @Test
    @DisplayName("SC32-07 carrera solicitada de otra universidad: 400 BAD_REQUEST y no guarda")
    void carreraSolicitadaDeOtraUniversidadLanza400() {
        SeleccionRequest request = new SeleccionRequest(1L, 20L, 3L);
        Malla malla = malla(3L, 2L, "Ingeniería de Sistemas", "2026");

        when(universidadRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(universidad(1L, "Universidad Mayor")));
        when(mallaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(malla));
        when(carreraRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(carrera(2L, 1L, "Ingeniería de Sistemas")));
        when(carreraRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(carrera(20L, 2L, "Otra carrera")));

        assertThatThrownBy(() -> seleccionService.saveSeleccionByUserId(USER_ID, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).isEqualTo("La carrera no pertenece a la universidad seleccionada");
            });

        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    @Test
    @DisplayName("SC32-08 carrera solicitada distinta a la de la malla: 400 BAD_REQUEST y no guarda")
    void carreraSolicitadaDistintaALaDeLaMallaLanza400() {
        SeleccionRequest request = new SeleccionRequest(1L, 20L, 3L);
        Malla malla = malla(3L, 10L, "Ingeniería de Sistemas", "2026");

        when(universidadRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(universidad(1L, "Universidad Mayor")));
        when(mallaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(malla));
        when(carreraRepository.findByIdAndActiveTrue(10L)).thenReturn(Optional.of(carrera(10L, 1L, "Ingeniería de Sistemas")));
        when(carreraRepository.findByIdAndActiveTrue(20L)).thenReturn(Optional.of(carrera(20L, 1L, "Otra carrera")));

        assertThatThrownBy(() -> seleccionService.saveSeleccionByUserId(USER_ID, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).isEqualTo("La malla no pertenece a la carrera seleccionada");
            });

        verify(estudianteRepository, never()).save(any(Estudiante.class));
    }

    private Estudiante estudiante(Long universidadId, Long carreraId, Malla malla) {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(USER_ID);
        estudiante.setUniversidadId(universidadId);
        estudiante.setCarreraId(carreraId);
        estudiante.setMalla(malla);
        return estudiante;
    }

    private Carrera carrera(Long id, Long universidadId, String nombre) {
        Carrera carrera = new Carrera();
        carrera.setId(id);
        carrera.setUniversidadId(universidadId);
        carrera.setNombre(nombre);
        carrera.setActive(true);
        return carrera;
    }

    private Universidad universidad(Long id, String nombre) {
        Universidad universidad = new Universidad();
        universidad.setId(id);
        universidad.setNombre(nombre);
        universidad.setActive(true);
        return universidad;
    }

    private Carrera carrera(Long id, String nombre) {
        Carrera carrera = new Carrera();
        carrera.setId(id);
        carrera.setNombre(nombre);
        carrera.setActive(true);
        return carrera;
    }

    private Malla malla(Long id, Long carreraId, String nombre, String version) {
        Malla malla = new Malla(id, carreraId, nombre, version);
        malla.setActive(true);
        return malla;
    }
}
