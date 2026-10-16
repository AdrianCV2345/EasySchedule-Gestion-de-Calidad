package com.easyschedule.backend.auth.service;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GoogleTokenVerifierService {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifierService.class);
    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final RestTemplate restTemplate;
    private final String clientId;

    public GoogleTokenVerifierService(@Value("${app.google.client-id}") String clientId) {
        this(new RestTemplate(), clientId);
    }

    GoogleTokenVerifierService(RestTemplate restTemplate, String clientId) {
        this.restTemplate = restTemplate;
        this.clientId = clientId;
    }

    public GoogleUserInfo verify(String credential) {
        if (credential == null || credential.isBlank()) {
            log.warn("[AUTH_GOOGLE] credential vacia");
            return null;
        }

        if (clientId == null || clientId.isBlank()) {
            log.warn("[AUTH_GOOGLE] client id no configurado");
            return null;
        }

        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(GOOGLE_TOKEN_INFO_URL)
                    .queryParam("id_token", credential)
                    .build()
                    .toUri();

            ResponseEntity<Map> response = restTemplate.getForEntity(uri, Map.class);
            Map<?, ?> body = response.getBody();

            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                log.warn("[AUTH_GOOGLE] token invalido o respuesta vacia de Google");
                return null;
            }

            return buildGoogleUserInfo(body);
        } catch (RestClientResponseException ex) {
            log.warn("[AUTH_GOOGLE] Google rechazo el token | status={}", ex.getStatusCode());
            return null;
        } catch (Exception ex) {
            log.warn("[AUTH_GOOGLE] error verificando token de Google: {}", ex.getMessage());
            return null;
        }
    }

    private GoogleUserInfo buildGoogleUserInfo(Map<?, ?> payload) {
        String audience = getString(payload, "aud");
        String issuer = getString(payload, "iss");
        String expiration = getString(payload, "exp");
        String googleId = getString(payload, "sub");
        String email = getString(payload, "email");
        String emailVerified = getString(payload, "email_verified");
        String name = getString(payload, "name");

        if (!clientId.equals(audience)) {
            log.warn("[AUTH_GOOGLE] audiencia invalida");
            return null;
        }

        if (!"accounts.google.com".equals(issuer)
                && !"https://accounts.google.com".equals(issuer)) {
            log.warn("[AUTH_GOOGLE] issuer invalido");
            return null;
        }

        if (isExpired(expiration)) {
            log.warn("[AUTH_GOOGLE] token expirado");
            return null;
        }

        if (googleId == null || googleId.isBlank() || email == null || email.isBlank()) {
            log.warn("[AUTH_GOOGLE] token sin datos minimos requeridos");
            return null;
        }

        if (!"true".equalsIgnoreCase(emailVerified)) {
            log.warn("[AUTH_GOOGLE] email no verificado por Google");
            return null;
        }

        String normalizedEmail = email.trim();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            log.warn("[AUTH_GOOGLE] email invalido");
            return null;
        }

        return new GoogleUserInfo(
                googleId,
            normalizedEmail.toLowerCase(),
                name
        );
    }

    private boolean isExpired(String expiration) {
        if (expiration == null || expiration.isBlank()) {
            return true;
        }

        try {
            long expirationEpochSeconds = Long.parseLong(expiration);
            return Instant.ofEpochSecond(expirationEpochSeconds).isBefore(Instant.now());
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    private String getString(Map<?, ?> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }
}