package com.easyschedule.backend.academico.horario.service;

import com.easyschedule.backend.academico.carrera.model.Carrera;
import com.easyschedule.backend.academico.carrera.repository.CarreraRepository;
import com.easyschedule.backend.academico.horario.dto.HorarioActualResponse;
import com.easyschedule.backend.academico.horario.dto.HorarioClaseResponse;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import com.easyschedule.backend.academico.universidad.model.Universidad;
import com.easyschedule.backend.academico.universidad.repository.UniversidadRepository;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class HorarioRecomendadoService {

    private static final String TIPO_HORARIO_ACADEMICO = "Horario academico";

    private static final String LABEL_UNIVERSIDAD = "Universidad";
    private static final String LABEL_CARRERA = "Carrera";
    private static final String LABEL_MALLA = "Malla";
    private static final String LABEL_SEMESTRE_OFERTA = "Semestre oferta";
    private static final String LABEL_SEMESTRE_ACTUAL = "Semestre actual";

    private static final String LABEL_MATERIA = "Materia";
    private static final String LABEL_PARALELO = "Paralelo";
    private static final String LABEL_DOCENTE = "Docente";
    private static final String FONT_SANS_SERIF = "SansSerif";



    private static final Color PDF_TITLE_COLOR = new Color(0x12, 0x17, 0x22);
    private static final Color PDF_SUBTITLE_COLOR = new Color(0x1B, 0x23, 0x30);
    private static final Color PDF_PRIMARY_COLOR = new Color(0x3F, 0x63, 0x83);
    private static final Color PDF_CARD_COLOR = new Color(0xDCE1E8);
    private static final Color PDF_BODY_COLOR = new Color(0xB9, 0xC0, 0xC9);
    private static final Color PDF_TEXT_COLOR = new Color(0x1B, 0x23, 0x30);

    private final EstudianteRepository estudianteRepository;
    private final UniversidadRepository universidadRepository;
    private final CarreraRepository carreraRepository;
    private final OfertaMateriaRepository ofertaMateriaRepository;
    private final ObjectMapper objectMapper;

    public HorarioRecomendadoService(
        EstudianteRepository estudianteRepository,
        UniversidadRepository universidadRepository,
        CarreraRepository carreraRepository,
        OfertaMateriaRepository ofertaMateriaRepository,
        ObjectMapper objectMapper
    ) {
        this.estudianteRepository = estudianteRepository;
        this.universidadRepository = universidadRepository;
        this.carreraRepository = carreraRepository;
        this.ofertaMateriaRepository = ofertaMateriaRepository;
        this.objectMapper = objectMapper;
    }

    public HorarioActualResponse getHorarioActualByUserId(Long userId) {
        Estudiante estudiante = estudianteRepository.findById(userId).orElse(null);

        if (estudiante == null || estudiante.getMalla() == null || estudiante.getSemestreActual() == null) {
            return new HorarioActualResponse(null, null, null, null, null, List.of());
        }

        if (estudiante.getMalla().getId() == null) {
            return new HorarioActualResponse(null, null, null, null, estudiante.getSemestreActual(), List.of());
        }

        Universidad universidad = estudiante.getUniversidadId() == null
            ? null
            : universidadRepository.findByIdAndActiveTrue(estudiante.getUniversidadId()).orElse(null);
        Carrera carrera = estudiante.getCarreraId() == null
            ? null
            : carreraRepository.findByIdAndActiveTrue(estudiante.getCarreraId()).orElse(null);

        List<OfertaMateriaRepository.HorarioOfertaRow> rows = ofertaMateriaRepository.findHorarioActualRows(
            userId,
            estudiante.getMalla().getId(),
            estudiante.getSemestreActual()
        );

        List<HorarioClaseResponse> clases = new ArrayList<>();
        String semestreOferta = null;
        for (OfertaMateriaRepository.HorarioOfertaRow row : rows) {
            if (semestreOferta == null) {
                semestreOferta = row.getSemestre();
            }
            clases.addAll(parseHorario(row));
        }

        String mallaLabel = estudiante.getMalla().getNombre();
        if (mallaLabel == null || mallaLabel.isBlank()) {
            if (estudiante.getMalla().getVersion() == null) {
                mallaLabel = null;
            } else {
                mallaLabel = "Malla " + estudiante.getMalla().getVersion();
            }
        }

        return new HorarioActualResponse(
            universidad == null ? null : universidad.getNombre(),
            carrera == null ? null : carrera.getNombre(),
            mallaLabel,
            semestreOferta,
            estudiante.getSemestreActual(),
            clases
        );
    }

    public byte[] buildHorarioActualCsv(Long userId) {
        HorarioActualResponse horario = getHorarioActualByUserId(userId);
        String csv = toCsv(horario);
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] buildHorarioActualPdf(Long userId) {
        HorarioActualResponse horario = getHorarioActualByUserId(userId);
        return toPdf(horario);
    }

    public byte[] buildHorarioActualImage(Long userId) {
        HorarioActualResponse horario = getHorarioActualByUserId(userId);
        return toImage(horario);
    }

    public boolean hasHorarioActual(Long userId) {
        HorarioActualResponse horario = getHorarioActualByUserId(userId);
        return horario != null && horario.clases() != null && !horario.clases().isEmpty();
    }

    private List<HorarioClaseResponse> parseHorario(OfertaMateriaRepository.HorarioOfertaRow row) {
        List<HorarioClaseResponse> clases = new ArrayList<>();
        try {
            JsonNode array = objectMapper.readTree(row.getHorarioJson());
            if (!array.isArray()) {
                return clases;
            }

            for (JsonNode slot : array) {
                String dia = text(slot, "dia");
                String horaInicio = text(slot, "inicio");
                if (horaInicio == null) {
                    horaInicio = text(slot, "hora_inicio");
                }
                String horaFin = text(slot, "fin");
                if (horaFin == null) {
                    horaFin = text(slot, "hora_fin");
                }

                if (dia == null || horaInicio == null || horaFin == null) {
                    continue;
                }

                clases.add(new HorarioClaseResponse(
                    row.getMateriaNombre(),
                    row.getParalelo(),
                    dia,
                    horaInicio,
                    horaFin,
                    row.getDocente(),
                    row.getAula(),
                    row.getCreditos(),
                    true,
                    row.getOfertaId()
                ));
            }
        } catch (Exception ignored) {
            // Si una oferta tiene horario_json invalido, se ignora para no romper toda la vista.
        }
        return clases;
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private String toCsv(HorarioActualResponse horario) {
        StringBuilder builder = new StringBuilder();
        appendCsvRow(builder, TIPO_HORARIO_ACADEMICO);
        appendCsvRow(builder, LABEL_UNIVERSIDAD, metaValue(horario == null ? null : horario.universidad()));
        appendCsvRow(builder, LABEL_CARRERA, metaValue(horario == null ? null : horario.carrera()));
        appendCsvRow(builder, LABEL_MALLA, metaValue(horario == null ? null : horario.malla()));
        appendCsvRow(builder, LABEL_SEMESTRE_OFERTA, metaValue(horario == null ? null : horario.semestreOferta()));
        appendCsvRow(builder, LABEL_SEMESTRE_ACTUAL, metaValue(horario == null ? null : horario.semestreActual()));
        appendCsvRow(builder);
        appendCsvRow(builder, LABEL_MATERIA, LABEL_PARALELO, "Dia", "HoraInicio", "HoraFin", "Aula", LABEL_DOCENTE);

        if (horario == null || horario.clases() == null || horario.clases().isEmpty()) {
            return builder.toString();
        }

        for (HorarioClaseResponse clase : horario.clases()) {
            builder
                .append(csv(clase.materia()))
                .append(',')
                .append(csv(clase.paralelo()))
                .append(',')
                .append(csv(clase.dia()))
                .append(',')
                .append(csv(clase.horaInicio()))
                .append(',')
                .append(csv(clase.horaFin()))
                .append(',')
                .append(csv(clase.aula()))
                .append(',')
                .append(csv(clase.docente()))
                .append('\n');
        }

        return builder.toString();
    }

    private void appendCsvRow(StringBuilder builder, String... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(csv(values[index]));
        }
        builder.append('\n');
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.trim();
        if (sanitized.isEmpty()) {
            return "";
        }
        String escaped = sanitized.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private byte[] toImage(HorarioActualResponse horario) {
        List<HorarioClaseResponse> clases = horario == null || horario.clases() == null
            ? List.of()
            : horario.clases();

        final int padding = 28;
        final int rowHeight = 34;
        final int headerHeight = 42;
        final int titleHeight = 54;
        final int subtitleHeight = 26;
        final int metadataRowHeight = 30;
        final int metadataCount = 5;

        int[] columnWidths = new int[] { 320, 95, 125, 110, 110, 130, 180 };
        String[] headers = new String[] { LABEL_MATERIA, LABEL_PARALELO, "Dia", "Inicio", "Fin", "Aula", LABEL_DOCENTE };
        String[][] rows = new String[clases.size()][headers.length];

        for (int i = 0; i < clases.size(); i++) {
            HorarioClaseResponse clase = clases.get(i);
            rows[i][0] = safeText(clase.materia());
            rows[i][1] = safeText(clase.paralelo());
            rows[i][2] = safeText(clase.dia());
            rows[i][3] = safeText(clase.horaInicio());
            rows[i][4] = safeText(clase.horaFin());
            rows[i][5] = safeText(clase.aula());
            rows[i][6] = safeText(clase.docente());
        }

        int tableWidth = 0;
        for (int width : columnWidths) {
            tableWidth += width;
        }

        int tableRows = Math.max(rows.length, 1);
        int tableHeight = headerHeight + (tableRows * rowHeight);
        int metadataHeight = metadataCount * metadataRowHeight;
        int width = tableWidth + (padding * 2);
        int height = padding + titleHeight + subtitleHeight + 14 + metadataHeight + 16 + tableHeight + padding;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            graphics.setColor(new Color(0xF5, 0xF7, 0xFB));
            graphics.fillRect(0, 0, width, height);

            java.awt.Font titleFont = new java.awt.Font(FONT_SANS_SERIF, java.awt.Font.BOLD, 28);
            java.awt.Font subtitleFont = new java.awt.Font(FONT_SANS_SERIF, java.awt.Font.PLAIN, 15);
            java.awt.Font metadataLabelFont = new java.awt.Font(FONT_SANS_SERIF, java.awt.Font.BOLD, 13);
            java.awt.Font metadataValueFont = new java.awt.Font(FONT_SANS_SERIF, java.awt.Font.PLAIN, 13);
            java.awt.Font headerFont = new java.awt.Font(FONT_SANS_SERIF, java.awt.Font.BOLD, 14);
            java.awt.Font bodyFont = new java.awt.Font(FONT_SANS_SERIF, java.awt.Font.PLAIN, 13);

            int y = padding;

            graphics.setFont(titleFont);
            graphics.setColor(PDF_TITLE_COLOR);
            drawText(graphics, TIPO_HORARIO_ACADEMICO, padding, y + 34);
            y += titleHeight;

            graphics.setFont(subtitleFont);
            graphics.setColor(PDF_SUBTITLE_COLOR);
            drawText(graphics, buildSubtitle(horario), padding, y + 16);
            y += subtitleHeight + 12;

            String[][] metadata = new String[][] {
                { LABEL_UNIVERSIDAD, metaValue(horario == null ? null : horario.universidad()) },
                { LABEL_CARRERA, metaValue(horario == null ? null : horario.carrera()) },
                { LABEL_MALLA, metaValue(horario == null ? null : horario.malla()) },
                { LABEL_SEMESTRE_OFERTA, metaValue(horario == null ? null : horario.semestreOferta()) },
                { LABEL_SEMESTRE_ACTUAL, metaValue(horario == null ? null : horario.semestreActual()) }
            };

            int metadataLabelWidth = 210;
            for (String[] pair : metadata) {
                graphics.setColor(PDF_PRIMARY_COLOR);
                graphics.fillRect(padding, y, metadataLabelWidth, metadataRowHeight);

                graphics.setColor(PDF_BODY_COLOR);
                graphics.fillRect(padding + metadataLabelWidth, y, tableWidth - metadataLabelWidth, metadataRowHeight);

                graphics.setFont(metadataLabelFont);
                graphics.setColor(Color.WHITE);
                drawText(graphics, pair[0], padding + 10, y + 19);

                graphics.setFont(metadataValueFont);
                graphics.setColor(PDF_TEXT_COLOR);
                drawText(graphics, pair[1], padding + metadataLabelWidth + 10, y + 19);

                y += metadataRowHeight;
            }

            y += 16;

            int x = padding;
            graphics.setFont(headerFont);
            for (int i = 0; i < headers.length; i++) {
                int colWidth = columnWidths[i];
                graphics.setColor(PDF_PRIMARY_COLOR);
                graphics.fillRect(x, y, colWidth, headerHeight);
                graphics.setColor(Color.WHITE);
                drawCenteredText(graphics, headers[i], x, y, colWidth, headerHeight);
                x += colWidth;
            }
            y += headerHeight;

            if (rows.length == 0) {
                graphics.setColor(PDF_CARD_COLOR);
                graphics.fillRect(padding, y, tableWidth, rowHeight);
                graphics.setColor(PDF_TEXT_COLOR);
                graphics.setFont(bodyFont);
                drawText(graphics, "No hay clases disponibles.", padding + 10, y + 21);
                y += rowHeight;
            } else {
                graphics.setFont(bodyFont);
                for (String[] row : rows) {
                    x = padding;
                    for (int i = 0; i < row.length; i++) {
                        int colWidth = columnWidths[i];
                        graphics.setColor(PDF_CARD_COLOR);
                        graphics.fillRect(x, y, colWidth, rowHeight);
                        graphics.setColor(PDF_TEXT_COLOR);
                        drawTextWithEllipsis(graphics, row[i], x + 8, y + 21, colWidth - 16);
                        x += colWidth;
                    }
                    y += rowHeight;
                }
            }

            graphics.setColor(PDF_BODY_COLOR);
            int borderY = padding + titleHeight + subtitleHeight + 14 + metadataHeight + 16;
            int totalRowsHeight = headerHeight + (Math.max(rows.length, 1) * rowHeight);
            x = padding;
            for (int widthCol : columnWidths) {
                graphics.drawRect(x, borderY, widthCol, totalRowsHeight);
                x += widthCol;
            }
            graphics.drawRect(padding, borderY, tableWidth, totalRowsHeight);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(image, "png", outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            return new byte[0];
        } finally {
            graphics.dispose();
        }
    }

    private byte[] toPdf(HorarioActualResponse horario) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f, PDF_TITLE_COLOR);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10f, PDF_SUBTITLE_COLOR);
            Font metadataLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9f, Color.WHITE);
            Font metadataValueFont = FontFactory.getFont(FontFactory.HELVETICA, 9f, PDF_TEXT_COLOR);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9f, PDF_TEXT_COLOR);

            document.add(new Paragraph(TIPO_HORARIO_ACADEMICO, titleFont));
            document.add(new Paragraph(buildSubtitle(horario), subtitleFont));
            document.add(new Paragraph(" "));

            PdfPTable metadataTable = new PdfPTable(2);
            metadataTable.setWidthPercentage(100f);
            metadataTable.setWidths(new float[] { 1.7f, 4.3f });
            metadataTable.setSpacingAfter(12f);

            addMetadataCell(metadataTable, LABEL_UNIVERSIDAD, metaValue(horario == null ? null : horario.universidad()), metadataLabelFont, metadataValueFont);
            addMetadataCell(metadataTable, LABEL_CARRERA, metaValue(horario == null ? null : horario.carrera()), metadataLabelFont, metadataValueFont);
            addMetadataCell(metadataTable, LABEL_MALLA, metaValue(horario == null ? null : horario.malla()), metadataLabelFont, metadataValueFont);
            addMetadataCell(metadataTable, LABEL_SEMESTRE_OFERTA, metaValue(horario == null ? null : horario.semestreOferta()), metadataLabelFont, metadataValueFont);
            addMetadataCell(metadataTable, LABEL_SEMESTRE_ACTUAL, metaValue(horario == null ? null : horario.semestreActual()), metadataLabelFont, metadataValueFont);

            document.add(metadataTable);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100f);
            table.setWidths(new float[] { 3.2f, 1.2f, 1.5f, 1.35f, 1.35f, 1.6f, 1.9f });

            addHeaderCell(table, LABEL_MATERIA, headerFont);
            addHeaderCell(table, LABEL_PARALELO, headerFont);
            addHeaderCell(table, "Dia", headerFont);
            addHeaderCell(table, "Inicio", headerFont);
            addHeaderCell(table, "Fin", headerFont);
            addHeaderCell(table, "Aula", headerFont);
            addHeaderCell(table, LABEL_DOCENTE, headerFont);

            if (horario != null && horario.clases() != null) {
                for (HorarioClaseResponse clase : horario.clases()) {
                    addBodyCell(table, safeText(clase.materia()), cellFont);
                    addBodyCell(table, safeText(clase.paralelo()), cellFont);
                    addBodyCell(table, safeText(clase.dia()), cellFont);
                    addBodyCell(table, safeText(clase.horaInicio()), cellFont);
                    addBodyCell(table, safeText(clase.horaFin()), cellFont);
                    addBodyCell(table, safeText(clase.aula()), cellFont);
                    addBodyCell(table, safeText(clase.docente()), cellFont);
                }
            }

            document.add(table);
        } catch (Exception ex) {
            return new byte[0];
        } finally {
            document.close();
        }
        return outputStream.toByteArray();
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(PDF_PRIMARY_COLOR);
        cell.setBorderColor(PDF_PRIMARY_COLOR);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        cell.setBackgroundColor(PDF_CARD_COLOR);
        cell.setBorderColor(PDF_BODY_COLOR);
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private void addMetadataCell(
        PdfPTable table,
        String label,
        String value,
        Font labelFont,
        Font valueFont
    ) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBackgroundColor(PDF_PRIMARY_COLOR);
        labelCell.setBorderColor(PDF_PRIMARY_COLOR);
        labelCell.setPadding(6f);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBackgroundColor(PDF_BODY_COLOR);
        valueCell.setBorderColor(PDF_BODY_COLOR);
        valueCell.setPadding(6f);
        table.addCell(valueCell);
    }

    private String buildSubtitle(HorarioActualResponse horario) {
        if (horario == null) {
            return "";
        }

        StringBuilder subtitle = new StringBuilder();
        subtitle.append(metaValue(horario.universidad()));
        subtitle.append(" | Carrera: ").append(metaValue(horario.carrera()));
        subtitle.append(" | Malla: ").append(metaValue(horario.malla()));
        subtitle.append(" | Semestre oferta: ").append(metaValue(horario.semestreOferta()));
        subtitle.append(" | Semestre actual: ").append(metaValue(horario.semestreActual()));
        return subtitle.toString();
    }

    private String metaValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }

    private String metaValue(Short value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private void drawText(Graphics2D graphics, String text, int x, int baselineY) {
        graphics.drawString(text == null ? "" : text, x, baselineY);
    }

    private void drawCenteredText(Graphics2D graphics, String text, int x, int y, int width, int height) {
        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = metrics.stringWidth(text == null ? "" : text);
        int drawX = x + Math.max(6, (width - textWidth) / 2);
        int drawY = y + ((height - metrics.getHeight()) / 2) + metrics.getAscent();
        graphics.drawString(text == null ? "" : text, drawX, drawY);
    }

    private void drawTextWithEllipsis(Graphics2D graphics, String text, int x, int baselineY, int maxWidth) {
        String value = text == null ? "" : text;
        FontMetrics metrics = graphics.getFontMetrics();

        if (metrics.stringWidth(value) <= maxWidth) {
            graphics.drawString(value, x, baselineY);
            return;
        }

        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        if (ellipsisWidth > maxWidth) {
            return;
        }

        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            String next = truncated.toString() + value.charAt(i);
            if (metrics.stringWidth(next) + ellipsisWidth > maxWidth) {
                break;
            }
            truncated.append(value.charAt(i));
        }

        graphics.drawString(truncated + ellipsis, x, baselineY);
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.trim();
    }
}
