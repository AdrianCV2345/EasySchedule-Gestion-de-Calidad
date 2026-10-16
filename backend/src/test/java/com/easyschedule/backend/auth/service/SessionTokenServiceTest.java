package com.easyschedule.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionTokenServiceTest {

    private SessionTokenService service;

    @BeforeEach
    void setUp() {
        service = new SessionTokenService();
    }

    @Test
    void issueTokenCreatesUrlSafeTokenAndStoresUserWithConfiguredTtl() {
        String token = service.issueToken(42L);

        assertEquals(64, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]+"));
        assertEquals(3600L, service.getTokenTtlSeconds());
        assertEquals(Optional.of(42L), service.validateAndGetUserId(token));
    }

    @Test
    void issueTokenCreatesDifferentTokensForRepeatedCalls() {
        String firstToken = service.issueToken(42L);
        String secondToken = service.issueToken(42L);

        assertNotEquals(firstToken, secondToken);
    }

    @Test
    void validateAndGetUserIdRejectsNullAndBlankTokens() {
        assertTrue(service.validateAndGetUserId(null).isEmpty());
        assertTrue(service.validateAndGetUserId("   ").isEmpty());
    }

    @Test
    void validateAndGetUserIdRejectsUnknownToken() {
        assertTrue(service.validateAndGetUserId("unknown-token").isEmpty());
    }

    @Test
    void validateAndGetUserIdRejectsExpiredTokenAndRemovesIt() throws Exception {
        String expiredToken = "expired-token";
        putRecord(expiredToken, 42L, Instant.now().minusSeconds(1));

        assertTrue(service.validateAndGetUserId(expiredToken).isEmpty());
        assertTrue(tokenStore().isEmpty());
    }

    @Test
    void revokeTokenRemovesExistingToken() {
        String token = service.issueToken(42L);

        service.revokeToken(token);

        assertTrue(service.validateAndGetUserId(token).isEmpty());
    }

    @Test
    void revokeTokenToleratesNullBlankAndUnknownTokens() {
        service.revokeToken(null);
        service.revokeToken("   ");
        service.revokeToken("unknown-token");

        assertTrue(tokenStore().isEmpty());
    }

    @Test
    void issueTokenCleansUpExpiredRecordsBeforeCreatingNewToken() throws Exception {
        String expiredToken = "expired-token";
        putRecord(expiredToken, 42L, Instant.now().minusSeconds(1));

        String activeToken = service.issueToken(7L);

        assertFalse(tokenStore().containsKey(expiredToken));
        assertEquals(Optional.of(7L), service.validateAndGetUserId(activeToken));
    }

    @Test
    void tokenRefReturnsExpectedReferenceForNullShortAndLongTokens() throws Exception {
        assertEquals("n/a", invokeTokenRef(null));
        assertEquals("short", invokeTokenRef("short"));
        assertEquals("abcdef...7890", invokeTokenRef("abcdef1234567890"));
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, Object> tokenStore() {
        try {
            Field field = SessionTokenService.class.getDeclaredField("tokenStore");
            field.setAccessible(true);
            return (ConcurrentMap<String, Object>) field.get(service);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("No se pudo acceder al almacenamiento de tokens", exception);
        }
    }

    private void putRecord(String token, Long userId, Instant expiresAt) throws Exception {
        Class<?> recordType = Class.forName(
            "com.easyschedule.backend.auth.service.SessionTokenService$SessionTokenRecord"
        );
        Constructor<?> constructor = recordType.getDeclaredConstructor(Long.class, Instant.class);
        constructor.setAccessible(true);
        tokenStore().put(token, constructor.newInstance(userId, expiresAt));
    }

    private String invokeTokenRef(String token) throws Exception {
        Method method = SessionTokenService.class.getDeclaredMethod("tokenRef", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, token);
    }
}
