package com.easyschedule.backend.academico.horario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.carrera.model.Carrera;
import com.easyschedule.backend.academico.carrera.repository.CarreraRepository;
import com.easyschedule.backend.academico.horario.dto.HorarioActualResponse;
import com.easyschedule.backend.academico.horario.dto.HorarioClaseResponse;
import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import com.easyschedule.backend.academico.universidad.model.Universidad;
import com.easyschedule.backend.academico.universidad.repository.UniversidadRepository;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * HU#45 — Pruebas unitarias del servicio de horario actual (HX45-09 a HX45-20).
 * ObjectMapper real; auxiliares privados cubiertos indirectamente.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HorarioRecomendadoService - Horario actual y exportacion (HX45)")
class HorarioRecomendadoServiceTest {

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private UniversidadRepository universidadRepository;

    @Mock
    private CarreraRepository carreraRepository;

    @Mock
    private OfertaMateriaRepository ofertaMateriaRepository;

    private HorarioRecomendadoService service;

    private static final String JSON_VALIDO =
            "[{\"dia\":\"Lunes\",\"inicio\":\"08:00\",\"fin\":\"10:00\"}]";

    @BeforeEach
    void setUp() {
        service = new HorarioRecomendadoService(
                estudianteRepository,
                universidadRepository,
                carreraRepository,
                ofertaMateriaRepository,
                new ObjectMapper()
        );
    }

    private Estudiante estudianteBase() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(7L);
        estudiante.setSemestreActual((short) 3);
        Malla malla = new Malla(10L, 1L, "Ingenieria", "2025");
        estudiante.setMalla(malla);
        estudiante.setUniversidadId(5L);
        estudiante.setCarreraId(8L);
        return estudiante;
    }

    private OfertaMateriaRepository.HorarioOfertaRow row(
            String semestre, String materia, String horarioJson) {
        return row(semestre, materia, horarioJson, "Docente", "101", 4, 100L);
    }

    private OfertaMateriaRepository.HorarioOfertaRow row(
            String semestre, String materia, String horarioJson,
            String docente, String aula, Integer creditos, Long ofertaId) {
        return new OfertaMateriaRepository.HorarioOfertaRow() {
            @Override
            public Long getOfertaId() { return ofertaId; }

            @Override
            public String getSemestre() { return semestre; }

            @Override
            public String getParalelo() { return "A"; }

            @Override
            public String getMateriaNombre() { return materia; }

            @Override
            public String getHorarioJson() { return horarioJson; }

            @Override
            public String getDocente() { return docente; }

            @Override
            public String getAula() { return aula; }

            @Override
            public Integer getCreditos() { return creditos; }
        };
    }

    private void stubUniversidadCarrera() {
        Universidad universidad = new Universidad();
        universidad.setNombre("UTB");
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(universidad));
        Carrera carrera = new Carrera();
        carrera.setNombre("Sistemas");
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.of(carrera));
    }

    @Test
    @DisplayName("HX45-09: estudiante inexistente -> horario vacio, hasHorarioActual false, CSV sin filas de clases")
    void hx45_09_estudianteInexistente() {
        when(estudianteRepository.findById(7L)).thenReturn(Optional.empty());

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertNull(horario.universidad());
        assertNull(horario.carrera());
        assertNull(horario.malla());
        assertNull(horario.semestreOferta());
        assertNull(horario.semestreActual());
        assertTrue(horario.clases().isEmpty());
        assertFalse(service.hasHorarioActual(7L));

        String csv = new String(service.buildHorarioActualCsv(7L), StandardCharsets.UTF_8);
        assertTrue(csv.contains("Horario academico"));
        assertTrue(csv.contains("Materia"));
        assertFalse(csv.contains("Lunes"));

        verify(ofertaMateriaRepository, org.mockito.Mockito.never())
                .findHorarioActualRows(anyLong(), anyLong(), anyShort());
        verifyNoInteractions(universidadRepository, carreraRepository);
    }

    @Test
    @DisplayName("HX45-10: estudiante sin malla -> horario sin clases")
    void hx45_10_estudianteSinMalla() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(7L);
        estudiante.setSemestreActual((short) 3);
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertTrue(horario.clases().isEmpty());
        assertNull(horario.semestreOferta());
        verifyNoInteractions(universidadRepository, carreraRepository, ofertaMateriaRepository);
    }

    @Test
    @DisplayName("HX45-11: semestreActual null -> horario sin clases")
    void hx45_11_semestreActualNulo() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(7L);
        Malla malla = new Malla(10L, 1L, "Ingenieria", "2025");
        estudiante.setMalla(malla);
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertTrue(horario.clases().isEmpty());
        assertNull(horario.semestreActual());
        verifyNoInteractions(universidadRepository, carreraRepository, ofertaMateriaRepository);
    }

    @Test
    @DisplayName("HX45-12: malla con id null -> horario sin clases conservando el semestre")
    void hx45_12_mallaIdNulo() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(7L);
        estudiante.setSemestreActual((short) 5);
        Malla malla = new Malla(null, 1L, "Ingenieria", "2025");
        estudiante.setMalla(malla);
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertTrue(horario.clases().isEmpty());
        assertEquals((short) 5, horario.semestreActual());
        verifyNoInteractions(universidadRepository, carreraRepository, ofertaMateriaRepository);
    }

    @Test
    @DisplayName("HX45-13: CSV con comas, comillas, saltos de linea, null y blancos -> escapado correcto y UTF-8")
    void hx45_13_csvCaracteresEspeciales() {
        Estudiante estudiante = estudianteBase();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        stubUniversidadCarrera();

        String json = "[{\"dia\":\"Lunes\",\"inicio\":\"08:00\",\"fin\":\"10:00\"}]";
        OfertaMateriaRepository.HorarioOfertaRow especial = row(
                "3", "Calculo, Integral", json, "Doc \"Juan\"", "Aula\n101", 4, 101L);
        OfertaMateriaRepository.HorarioOfertaRow simple = row(
                "3", "Fisica", json, null, "  ", null, 102L);
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
                .thenReturn(List.of(especial, simple));

        String csv = new String(service.buildHorarioActualCsv(7L), StandardCharsets.UTF_8);

        assertTrue(csv.startsWith("Horario academico\n"));
        assertTrue(csv.contains("Universidad,UTB"));
        assertTrue(csv.contains("Carrera,Sistemas"));
        assertTrue(csv.contains("Malla,Ingenieria"));
        assertTrue(csv.contains("Semestre oferta,3"));
        assertTrue(csv.contains("Semestre actual,3"));
        assertTrue(csv.contains("\"Calculo, Integral\""));
        assertTrue(csv.contains("\"Doc \"\"Juan\"\"\""));
        assertTrue(csv.contains("\"Aula\n101\""));
        assertTrue(csv.contains("Fisica"));
        assertTrue(csv.contains(",,"));
    }

    @Test
    @DisplayName("HX45-14: PDF con una clase valida -> bytes no vacios que empiezan con %PDF-")
    void hx45_14_pdfMarcaOpenPdf() {
        Estudiante estudiante = estudianteBase();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        stubUniversidadCarrera();
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
                .thenReturn(List.of(row("3", "Fisica", JSON_VALIDO)));

        byte[] pdf = service.buildHorarioActualPdf(7L);

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
        String cabecera = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
        assertEquals("%PDF-", cabecera);
    }

    @Test
    @DisplayName("HX45-15: PNG con clase y textos largos -> imagen valida, dimensiones positivas, elipsis ejercitada")
    void hx45_15_pngConTextosLargos() throws Exception {
        Estudiante estudiante = estudianteBase();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        stubUniversidadCarrera();

        String textoLargo = "Materia con nombre extremadamente largo que debe truncarse con elipsis en la imagen";
        OfertaMateriaRepository.HorarioOfertaRow row = row(
                "3", textoLargo, JSON_VALIDO, textoLargo, "Aula", 4, 101L);
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
                .thenReturn(List.of(row));

        byte[] png = service.buildHorarioActualImage(7L);

        assertNotNull(png);
        assertTrue(png.length > 0);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);

        var imagen = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(imagen);
        assertTrue(imagen.getWidth() > 0);
        assertTrue(imagen.getHeight() > 0);
    }

    @Test
    @DisplayName("HX45-16: horarioJson con objeto en vez de arreglo -> se ignora esa oferta")
    void hx45_16_jsonObjetoNoArreglo() {
        Estudiante estudiante = estudianteBase();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        stubUniversidadCarrera();
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
                .thenReturn(List.of(row("3", "Fisica", "{\"dia\":\"Lunes\"}")));

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertTrue(horario.clases().isEmpty());
        assertFalse(service.hasHorarioActual(7L));
    }

    @Test
    @DisplayName("HX45-17: horarioJson malformado -> se ignora sin propagar excepcion")
    void hx45_17_jsonMalformado() {
        Estudiante estudiante = estudianteBase();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        stubUniversidadCarrera();
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
                .thenReturn(List.of(row("3", "Fisica", "{invalid json")));

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertTrue(horario.clases().isEmpty());
        assertFalse(service.hasHorarioActual(7L));
    }

    @Test
    @DisplayName("HX45-18: metadatos incompletos -> valores alternativos '-' en CSV sin perder clases")
    void hx45_18_metadatosIncompletos() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(7L);
        estudiante.setSemestreActual((short) 3);
        estudiante.setMalla(new Malla(10L, 1L, null, null));
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
                .thenReturn(List.of(row("3", "Fisica", JSON_VALIDO)));

        String csv = new String(service.buildHorarioActualCsv(7L), StandardCharsets.UTF_8);

        assertTrue(csv.contains("Universidad,-"));
        assertTrue(csv.contains("Carrera,-"));
        assertTrue(csv.contains("Malla,-"));
        assertTrue(csv.contains("Semestre oferta,3"));
        assertTrue(csv.contains("Fisica"));
    }

    @Test
    @DisplayName("HX45-19: malla en blanco con version 2026 -> etiqueta 'Malla 2026'")
    void hx45_19_etiquetaMallaPorVersion() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(7L);
        estudiante.setSemestreActual((short) 3);
        estudiante.setMalla(new Malla(10L, 1L, "   ", "2026"));
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
                .thenReturn(List.of());

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertEquals("Malla 2026", horario.malla());
    }

    @Test
    @DisplayName("HX45-20: claves alternativas hora_inicio/hora_fin e incompletas -> solo se incluyen las validas")
    void hx45_20_clavesAlternativasYEntradasIncompletas() {
        Estudiante estudiante = estudianteBase();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        stubUniversidadCarrera();

        String json = """
                [
                  {"dia":"Lunes","hora_inicio":"08:00","hora_fin":"10:00"},
                  {"dia":"Martes","inicio":"10:00","fin":"12:00"},
                  {"inicio":"08:00","fin":"10:00"},
                  {"dia":"Miercoles","fin":"10:00"},
                  {"dia":"Jueves","inicio":"08:00"}
                ]
                """;
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
                .thenReturn(List.of(row("3", "Calculo", json)));

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertEquals(2, horario.clases().size());
        HorarioClaseResponse primera = horario.clases().get(0);
        assertEquals("Lunes", primera.dia());
        assertEquals("08:00", primera.horaInicio());
        assertEquals("10:00", primera.horaFin());
        assertTrue(primera.esMateriaActual());
        assertEquals(100L, primera.ofertaMateriaId());
        assertEquals("Calculo", primera.materia());

        HorarioClaseResponse segunda = horario.clases().get(1);
        assertEquals("Martes", segunda.dia());
        assertEquals("10:00", segunda.horaInicio());
        assertEquals("12:00", segunda.horaFin());
    }

    // ==================================================================
    // HU#44 — Consultar horario actual: caminos faltantes del servicio
    // ==================================================================

    @Test
    @DisplayName("HU44-05: caso feliz completo -> universidad, carrera, malla, clases")
    void hu44_05_casoFelizCompleto() {
        Estudiante estudiante = estudianteBase();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        stubUniversidadCarrera();
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
            .thenReturn(List.of(row("3", "Calculo", JSON_VALIDO)));

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertEquals("UTB", horario.universidad());
        assertEquals("Sistemas", horario.carrera());
        assertEquals("Ingenieria", horario.malla());
        assertEquals("3", horario.semestreOferta());
        assertEquals((short) 3, horario.semestreActual());
        assertEquals(1, horario.clases().size());
        assertEquals("Calculo", horario.clases().get(0).materia());
        assertEquals("A", horario.clases().get(0).paralelo());
        assertEquals("Lunes", horario.clases().get(0).dia());
        assertEquals("08:00", horario.clases().get(0).horaInicio());
        assertEquals("10:00", horario.clases().get(0).horaFin());
        assertEquals("Docente", horario.clases().get(0).docente());
        assertEquals("101", horario.clases().get(0).aula());
        assertEquals(4, horario.clases().get(0).creditos());
        assertEquals(100L, horario.clases().get(0).ofertaMateriaId());
        assertTrue(horario.clases().get(0).esMateriaActual());

        verify(estudianteRepository).findById(7L);
        verify(universidadRepository).findByIdAndActiveTrue(5L);
        verify(carreraRepository).findByIdAndActiveTrue(8L);
        verify(ofertaMateriaRepository).findHorarioActualRows(7L, 10L, (short) 3);
    }

    @Test
    @DisplayName("HU44-06: universidad asociada pero no encontrada -> universidad null")
    void hu44_06_universidadNoEncontrada() {
        Estudiante estudiante = estudianteBase();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.empty());
        Carrera carrera = new Carrera();
        carrera.setNombre("Sistemas");
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.of(carrera));
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
            .thenReturn(List.of());

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertNull(horario.universidad());
        assertEquals("Sistemas", horario.carrera());
        assertEquals("Ingenieria", horario.malla());
        assertNull(horario.semestreOferta());
        assertEquals((short) 3, horario.semestreActual());
        assertTrue(horario.clases().isEmpty());
        assertFalse(service.hasHorarioActual(7L));
    }

    @Test
    @DisplayName("HU44-07: carrera asociada pero no encontrada -> carrera null")
    void hu44_07_carreraNoEncontrada() {
        Estudiante estudiante = estudianteBase();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        Universidad universidad = new Universidad();
        universidad.setNombre("UTB");
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(universidad));
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.empty());
        when(ofertaMateriaRepository.findHorarioActualRows(7L, 10L, (short) 3))
            .thenReturn(List.of());

        HorarioActualResponse horario = service.getHorarioActualByUserId(7L);

        assertEquals("UTB", horario.universidad());
        assertNull(horario.carrera());
        assertEquals("Ingenieria", horario.malla());
        assertNull(horario.semestreOferta());
        assertEquals((short) 3, horario.semestreActual());
        assertTrue(horario.clases().isEmpty());
        assertFalse(service.hasHorarioActual(7L));
    }
}
