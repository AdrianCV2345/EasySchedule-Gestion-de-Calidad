package com.easyschedule.backend.academico.horario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.horario.dto.HorarioActualResponse;
import com.easyschedule.backend.academico.horario.dto.HorarioClaseResponse;
import com.easyschedule.backend.academico.horario.dto.HorarioGeneradoResponse;
import com.easyschedule.backend.academico.horario.dto.HorarioGeneradorRequest;
import com.easyschedule.backend.academico.horario.dto.MateriaSeleccionadaRequest;
import com.easyschedule.backend.academico.malla.dto.MallaMateriaResponse;
import com.easyschedule.backend.academico.malla.service.MallaDisponibilidadService;
import com.easyschedule.backend.academico.oferta_materia.model.OfertaMateria;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class HorarioGeneradorServiceTest {

    @Mock
    private MallaDisponibilidadService mallaDisponibilidadService;

    @Mock
    private OfertaMateriaRepository ofertaMateriaRepository;

    @Mock
    private HorarioRecomendadoService horarioRecomendadoService;

    private HorarioGeneradorService service;

    @BeforeEach
    void setUp() {
        service = new HorarioGeneradorService(
            mallaDisponibilidadService,
            ofertaMateriaRepository,
            horarioRecomendadoService,
            new ObjectMapper()
        );
    }

    // ==================================================================
    // Helpers de construcción de objetos
    // ==================================================================

    private MallaMateriaResponse materiaDisponible(Long id, Long materiaId, String nombre) {
        return new MallaMateriaResponse(id, materiaId, "COD-" + id, nombre, (short) 4,
            (short) 1, "pendiente", List.of());
    }

    private OfertaMateria oferta(Long id, Long mallaMateriaId, String paralelo, String horarioJson) {
        OfertaMateria o = new OfertaMateria();
        o.setId(id);
        o.setMallaMateriaId(mallaMateriaId);
        o.setSemestre("2025-1");
        o.setParalelo(paralelo);
        o.setDocente("Docente " + paralelo);
        o.setAula("Aula " + paralelo);
        o.setHorarioJson(horarioJson);
        return o;
    }

    private HorarioGeneradorRequest request(Long mallaId, Long userId,
                                            List<MateriaSeleccionadaRequest> seleccionadas,
                                            List<String> prioridades) {
        return new HorarioGeneradorRequest(userId, mallaId, seleccionadas, prioridades);
    }

    // ==================================================================
    // Pruebas HU#46
    // ==================================================================

    // HU46-01: generarHorarios con lista de seleccionadas vacía → materiasConParalelos vacío → []
    @Test
    void generarHorariosConSeleccionadasVaciaRetornaListaVacia() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of());

        List<HorarioGeneradoResponse> resultado = service.generarHorarios(request(1L, 1L, List.of(), List.of()));

        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    // HU46-02: generarHorarios con materia seleccionada no disponible → ResponseStatusException 400
    @Test
    void generarHorariosConMateriaNoDisponibleLanzaExcepcion() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(materiaDisponible(10L, 100L, "Matemáticas")));

        List<MateriaSeleccionadaRequest> seleccionadas = List.of(
            new MateriaSeleccionadaRequest(999L, List.of())
        );

        assertThrows(ResponseStatusException.class,
            () -> service.generarHorarios(request(1L, 1L, seleccionadas, List.of())));
    }

    // HU46-03: generarHorarios caso feliz con 1 materia y 1 paralelo sin cruce
    @Test
    void generarHorariosCasoFelizUnaMateriaSinPrioridades() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(materiaDisponible(10L, 100L, "Matemáticas")));
        when(ofertaMateriaRepository.findByMallaMateriaId(10L))
            .thenReturn(List.of(oferta(1L, 10L, "A",
                "[{\"dia\":\"Lunes\",\"inicio\":\"08:00\",\"fin\":\"10:00\"}]")));
        when(horarioRecomendadoService.getHorarioActualByUserId(1L))
            .thenReturn(new HorarioActualResponse(null, null, null, null, null, List.of()));

        List<HorarioGeneradoResponse> resultado =
            service.generarHorarios(request(1L, 1L,
                List.of(new MateriaSeleccionadaRequest(10L, null)), List.of()));

        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        assertEquals("Matemáticas", resultado.get(0).clases().get(0).materia());
        assertEquals("A", resultado.get(0).clases().get(0).paralelo());
        assertEquals("Lunes", resultado.get(0).clases().get(0).dia());
        assertEquals("08:00", resultado.get(0).clases().get(0).horaInicio());
        assertEquals("10:00", resultado.get(0).clases().get(0).horaFin());
    }

    // HU46-04: generarHorarios con 2 materias que tienen cruce de horario → se omite la combinación con cruce
    @Test
    void generarHorariosConCruceOmiteCombinacion() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(
                materiaDisponible(10L, 100L, "Matemáticas"),
                materiaDisponible(20L, 101L, "Física")
            ));
        when(ofertaMateriaRepository.findByMallaMateriaId(10L))
            .thenReturn(List.of(oferta(1L, 10L, "A",
                "[{\"dia\":\"Lunes\",\"inicio\":\"08:00\",\"fin\":\"10:00\"}]")));
        when(ofertaMateriaRepository.findByMallaMateriaId(20L))
            .thenReturn(List.of(oferta(2L, 20L, "A",
                "[{\"dia\":\"Lunes\",\"inicio\":\"09:00\",\"fin\":\"11:00\"}]")));
        when(horarioRecomendadoService.getHorarioActualByUserId(1L))
            .thenReturn(new HorarioActualResponse(null, null, null, null, null, List.of()));

        List<HorarioGeneradoResponse> resultado =
            service.generarHorarios(request(1L, 1L,
                List.of(
                    new MateriaSeleccionadaRequest(10L, null),
                    new MateriaSeleccionadaRequest(20L, null)
                ), List.of()));

        // Con cruce, no hay combinación válida → lista vacía
        assertTrue(resultado.isEmpty());
    }

    // HU46-05: generarHorarios con prioridad EVITAR_PRIMERA_HORA y clase a las 07:00 → penalización
    @Test
    void generarHorariosConPrioridadEvitarPrimeraHora() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(materiaDisponible(10L, 100L, "Matemáticas")));
        when(ofertaMateriaRepository.findByMallaMateriaId(10L))
            .thenReturn(List.of(oferta(1L, 10L, "A",
                "[{\"dia\":\"Lunes\",\"inicio\":\"07:00\",\"fin\":\"08:00\"}]")));
        when(horarioRecomendadoService.getHorarioActualByUserId(1L))
            .thenReturn(new HorarioActualResponse(null, null, null, null, null, List.of()));

        List<HorarioGeneradoResponse> resultado =
            service.generarHorarios(request(1L, 1L,
                List.of(new MateriaSeleccionadaRequest(10L, null)),
                List.of("EVITAR_PRIMERA_HORA")));

        assertEquals(1, resultado.size());
        // Penalización: 1.0 * 10000 = 10000.0
        assertEquals(10000.0, resultado.get(0).puntajeTotal(), 0.001);
    }

    // HU46-06: generarHorarios con prioridad MINIMIZAR_VENTANAS y clases con hueco → penalización
    @Test
    void generarHorariosConPrioridadMinimizarVentanas() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(materiaDisponible(10L, 100L, "Matemáticas")));
        when(ofertaMateriaRepository.findByMallaMateriaId(10L))
            .thenReturn(List.of(oferta(1L, 10L, "A",
                "[{\"dia\":\"Lunes\",\"inicio\":\"08:00\",\"fin\":\"09:00\"}," +
                "{\"dia\":\"Lunes\",\"inicio\":\"11:00\",\"fin\":\"12:00\"}]")));
        when(horarioRecomendadoService.getHorarioActualByUserId(1L))
            .thenReturn(new HorarioActualResponse(null, null, null, null, null, List.of()));

        List<HorarioGeneradoResponse> resultado =
            service.generarHorarios(request(1L, 1L,
                List.of(new MateriaSeleccionadaRequest(10L, null)),
                List.of("MINIMIZAR_VENTANAS")));

        assertEquals(1, resultado.size());
        // 1 hora de hueco entre 09:00 y 11:00, 1 día de clase
        // puntajeBruto = 2 / 1.75 / 1 = 1.1428...
        // multiplicador[0] = 10000 → puntajeTotal ≈ 11428.57
        assertTrue(resultado.get(0).puntajeTotal() > 0);
    }

    // HU46-07: generarHorarios con prioridades null → puntaje 0.0
    @Test
    void generarHorariosConPrioridadesNulas() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(materiaDisponible(10L, 100L, "Matemáticas")));
        when(ofertaMateriaRepository.findByMallaMateriaId(10L))
            .thenReturn(List.of(oferta(1L, 10L, "A",
                "[{\"dia\":\"Lunes\",\"inicio\":\"08:00\",\"fin\":\"10:00\"}]")));
        when(horarioRecomendadoService.getHorarioActualByUserId(1L))
            .thenReturn(new HorarioActualResponse(null, null, null, null, null, List.of()));

        List<HorarioGeneradoResponse> resultado =
            service.generarHorarios(request(1L, 1L,
                List.of(new MateriaSeleccionadaRequest(10L, null)), null));

        assertEquals(1, resultado.size());
        assertEquals(0.0, resultado.get(0).puntajeTotal(), 0.001);
    }

    // HU46-08: generarHorarios con horario actual del estudiante con clases (mapActualToParalelos)
    @Test
    void generarHorariosConHorarioActualDelEstudiante() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(materiaDisponible(10L, 100L, "Matemáticas")));
        when(ofertaMateriaRepository.findByMallaMateriaId(10L))
            .thenReturn(List.of(oferta(1L, 10L, "A",
                "[{\"dia\":\"Martes\",\"inicio\":\"14:00\",\"fin\":\"16:00\"}]")));
        when(horarioRecomendadoService.getHorarioActualByUserId(1L))
            .thenReturn(new HorarioActualResponse("UTB", "Sistemas", "Malla 1", "2025-1",
                (short) 3,
                List.of(new HorarioClaseResponse("Física", "B", "Lunes",
                    "08:00", "10:00", "Docente", "Aula 2", 4, true, 50L))));

        List<HorarioGeneradoResponse> resultado =
            service.generarHorarios(request(1L, 1L,
                List.of(new MateriaSeleccionadaRequest(10L, null)), List.of()));

        assertEquals(1, resultado.size());
        assertEquals(2, resultado.get(0).clases().size());
        // La clase del horario actual debe aparecer con esMateriaActual=true
        assertTrue(resultado.get(0).clases().stream()
            .anyMatch(c -> c.materia().equals("Física") && c.esMateriaActual()));
    }

    // HU46-09: parseHorarioJson con JSON malformado → lista vacía (probado vía toParalelo/buildParalelos)
    @Test
    void generarHorariosConJsonMalformadoRetornaSinClases() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(materiaDisponible(10L, 100L, "Matemáticas")));
        when(ofertaMateriaRepository.findByMallaMateriaId(10L))
            .thenReturn(List.of(oferta(1L, 10L, "A", "{invalid json")));
        when(horarioRecomendadoService.getHorarioActualByUserId(1L))
            .thenReturn(new HorarioActualResponse(null, null, null, null, null, List.of()));

        List<HorarioGeneradoResponse> resultado =
            service.generarHorarios(request(1L, 1L,
                List.of(new MateriaSeleccionadaRequest(10L, null)), List.of()));

        assertEquals(1, resultado.size());
        assertTrue(resultado.get(0).clases().isEmpty());
    }

    // HU46-10: parseHorarioJson con JSON que no es arreglo → lista vacía
    @Test
    void generarHorariosConJsonNoArregloRetornaSinClases() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(materiaDisponible(10L, 100L, "Matemáticas")));
        when(ofertaMateriaRepository.findByMallaMateriaId(10L))
            .thenReturn(List.of(oferta(1L, 10L, "A", "{\"dia\":\"Lunes\"}")));
        when(horarioRecomendadoService.getHorarioActualByUserId(1L))
            .thenReturn(new HorarioActualResponse(null, null, null, null, null, List.of()));

        List<HorarioGeneradoResponse> resultado =
            service.generarHorarios(request(1L, 1L,
                List.of(new MateriaSeleccionadaRequest(10L, null)), List.of()));

        assertEquals(1, resultado.size());
        assertTrue(resultado.get(0).clases().isEmpty());
    }

    // HU46-11: parseHorarioJson con claves alternativas hora_inicio/hora_fin
    @Test
    void generarHorariosConClavesAlternativasHora() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(materiaDisponible(10L, 100L, "Matemáticas")));
        when(ofertaMateriaRepository.findByMallaMateriaId(10L))
            .thenReturn(List.of(oferta(1L, 10L, "A",
                "[{\"dia\":\"Lunes\",\"hora_inicio\":\"08:00\",\"hora_fin\":\"10:00\"}]")));
        when(horarioRecomendadoService.getHorarioActualByUserId(1L))
            .thenReturn(new HorarioActualResponse(null, null, null, null, null, List.of()));

        List<HorarioGeneradoResponse> resultado =
            service.generarHorarios(request(1L, 1L,
                List.of(new MateriaSeleccionadaRequest(10L, null)), List.of()));

        assertEquals(1, resultado.size());
        assertEquals(1, resultado.get(0).clases().size());
        assertEquals("08:00", resultado.get(0).clases().get(0).horaInicio());
        assertEquals("10:00", resultado.get(0).clases().get(0).horaFin());
    }

    // HU46-12: generarHorarios con todas las prioridades → cubre evaluarConcentrarManana,
    // evaluarConcentrarTarde, evaluarTenerDiasLibres y calcularPuntaje con switch completo
    @Test
    void generarHorariosConTodasLasPrioridades() {
        when(mallaDisponibilidadService.getMateriasDisponibles(1L, 1L))
            .thenReturn(List.of(materiaDisponible(10L, 100L, "Matemáticas")));
        when(ofertaMateriaRepository.findByMallaMateriaId(10L))
            .thenReturn(List.of(oferta(1L, 10L, "A",
                "[{\"dia\":\"Lunes\",\"inicio\":\"07:00\",\"fin\":\"08:00\"}," +
                "{\"dia\":\"Lunes\",\"inicio\":\"14:00\",\"fin\":\"15:00\"}," +
                "{\"dia\":\"Martes\",\"inicio\":\"08:00\",\"fin\":\"09:00\"}]")));
        when(horarioRecomendadoService.getHorarioActualByUserId(1L))
            .thenReturn(new HorarioActualResponse(null, null, null, null, null, List.of()));

        List<HorarioGeneradoResponse> resultado =
            service.generarHorarios(request(1L, 1L,
                List.of(new MateriaSeleccionadaRequest(10L, null)),
                List.of("EVITAR_PRIMERA_HORA", "CONCENTRAR_MANANA",
                    "CONCENTRAR_TARDE", "MINIMIZAR_VENTANAS", "TENER_DIAS_LIBRES")));

        assertEquals(1, resultado.size());
        // EVITAR_PRIMERA_HORA: 1 clase a las 07:00 en Lunes → penalty 1 * 10000 = 10000
        // CONCENTRAR_MANANA: 1 clase a las 14:00 (>=13:00) → penalty 1 * 1000 = 1000
        // CONCENTRAR_TARDE: 1 clase a las 08:00 (<14:00) → penalty 1 * 100 = 100
        // MINIMIZAR_VENTANAS: huecos en Lunes y Martes → penalty * 10
        // TENER_DIAS_LIBRES: 2 días → 2 * 1 = 2
        assertTrue(resultado.get(0).puntajeTotal() > 10000);
    }
}
