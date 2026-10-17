package com.easyschedule.backend.estudiante.controller;

import com.easyschedule.backend.auth.dto.RegistroRequest;
import com.easyschedule.backend.estudiante.dto.AvanceGraduacionExport;
import com.easyschedule.backend.estudiante.dto.EstudianteResponse;
import com.easyschedule.backend.estudiante.dto.EstudianteUpdateRequest;
import com.easyschedule.backend.estudiante.dto.PerfilUpdateRequest;
import com.easyschedule.backend.estudiante.service.EstudianteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.easyschedule.backend.estudiante.service.EstudianteMallaExportService;

import java.security.Principal;

import java.util.List;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/estudiantes")
public class EstudianteController {

    private static final Logger log = LoggerFactory.getLogger(EstudianteController.class);

    private final EstudianteService estudianteService;
    private final EstudianteMallaExportService exportService;
    public EstudianteController(EstudianteService estudianteService, EstudianteMallaExportService exportService) {
        this.estudianteService = estudianteService;
        this.exportService = exportService;
    }

    @GetMapping
    public List<EstudianteResponse> findAll() {
        return estudianteService.findAll();
    }

    @GetMapping("/{id}")
    public EstudianteResponse findById(@PathVariable("id") Long id) {
        return estudianteService.findById(id);
    }

    @PutMapping("/{id}")
    public EstudianteResponse update(@PathVariable("id") Long id, @RequestBody EstudianteUpdateRequest request) {
        return estudianteService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Long id) {
        estudianteService.delete(id);
    }

    @PostMapping("/registro")
    public ResponseEntity<EstudianteResponse> register(@Valid @RequestBody RegistroRequest request) {
        String normalizedUsername = request.username() == null ? "" : request.username().trim();
        String normalizedEmail = request.email() == null ? "" : request.email().trim().toLowerCase();
        log.info("[ESTUDIANTE_REGISTRO] Intento de registro de nuevo estudiante: {} / {}", normalizedUsername,
                normalizedEmail);
        try {
            EstudianteResponse response = estudianteService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception ex) {
            log.error("[ESTUDIANTE_REGISTRO] Error inesperado durante el registro de estudiante: {}",
                    normalizedUsername, ex);
            throw ex;
        }
    }

    @GetMapping("/perfil/{username}")
    public EstudianteResponse findProfileByUsername(
            @PathVariable("username") String username,
            Principal principal) {
        Long userId = getAuthenticatedUserId(principal);
        validateProfileOwnership(username, userId);
        log.info("[PERFIL] Carga de perfil solicitada para el estudiante con ID: {}", userId);
        return estudianteService.findByUsername(username);
    }

    @RequestMapping(value = "/perfil/{username}", method = { RequestMethod.PUT, RequestMethod.PATCH })
    public EstudianteResponse updateProfile(
            @PathVariable("username") String username,
            @Valid @RequestBody PerfilUpdateRequest request,
            Principal principal) {
        Long userId = getAuthenticatedUserId(principal);
        validateProfileOwnership(username, userId);
        log.info("[PERFIL-EDICION] Intento de actualización de perfil para el estudiante con ID: {}", userId);
        return estudianteService.updateProfile(username, request);
    }

    @GetMapping("/me/avance-graduacion/export")
    public ResponseEntity<byte[]> exportarAvanceGraduacion(Principal principal) {
        Long userId = getAuthenticatedUserId(principal);
        AvanceGraduacionExport export = exportService.exportarAvanceGraduacion(userId, "pdf");

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.filename() + "\"")
            .contentType(MediaType.parseMediaType(export.contentType()))
            .body(export.contenido());
    }

    private void validateProfileOwnership(String username, Long userId) {
        if (!estudianteService.canAccessProfile(username, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para acceder a este perfil");
        }
    }

    private Long getAuthenticatedUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida");
        }

        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida");
        }
    }
}
