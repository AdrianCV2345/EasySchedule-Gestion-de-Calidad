package com.easyschedule.backend.academico.malla.service;

import com.easyschedule.backend.academico.malla.dto.MallaMateriaResponse;
import com.easyschedule.backend.academico.malla.dto.MateriaDisponibleConOfertasResponse;
import com.easyschedule.backend.academico.oferta_materia.dto.OfertaMateriaResponse;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MallaDisponibilidadService {

    private final MallaService mallaService;
    private final OfertaMateriaRepository ofertaMateriaRepository;

    public MallaDisponibilidadService(MallaService mallaService, OfertaMateriaRepository ofertaMateriaRepository) {
        this.mallaService = mallaService;
        this.ofertaMateriaRepository = ofertaMateriaRepository;
    }

    public List<MallaMateriaResponse> getMateriasDisponibles(Long mallaId, Long userId) {
        List<MallaMateriaResponse> todasLasMaterias = mallaService.findMateriasByMalla(mallaId, userId);

        Map<Long, List<Long>> adj = new HashMap<>();
        Map<Long, Integer> inDegree = inicializarGrafo(todasLasMaterias, adj);

        Map<Long, Integer> effectiveInDegree = new HashMap<>(inDegree);
        propagarMateriasAprobadas(effectiveInDegree, todasLasMaterias, adj);

        return todasLasMaterias.stream()
            .filter(m -> !estaCompletadaOCursando(m.estado())
                && effectiveInDegree.getOrDefault(m.id(), 0) <= 0)
            .toList();
    }

    private Map<Long, Integer> inicializarGrafo(
        List<MallaMateriaResponse> materias,
        Map<Long, List<Long>> adj
    ) {
        Map<Long, Integer> inDegree = new HashMap<>();

        for (MallaMateriaResponse materia : materias) {
            List<Long> prerequisitos = materia.prerequisitosIds();
            inDegree.put(materia.id(), prerequisitos != null ? prerequisitos.size() : 0);
            adj.putIfAbsent(materia.id(), new ArrayList<>());

            // Construir lista de adyacencia (de prerequisito -> dependiente)
            if (prerequisitos != null) {
                for (Long prereqId : prerequisitos) {
                    adj.putIfAbsent(prereqId, new ArrayList<>());
                    adj.get(prereqId).add(materia.id());
                }
            }
        }
        return inDegree;
    }

    private void propagarMateriasAprobadas(
        Map<Long, Integer> effectiveInDegree,
        List<MallaMateriaResponse> materias,
        Map<Long, List<Long>> adj
    ) {
        for (MallaMateriaResponse materia : materias) {
            // Basado en el MCP, el estado para materias completadas es "aprobada"
            if ("aprobada".equalsIgnoreCase(materia.estado())) {
                List<Long> dependientes = adj.get(materia.id());
                if (dependientes != null) {
                    for (Long depId : dependientes) {
                        effectiveInDegree.put(depId, effectiveInDegree.getOrDefault(depId, 0) - 1);
                    }
                }
            }
        }
    }

    private boolean estaCompletadaOCursando(String estado) {
        // Está disponible si NO está "aprobada" ni "cursando"
        // (puede ser "pendiente" o null cuando no hay registro en DB)
        return "aprobada".equalsIgnoreCase(estado) || "cursando".equalsIgnoreCase(estado);
    }

    public List<MateriaDisponibleConOfertasResponse> getMateriasDisponiblesConOfertas(Long mallaId, Long userId) {
        List<MallaMateriaResponse> disponibles = getMateriasDisponibles(mallaId, userId);
        
        return disponibles.stream().map(m -> {
            List<OfertaMateriaResponse> ofertas = ofertaMateriaRepository.findByMateriaId(m.materiaId()).stream()
                .map(o -> new OfertaMateriaResponse(
                    o.getId(),
                    o.getSemestre(),
                    o.getParalelo(),
                    o.getDocente(),
                    o.getAula()
                ))
                .toList();
            
            return new MateriaDisponibleConOfertasResponse(
                m.id(),
                m.materiaId(),
                m.codigoMateria(),
                m.nombreMateria(),
                m.creditos(),
                m.semestreSugerido(),
                m.estado(),
                m.prerequisitosIds(),
                ofertas
            );
        }).collect(Collectors.toList());
    }
}
