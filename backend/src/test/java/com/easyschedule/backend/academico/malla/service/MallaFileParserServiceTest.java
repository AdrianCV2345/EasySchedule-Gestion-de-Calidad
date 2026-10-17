package com.easyschedule.backend.academico.malla.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.dto.MallaImportRequest;
import com.easyschedule.backend.academico.malla.dto.MateriaImportRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("MallaFileParserService - pruebas unitarias (HU24: parseo de archivos JSON y CSV)")
class MallaFileParserServiceTest {

    private static final String CSV_HEADER = "codigo,nombre,semestre,creditos,prerequisitos";

    private static final String CSV_VALIDO = """
        codigo,nombre,semestre,creditos,prerequisitos
        MAT101,Matemática I,1,5,
        FIS102,Física I,2,4,MAT101
        PRO201,Programación II,3,5,MAT101;FIS102
        """;

    private final MallaFileParserService parser = new MallaFileParserService();

    @Mock
    private MultipartFile mockFile;

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "malla.csv", "text/csv",
            content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("PF-01 parseFile con JSON valido devuelve nombre, version, carreraId y materias correctos")
    void parseFileJsonValidoDevuelveMallaImportRequest() {
        String json = """
            {
              "nombre": "Malla Ingeniería 2024",
              "version": "2.1",
              "carreraId": 7,
              "materias": [
                {"codigo":"MAT101","nombre":"Matemática I","semestre":1,"creditos":5,"prerequisitos":[]},
                {"codigo":"FIS102","nombre":"Física I","semestre":2,"creditos":4,"prerequisitos":["MAT101"]}
              ]
            }
            """;
        MockMultipartFile file = new MockMultipartFile("file", "malla.json", "application/json",
            json.getBytes(StandardCharsets.UTF_8));

        MallaImportRequest result = parser.parseFile(file);

        assertThat(result.nombre()).isEqualTo("Malla Ingeniería 2024");
        assertThat(result.version()).isEqualTo("2.1");
        assertThat(result.carreraId()).isEqualTo(7L);
        assertThat(result.materias()).hasSize(2);
        assertThat(result.materias().get(0))
            .extracting(MateriaImportRequest::codigo, MateriaImportRequest::semestre,
                MateriaImportRequest::creditos)
            .containsExactly("MAT101", 1, 5);
        assertThat(result.materias().get(0).prerequisitos()).isEmpty();
        assertThat(result.materias().get(1).prerequisitos()).containsExactly("MAT101");
    }

    @ParameterizedTest(name = "BOM presente: {0}")
    @ValueSource(booleans = {false, true})
    @DisplayName("PF-02 parseFile con CSV valido transforma las tres materias correctamente (con y sin BOM)")
    void parseFileCsvValidoTransformaMaterias(boolean conBom) {
        String contenido = conBom ? "\uFEFF" + CSV_VALIDO : CSV_VALIDO;
        MockMultipartFile file = csv(contenido);

        MallaImportRequest result = parser.parseFile(file);

        assertThat(result.nombre()).isEqualTo("Malla Importada");
        assertThat(result.version()).isEqualTo("1.0");
        assertThat(result.carreraId()).isNull();
        assertThat(result.materias()).hasSize(3);

        MateriaImportRequest matematica = result.materias().get(0);
        assertThat(matematica.codigo()).isEqualTo("MAT101");
        assertThat(matematica.nombre()).isEqualTo("Matemática I");
        assertThat(matematica.semestre()).isEqualTo(1);
        assertThat(matematica.creditos()).isEqualTo(5);
        assertThat(matematica.prerequisitos()).isEmpty();

        MateriaImportRequest fisica = result.materias().get(1);
        assertThat(fisica.codigo()).isEqualTo("FIS102");
        assertThat(fisica.semestre()).isEqualTo(2);
        assertThat(fisica.creditos()).isEqualTo(4);
        assertThat(fisica.prerequisitos()).containsExactly("MAT101");

        MateriaImportRequest programacion = result.materias().get(2);
        assertThat(programacion.codigo()).isEqualTo("PRO201");
        assertThat(programacion.semestre()).isEqualTo(3);
        assertThat(programacion.creditos()).isEqualTo(5);
        assertThat(programacion.prerequisitos()).containsExactly("MAT101", "FIS102");
    }

    @Test
    @DisplayName("PF-03 parseFile con nombre de archivo null lanza IllegalArgumentException")
    void parseFileNombreNullLanzaIllegalArgumentException() {
        when(mockFile.getOriginalFilename()).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> parser.parseFile(mockFile));

        assertThat(exception).hasMessage("Nombre de archivo inválido");
    }

    @Test
    @DisplayName("PF-04 parseFile con extension .txt lanza IllegalArgumentException por formato no soportado")
    void parseFileExtensionNoSoportadaLanzaIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile("file", "malla.txt", "text/plain",
            "contenido".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> parser.parseFile(file));

        assertThat(exception).hasMessage("Formato de archivo no soportado. Use CSV o JSON");
    }

    @Test
    @DisplayName("PF-05 parseFile con JSON malformado lanza IllegalArgumentException iniciando con Error al parsear JSON")
    void parseFileJsonMalformadoLanzaIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile("file", "malla.json", "application/json",
            "{esto no es un json valido".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> parser.parseFile(file));

        assertThat(exception.getMessage()).startsWith("Error al parsear JSON:");
    }

    @Test
    @DisplayName("PF-06 parseFile con CSV vacio lanza IllegalArgumentException sin datos")
    void parseFileCsvVacioLanzaIllegalArgumentException() {
        MockMultipartFile file = csv("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> parser.parseFile(file));

        assertThat(exception).hasMessage("El CSV no contiene datos");
    }

    @Test
    @DisplayName("PF-07 parseFile con encabezado de menos de cuatro columnas indica las columnas minimas")
    void parseFileCsvEncabezadoInsuficienteLanzaIllegalArgumentException() {
        MockMultipartFile file = csv("codigo,nombre,semestre\nMAT101,Matemática I,1\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> parser.parseFile(file));

        assertThat(exception).hasMessage("El CSV debe tener al menos: codigo,nombre,semestre,creditos");
    }

    @Test
    @DisplayName("PF-08 parseFile con fila de menos de cuatro columnas indica la linea fisica y la falta de columnas")
    void parseFileCsvFilaSinColumnasIndicaLineaYColumnas() {
        String contenido = CSV_HEADER + "\n\nMAT101,Matemática I\n";
        MockMultipartFile file = csv(contenido);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> parser.parseFile(file));

        assertThat(exception.getMessage())
            .contains("Línea 3")
            .contains("al menos 4 columnas");
    }

    @Test
    @DisplayName("PF-09 parseFile con valor no numerico en semestre indica el error numerico y la linea")
    void parseFileCsvSemestreNoNumericoIndicaLinea() {
        MockMultipartFile file = csv(CSV_HEADER + "\nMAT101,Matemática I,uno,5,\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> parser.parseFile(file));

        assertThat(exception.getMessage())
            .contains("Línea 2")
            .contains("Error en formato numérico");
    }

    @Test
    @DisplayName("PF-10 parseFile con encabezado valido pero sin materias lanza IllegalArgumentException")
    void parseFileCsvSinMateriasLanzaIllegalArgumentException() {
        MockMultipartFile file = csv(CSV_HEADER + "\n");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> parser.parseFile(file));

        assertThat(exception).hasMessage("El archivo CSV no contiene materias válidas");
    }

    @Test
    @DisplayName("PF-11 parseFile con InputStream que lanza IOException envuelve el error de lectura CSV")
    void parseFileCsvConIOExceptionLanzaIllegalArgumentException() throws IOException {
        when(mockFile.getOriginalFilename()).thenReturn("malla.csv");
        when(mockFile.getInputStream()).thenThrow(new IOException("fallo simulado"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> parser.parseFile(mockFile));

        assertThat(exception.getMessage()).startsWith("Error al leer archivo CSV:");
    }

    @Test
    @DisplayName("PF-12 parseFile con cuatro nombres de encabezado incorrectos debe rechazarse")
    void parseFileCsvEncabezadosIncorrectosSeRechaza() {
        MockMultipartFile file = csv("columna_a,columna_b,columna_c,columna_d\nMAT101,Matemática I,1,5,\n");

        assertThrows(IllegalArgumentException.class, () -> parser.parseFile(file));
    }

    @Test
    @DisplayName("PF-13 [cobertura] parseFile asigna creditos 0 cuando estan vacios y omite prerrequisitos ausentes o vacios")
    void parseFileCsvCreditosVaciosYPrerrequisitosOmitidos() {
        MockMultipartFile file = csv(CSV_HEADER
            + "\nMAT101,Matemática I,1,\n"
            + "FIS102,Física I,2,4,MAT101;;FIS102\n");

        MallaImportRequest result = parser.parseFile(file);

        assertThat(result.materias()).hasSize(2);
        assertThat(result.materias().get(0).semestre()).isEqualTo(1);
        assertThat(result.materias().get(0).creditos()).isZero();
        assertThat(result.materias().get(0).prerequisitos()).isEmpty();
        assertThat(result.materias().get(1).creditos()).isEqualTo(4);
        assertThat(result.materias().get(1).prerequisitos()).containsExactly("MAT101", "FIS102");
    }
}
