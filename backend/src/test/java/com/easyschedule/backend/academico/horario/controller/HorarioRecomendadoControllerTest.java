package com.easyschedule.backend.academico.horario.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.horario.dto.HorarioActualResponse;
import com.easyschedule.backend.academico.horario.service.HorarioRecomendadoService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/**
 * HU#45 — Pruebas unitarias de exportHorarioActual (HX45-01 a HX45-08).
 * HU#44 — Pruebas unitarias de getHorarioActual() (HU44-01 a HU44-04).
 * Caja blanca pura: sin MockMvc ni contexto Spring.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HorarioRecomendadoController - Exportar horario actual (HX45)")
class HorarioRecomendadoControllerTest {

    @Mock
    private HorarioRecomendadoService horarioRecomendadoService;

    @InjectMocks
    private HorarioRecomendadoController controller;

    private Principal principal(String name) {
        return () -> name;
    }

    private String expectedDateLabel() {
        return LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private void verifyNoGeneratorCalled() {
        verify(horarioRecomendadoService, never()).buildHorarioActualCsv(org.mockito.ArgumentMatchers.anyLong());
        verify(horarioRecomendadoService, never()).buildHorarioActualPdf(org.mockito.ArgumentMatchers.anyLong());
        verify(horarioRecomendadoService, never()).buildHorarioActualImage(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("HX45-01: formato csv, usuario autorizado, horario disponible -> 200, bytes CSV, text/csv, .csv")
    void hx45_01_exportCsv() {
        byte[] payload = "Horario academico\n".getBytes();
        when(horarioRecomendadoService.hasHorarioActual(7L)).thenReturn(true);
        when(horarioRecomendadoService.buildHorarioActualCsv(7L)).thenReturn(payload);

        ResponseEntity<byte[]> response = controller.exportHorarioActual(7L, "csv", principal("7"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(payload, response.getBody());
        assertEquals(MediaType.valueOf("text/csv"), response.getHeaders().getContentType());
        assertEquals(payload.length, response.getHeaders().getContentLength());
        String expectedFilename = "horario_7_" + expectedDateLabel() + ".csv";
        assertEquals(expectedFilename, response.getHeaders().getContentDisposition().getFilename());
        assertEquals("form-data", response.getHeaders().getContentDisposition().getType());
        assertEquals("attachment", response.getHeaders().getContentDisposition().getName());
        verify(horarioRecomendadoService).buildHorarioActualCsv(7L);
        verify(horarioRecomendadoService, never()).buildHorarioActualPdf(org.mockito.ArgumentMatchers.anyLong());
        verify(horarioRecomendadoService, never()).buildHorarioActualImage(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("HX45-02: formato ' PDF ' normalizado -> 200, application/pdf, .pdf")
    void hx45_02_exportPdfConEspacios() {
        byte[] payload = "%PDF-1.4".getBytes();
        when(horarioRecomendadoService.hasHorarioActual(7L)).thenReturn(true);
        when(horarioRecomendadoService.buildHorarioActualPdf(7L)).thenReturn(payload);

        ResponseEntity<byte[]> response = controller.exportHorarioActual(7L, " PDF ", principal("7"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(payload, response.getBody());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals(payload.length, response.getHeaders().getContentLength());
        assertEquals("horario_7_" + expectedDateLabel() + ".pdf",
                response.getHeaders().getContentDisposition().getFilename());
        verify(horarioRecomendadoService).buildHorarioActualPdf(7L);
        verify(horarioRecomendadoService, never()).buildHorarioActualCsv(org.mockito.ArgumentMatchers.anyLong());
        verify(horarioRecomendadoService, never()).buildHorarioActualImage(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("HX45-03: formato IMAGEN en mayusculas -> 200, image/png, .png")
    void hx45_03_exportImagen() {
        byte[] payload = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
        when(horarioRecomendadoService.hasHorarioActual(7L)).thenReturn(true);
        when(horarioRecomendadoService.buildHorarioActualImage(7L)).thenReturn(payload);

        ResponseEntity<byte[]> response = controller.exportHorarioActual(7L, "IMAGEN", principal("7"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(payload, response.getBody());
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertEquals(payload.length, response.getHeaders().getContentLength());
        assertEquals("horario_7_" + expectedDateLabel() + ".png",
                response.getHeaders().getContentDisposition().getFilename());
        verify(horarioRecomendadoService).buildHorarioActualImage(7L);
        verify(horarioRecomendadoService, never()).buildHorarioActualCsv(org.mockito.ArgumentMatchers.anyLong());
        verify(horarioRecomendadoService, never()).buildHorarioActualPdf(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("HX45-04: formato null usa CSV por defecto")
    void hx45_04_formatoNullPorDefectoCsv() {
        byte[] payload = "csv-default".getBytes();
        when(horarioRecomendadoService.hasHorarioActual(7L)).thenReturn(true);
        when(horarioRecomendadoService.buildHorarioActualCsv(7L)).thenReturn(payload);

        ResponseEntity<byte[]> response = controller.exportHorarioActual(7L, null, principal("7"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(payload, response.getBody());
        assertEquals(MediaType.valueOf("text/csv"), response.getHeaders().getContentType());
        verify(horarioRecomendadoService).buildHorarioActualCsv(7L);
        verify(horarioRecomendadoService, never()).buildHorarioActualPdf(org.mockito.ArgumentMatchers.anyLong());
        verify(horarioRecomendadoService, never()).buildHorarioActualImage(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("HX45-05: estudianteId null -> 403 sin invocar hasHorarioActual ni generadores")
    void hx45_05_estudianteIdNuloForbidden() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.exportHorarioActual(null, "csv", principal("7")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(horarioRecomendadoService, never()).hasHorarioActual(org.mockito.ArgumentMatchers.anyLong());
        verifyNoGeneratorCalled();
    }

    @Test
    @DisplayName("HX45-06: estudianteId distinto al usuario autenticado -> 403 sin hasHorarioActual")
    void hx45_06_estudianteAjenoForbidden() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.exportHorarioActual(8L, "csv", principal("7")));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(horarioRecomendadoService, never()).hasHorarioActual(org.mockito.ArgumentMatchers.anyLong());
        verifyNoGeneratorCalled();
    }

    @Test
    @DisplayName("HX45-07: formato docx -> 400 antes de hasHorarioActual")
    void hx45_07_formatoNoSoportadoBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.exportHorarioActual(7L, "docx", principal("7")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(horarioRecomendadoService, never()).hasHorarioActual(org.mockito.ArgumentMatchers.anyLong());
        verifyNoGeneratorCalled();
    }

    @Test
    @DisplayName("HX45-08: hasHorarioActual false -> 404 sin invocar generadores")
    void hx45_08_sinHorarioNotFound() {
        when(horarioRecomendadoService.hasHorarioActual(7L)).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.exportHorarioActual(7L, "csv", principal("7")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(horarioRecomendadoService).hasHorarioActual(7L);
        verifyNoGeneratorCalled();
    }

    // ==================================================================
    // HU#44 — Consultar horario actual: getHorarioActual()
    // ==================================================================

    @Test
    @DisplayName("HU44-01: principal valido -> delega al servicio con el userId correcto")
    void hu44_01_principalValidoDelegaservicio() {
        HorarioActualResponse esperado = new HorarioActualResponse(
            "UTB", "Sistemas", "Ingenieria", "3", (short) 3, List.of()
        );
        when(horarioRecomendadoService.getHorarioActualByUserId(7L)).thenReturn(esperado);

        HorarioActualResponse resultado = controller.getHorarioActual(principal("7"));

        assertEquals(esperado, resultado);
        verify(horarioRecomendadoService).getHorarioActualByUserId(7L);
    }

    @Test
    @DisplayName("HU44-02: principal nulo -> 401 Sesion invalida")
    void hu44_02_principalNulo() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getHorarioActual((Principal) null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Sesion invalida", ex.getReason());
        verifyNoInteractions(horarioRecomendadoService);
    }

    @Test
    @DisplayName("HU44-03: principal con nombre nulo -> 401 Sesion invalida")
    void hu44_03_nombreNulo() {
        Principal principalNuloNombre = () -> null;
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getHorarioActual(principalNuloNombre));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Sesion invalida", ex.getReason());
        verifyNoInteractions(horarioRecomendadoService);
    }

    @Test
    @DisplayName("HU44-04: principal con nombre no numerico -> 401 Sesion invalida")
    void hu44_04_nombreNoNumerico() {
        Principal principalNoNumerico = () -> "usuario-invalido";
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getHorarioActual(principalNoNumerico));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Sesion invalida", ex.getReason());
        verifyNoInteractions(horarioRecomendadoService);
    }

    private void verifyNoInteractions(HorarioRecomendadoService service) {
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
