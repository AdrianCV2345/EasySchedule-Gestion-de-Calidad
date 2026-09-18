package com.easyschedule.backend.estudiante.dto;

import java.util.Arrays;

public record AvanceGraduacionExport(
    byte[] contenido,
    String contentType,
    String filename
) {

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AvanceGraduacionExport other)) {
            return false;
        }
        return Arrays.equals(contenido, other.contenido)
            && java.util.Objects.equals(contentType, other.contentType)
            && java.util.Objects.equals(filename, other.filename);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(contenido);
        result = 31 * result + java.util.Objects.hashCode(contentType);
        result = 31 * result + java.util.Objects.hashCode(filename);
        return result;
    }
}
