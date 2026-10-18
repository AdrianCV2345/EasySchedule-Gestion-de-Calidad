package com.easyschedule.backend.academico.selecciontemporal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.model.MallaMateria;
import com.easyschedule.backend.academico.malla.repository.MallaMateriaRepository;
import com.easyschedule.backend.academico.materia.model.Materia;
import com.easyschedule.backend.academico.oferta_materia.model.OfertaMateria;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import com.easyschedule.backend.academico.selecciontemporal.dto.SeleccionTemporalRequest;
import com.easyschedule.backend.academico.selecciontemporal.dto.SeleccionTemporalResponse;
import com.easyschedule.backend.academico.selecciontemporal.model.SeleccionTemporal;
import com.easyschedule.backend.academico.selecciontemporal.repository.SeleccionTemporalRepository;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeleccionTemporalService - pruebas unitarias (HU33/34/35/36)")
class SeleccionTemporalServiceTest {

    @Mock
    private SeleccionTemporalRepository seleccionRepository;

    @Mock
    private OfertaMateriaRepository ofertaMateriaRepository;

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private MallaMateriaRepository mallaMateriaRepository;

    private SeleccionTemporalService seleccionService;

    @BeforeEach
    void setUp() {
        seleccionService = new SeleccionTemporalService(
            seleccionRepository, ofertaMateriaRepository, estudianteRepository, mallaMateriaRepository);
    }

    @Test
    @DisplayName("ST-05 userId 7: invoca deleteByEstudianteId(7L) exactamente una vez sin excepciones")
    void clearSelectionsDelegaAlRepositorio() {
        assertDoesNotThrow(() -> seleccionService.clearSelections(7L));

        verify(seleccionRepository).deleteByEstudianteId(7L);
    }

    @Test
    @DisplayName("ST35-02 seleccion encontrada: busca con (7L,42L) y elimina la seleccion encontrada sin excepciones")
    void removeSelectionEliminaSeleccionEncontrada() {
        SeleccionTemporal seleccion = mock(SeleccionTemporal.class);
        when(seleccionRepository.findByEstudianteIdAndOfertaMateriaId(7L, 42L))
                .thenReturn(Optional.of(seleccion));

        assertDoesNotThrow(() -> seleccionService.removeSelection(7L, 42L));

        verify(seleccionRepository).findByEstudianteIdAndOfertaMateriaId(7L, 42L);
        verify(seleccionRepository, times(1)).delete(seleccion);
    }

    @Test
    @DisplayName("ST34-02 nueva seleccion (P1): crea, asigna estudiante/oferta/fecha, guarda una vez y retorna respuesta")
    void addSelectionCreaNuevaSeleccion() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(7L);
        OfertaMateria oferta = oferta(42L, 100L, "A");
        MallaMateria mallaMateria = mallaMateria(100L, 500L, "Calculo I");

        SeleccionTemporalRequest request = new SeleccionTemporalRequest();
        request.setOfertaMateriaId(42L);

        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(ofertaMateriaRepository.findById(42L)).thenReturn(Optional.of(oferta));
        when(seleccionRepository.findByEstudianteIdAndMallaMateriaId(7L, 100L)).thenReturn(Optional.empty());
        when(seleccionRepository.save(any(SeleccionTemporal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mallaMateriaRepository.findById(100L)).thenReturn(Optional.of(mallaMateria));

        SeleccionTemporalResponse resultado = seleccionService.addSelection(7L, request);

        ArgumentCaptor<SeleccionTemporal> captor = ArgumentCaptor.forClass(SeleccionTemporal.class);
        verify(seleccionRepository, times(1)).save(captor.capture());
        SeleccionTemporal guardada = captor.getValue();
        assertSame(estudiante, guardada.getEstudiante());
        assertSame(oferta, guardada.getOfertaMateria());
        assertThat(guardada.getFechaSeleccion()).isNotNull();

        assertThat(resultado.getOfertaMateriaId()).isEqualTo(42L);
        assertThat(resultado.getMateriaId()).isEqualTo(500L);
        assertThat(resultado.getMateriaNombre()).isEqualTo("Calculo I");
        assertThat(resultado.getParalelo()).isEqualTo("A");
    }

    @Test
    @DisplayName("ST34-03 mismo paralelo (P2): reutiliza la seleccion sin guardar y conserva oferta y fecha")
    void addSelectionMismoParaleloEsIdempotente() {
        OfertaMateria oferta = oferta(42L, 100L, "A");
        OffsetDateTime fechaOriginal = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        SeleccionTemporal seleccion = new SeleccionTemporal();
        seleccion.setId(9L);
        seleccion.setOfertaMateria(oferta);
        seleccion.setFechaSeleccion(fechaOriginal);

        MallaMateria mallaMateria = mallaMateria(100L, 500L, "Calculo I");
        SeleccionTemporalRequest request = new SeleccionTemporalRequest();
        request.setOfertaMateriaId(42L);

        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(new Estudiante()));
        when(ofertaMateriaRepository.findById(42L)).thenReturn(Optional.of(oferta));
        when(seleccionRepository.findByEstudianteIdAndMallaMateriaId(7L, 100L)).thenReturn(Optional.of(seleccion));
        when(mallaMateriaRepository.findById(100L)).thenReturn(Optional.of(mallaMateria));

        SeleccionTemporalResponse resultado = seleccionService.addSelection(7L, request);

        verify(seleccionRepository, never()).save(any(SeleccionTemporal.class));
        assertSame(oferta, seleccion.getOfertaMateria());
        assertThat(seleccion.getFechaSeleccion()).isEqualTo(fechaOriginal);
        assertThat(resultado.getId()).isEqualTo(9L);
        assertThat(resultado.getOfertaMateriaId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("ST34-04 otro paralelo (P3): reemplaza oferta sobre la misma entidad, actualiza fecha y guarda una vez")
    void addSelectionReemplazaParaleloExistente() {
        OfertaMateria ofertaVieja = oferta(30L, 100L, "A");
        OfertaMateria ofertaNueva = oferta(42L, 100L, "B");
        OffsetDateTime fechaOriginal = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        SeleccionTemporal seleccion = new SeleccionTemporal();
        seleccion.setId(9L);
        seleccion.setOfertaMateria(ofertaVieja);
        seleccion.setFechaSeleccion(fechaOriginal);

        MallaMateria mallaMateria = mallaMateria(100L, 500L, "Calculo I");
        SeleccionTemporalRequest request = new SeleccionTemporalRequest();
        request.setOfertaMateriaId(42L);

        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(new Estudiante()));
        when(ofertaMateriaRepository.findById(42L)).thenReturn(Optional.of(ofertaNueva));
        when(seleccionRepository.findByEstudianteIdAndMallaMateriaId(7L, 100L)).thenReturn(Optional.of(seleccion));
        when(seleccionRepository.save(any(SeleccionTemporal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mallaMateriaRepository.findById(100L)).thenReturn(Optional.of(mallaMateria));

        SeleccionTemporalResponse resultado = seleccionService.addSelection(7L, request);

        ArgumentCaptor<SeleccionTemporal> captor = ArgumentCaptor.forClass(SeleccionTemporal.class);
        verify(seleccionRepository, times(1)).save(captor.capture());
        assertSame(seleccion, captor.getValue());
        assertSame(ofertaNueva, seleccion.getOfertaMateria());
        assertThat(seleccion.getFechaSeleccion()).isAfter(fechaOriginal);
        assertThat(resultado.getOfertaMateriaId()).isEqualTo(42L);
        assertThat(resultado.getParalelo()).isEqualTo("B");
    }

    private OfertaMateria oferta(Long id, Long mallaMateriaId, String paralelo) {
        OfertaMateria oferta = new OfertaMateria();
        oferta.setId(id);
        oferta.setMallaMateriaId(mallaMateriaId);
        oferta.setParalelo(paralelo);
        oferta.setDocente("Docente " + paralelo);
        oferta.setHorarioJson("[]");
        oferta.setAula("Aula " + paralelo);
        return oferta;
    }

    private MallaMateria mallaMateria(Long id, Long materiaId, String nombreMateria) {
        Materia materia = new Materia(materiaId, "MAT" + materiaId, nombreMateria, (short) 4);
        return new MallaMateria(id, null, materia);
    }
}
