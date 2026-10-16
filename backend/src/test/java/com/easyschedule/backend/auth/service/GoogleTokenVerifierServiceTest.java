package com.easyschedule.backend.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class GoogleTokenVerifierServiceTest {

    private static final String CLIENT_ID = "configured-client-id";

    @Mock
    private RestTemplate restTemplate;

    private GoogleTokenVerifierService service;

    @BeforeEach
    void setUp() {
        service = new GoogleTokenVerifierService(restTemplate, CLIENT_ID);
    }

    @Test
    void givenValidGooglePayload_whenVerify_thenReturnsNormalizedUserInfo() {
        Map<String, Object> payload = validPayload();
        payload.put("email", " User@Example.COM ");

        whenGoogleResponds(HttpStatus.OK, payload);

        GoogleUserInfo result = service.verify("valid-token");

        assertEquals(new GoogleUserInfo("google-subject", "user@example.com", "Ada Lovelace"), result);
    }

    @Test
    void givenValidAlternateIssuer_whenVerify_thenReturnsUserInfo() {
        Map<String, Object> payload = validPayload();
        payload.put("iss", "accounts.google.com");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertEquals(new GoogleUserInfo("google-subject", "user@example.com", "Ada Lovelace"), service.verify("token"));
    }

    @Test
    void givenInvalidClientId_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.put("aud", "another-client-id");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenInvalidIssuer_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.put("iss", "malicious.example.com");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenExpiredToken_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.put("exp", String.valueOf(Instant.now().minusSeconds(1).getEpochSecond()));
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("expired-token"));
    }

    @Test
    void givenMalformedExpiration_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.put("exp", "not-a-number");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenMissingExpiration_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.remove("exp");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenBlankExpiration_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.put("exp", "   ");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenNullBody_whenVerify_thenReturnsNull() {
        whenGoogleResponds(HttpStatus.OK, null);

        assertNull(service.verify("token"));
    }

    @Test
    void givenNonSuccessfulResponse_whenVerify_thenReturnsNull() {
        whenGoogleResponds(HttpStatus.UNAUTHORIZED, validPayload());

        assertNull(service.verify("token"));
    }

    @Test
    void givenMissingSubject_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.remove("sub");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenBlankSubject_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.put("sub", "   ");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenMissingEmail_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.remove("email");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenBlankEmail_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.put("email", "   ");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenInvalidEmail_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.put("email", "not-an-email");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenUnverifiedEmail_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.put("email_verified", "false");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenMissingEmailVerification_whenVerify_thenReturnsNull() {
        Map<String, Object> payload = validPayload();
        payload.remove("email_verified");
        whenGoogleResponds(HttpStatus.OK, payload);

        assertNull(service.verify("token"));
    }

    @Test
    void givenGoogleHttpError_whenVerify_thenReturnsNull() {
        HttpClientErrorException exception = HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED,
            "Unauthorized",
            HttpHeaders.EMPTY,
            new byte[0],
            StandardCharsets.UTF_8
        );
        when(restTemplate.getForEntity(any(URI.class), eq(Map.class))).thenThrow(exception);

        assertNull(service.verify("token"));
    }

    @Test
    void givenUnexpectedVerificationError_whenVerify_thenReturnsNull() {
        when(restTemplate.getForEntity(any(URI.class), eq(Map.class)))
            .thenThrow(new RestClientException("network failure"));

        assertNull(service.verify("token"));
    }

    @Test
    void givenBlankCredential_whenVerify_thenReturnsNullWithoutCallingGoogle() {
        assertNull(service.verify("   "));
    }

    @Test
    void givenNullCredential_whenVerify_thenReturnsNullWithoutCallingGoogle() {
        assertNull(service.verify(null));
    }

    @Test
    void givenMissingClientId_whenVerify_thenReturnsNullWithoutCallingGoogle() {
        GoogleTokenVerifierService serviceWithoutClientId = new GoogleTokenVerifierService(restTemplate, " ");

        assertNull(serviceWithoutClientId.verify("token"));
    }

    @Test
    void givenNullClientId_whenVerify_thenReturnsNullWithoutCallingGoogle() {
        GoogleTokenVerifierService serviceWithoutClientId = new GoogleTokenVerifierService(restTemplate, null);

        assertNull(serviceWithoutClientId.verify("token"));
    }

    @Test
    void givenSpringConstructor_whenVerify_thenUsesConfiguredClientId() {
        GoogleTokenVerifierService serviceWithPublicConstructor = new GoogleTokenVerifierService(CLIENT_ID);

        assertNull(serviceWithPublicConstructor.verify("   "));
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("aud", CLIENT_ID);
        payload.put("iss", "https://accounts.google.com");
        payload.put("exp", String.valueOf(Instant.now().plusSeconds(3600).getEpochSecond()));
        payload.put("sub", "google-subject");
        payload.put("email", "user@example.com");
        payload.put("email_verified", "true");
        payload.put("name", "Ada Lovelace");
        return payload;
    }

    private void whenGoogleResponds(HttpStatus status, Map<String, Object> payload) {
        when(restTemplate.getForEntity(any(URI.class), eq(Map.class)))
            .thenReturn(new ResponseEntity<>(payload, status));
    }
}
