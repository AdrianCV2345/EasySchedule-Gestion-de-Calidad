package com.easyschedule.backend.academico.malla.service;

import com.easyschedule.backend.academico.malla.dto.MallaImportRequest;
import com.easyschedule.backend.academico.malla.dto.MateriaImportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class MallaFileParserService {

    private static final Logger logger = LoggerFactory.getLogger(MallaFileParserService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MallaImportRequest parseFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Nombre de archivo inválido");
        }

        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".json")) {
            return parseJson(file);
        } else if (lowerFilename.endsWith(".csv")) {
            return parseCsv(file);
        } else {
            throw new IllegalArgumentException("Formato de archivo no soportado. Use CSV o JSON");
        }
    }

    private MallaImportRequest parseJson(MultipartFile file) {
        try {
            MallaJsonWrapper wrapper = objectMapper.readValue(file.getInputStream(), MallaJsonWrapper.class);
            return new MallaImportRequest(
                wrapper.nombre(),
                wrapper.version(),
                wrapper.carreraId(),
                wrapper.materias()
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al parsear JSON: " + e.getMessage());
        }
    }

    private MallaImportRequest parseCsv(MultipartFile file) {
        logger.info("Parsing CSV file: {}, size={} bytes", file.getOriginalFilename(), file.getSize());

        try (BufferedReader reader = openReader(file)) {
            List<String> lines = readCsvLines(reader);
            validateHasHeader(lines);
            validateCsvHeaders(lines.get(0).split(",", -1));
            ParsedCsv parsedCsv = parseCsvRows(lines);
            validateParsedCsv(parsedCsv);
            return new MallaImportRequest("Malla Importada", "1.0", null, parsedCsv.materias());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Error al leer archivo CSV: " + e.getMessage());
        }
    }

    private BufferedReader openReader(MultipartFile file) throws java.io.IOException {
        return new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
    }

    private List<String> readCsvLines(BufferedReader reader) throws java.io.IOException {
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            lines.add(normalizeCsvLine(line, lines.size() + 1));
        }
        return lines;
    }

    private String normalizeCsvLine(String line, int lineNumber) {
        if (lineNumber == 1 && line.startsWith("\uFEFF")) {
            logger.debug("BOM detectado y removido de la primera línea");
            return line.substring(1);
        }
        if (lineNumber <= 3) {
            logger.debug("Línea {}: {}", lineNumber, line);
        }
        return line;
    }

    private void validateHasHeader(List<String> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("El CSV no contiene datos");
        }
    }

    private ParsedCsv parseCsvRows(List<String> lines) {
        List<MateriaImportRequest> materias = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            parseCsvRow(lines.get(index), index + 1, materias, errors);
        }
        return new ParsedCsv(materias, errors);
    }

    private void parseCsvRow(
            String line,
            int lineNumber,
            List<MateriaImportRequest> materias,
            List<String> errors) {
        String[] parts = line.split(",", -1);
        if (parts.length < 4) {
            errors.add("Línea " + lineNumber + ": Se requieren al menos 4 columnas");
            return;
        }
        try {
            materias.add(toMateria(parts));
        } catch (NumberFormatException exception) {
            errors.add("Línea " + lineNumber + ": Error en formato numérico");
        }
    }

    private MateriaImportRequest toMateria(String[] parts) {
        String codigo = parts[0].trim();
        String nombre = parts[1].trim();
        int semestre = Integer.parseInt(parts[2].trim());
        int creditos = parts[3].trim().isEmpty() ? 0 : Integer.parseInt(parts[3].trim());
        return new MateriaImportRequest(codigo, nombre, semestre, creditos, parsePrerequisitos(parts));
    }

    private List<String> parsePrerequisitos(String[] parts) {
        if (parts.length <= 4 || parts[4].trim().isEmpty()) return new ArrayList<>();
        List<String> prerequisitos = new ArrayList<>();
        for (String requisito : parts[4].split(";")) {
            if (!requisito.trim().isEmpty()) prerequisitos.add(requisito.trim());
        }
        return prerequisitos;
    }

    private void validateParsedCsv(ParsedCsv parsedCsv) {
        if (!parsedCsv.errors().isEmpty()) {
            throw new IllegalArgumentException("Errores en CSV:\n" + String.join("\n", parsedCsv.errors()));
        }
        if (parsedCsv.materias().isEmpty()) {
            throw new IllegalArgumentException("El archivo CSV no contiene materias válidas");
        }
    }

    private void validateCsvHeaders(String[] headers) {
        if (headers.length < 4) {
            throw new IllegalArgumentException("El CSV debe tener al menos: codigo,nombre,semestre,creditos");
        }
    }

    private record ParsedCsv(List<MateriaImportRequest> materias, List<String> errors) {}

    public record MallaJsonWrapper(
        String nombre,
        String version,
        Long carreraId,
        List<MateriaImportRequest> materias
    ) {}
}
