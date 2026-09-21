package com.easyschedule.backend.estudiante.service;

import com.easyschedule.backend.academico.carrera.model.Carrera;
import com.easyschedule.backend.academico.carrera.repository.CarreraRepository;
import com.easyschedule.backend.academico.malla.dto.MallaMateriaResponse;
import com.easyschedule.backend.academico.malla.service.MallaService;
import com.easyschedule.backend.academico.universidad.model.Universidad;
import com.easyschedule.backend.academico.universidad.repository.UniversidadRepository;
import com.easyschedule.backend.estudiante.dto.AvanceGraduacionExport;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import com.easyschedule.backend.shared.exception.ResourceNotFoundException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EstudianteMallaExportService {

    private static final String ESTADO_APROBADA = "aprobada";

    private final EstudianteRepository estudianteRepository;
    private final MallaService mallaService;
    private final UniversidadRepository universidadRepository;
    private final CarreraRepository carreraRepository;

    public EstudianteMallaExportService(
            EstudianteRepository estudianteRepository,
            MallaService mallaService,
            UniversidadRepository universidadRepository,
            CarreraRepository carreraRepository) {
        this.estudianteRepository = estudianteRepository;
        this.mallaService = mallaService;
        this.universidadRepository = universidadRepository;
        this.carreraRepository = carreraRepository;
    }

    public AvanceGraduacionExport exportarAvanceGraduacion(Long estudianteId, String formato) {
        String formatoNormalizado = formato == null ? "pdf" : formato.trim().toLowerCase(Locale.ROOT);
        if (!"pdf".equals(formatoNormalizado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de exportacion no soportado");
        }

        Estudiante estudiante = estudianteRepository.findById(estudianteId)
                .orElseThrow(() -> new ResourceNotFoundException("Estudiante no encontrado con id: " + estudianteId));

        validarDatosSuficientes(estudiante);

        Universidad universidad = universidadRepository.findByIdAndActiveTrue(estudiante.getUniversidadId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No existen datos suficientes para generar el reporte"));
        Carrera carrera = carreraRepository.findByIdAndActiveTrue(estudiante.getCarreraId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No existen datos suficientes para generar el reporte"));

        List<MallaMateriaResponse> materias = mallaService.findMateriasByMalla(estudiante.getMalla().getId(), estudianteId);
        if (materias.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No existen materias suficientes para generar el reporte");
        }

        byte[] pdf = generarPdf(estudiante, universidad, carrera, materias);
        return new AvanceGraduacionExport(pdf, "application/pdf", "avance_graduacion.pdf");
    }

    private void validarDatosSuficientes(Estudiante estudiante) {
        if (estudiante.getMalla() == null || estudiante.getUniversidadId() == null || estudiante.getCarreraId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No existen datos suficientes para generar el reporte");
        }
    }

    private byte[] generarPdf(
        Estudiante estudiante,
        Universidad universidad,
        Carrera carrera,
        List<MallaMateriaResponse> materias
    ) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 42, 42);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            ReporteResumen resumen = calcularResumen(materias);
            agregarTitulo(document);
            agregarDatosGenerales(document, estudiante, universidad, carrera);
            agregarResumen(document, resumen);
            agregarMateriasPorSemestre(document, materias);
            agregarMateriasFaltantes(document, materias);

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el reporte de avance");
        } catch (java.io.IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo preparar el reporte de avance");
        }
    }

    private void agregarTitulo(Document document) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(32, 44, 64));
        Paragraph title = new Paragraph("Reporte de avance de graduacion", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(14);
        document.add(title);
    }

    private void agregarDatosGenerales(Document document, Estudiante estudiante, Universidad universidad, Carrera carrera)
        throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(32, 44, 64));
        document.add(new Paragraph("Datos generales", sectionFont));

        PdfPTable table = new PdfPTable(new float[] { 1.1f, 2.2f });
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(12);

        agregarFilaDato(table, "Estudiante", nombreCompleto(estudiante));
        agregarFilaDato(table, "Usuario", estudiante.getUsername());
        agregarFilaDato(table, "Correo", estudiante.getCorreo());
        agregarFilaDato(table, "Universidad", universidad.getNombre());
        agregarFilaDato(table, "Carrera", carrera.getNombre());
        agregarFilaDato(table, "Malla", estudiante.getMalla().getNombre() + " - " + estudiante.getMalla().getVersion());
        agregarFilaDato(table, "Fecha de emision", OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        document.add(table);
    }

    private void agregarResumen(Document document, ReporteResumen resumen) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(32, 44, 64));
        document.add(new Paragraph("Resumen de avance", sectionFont));

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        table.setSpacingAfter(12);
        agregarHeader(table, "Avance total");
        agregarHeader(table, "Aprobadas");
        agregarHeader(table, "En curso");
        agregarHeader(table, "Pendientes");
        agregarCelda(table, String.format(Locale.ROOT, "%.2f%%", resumen.porcentajeAvance()));
        agregarCelda(table, String.valueOf(resumen.aprobadas()));
        agregarCelda(table, String.valueOf(resumen.cursando()));
        agregarCelda(table, String.valueOf(resumen.pendientes()));
        document.add(table);
    }

    private void agregarMateriasPorSemestre(Document document, List<MallaMateriaResponse> materias) throws DocumentException {
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(32, 44, 64));
        document.add(new Paragraph("Malla curricular por semestre", sectionFont));

        Map<Short, List<MallaMateriaResponse>> porSemestre = materias.stream()
            .sorted(Comparator.comparing(MallaMateriaResponse::semestreSugerido, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(MallaMateriaResponse::codigoMateria, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .collect(Collectors.groupingBy(
                materia -> materia.semestreSugerido() == null ? 0 : materia.semestreSugerido(),
                java.util.LinkedHashMap::new,
                Collectors.toList()
            ));

        for (Map.Entry<Short, List<MallaMateriaResponse>> entry : porSemestre.entrySet()) {
            Paragraph semester = new Paragraph(entry.getKey() == 0 ? "Sin semestre" : "Semestre " + entry.getKey(),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
            semester.setSpacingBefore(8);
            semester.setSpacingAfter(4);
            document.add(semester);

            PdfPTable table = new PdfPTable(new float[] { 1f, 3f, 1.2f });
            table.setWidthPercentage(100);
            agregarHeader(table, "Codigo");
            agregarHeader(table, "Materia");
            agregarHeader(table, "Estado");

            for (MallaMateriaResponse materia : entry.getValue()) {
                agregarCelda(table, valor(materia.codigoMateria()));
                agregarCelda(table, valor(materia.nombreMateria()));
                agregarCelda(table, etiquetaEstado(materia.estado()));
            }

            document.add(table);
        }
    }

    private void agregarMateriasFaltantes(Document document, List<MallaMateriaResponse> materias) throws DocumentException {
        List<MallaMateriaResponse> faltantes = materias.stream()
            .filter(materia -> !ESTADO_APROBADA.equals(normalizarEstado(materia.estado())))
            .toList();

        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(32, 44, 64));
        Paragraph title = new Paragraph("Materias faltantes para completar la carrera", sectionFont);
        title.setSpacingBefore(12);
        document.add(title);

        if (faltantes.isEmpty()) {
            document.add(new Paragraph("Todas las materias de la malla estan aprobadas."));
            return;
        }

        PdfPTable table = new PdfPTable(new float[] { 1f, 3f, 1.2f });
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        agregarHeader(table, "Codigo");
        agregarHeader(table, "Materia");
        agregarHeader(table, "Estado");

        for (MallaMateriaResponse materia : faltantes) {
            agregarCelda(table, valor(materia.codigoMateria()));
            agregarCelda(table, valor(materia.nombreMateria()));
            agregarCelda(table, etiquetaEstado(materia.estado()));
        }

        document.add(table);
    }

    private ReporteResumen calcularResumen(List<MallaMateriaResponse> materias) {
        long aprobadas = materias.stream().filter(materia -> ESTADO_APROBADA.equals(normalizarEstado(materia.estado()))).count();
        long cursando = materias.stream().filter(materia -> "cursando".equals(normalizarEstado(materia.estado()))).count();
        long pendientes = materias.size() - aprobadas - cursando;
        double porcentaje = materias.isEmpty() ? 0.0 : (aprobadas * 100.0) / materias.size();
        return new ReporteResumen(materias.size(), aprobadas, cursando, pendientes, porcentaje);
    }

    private void agregarFilaDato(PdfPTable table, String label, String value) {
        agregarHeader(table, label);
        agregarCelda(table, valor(value));
    }

    private void agregarHeader(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        cell.setBackgroundColor(new Color(63, 99, 131));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void agregarCelda(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private String nombreCompleto(Estudiante estudiante) {
        String nombre = valor(estudiante.getNombre());
        String apellido = valor(estudiante.getApellido());
        String completo = (nombre + " " + apellido).trim();
        return completo.isBlank() ? valor(estudiante.getUsername()) : completo;
    }

    private String etiquetaEstado(String estado) {
        return switch (normalizarEstado(estado)) {
            case ESTADO_APROBADA -> "Aprobada";
            case "cursando" -> "En curso";
            default -> "Pendiente";
        };
    }

    private String normalizarEstado(String estado) {
        return estado == null ? "pendiente" : estado.trim().toLowerCase(Locale.ROOT);
    }

    private String valor(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private record ReporteResumen(long total, long aprobadas, long cursando, long pendientes, double porcentajeAvance) {}
}
