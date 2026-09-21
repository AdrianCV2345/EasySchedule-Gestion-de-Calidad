
package com.easyschedule.backend.shared.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_MESSAGE = "message";



    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleResourceNotFound(ResourceNotFoundException ex, WebRequest request) {
        log.warn("[GLOBAL_EXCEPTION] recurso no encontrado | path={} message={}", pathOf(request), ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put(FIELD_TIMESTAMP, OffsetDateTime.now());
        body.put(FIELD_STATUS, HttpStatus.NOT_FOUND.value());
        body.put(FIELD_ERROR, "Not Found");
        body.put(FIELD_MESSAGE, ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        log.warn("[GLOBAL_EXCEPTION] validacion fallida | path={} errors={}", pathOf(request), ex.getBindingResult().getErrorCount());

        Map<String, Object> body = new HashMap<>();
        body.put(FIELD_TIMESTAMP, OffsetDateTime.now());
        body.put(FIELD_STATUS, HttpStatus.BAD_REQUEST.value());
        body.put(FIELD_ERROR, "Bad Request");

        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(error -> error.getDefaultMessage())
            .orElse("Datos inválidos");
        body.put(FIELD_MESSAGE, message);

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex, WebRequest request) {
        log.warn(
            "[GLOBAL_EXCEPTION] response status exception | path={} status={} reason={}",
            pathOf(request),
            ex.getStatusCode().value(),
            ex.getReason()
        );

        Map<String, Object> body = new HashMap<>();
        body.put(FIELD_TIMESTAMP, OffsetDateTime.now());
        body.put(FIELD_STATUS, ex.getStatusCode().value());
        body.put(FIELD_ERROR, ex.getStatusCode().toString());
        body.put(FIELD_MESSAGE, ex.getReason() != null ? ex.getReason() : "Error en la solicitud");

        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Object> handleUserAlreadyExists(UserAlreadyExistsException ex, WebRequest request) {
        log.warn("[GLOBAL_EXCEPTION] usuario ya existe | path={} message={}", pathOf(request), ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put(FIELD_TIMESTAMP, OffsetDateTime.now());
        body.put(FIELD_STATUS, HttpStatus.CONFLICT.value());
        body.put(FIELD_ERROR, "Conflict");
        body.put(FIELD_MESSAGE, ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        log.warn("[GLOBAL_EXCEPTION] argumento invalido | path={} message={}", pathOf(request), ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put(FIELD_TIMESTAMP, OffsetDateTime.now());
        body.put(FIELD_STATUS, HttpStatus.BAD_REQUEST.value());
        body.put(FIELD_ERROR, "Bad Request");
        body.put(FIELD_MESSAGE, ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("[GLOBAL_EXCEPTION] data integrity violation | path={} message={}", pathOf(request), ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put(FIELD_TIMESTAMP, OffsetDateTime.now());
        body.put(FIELD_STATUS, HttpStatus.CONFLICT.value());
        body.put(FIELD_ERROR, "Conflict");

        String message = cleanDatabaseMessage(ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : null);
        if (message == null || message.isBlank()) {
            message = "Conflicto de integridad al guardar los datos.";
        }
        if (ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
            org.hibernate.exception.ConstraintViolationException cve = (org.hibernate.exception.ConstraintViolationException) ex.getCause();
            String constraintName = cve.getConstraintName();
            if (constraintName != null) {
                if (constraintName.contains("users_username_key")) {
                    message = "Error: Username is already taken!";
                } else if (constraintName.contains("users_email_key")) {
                    message = "Error: Email is already in use!";
                } else if (constraintName.contains("uq_toma_user_oferta")) {
                    message = "Ya registraste esta materia/paralelo.";
                } else if (constraintName.contains("ck_estado_materia")) {
                    message = "El estado de la materia no es válido.";
                }
            }
        }
        
        body.put(FIELD_MESSAGE, message);

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex, WebRequest request) {
        log.error("[GLOBAL_EXCEPTION] runtime exception | path={} message={}", pathOf(request), ex.getMessage(), ex);

        Map<String, Object> body = new HashMap<>();
        body.put(FIELD_TIMESTAMP, OffsetDateTime.now());
        body.put(FIELD_STATUS, HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put(FIELD_ERROR, "Internal Server Error");
        body.put(FIELD_MESSAGE, ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception ex, WebRequest request) {
        log.error("[GLOBAL_EXCEPTION] exception no controlada | path={} message={}", pathOf(request), ex.getMessage(), ex);

        Map<String, Object> body = new HashMap<>();
        body.put(FIELD_TIMESTAMP, OffsetDateTime.now());
        body.put(FIELD_STATUS, HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put(FIELD_ERROR, "Internal Server Error");
        body.put(FIELD_MESSAGE, "Error interno del servidor");

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        log.warn(
            "[GLOBAL_EXCEPTION] metodo no soportado | path={} method={} supported={}",
            pathOf(request),
            ex.getMethod(),
            ex.getSupportedHttpMethods()
        );

        Map<String, Object> body = new HashMap<>();
        body.put(FIELD_TIMESTAMP, OffsetDateTime.now());
        body.put(FIELD_STATUS, HttpStatus.METHOD_NOT_ALLOWED.value());
        body.put(FIELD_ERROR, "Method Not Allowed");
        body.put(FIELD_MESSAGE, "Metodo HTTP no soportado para este endpoint");

        return new ResponseEntity<>(body, HttpStatus.METHOD_NOT_ALLOWED);
    }

    private String pathOf(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    private String cleanDatabaseMessage(String message) {
        if (message == null) {
            return null;
        }

        String cleaned = message.trim().replaceFirst("(?i)^error:\\s*", "");
        int newlineIndex = cleaned.indexOf('\n');
        if (newlineIndex >= 0) {
            cleaned = cleaned.substring(0, newlineIndex).trim();
        }

        return cleaned;
    }
}
