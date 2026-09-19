package com.easyschedule.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionTokenServiceTest {

    private final SessionTokenService sessionTokenService = new SessionTokenService();

    @Test
    void issueValidateAndRevokeTokenFlowWorks() {
        String token = sessionTokenService.issueToken(42L);

        Optional<Long> userId = sessionTokenService.validateAndGetUserId(token);
        assertTrue(userId.isPresent());
        assertEquals(42L, userId.get());

        sessionTokenService.revokeToken(token);

        Optional<Long> afterRevoke = sessionTokenService.validateAndGetUserId(token);
        assertTrue(afterRevoke.isEmpty());
    }

    @Test
    void tokenTtlIsOneHour() {
        assertEquals(3600L, sessionTokenService.getTokenTtlSeconds());
    }

    @Test
    void validateReturnsEmptyWhenTokenNotFound() {
        assertTrue(sessionTokenService.validateAndGetUserId("unknown").isEmpty());
    }

    @Test
    void validateReturnsEmptyWhenTokenIsBlank() {
        assertTrue(sessionTokenService.validateAndGetUserId("").isEmpty());
        assertTrue(sessionTokenService.validateAndGetUserId(null).isEmpty());
    }

    @Test
    void revokeDoesNotThrowWhenTokenNotFound() {
        assertDoesNotThrow(() -> sessionTokenService.revokeToken("unknown"));
    }

    @Test
    void revokeDoesNotThrowWhenTokenIsBlank() {
        assertDoesNotThrow(() -> sessionTokenService.revokeToken(""));
        assertDoesNotThrow(() -> sessionTokenService.revokeToken(null));
    }
}
