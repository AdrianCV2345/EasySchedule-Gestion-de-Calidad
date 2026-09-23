package com.easyschedule.backend.estudiante.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.easyschedule.backend.shared.validation.EdadRange;

import java.time.LocalDate;

public record PerfilUpdateRequest(
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(max = 20, message = "El nombre de usuario no puede exceder 20 caracteres")
    String username,

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ]++(?: [A-Za-zÁÉÍÓÚáéíóúÑñ]++)*+$", message = "El nombre solo puede contener letras, espacios y acentos")
    String nombre,

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(min = 3, max = 100, message = "Los apellidos deben tener entre 3 y 100 caracteres")
    @Pattern(regexp = "^[A-Za-zÁÉÍÓÚáéíóúÑñ]++(?: [A-Za-zÁÉÍÓÚáéíóúÑñ]++)*+$", message = "Los apellidos solo pueden contener letras, espacios y acentos")
    String apellido,

    @NotBlank(message = "El correo electronico es obligatorio")
    @Email(message = "Formato de correo electronico invalido")
    @Size(max = 50, message = "El correo electronico no puede exceder 50 caracteres")
    String email,

    @NotBlank(message = "El carnet de identidad es obligatorio")
    @Size(min = 6, max = 16, message = "El carnet de identidad debe tener entre 6 y 16 caracteres")
    @Pattern(regexp = "(?i)^\\d{6,10}(?:-?(?:\\d[A-Z0-9]?|[A-Z0-9]?\\d))?(?:\\s?(?:LP|CB|SC|OR|PT|TJ|CH|BN|PD))?$", message = "Formato de carnet de identidad invalido para Bolivia")
    String carnetIdentidad,

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @Past(message = "La fecha de nacimiento debe estar en el pasado")
    @EdadRange(min = 16, max = 70, message = "La fecha de nacimiento debe corresponder a una edad entre 16 y 70 años")
    LocalDate fechaNacimiento,

    @Size(max = 120, message = "La carrera no puede exceder 120 caracteres")
    String carrera,

    @Size(max = 150, message = "La universidad no puede exceder 150 caracteres")
    String universidad
) {
}
