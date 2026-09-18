package com.easyschedule.backend.academico.estado_materia.service;

import com.easyschedule.backend.academico.estado_materia.dto.EstadoMateriaRequest;
import com.easyschedule.backend.academico.estado_materia.dto.EstadoMateriaResponse;
import com.easyschedule.backend.academico.estado_materia.model.EstadoMateria;
import com.easyschedule.backend.academico.estado_materia.repository.EstadoMateriaRepository;
import com.easyschedule.backend.academico.materia.model.Prerequisito;
import com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstadoMateriaService {

    private static final String ESTADO_CURSANDO = "cursando";
    private static final String ESTADO_APROBADA = "aprobada";
    private static final String ESTADO_PENDIENTE = "pendiente";

    private final EstadoMateriaRepository estadoMateriaRepository;
    private final PrerequisitoRepository prerequisitoRepository;
    private final EstadoMateriaService selfService;

    public EstadoMateriaService(
            EstadoMateriaRepository estadoMateriaRepository,
            PrerequisitoRepository prerequisitoRepository,
            @Lazy EstadoMateriaService selfService) {
        this.estadoMateriaRepository = estadoMateriaRepository;
        this.prerequisitoRepository = prerequisitoRepository;
        this.selfService = selfService;
    }

    public String getEstadoMateria(Long userId, Long mallaMateriaId) {
        return estadoMateriaRepository
                .findByUserIdAndMallaMateria_Id(userId, mallaMateriaId)
                .map(EstadoMateria::getEstado)
                .orElse(null);
    }

    public List<EstadoMateriaResponse> getEstadosByUserId(Long userId) {
        return estadoMateriaRepository.findByUser_Id(userId)
            .stream()
            .map(this::mapToResponse)
            .toList();
    }

    public List<EstadoMateriaResponse> getEstadosByMalla(Long userId, Long mallaId) {
        return estadoMateriaRepository.findByUserIdAndMallaId(userId, mallaId)
            .stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Transactional
    public EstadoMateriaResponse saveEstado(Long userId, EstadoMateriaRequest request) {
        // Proteger contra estado CURSANDO - solo se debe asignar al tomar una materia
        String estadoNormalizado = request.estado() == null ? "" : request.estado().trim().toLowerCase(Locale.ROOT);
        if (ESTADO_CURSANDO.equals(estadoNormalizado)) {
            throw new IllegalArgumentException("El estado 'cursando' se asigna automaticamente al tomar la materia. Solo se permite cambiar a 'aprobada' o 'pendiente'.");
        }

        // Validar prerequisitos si se intenta cambiar a APROBADA
        if (ESTADO_APROBADA.equals(estadoNormalizado)) {
            validarPrerequisitosCompletados(userId, request.mallaMateriaId());
        }

        estadoMateriaRepository.upsertEstado(
            userId,
            request.mallaMateriaId(),
            request.estado()
        );
        EstadoMateria estado = estadoMateriaRepository
            .findByUserIdAndMallaMateria_Id(userId, request.mallaMateriaId())
            .orElseThrow(() -> new RuntimeException("Estado no guardado"));
        return mapToResponse(estado);
    }

    @Transactional
    public List<EstadoMateriaResponse> saveEstados(Long userId, List<EstadoMateriaRequest> requests) {
        return requests.stream()
            .filter(request -> {
                String estadoNormalizado = request.estado() == null ? "" : request.estado().trim().toLowerCase(Locale.ROOT);
                return !ESTADO_CURSANDO.equals(estadoNormalizado);
            })
            .map(request -> selfService.saveEstado(userId, request))
            .toList();
    }

    @Transactional
    public void syncEstadoFromToma(Long userId, Long mallaMateriaId, String tomaEstado) {
        String estadoDestino = mapEstadoTomaToEstadoMateria(tomaEstado);
        estadoMateriaRepository.upsertEstado(userId, mallaMateriaId, estadoDestino);
    }

    @Transactional
    public void markPendiente(Long userId, Long mallaMateriaId) {
        estadoMateriaRepository.upsertEstado(userId, mallaMateriaId, ESTADO_PENDIENTE);
    }

    private EstadoMateriaResponse mapToResponse(EstadoMateria entity) {
        return new EstadoMateriaResponse(
            entity.getId(),
            entity.getUser().getId(),
            entity.getMallaMateria().getId(),
            entity.getEstado(),
            entity.getFechaActualizacion()
        );
    }

    private String mapEstadoTomaToEstadoMateria(String tomaEstado) {
        if (tomaEstado == null) {
            return ESTADO_CURSANDO;
        }

        String normalized = tomaEstado.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case ESTADO_APROBADA -> ESTADO_APROBADA;
            case "inscrita" -> ESTADO_CURSANDO;
            case "retirada", "reprobada" -> ESTADO_PENDIENTE;
            default -> ESTADO_CURSANDO;
        };
    }

    private void validarPrerequisitosCompletados(Long userId, Long mallaMateriaId) {
        // Obtener todos los prerequisitos de la materia
        List<Prerequisito> prerequisitos = prerequisitoRepository.findByMallaMateria_Id(mallaMateriaId);
        
        if (prerequisitos.isEmpty()) {
            // No tiene prerequisitos, puede ser aprobada
            return;
        }

        // Verificar que todos los prerequisitos estan aprobados
        for (Prerequisito prereq : prerequisitos) {
            Long prereqMallaMateriaId = prereq.getPrerequisito().getId();
            
            EstadoMateria estadoPrereq = estadoMateriaRepository
                .findByUserIdAndMallaMateria_Id(userId, prereqMallaMateriaId)
                .orElse(null);
            
            // Si el prerequisito no tiene estado registrado o no está aprobado, lanzar error
            if (estadoPrereq == null || !ESTADO_APROBADA.equalsIgnoreCase(estadoPrereq.getEstado())) {
                String nombrePrereq = prereq.getPrerequisito().getMateria().getNombre();
                throw new IllegalArgumentException(
                    "No se puede cambiar a completado. Primero debe completar el prerequisito: " + nombrePrereq
                );
            }
        }
    }
}
