package com.easyschedule.backend.auth.dto.request;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easyschedule.backend.shared.validation.PasswordPolicy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChangePasswordRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void cpV01RejectsNewPasswordThatViolatesPasswordPolicy() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("ActualPass123!");
        request.setNewPassword("12345678");
        request.setConfirmNewPassword("12345678");

        Set<ConstraintViolation<ChangePasswordRequest>> violations =
            validator.validate(request);

        assertFalse(violations.isEmpty(), "Se esperaba al menos una violacion de validacion");
        boolean policyViolation = violations.stream().anyMatch(v ->
            v.getPropertyPath().toString().equals("newPassword")
                && v.getConstraintDescriptor().getAnnotation().annotationType() == PasswordPolicy.class
        );
        assertTrue(policyViolation,
            "La politica de contrasenia debe rechazar '12345678' sobre el campo newPassword");
    }
}
