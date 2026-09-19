package com.easyschedule.backend.academico.oferta_materia.service;

import com.easyschedule.backend.academico.malla.model.MallaMateria;
import com.easyschedule.backend.academico.malla.repository.MallaMateriaRepository;
import com.easyschedule.backend.academico.materia.model.Materia;
import com.easyschedule.backend.academico.materia.repository.MateriaRepository;
import com.easyschedule.backend.academico.oferta_materia.dto.Importacion.OfertaImportErrorResponse;
import com.easyschedule.backend.academico.oferta_materia.dto.Importacion.OfertaImportHorarioResponse;
import com.easyschedule.backend.academico.oferta_materia.dto.Importacion.OfertaImportPreviewResponse;
import com.easyschedule.backend.academico.oferta_materia.dto.Importacion.OfertaImportResultResponse;
import com.easyschedule.backend.academico.oferta_materia.dto.Importacion.OfertaImportSummaryResponse;
import com.easyschedule.backend.academico.oferta_materia.dto.Importacion.OfertaImportWarningResponse;
import com.easyschedule.backend.academico.oferta_materia.model.OfertaMateria;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OfertaMateriaImportService {

    private static final String CSV_EXTENSION = ".csv";
    private static final String COLUMNA_CODIGO_MATERIA = "codigo_materia";

    private static final List<String> REQUIRED_COLUMNS = List.of(
        COLUMNA_CODIGO_MATERIA,
        "paralelo",
        "semestre_academico",
        "dia",
        "hora_inicio",
        "hora_fin"
    );

    private static final Set<String> ALLOWED_DAYS = Set.of(
        "lunes",
        "martes",
        "miercoles",
        "jueves",
        "viernes",
        "sabado",
        "domingo"
    );

    private final MateriaRepository materiaRepository;
    private final MallaMateriaRepository mallaMateriaRepository;
    private final OfertaMateriaRepository ofertaMateriaRepository;
    private final ObjectMapper objectMapper;

    public OfertaMateriaImportService(
        MateriaRepository materiaRepository,
        MallaMateriaRepository mallaMateriaRepository,
        OfertaMateriaRepository ofertaMateriaRepository,
        ObjectMapper objectMapper
    ) {
        this.materiaRepository = materiaRepository;
        this.mallaMateriaRepository = mallaMateriaRepository;
        this.ofertaMateriaRepository = ofertaMateriaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OfertaImportResultResponse importCsv(Long mallaId, MultipartFile file) {
        OfertaImportResultResponse validationResult = validateCsv(mallaId, file);

        if (hasCriticalErrors(validationResult)) {
            return validationResult;
        }

        ImportCounters counters = persistOffers(validationResult.offers());

        OfertaImportSummaryResponse summary = new OfertaImportSummaryResponse(
            validationResult.summary().totalRows(),
            counters.created(),
            counters.updated(),
            validationResult.summary().scheduleBlocks(),
            validationResult.summary().skippedRows(),
            validationResult.summary().errorsCount(),
            validationResult.summary().warningsCount()
        );

        return new OfertaImportResultResponse(
            summary,
            validationResult.offers(),
            validationResult.errors(),
            validationResult.warnings()
        );
    }

    @Transactional(readOnly = true)
    public OfertaImportResultResponse validateCsv(Long mallaId, MultipartFile file) {
        List<OfertaImportErrorResponse> errors = new ArrayList<>();
        List<OfertaImportWarningResponse> warnings = new ArrayList<>();
        Map<String, OfertaImportPreviewBuilder> groupedOffers = new LinkedHashMap<>();

        if (mallaId == null) {
            errors.add(new OfertaImportErrorResponse(
                0,
                "mallaId",
                "Debe especificarse la malla para importar ofertas.",
                true
            ));

            return buildResponse(0, groupedOffers, errors, warnings);
        }

        if (file == null || file.isEmpty()) {
            errors.add(new OfertaImportErrorResponse(
                0,
                "archivo",
                "El archivo no puede estar vacío.",
                true
            ));

            return buildResponse(0, groupedOffers, errors, warnings);
        }

        if (!hasCsvExtension(file)) {
            errors.add(new OfertaImportErrorResponse(
                0,
                "archivo",
                "Solo se permiten archivos CSV.",
                true
            ));

            return buildResponse(0, groupedOffers, errors, warnings);
        }

        List<String> lines;

        try {
            lines = readNonEmptyLines(file);
        } catch (IOException exception) {
            errors.add(new OfertaImportErrorResponse(
                0,
                "archivo",
                "No se pudo leer el archivo CSV.",
                true
            ));

            return buildResponse(0, groupedOffers, errors, warnings);
        }

        if (lines.isEmpty()) {
            errors.add(new OfertaImportErrorResponse(
                0,
                "archivo",
                "El archivo debe contener encabezados y al menos una fila de datos.",
                true
            ));

            return buildResponse(0, groupedOffers, errors, warnings);
        }

        List<String> headers = parseCsvLine(lines.get(0))
            .stream()
            .map(this::normalizeColumn)
            .toList();

        validateRequiredColumns(headers, errors);

        if (!errors.isEmpty()) {
            return buildResponse(0, groupedOffers, errors, warnings);
        }

        List<String> dataLines = lines.subList(1, lines.size());

        if (dataLines.isEmpty()) {
            errors.add(new OfertaImportErrorResponse(
                0,
                "archivo",
                "El archivo debe contener al menos una fila de datos.",
                true
            ));

            return buildResponse(0, groupedOffers, errors, warnings);
        }

        for (int index = 0; index < dataLines.size(); index++) {
            int rowNumber = index + 2;
            List<String> values = parseCsvLine(dataLines.get(index));
            OfertaImportRow row = mapRow(headers, values, rowNumber);

            validateRow(mallaId, row, groupedOffers, errors, warnings);
        }

        return buildResponse(dataLines.size(), groupedOffers, errors, warnings);
    }

    private boolean hasCriticalErrors(OfertaImportResultResponse result) {
        return result.errors()
            .stream()
            .anyMatch(OfertaImportErrorResponse::critical);
    }

    private ImportCounters persistOffers(List<OfertaImportPreviewResponse> offers) {
        int created = 0;
        int updated = 0;

        for (OfertaImportPreviewResponse offer : offers) {
            Optional<OfertaMateria> existingOffer =
                ofertaMateriaRepository.findByMallaMateriaIdAndSemestreAndParalelo(
                    offer.mallaMateriaId(),
                    offer.semestreAcademico(),
                    offer.paralelo()
                );

            if (existingOffer.isPresent()) {
                updateExistingOffer(existingOffer.get(), offer);
                updated++;
            } else {
                createNewOffer(offer);
                created++;
            }
        }

        return new ImportCounters(created, updated);
    }

    private void updateExistingOffer(
        OfertaMateria ofertaMateria,
        OfertaImportPreviewResponse offer
    ) {
        ofertaMateria.setHorarioJson(buildHorarioJson(offer.horarios()));
        ofertaMateria.setDocente(toNullableValue(offer.docente()));
        ofertaMateria.setAula(toNullableValue(offer.aula()));
        ofertaMateria.setFechaActualizacion(OffsetDateTime.now());

        ofertaMateriaRepository.save(ofertaMateria);
    }

    private void createNewOffer(OfertaImportPreviewResponse offer) {
        OffsetDateTime now = OffsetDateTime.now();

        OfertaMateria ofertaMateria = new OfertaMateria();
        ofertaMateria.setMallaMateriaId(offer.mallaMateriaId());
        ofertaMateria.setSemestre(offer.semestreAcademico());
        ofertaMateria.setParalelo(offer.paralelo());
        ofertaMateria.setHorarioJson(buildHorarioJson(offer.horarios()));
        ofertaMateria.setDocente(toNullableValue(offer.docente()));
        ofertaMateria.setAula(toNullableValue(offer.aula()));
        ofertaMateria.setFechaCreacion(now);
        ofertaMateria.setFechaActualizacion(now);

        ofertaMateriaRepository.save(ofertaMateria);
    }

    private String buildHorarioJson(List<OfertaImportHorarioResponse> horarios) {
        List<HorarioJsonBlock> horarioJson = horarios.stream()
            .map(horario -> new HorarioJsonBlock(
                horario.dia(),
                horario.horaInicio(),
                horario.horaFin()
            ))
            .toList();

        try {
            return objectMapper.writeValueAsString(horarioJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo construir el horario_json.", exception);
        }
    }

    private String toNullableValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private boolean hasCsvExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();

        return filename != null
            && filename.toLowerCase(Locale.ROOT).endsWith(CSV_EXTENSION);
    }

    private List<String> readNonEmptyLines(MultipartFile file) throws IOException {
        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)
            )
        ) {
            return reader.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        }
    }

    private void validateRequiredColumns(
        List<String> headers,
        List<OfertaImportErrorResponse> errors
    ) {
        for (String requiredColumn : REQUIRED_COLUMNS) {
            if (!headers.contains(requiredColumn)) {
                errors.add(new OfertaImportErrorResponse(
                    1,
                    requiredColumn,
                    "Falta la columna obligatoria \"" + requiredColumn + "\".",
                    true
                ));
            }
        }
    }

    private OfertaImportRow mapRow(List<String> headers, List<String> values, int rowNumber) {
        return new OfertaImportRow(
            rowNumber,
            getValue(headers, values, COLUMNA_CODIGO_MATERIA),
            getValue(headers, values, "nombre_materia"),
            getValue(headers, values, "paralelo"),
            getValue(headers, values, "semestre_academico"),
            getValue(headers, values, "dia"),
            getValue(headers, values, "hora_inicio"),
            getValue(headers, values, "hora_fin"),
            getValue(headers, values, "docente"),
            getValue(headers, values, "aula")
        );
    }

    private String getValue(List<String> headers, List<String> values, String column) {
        int index = headers.indexOf(column);

        if (index < 0 || index >= values.size()) {
            return "";
        }

        return values.get(index).trim();
    }

    private void validateRow(
        Long mallaId,
        OfertaImportRow row,
        Map<String, OfertaImportPreviewBuilder> groupedOffers,
        List<OfertaImportErrorResponse> errors,
        List<OfertaImportWarningResponse> warnings
    ) {
        int errorsBeforeRow = errors.size();

        validateRequiredValue(row.rowNumber(), COLUMNA_CODIGO_MATERIA, row.codigoMateria(), errors);
        validateRequiredValue(row.rowNumber(), "paralelo", row.paralelo(), errors);
        validateRequiredValue(row.rowNumber(), "semestre_academico", row.semestreAcademico(), errors);
        validateRequiredValue(row.rowNumber(), "dia", row.dia(), errors);
        validateRequiredValue(row.rowNumber(), "hora_inicio", row.horaInicio(), errors);
        validateRequiredValue(row.rowNumber(), "hora_fin", row.horaFin(), errors);

        validateDay(row, errors);
        validateHourRange(row, errors);

        Optional<ResolvedMallaMateria> resolvedMallaMateria = resolveMallaMateria(
            mallaId,
            row,
            errors
        );

        addOptionalFieldWarnings(row, warnings);

        boolean hasCriticalErrorInRow = errors.size() > errorsBeforeRow;

        if (hasCriticalErrorInRow || resolvedMallaMateria.isEmpty()) {
            return;
        }

        addToPreview(resolvedMallaMateria.get(), row, groupedOffers, warnings);
    }

    private void validateRequiredValue(
        int rowNumber,
        String field,
        String value,
        List<OfertaImportErrorResponse> errors
    ) {
        if (value == null || value.isBlank()) {
            errors.add(new OfertaImportErrorResponse(
                rowNumber,
                field,
                "Campo obligatorio vacío.",
                true
            ));
        }
    }

    private void validateDay(
        OfertaImportRow row,
        List<OfertaImportErrorResponse> errors
    ) {
        if (row.dia().isBlank()) {
            return;
        }

        String normalizedDay = normalizeText(row.dia());

        if (!ALLOWED_DAYS.contains(normalizedDay)) {
            errors.add(new OfertaImportErrorResponse(
                row.rowNumber(),
                "dia",
                "El día debe ser un valor permitido: Lunes, Martes, Miercoles, Jueves, Viernes, Sabado o Domingo.",
                true
            ));
        }
    }

    private void validateHourRange(
        OfertaImportRow row,
        List<OfertaImportErrorResponse> errors
    ) {
        if (row.horaInicio().isBlank() || row.horaFin().isBlank()) {
            return;
        }

        LocalTime startTime;
        LocalTime endTime;

        try {
            startTime = LocalTime.parse(row.horaInicio());
        } catch (DateTimeParseException exception) {
            errors.add(new OfertaImportErrorResponse(
                row.rowNumber(),
                "hora_inicio",
                "La hora de inicio debe tener formato HH:mm.",
                true
            ));
            return;
        }

        try {
            endTime = LocalTime.parse(row.horaFin());
        } catch (DateTimeParseException exception) {
            errors.add(new OfertaImportErrorResponse(
                row.rowNumber(),
                "hora_fin",
                "La hora de fin debe tener formato HH:mm.",
                true
            ));
            return;
        }

        if (!startTime.isBefore(endTime)) {
            errors.add(new OfertaImportErrorResponse(
                row.rowNumber(),
                "horario",
                "La hora de inicio debe ser anterior a la hora de fin.",
                true
            ));
        }
    }

    private Optional<ResolvedMallaMateria> resolveMallaMateria(
        Long mallaId,
        OfertaImportRow row,
        List<OfertaImportErrorResponse> errors
    ) {
        if (row.codigoMateria().isBlank()) {
            return Optional.empty();
        }

        Optional<Materia> materia = materiaRepository.findByCodigoIgnoreCase(
            row.codigoMateria().trim()
        );

        if (materia.isEmpty()) {
            errors.add(new OfertaImportErrorResponse(
                row.rowNumber(),
                COLUMNA_CODIGO_MATERIA,
                "El código de materia no existe.",
                true
            ));
            return Optional.empty();
        }

        Optional<MallaMateria> mallaMateria = mallaMateriaRepository.findByMallaIdAndMateriaId(
            mallaId,
            materia.get().getId()
        );

        if (mallaMateria.isEmpty()) {
            errors.add(new OfertaImportErrorResponse(
                row.rowNumber(),
                COLUMNA_CODIGO_MATERIA,
                "La materia existe, pero no pertenece a la malla seleccionada.",
                true
            ));
            return Optional.empty();
        }

        return Optional.of(new ResolvedMallaMateria(materia.get(), mallaMateria.get()));
    }

    private void addOptionalFieldWarnings(
        OfertaImportRow row,
        List<OfertaImportWarningResponse> warnings
    ) {
        if (row.docente().isBlank()) {
            warnings.add(new OfertaImportWarningResponse(
                row.rowNumber(),
                "docente",
                "El docente está vacío. Se procesará como valor opcional."
            ));
        }

        if (row.aula().isBlank()) {
            warnings.add(new OfertaImportWarningResponse(
                row.rowNumber(),
                "aula",
                "El aula está vacía. Se procesará como valor opcional."
            ));
        }
    }

    private void addToPreview(
        ResolvedMallaMateria resolvedMallaMateria,
        OfertaImportRow row,
        Map<String, OfertaImportPreviewBuilder> groupedOffers,
        List<OfertaImportWarningResponse> warnings
    ) {
        String normalizedParalelo = row.paralelo().trim().toUpperCase(Locale.ROOT);
        String normalizedSemestre = row.semestreAcademico().trim();

        String key = resolvedMallaMateria.mallaMateria().getId()
            + "|"
            + normalizedSemestre
            + "|"
            + normalizedParalelo;

        OfertaImportPreviewBuilder builder = groupedOffers.computeIfAbsent(
            key,
            ignored -> new OfertaImportPreviewBuilder(
                row.codigoMateria().trim(),
                resolveNombreMateria(resolvedMallaMateria.materia(), row),
                resolvedMallaMateria.mallaMateria().getId(),
                normalizedParalelo,
                normalizedSemestre,
                row.docente().trim(),
                row.aula().trim()
            )
        );

        warnIfGroupedMetadataDiffers(row, builder, warnings);

        builder.addHorario(new OfertaImportHorarioResponse(
            row.rowNumber(),
            normalizeDay(row.dia()),
            row.horaInicio(),
            row.horaFin()
        ));
    }

    private String resolveNombreMateria(Materia materia, OfertaImportRow row) {
        if (!row.nombreMateria().isBlank()) {
            return row.nombreMateria().trim();
        }

        return materia.getNombre();
    }

    private void warnIfGroupedMetadataDiffers(
        OfertaImportRow row,
        OfertaImportPreviewBuilder builder,
        List<OfertaImportWarningResponse> warnings
    ) {
        if (!row.docente().isBlank() && !builder.hasSameDocente(row.docente())) {
            warnings.add(new OfertaImportWarningResponse(
                row.rowNumber(),
                "docente",
                "El docente difiere de otra fila agrupada en la misma oferta. Se conservará el primer valor detectado."
            ));
        }

        if (!row.aula().isBlank() && !builder.hasSameAula(row.aula())) {
            warnings.add(new OfertaImportWarningResponse(
                row.rowNumber(),
                "aula",
                "El aula difiere de otra fila agrupada en la misma oferta. Se conservará el primer valor detectado."
            ));
        }
    }

    private String normalizeDay(String day) {
        String normalizedDay = normalizeText(day);

        return switch (normalizedDay) {
            case "lunes" -> "Lunes";
            case "martes" -> "Martes";
            case "miercoles" -> "Miercoles";
            case "jueves" -> "Jueves";
            case "viernes" -> "Viernes";
            case "sabado" -> "Sabado";
            case "domingo" -> "Domingo";
            default -> day.trim();
        };
    }

    private OfertaImportResultResponse buildResponse(
        int totalRows,
        Map<String, OfertaImportPreviewBuilder> groupedOffers,
        List<OfertaImportErrorResponse> errors,
        List<OfertaImportWarningResponse> warnings
    ) {
        List<OfertaImportPreviewResponse> offers = groupedOffers.values()
            .stream()
            .map(OfertaImportPreviewBuilder::toResponse)
            .toList();

        int scheduleBlocks = offers.stream()
            .mapToInt(offer -> offer.horarios().size())
            .sum();

        int skippedRows = countRowsWithCriticalErrors(errors);

        OfertaImportSummaryResponse summary = new OfertaImportSummaryResponse(
            totalRows,
            0,
            0,
            scheduleBlocks,
            skippedRows,
            errors.size(),
            warnings.size()
        );

        return new OfertaImportResultResponse(
            summary,
            offers,
            errors,
            warnings
        );
    }

    private int countRowsWithCriticalErrors(List<OfertaImportErrorResponse> errors) {
        return (int) errors.stream()
            .filter(OfertaImportErrorResponse::critical)
            .map(OfertaImportErrorResponse::rowNumber)
            .filter(rowNumber -> rowNumber > 1)
            .distinct()
            .count();
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);

            if (character == '"') {
                boolean escapedQuote = insideQuotes
                    && index + 1 < line.length()
                    && line.charAt(index + 1) == '"';

                if (escapedQuote) {
                    current.append('"');
                    index++;
                } else {
                    insideQuotes = !insideQuotes;
                }

                continue;
            }

            if (character == ',' && !insideQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
                continue;
            }

            current.append(character);
        }

        values.add(current.toString().trim());
        return values;
    }

    private String normalizeColumn(String column) {
        return normalizeText(column)
            .replaceAll("\\s+", "_");
    }

    private String normalizeText(String text) {
        String normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFD);

        return normalized
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }

    private record OfertaImportRow(
        int rowNumber,
        String codigoMateria,
        String nombreMateria,
        String paralelo,
        String semestreAcademico,
        String dia,
        String horaInicio,
        String horaFin,
        String docente,
        String aula
    ) {
    }

    private record ResolvedMallaMateria(
        Materia materia,
        MallaMateria mallaMateria
    ) {
    }

    private record ImportCounters(
        int created,
        int updated
    ) {
    }

    private record HorarioJsonBlock(
        String dia,
        String inicio,
        String fin
    ) {
    }

    private static final class OfertaImportPreviewBuilder {

        private final String codigoMateria;
        private final String nombreMateria;
        private final Long mallaMateriaId;
        private final String paralelo;
        private final String semestreAcademico;
        private final String docente;
        private final String aula;
        private final List<OfertaImportHorarioResponse> horarios = new ArrayList<>();

        private OfertaImportPreviewBuilder(
            String codigoMateria,
            String nombreMateria,
            Long mallaMateriaId,
            String paralelo,
            String semestreAcademico,
            String docente,
            String aula
        ) {
            this.codigoMateria = codigoMateria;
            this.nombreMateria = nombreMateria;
            this.mallaMateriaId = mallaMateriaId;
            this.paralelo = paralelo;
            this.semestreAcademico = semestreAcademico;
            this.docente = docente;
            this.aula = aula;
        }

        private void addHorario(OfertaImportHorarioResponse horario) {
            horarios.add(horario);
        }

        private boolean hasSameDocente(String otherDocente) {
            return docente.equalsIgnoreCase(otherDocente.trim());
        }

        private boolean hasSameAula(String otherAula) {
            return aula.equalsIgnoreCase(otherAula.trim());
        }

        private OfertaImportPreviewResponse toResponse() {
            return new OfertaImportPreviewResponse(
                codigoMateria,
                nombreMateria,
                mallaMateriaId,
                paralelo,
                semestreAcademico,
                docente,
                aula,
                List.copyOf(horarios)
            );
        }
    }
}