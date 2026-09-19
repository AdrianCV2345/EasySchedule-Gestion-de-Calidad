package com.easyschedule.backend.academico.materia.controller;

import com.easyschedule.backend.academico.materia.dto.PrerequisitoRequest;
import com.easyschedule.backend.academico.materia.dto.PrerequisitoResponse;
import com.easyschedule.backend.academico.materia.service.MateriaService;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/academico/prerequisitos")
@CrossOrigin(origins = {"http://localhost:4200", "https://easyschedule2.netlify.app"}, allowedHeaders = "*", allowCredentials = "true")
public class MateriaController {

    private static final Logger log = LoggerFactory.getLogger(MateriaController.class);

    private final MateriaService materiaService;

    public MateriaController(MateriaService materiaService) {
        this.materiaService = materiaService;
    }

    @PostMapping
    public ResponseEntity<Object> crearPrerequisito(@RequestBody PrerequisitoRequest request) {
        log.info("POST /api/academico/prerequisitos | body={}", request);
        try {
            PrerequisitoResponse response = materiaService.crearPrerequisito(request);
            log.info("Prerrequisito creado | id={}", response.id());
            return ResponseEntity.ok().body(response);
        } catch (IllegalArgumentException | EntityNotFoundException e) {
            log.warn("Error al crear prerrequisito: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<Object> eliminarPrerequisito(
            @RequestParam Long mallaMateriaId,
            @RequestParam Long prerequisitoMallaMateriaId) {
        log.info("DELETE /api/academico/prerequisitos | mallaMateriaId={} prerequisitoId={}", mallaMateriaId, prerequisitoMallaMateriaId);
        try {
            materiaService.eliminarPrerequisito(mallaMateriaId, prerequisitoMallaMateriaId);
            log.info("Prerrequisito eliminado | mallaMateriaId={} prerequisitoId={}", mallaMateriaId, prerequisitoMallaMateriaId);
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            log.warn("Error al eliminar prerrequisito: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
