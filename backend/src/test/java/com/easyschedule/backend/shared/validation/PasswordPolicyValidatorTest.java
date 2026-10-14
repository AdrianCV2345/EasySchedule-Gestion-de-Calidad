package com.easyschedule.backend.shared.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyValidator();
    }

    @Test
    void rejectsNullPassword() {
        assertFalse(validator.isValid(null, null));
    }

    @Test
    void rejectsBlankPassword() {
        assertFalse(validator.isValid("   ", null));
    }

    @Test
    void rejectsPasswordOutsideLengthRange() {
        assertFalse(validator.isValid("Aa1!xyz", null));
        assertFalse(validator.isValid("Aa1!" + "a".repeat(117), null));
    }

    @Test
    void rejectsPasswordWithoutUppercase() {
        assertFalse(validator.isValid("abcdef1!", null));
    }

    @Test
    void rejectsPasswordWithoutLowercase() {
        assertFalse(validator.isValid("ABCDEF1!", null));
    }

    @Test
    void rejectsPasswordWithoutDigit() {
        assertFalse(validator.isValid("Abcdefgh!", null));
    }

    @Test
    void rejectsPasswordWithoutSpecialCharacter() {
        assertFalse(validator.isValid("Abcdefg1", null));
    }

    @Test
    void acceptsPasswordWithAllRequiredCharacteristics() {
        assertTrue(validator.isValid("SecurePass123!", null));
    }
}
