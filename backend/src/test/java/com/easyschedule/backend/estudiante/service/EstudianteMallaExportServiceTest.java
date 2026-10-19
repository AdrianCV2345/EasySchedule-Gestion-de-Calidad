package com.easyschedule.backend.estudiante.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.carrera.model.Carrera;
import com.easyschedule.backend.academico.carrera.repository.CarreraRepository;
import com.easyschedule.backend.academico.malla.dto.MallaMateriaResponse;
import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.service.MallaService;
import com.easyschedule.backend.academico.universidad.model.Universidad;
import com.easyschedule.backend.academico.universidad.repository.UniversidadRepository;
import com.easyschedule.backend.estudiante.dto.AvanceGraduacionExport;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import com.easyschedule.backend.shared.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EstudianteMallaExportServiceTest {

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private MallaService mallaService;

    @Mock
    private UniversidadRepository universidadRepository;

    @Mock
    private CarreraRepository carreraRepository;

    @InjectMocks
    private EstudianteMallaExportService service;

    // ==================================================================
    // Helpers de construcción de objetos de prueba
    // ==================================================================

    private Estudiante estudianteConDatosCompletos() {
        Estudiante e = new Estudiante();
        e.setId(7L);
        e.setUsername("juan");
        e.setCorreo("juan@correo.com");
        e.setNombre("Juan");
        e.setApellido("Pérez");
        e.setUniversidadId(5L);
        e.setCarreraId(8L);

        Malla malla = new Malla();
        malla.setId(10L);
        malla.setNombre("Ingeniería");
        malla.setVersion("2025");
        e.setMalla(malla);

        return e;
    }

    private Universidad universidadValida() {
        Universidad u = new Universidad();
        u.setId(5L);
        u.setNombre("Universidad Técnica");
        u.setCodigo("UTB");
        u.setActive(true);
        return u;
    }

    private Carrera carreraValida() {
        Carrera c = new Carrera();
        c.setId(8L);
        c.setNombre("Ingeniería de Sistemas");
        c.setCodigo("SIS");
        c.setUniversidadId(5L);
        c.setActive(true);
        return c;
    }

    private MallaMateriaResponse materiaAprobada() {
        return new MallaMateriaResponse(100L, 1L, "INF-101", "Programación I", (short) 4,
            (short) 1, "aprobada", List.of());
    }

    private MallaMateriaResponse materiaCursando() {
        return new MallaMateriaResponse(101L, 2L, "INF-102", "Programación II", (short) 4,
            (short) 2, "cursando", List.of());
    }

    private MallaMateriaResponse materiaPendiente() {
        return new MallaMateriaResponse(102L, 3L, "INF-103", "Estructuras de Datos", (short) 4,
            (short) 3, "pendiente", List.of());
    }

    private MallaMateriaResponse materiaEstadoNulo() {
        return new MallaMateriaResponse(103L, 4L, "INF-104", "Base de Datos", (short) 4,
            (short) 4, null, List.of());
    }

    // ==================================================================
    // Pruebas HU#17 - Exportación del avance de graduación
    // ==================================================================

    // HU17-01: Formato null → normaliza a "pdf" y continúa con la generación
    @Test
    void exportarAvanceGraduacionConFormatoNullGeneraPdf() {
        Estudiante estudiante = estudianteConDatosCompletos();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(universidadValida()));
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.of(carreraValida()));
        when(mallaService.findMateriasByMalla(10L, 7L))
            .thenReturn(List.of(materiaAprobada(), materiaCursando(), materiaPendiente()));

        AvanceGraduacionExport resultado = service.exportarAvanceGraduacion(7L, null);

        assertNotNull(resultado);
        assertEquals("application/pdf", resultado.contentType());
        assertEquals("avance_graduacion.pdf", resultado.filename());
        assertNotNull(resultado.contenido());
        assertTrue(resultado.contenido().length > 0);
    }

    // HU17-02: Formato "csv" → HTTP 400 Bad Request
    @Test
    void exportarAvanceGraduacionConFormatoCsvRechaza() {
        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> service.exportarAvanceGraduacion(7L, "csv"));

        assertEquals(HttpStatus.BAD_REQUEST, excepcion.getStatusCode());
        assertEquals("Formato de exportacion no soportado", excepcion.getReason());
    }

    // HU17-03: Formato "PDF " (con espacios y mayúsculas) → normaliza y continúa
    @Test
    void exportarAvanceGraduacionConFormatoPdfEspaciadoGeneraPdf() {
        Estudiante estudiante = estudianteConDatosCompletos();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(universidadValida()));
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.of(carreraValida()));
        when(mallaService.findMateriasByMalla(10L, 7L))
            .thenReturn(List.of(materiaAprobada()));

        AvanceGraduacionExport resultado = service.exportarAvanceGraduacion(7L, " PDF ");

        assertNotNull(resultado);
        assertEquals("application/pdf", resultado.contentType());
        assertNotNull(resultado.contenido());
        assertTrue(resultado.contenido().length > 0);
    }

    // HU17-04: Estudiante no encontrado → ResourceNotFoundException
    @Test
    void exportarAvanceGraduacionConEstudianteInexistenteLanzaExcepcion() {
        when(estudianteRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException excepcion = assertThrows(ResourceNotFoundException.class,
            () -> service.exportarAvanceGraduacion(99L, "pdf"));

        assertEquals("Estudiante no encontrado con id: 99", excepcion.getMessage());
    }

    // HU17-05: Datos insuficientes (malla null) → HTTP 422
    @Test
    void exportarAvanceGraduacionConMallaNulaRechaza() {
        Estudiante estudiante = new Estudiante();
        estudiante.setId(7L);
        estudiante.setUniversidadId(5L);
        estudiante.setCarreraId(8L);
        // malla no se setea → null
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> service.exportarAvanceGraduacion(7L, "pdf"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, excepcion.getStatusCode());
    }

    // HU17-06: Universidad no encontrada → HTTP 422
    @Test
    void exportarAvanceGraduacionConUniversidadNoEncontradaRechaza() {
        Estudiante estudiante = estudianteConDatosCompletos();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.empty());

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> service.exportarAvanceGraduacion(7L, "pdf"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, excepcion.getStatusCode());
    }

    // HU17-07: Carrera no encontrada → HTTP 422
    @Test
    void exportarAvanceGraduacionConCarreraNoEncontradaRechaza() {
        Estudiante estudiante = estudianteConDatosCompletos();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(universidadValida()));
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.empty());

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> service.exportarAvanceGraduacion(7L, "pdf"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, excepcion.getStatusCode());
    }

    // HU17-08: Sin materias → HTTP 422
    @Test
    void exportarAvanceGraduacionSinMateriasRechaza() {
        Estudiante estudiante = estudianteConDatosCompletos();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(universidadValida()));
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.of(carreraValida()));
        when(mallaService.findMateriasByMalla(10L, 7L)).thenReturn(List.of());

        ResponseStatusException excepcion = assertThrows(ResponseStatusException.class,
            () -> service.exportarAvanceGraduacion(7L, "pdf"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, excepcion.getStatusCode());
        assertEquals("No existen materias suficientes para generar el reporte", excepcion.getReason());
    }

    // HU17-09: Caso feliz completo → PDF generado con información académica
    @Test
    void exportarAvanceGraduacionCasoFelizGeneraPdfConInformacionAcademica() {
        Estudiante estudiante = estudianteConDatosCompletos();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(universidadValida()));
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.of(carreraValida()));
        when(mallaService.findMateriasByMalla(10L, 7L))
            .thenReturn(List.of(materiaAprobada(), materiaCursando(), materiaPendiente(), materiaEstadoNulo()));

        AvanceGraduacionExport resultado = service.exportarAvanceGraduacion(7L, "pdf");

        assertNotNull(resultado);
        assertEquals("application/pdf", resultado.contentType());
        assertEquals("avance_graduacion.pdf", resultado.filename());
        assertNotNull(resultado.contenido());
        assertTrue(resultado.contenido().length > 0);

        // Verificar que es un PDF válido (cabecera %PDF-)
        String cabecera = new String(resultado.contenido(), 0, 5);
        assertEquals("%PDF-", cabecera);
    }

    // HU17-10: Todas las materias aprobadas → se genera PDF válido (agregarMateriasFaltantes → mensaje interno)
    @Test
    void exportarAvanceGraduacionTodasAprobadasGeneraPdf() {
        Estudiante estudiante = estudianteConDatosCompletos();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(universidadValida()));
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.of(carreraValida()));
        when(mallaService.findMateriasByMalla(10L, 7L))
            .thenReturn(List.of(materiaAprobada(), materiaAprobada()));

        AvanceGraduacionExport resultado = service.exportarAvanceGraduacion(7L, "pdf");

        assertNotNull(resultado);
        assertEquals("application/pdf", resultado.contentType());
        assertEquals("avance_graduacion.pdf", resultado.filename());
        assertNotNull(resultado.contenido());
        assertTrue(resultado.contenido().length > 0);
        // Verificar cabecera PDF
        String cabecera = new String(resultado.contenido(), 0, 5);
        assertEquals("%PDF-", cabecera);
    }

    // HU17-11: Mezcla de estados (aprobada, cursando, pendiente, null) → se genera PDF válido
    @Test
    void exportarAvanceGraduacionConMezclaDeEstadosCalculaResumen() {
        Estudiante estudiante = estudianteConDatosCompletos();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(universidadValida()));
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.of(carreraValida()));
        when(mallaService.findMateriasByMalla(10L, 7L))
            .thenReturn(List.of(materiaAprobada(), materiaCursando(), materiaPendiente(), materiaEstadoNulo()));

        AvanceGraduacionExport resultado = service.exportarAvanceGraduacion(7L, "pdf");

        assertNotNull(resultado);
        assertEquals("application/pdf", resultado.contentType());
        assertEquals("avance_graduacion.pdf", resultado.filename());
        assertNotNull(resultado.contenido());
        assertTrue(resultado.contenido().length > 0);
        // Verificar cabecera PDF
        String cabecera = new String(resultado.contenido(), 0, 5);
        assertEquals("%PDF-", cabecera);
    }

    // HU17-12: Materia sin semestre sugerido → se genera PDF válido (agrupamiento "Sin semestre" interno)
    @Test
    void exportarAvanceGraduacionConMateriaSinSemestreAgrupaSinSemestre() {
        MallaMateriaResponse sinSemestre = new MallaMateriaResponse(104L, 5L, "INF-104",
            "Sin Semestre Materia", (short) 3, null, "pendiente", List.of());
        Estudiante estudiante = estudianteConDatosCompletos();
        when(estudianteRepository.findById(7L)).thenReturn(Optional.of(estudiante));
        when(universidadRepository.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(universidadValida()));
        when(carreraRepository.findByIdAndActiveTrue(8L)).thenReturn(Optional.of(carreraValida()));
        when(mallaService.findMateriasByMalla(10L, 7L))
            .thenReturn(List.of(sinSemestre));

        AvanceGraduacionExport resultado = service.exportarAvanceGraduacion(7L, "pdf");

        assertNotNull(resultado);
        assertEquals("application/pdf", resultado.contentType());
        assertEquals("avance_graduacion.pdf", resultado.filename());
        assertNotNull(resultado.contenido());
        assertTrue(resultado.contenido().length > 0);
        // Verificar cabecera PDF
        String cabecera = new String(resultado.contenido(), 0, 5);
        assertEquals("%PDF-", cabecera);
    }
}
