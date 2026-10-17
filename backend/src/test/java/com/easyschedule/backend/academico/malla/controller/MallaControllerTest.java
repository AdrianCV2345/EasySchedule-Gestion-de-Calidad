package com.easyschedule.backend.academico.malla.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.dto.MallaImportRequest;
import com.easyschedule.backend.academico.malla.dto.MallaImportResponse;
import com.easyschedule.backend.academico.malla.dto.MateriaImportRequest;
import com.easyschedule.backend.academico.malla.service.MallaFileParserService;
import com.easyschedule.backend.academico.malla.service.MallaImportService;
import com.easyschedule.backend.academico.malla.service.MallaService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("MallaController - pruebas unitarias (HU24: importarMalla)")
class MallaControllerTest {

    private static final List<MateriaImportRequest> MATERIAS = List.of(
        new MateriaImportRequest("MAT101", "Matemática I", 1, 5, List.of()),
        new MateriaImportRequest("FIS102", "Física I", 2, 4, List.of("MAT101"))
    );

    @Mock
    private MallaService mallaService;

    @Mock
    private MallaFileParserService fileParserService;

    @Mock
    private MallaImportService mallaImportService;

    @InjectMocks
    private MallaController mallaController;

    private final MultipartFile file = new MockMultipartFile("file", "malla.csv", "text/csv",
        "codigo,nombre,semestre,creditos,prerequisitos".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("MC-01 importarMalla con carreraId null delega el request original y devuelve la respuesta del servicio")
    void importarMallaSinCarreraIdDelegaRequestOriginal() {
        MallaImportRequest request = new MallaImportRequest("Malla Importada", "1.0", null, MATERIAS);
        MallaImportResponse response = new MallaImportResponse(10L, "Malla Importada", 2, 1, "Importación exitosa");
        when(fileParserService.parseFile(file)).thenReturn(request);
        when(mallaImportService.importarMalla(request)).thenReturn(response);

        MallaImportResponse result = mallaController.importarMalla(file, null, "Malla Importada", "1.0");

        assertThat(result).isSameAs(response);
        verify(fileParserService).parseFile(file);
        verify(mallaImportService).importarMalla(request);
    }

    @Test
    @DisplayName("MC-02 importarMalla con carreraId reconstruye el request con nombre, version e id proporcionados")
    void importarMallaConCarreraIdReconstruyeRequest() {
        MallaImportRequest parsed = new MallaImportRequest("Nombre Original", "0.9", null, MATERIAS);
        MallaImportRequest expected = new MallaImportRequest("Mi Malla", "3.0", 1L, MATERIAS);
        MallaImportResponse response = new MallaImportResponse(20L, "Mi Malla", 2, 1, "Importación exitosa");
        when(fileParserService.parseFile(file)).thenReturn(parsed);
        when(mallaImportService.importarMalla(any(MallaImportRequest.class))).thenReturn(response);

        MallaImportResponse result = mallaController.importarMalla(file, 1L, "Mi Malla", "3.0");

        ArgumentCaptor<MallaImportRequest> captor = ArgumentCaptor.forClass(MallaImportRequest.class);
        verify(fileParserService).parseFile(file);
        verify(mallaImportService).importarMalla(captor.capture());
        MallaImportRequest enviado = captor.getValue();
        assertThat(enviado.carreraId()).isEqualTo(1L);
        assertThat(enviado.nombre()).isEqualTo("Mi Malla");
        assertThat(enviado.version()).isEqualTo("3.0");
        assertThat(enviado.materias()).isEqualTo(MATERIAS);
        assertThat(enviado).isEqualTo(expected);
        assertThat(result).isSameAs(response);
    }

    @Test
    @DisplayName("MC-03 importarMalla convierte IllegalArgumentException del parser en ResponseStatusException 400")
    void importarMallaConIllegalArgumentExceptionDevuelve400() {
        when(fileParserService.parseFile(file)).thenThrow(new IllegalArgumentException("Formato de archivo no soportado"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> mallaController.importarMalla(file, null, "Malla Importada", "1.0"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(mallaImportService);
    }

    @Test
    @DisplayName("MC-04 importarMalla convierte RuntimeException del servicio en ResponseStatusException 500")
    void importarMallaConRuntimeExceptionDevuelve500() {
        MallaImportRequest request = new MallaImportRequest("Malla Importada", "1.0", null, MATERIAS);
        when(fileParserService.parseFile(file)).thenReturn(request);
        when(mallaImportService.importarMalla(request)).thenThrow(new RuntimeException("fallo interno"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> mallaController.importarMalla(file, null, "Malla Importada", "1.0"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
