
package com.easyschedule.backend.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.easyschedule.backend.auth.dto.request.SignupRequest;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.dto.request.GoogleLoginRequest;

import com.easyschedule.backend.auth.repositories.UserRepository;
import com.easyschedule.backend.auth.dto.request.ChangePasswordRequest;
import com.easyschedule.backend.shared.exception.UserAlreadyExistsException;
import java.util.Optional;
import java.util.Map;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import com.easyschedule.backend.auth.dto.request.LoginRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String CREDENCIALES_INCORRECTAS_MESSAGE = "Credenciales incorrectas";

    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final SessionTokenService sessionTokenService;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    public AuthService(UserRepository userRepository, PasswordEncoder encoder, SessionTokenService sessionTokenService, GoogleTokenVerifierService googleTokenVerifierService) {
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.sessionTokenService = sessionTokenService;
        this.googleTokenVerifierService = googleTokenVerifierService;
    }

    public void registerUser(SignupRequest signUpRequest) {
        String normalizedUsername = signUpRequest.getUsername().trim();
        String normalizedEmail = signUpRequest.getEmail().trim().toLowerCase();
        log.debug("[AUTH_REGISTRO] datos normalizados | username={} email={}", normalizedUsername, normalizedEmail);
        log.info("[AUTH_REGISTRO] Intento de registro de nuevo usuario: {} / {}", normalizedUsername, normalizedEmail);

        if (Boolean.TRUE.equals(userRepository.existsByUsernameIgnoreCase(normalizedUsername))) {
            log.warn("[AUTH_REGISTRO] el username {} ya existe", normalizedUsername);
            throw new UserAlreadyExistsException("Error: El nombre de usuario ya está en uso");
        }

        if (Boolean.TRUE.equals(userRepository.existsByEmailIgnoreCase(normalizedEmail))) {
            log.warn("[AUTH_REGISTRO] el email {} ya está en uso", normalizedEmail);
            throw new UserAlreadyExistsException("Error: El correo electrónico ya está registrado");
        }

        User user = new User(
            normalizedUsername,
            normalizedEmail,
            encoder.encode(signUpRequest.getPassword())
        );
        
        userRepository.save(user);
        log.info("[AUTH_REGISTRO] Usuario creado exitosamente: {}", normalizedUsername);
    }
    public ResponseEntity<Object> login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        log.debug("[AUTH_LOGIN] normalizando identificador | identifier={}", identifier);
        log.info("[AUTH_LOGIN] intento autenticacion | identifier={}", identifier);

        Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(identifier)
            .or(() -> userRepository.findByEmailIgnoreCase(identifier));

        log.debug("[AUTH_LOGIN] resultado busqueda usuario | identifier={} encontrado={}", identifier, userOpt.isPresent());

        if (userOpt.isEmpty()) {
            log.warn("[AUTH_LOGIN] fallo autenticacion | identifier={} motivo=usuario_no_encontrado", identifier);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(CREDENCIALES_INCORRECTAS_MESSAGE);
        }
    
        User user = userOpt.get();
    

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            log.warn("[AUTH_LOGIN] fallo autenticacion | userId={} motivo=password_no_configurada", user.getId());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(CREDENCIALES_INCORRECTAS_MESSAGE);
        }

        if (!encoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("[AUTH_LOGIN] fallo autenticacion | userId={} motivo=password_incorrecta", user.getId());
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(CREDENCIALES_INCORRECTAS_MESSAGE);
        }

    log.debug("[AUTH_LOGIN] credenciales validadas | userId={}", user.getId());

        String token = sessionTokenService.issueToken(user.getId());
        log.info("[AUTH_LOGIN] autenticacion exitosa | userId={} username={}", user.getId(), user.getUsername());

        return ResponseEntity.ok().body(
            Map.of(
                "token", token,
                "username", user.getUsername(),
                "expiresInSeconds", sessionTokenService.getTokenTtlSeconds(),
                "message", "Login exitoso"
            )
        );
    }

    public ResponseEntity<Object> loginWithGoogle(GoogleLoginRequest request) {
        String credential = request.getCredential();

        if (credential == null || credential.isBlank()) {
            log.warn("[AUTH_GOOGLE] fallo autenticacion | motivo=credential_vacia");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Login con Google invalido");
        }

        GoogleUserInfo googleUserInfo = googleTokenVerifierService.verify(credential);

        if (googleUserInfo == null) {
            log.warn("[AUTH_GOOGLE] fallo autenticacion | motivo=token_invalido");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Login con Google invalido");
        }

        User user = userRepository.findByEmailIgnoreCase(googleUserInfo.email())
                .map(existingUser -> updateGoogleAssociation(existingUser, googleUserInfo))
                .orElseGet(() -> createGoogleUser(googleUserInfo));

        String token = sessionTokenService.issueToken(user.getId());

        log.info("[AUTH_GOOGLE] autenticacion exitosa | userId={} username={}", user.getId(), user.getUsername());

        return ResponseEntity.ok().body(
                Map.of(
                        "token", token,
                        "username", user.getUsername(),
                        "expiresInSeconds", sessionTokenService.getTokenTtlSeconds(),
                        "message", "Login con Google exitoso"
                )
        );
    }

    public ResponseEntity<Object> logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        log.debug("[AUTH_LOGOUT] token extraido para revocacion | tokenPresent={}", !token.isBlank());
        sessionTokenService.revokeToken(token);
        return ResponseEntity.ok().body(Map.of("message", "Sesion cerrada correctamente"));
    }

    public ResponseEntity<Object> changePassword(Long userId, ChangePasswordRequest request) {
        log.debug("[AUTH_CHANGE_PASSWORD] inicio de cambio de contraseña | userId={}", userId);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    

        if (!encoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            log.warn("[AUTH_CHANGE_PASSWORD] fallo | userId={} | reason=CURRENT_PASSWORD_INCORRECT", userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contrasenia actual es incorrecta");
        }
    

        if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
            log.warn("[AUTH_CHANGE_PASSWORD] fallo | userId={} | reason=PASSWORD_CONFIRMATION_MISMATCH", userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contrasenia y su confirmacion no coinciden");
        }
    

        if (encoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            log.warn("[AUTH_CHANGE_PASSWORD] fallo | userId={} | reason=NEW_PASSWORD_EQUALS_OLD", userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contrasenia debe ser diferente a la actual");
        }
    

        user.setPasswordHash(encoder.encode(request.getNewPassword()));
        user.setUpdatedAt(OffsetDateTime.now());
        userRepository.save(user);
    
        log.info("[AUTH_CHANGE_PASSWORD] exito | userId={}", userId);
    
        return ResponseEntity.ok().body(Map.of("message", "Contrasenia actualizada correctamente"));
    }

    private User updateGoogleAssociation(User user, GoogleUserInfo googleUserInfo) {
        boolean changed = false;

        if (user.getGoogleId() == null || user.getGoogleId().isBlank()) {
            user.setGoogleId(googleUserInfo.googleId());
            changed = true;
        }

        if (user.getAuthProvider() == null || user.getAuthProvider().isBlank()) {
            user.setAuthProvider("GOOGLE");
            changed = true;
        }

        if (changed) {
            user.setUpdatedAt(OffsetDateTime.now());
            return userRepository.save(user);
        }

        return user;
    }

    private User createGoogleUser(GoogleUserInfo googleUserInfo) {
        String baseUsername = buildUsernameFromEmail(googleUserInfo.email());
        String username = buildUniqueUsername(baseUsername);

        User user = new User();
        user.setUsername(username);
        user.setEmail(googleUserInfo.email());
        user.setPasswordHash(null);
        user.setGoogleId(googleUserInfo.googleId());
        user.setAuthProvider("GOOGLE");
        user.setActive(true);
        user.setTokenVersion(0);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());

        User savedUser = userRepository.save(user);

        log.info("[AUTH_GOOGLE] usuario creado con Google | userId={} username={}", savedUser.getId(), savedUser.getUsername());

        return savedUser;
    }

    private String buildUsernameFromEmail(String email) {
        String localPart = email.split("@")[0]
                .replaceAll("[^a-zA-Z0-9_]", "");

        if (localPart.length() < 3) {
            localPart = "user";
        }

        if (localPart.length() > 20) {
            return localPart.substring(0, 20);
        }

        return localPart;
    }

    private String buildUniqueUsername(String baseUsername) {
        String candidate = baseUsername;
        int suffix = 1;

        while (Boolean.TRUE.equals(userRepository.existsByUsernameIgnoreCase(candidate))) {
            String suffixText = String.valueOf(suffix);
            int maxBaseLength = 20 - suffixText.length();

            String trimmedBase = baseUsername.length() > maxBaseLength
                    ? baseUsername.substring(0, maxBaseLength)
                    : baseUsername;

            candidate = trimmedBase + suffixText;
            suffix++;
        }

        return candidate;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return "";
        }

        return authorizationHeader.substring(7).trim();
    }
}
