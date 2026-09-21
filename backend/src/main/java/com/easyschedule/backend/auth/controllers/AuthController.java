
package com.easyschedule.backend.auth.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.easyschedule.backend.auth.dto.request.ChangePasswordRequest;
import com.easyschedule.backend.auth.dto.request.LoginRequest;
import com.easyschedule.backend.auth.dto.request.SignupRequest;
import com.easyschedule.backend.auth.service.AuthService;
import com.easyschedule.backend.auth.dto.request.GoogleLoginRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/registro")
    public ResponseEntity<Void> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        String identifier = signUpRequest.getUsername() == null ? "" : signUpRequest.getUsername().trim();
        String email = signUpRequest.getEmail() == null ? "" : signUpRequest.getEmail().trim().toLowerCase();
        log.debug("[AUTH_REGISTRO] request recibido | username={} email={}", identifier, email);
        log.info("[AUTH_REGISTRO] Intento de registro de nuevo usuario: {} / {}", identifier, email);
        try {
            authService.registerUser(signUpRequest);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (Exception ex) {
            log.error("[AUTH_REGISTRO] Error inesperado durante el registro de usuario: {}", identifier, ex);
            throw ex;
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest request) {
        String identifier = request.getIdentifier() == null ? "" : request.getIdentifier().trim();
        log.debug("[AUTH_LOGIN] request recibido | identifier={}", identifier);
        log.info("[AUTH_LOGIN] request recibido | identifier={}", identifier);
        return authService.login(request);
    }
    @PostMapping("/login/google")
    public ResponseEntity<Object> loginWithGoogle(@RequestBody GoogleLoginRequest request) {
        log.debug(
            "[AUTH_GOOGLE] request recibido | credentialPresent={}",
            request.getCredential() != null && !request.getCredential().isBlank()
        );
        return authService.loginWithGoogle(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Object> logout(HttpServletRequest request) {
        log.debug("[AUTH_LOGOUT] request recibido | authorizationHeaderPresent={}", request.getHeader("Authorization") != null);
        return authService.logout(request.getHeader("Authorization"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Object> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        Principal principal
    ) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
        }

        Long userId;
        try {
            userId = Long.valueOf(principal.getName());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida");
        }
        log.debug("[AUTH_CHANGE_PASSWORD] principal resuelto | userId={}", userId);
        log.info("[AUTH_CHANGE_PASSWORD] intento | userId={}", userId);
        return authService.changePassword(userId, request);
    }

}
