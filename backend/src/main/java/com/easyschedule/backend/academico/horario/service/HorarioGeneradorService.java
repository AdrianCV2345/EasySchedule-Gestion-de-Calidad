package com.easyschedule.backend.academico.horario.service;

import com.easyschedule.backend.academico.horario.dto.*;
import com.easyschedule.backend.academico.malla.dto.MallaMateriaResponse;
import com.easyschedule.backend.academico.malla.service.MallaDisponibilidadService;
import com.easyschedule.backend.academico.oferta_materia.model.OfertaMateria;
import com.easyschedule.backend.academico.oferta_materia.repository.OfertaMateriaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HorarioGeneradorService {

    private final MallaDisponibilidadService mallaDisponibilidadService;
    private final OfertaMateriaRepository ofertaMateriaRepository;
    private final HorarioRecomendadoService horarioRecomendadoService;
    private final ObjectMapper objectMapper;

    public HorarioGeneradorService(
            MallaDisponibilidadService mallaDisponibilidadService,
            OfertaMateriaRepository ofertaMateriaRepository,
            HorarioRecomendadoService horarioRecomendadoService,
            ObjectMapper objectMapper) {
        this.mallaDisponibilidadService = mallaDisponibilidadService;
        this.ofertaMateriaRepository = ofertaMateriaRepository;
        this.horarioRecomendadoService = horarioRecomendadoService;
        this.objectMapper = objectMapper;
    }

    public List<HorarioGeneradoResponse> generarHorarios(HorarioGeneradorRequest request) {
        // 1. Validación de Disponibilidad
        List<MallaMateriaResponse> disponibles = mallaDisponibilidadService.getMateriasDisponibles(request.mallaId(), request.userId());
        Set<Long> disponiblesIds = disponibles.stream().map(MallaMateriaResponse::id).collect(Collectors.toSet());

        for (MateriaSeleccionadaRequest ms : request.materiasSeleccionadas()) {
            if (!disponiblesIds.contains(ms.materiaId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La materia con ID " + ms.materiaId() + " no está disponible o no existe en la malla.");
            }
        }

        // 2. Preparación de Datos
        List<List<ParaleloEstructuradoDTO>> materiasConParalelos = new ArrayList<>();
        Map<Long, String> materiaNombres = disponibles.stream().collect(Collectors.toMap(MallaMateriaResponse::id, MallaMateriaResponse::nombreMateria));

        for (MateriaSeleccionadaRequest ms : request.materiasSeleccionadas()) {
            List<OfertaMateria> todas = ofertaMateriaRepository.findByMallaMateriaId(ms.materiaId());
            List<OfertaMateria> ofertas;
            if (ms.paralelos() == null || ms.paralelos().isEmpty()) {
                ofertas = todas;
            } else {
                ofertas = todas.stream()
                    .filter(o -> ms.paralelos().contains(o.getParalelo()))
                    .collect(Collectors.toList());
            }
            
            List<ParaleloEstructuradoDTO> paralelosParsed = new ArrayList<>();

            for (OfertaMateria oferta : ofertas) {
                // Ensure the offer belongs to the selected materia
                if (!oferta.getMallaMateriaId().equals(ms.materiaId())) continue;

                List<ClaseBloqueDTO> bloques = parseHorarioJson(oferta.getHorarioJson());
                String nombreMateria = materiaNombres.getOrDefault(ms.materiaId(), "Desconocida");
                
                paralelosParsed.add(new ParaleloEstructuradoDTO(
                        oferta.getId(),
                        oferta.getMallaMateriaId(),
                        nombreMateria,
                        oferta.getParalelo(),
                        oferta.getDocente(),
                        oferta.getAula(),
                        bloques,
                        false // esMateriaActual
                ));
            }

            if (!paralelosParsed.isEmpty()) {
                materiasConParalelos.add(paralelosParsed);
            }
        }

        if (materiasConParalelos.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Algoritmo de Backtracking
        PriorityQueue<HorarioGeneradoResponse> mejoresHorarios = new PriorityQueue<>(Collections.reverseOrder()); // Max-Heap for the worst score at top
        
        HorarioActualResponse actualResponse = horarioRecomendadoService.getHorarioActualByUserId(request.userId());
        List<ParaleloEstructuradoDTO> combinacionActual = mapActualToParalelos(actualResponse.clases());
        
        long startTime = System.currentTimeMillis();
        backtrack(0, materiasConParalelos, combinacionActual, mejoresHorarios, request.prioridades(), startTime);

        // Convert Max-Heap to sorted list (Min to Max score)
        List<HorarioGeneradoResponse> resultado = new ArrayList<>();
        while (!mejoresHorarios.isEmpty()) {
            resultado.add(mejoresHorarios.poll());
        }
        Collections.reverse(resultado);

        return resultado;
    }

    private void backtrack(
            int indexMateria,
            List<List<ParaleloEstructuradoDTO>> materiasConParalelos,
            List<ParaleloEstructuradoDTO> combinacionActual,
            PriorityQueue<HorarioGeneradoResponse> mejoresHorarios,
            List<String> prioridades,
            long startTime) {
        
        // Timeout check (1500 ms)
        if (System.currentTimeMillis() - startTime > 1500) {
            return;
        }

        // Branch and Bound (Poda por Puntaje)
        double puntajeParcial = calcularPuntaje(combinacionActual, prioridades);
        if (mejoresHorarios.size() == 50 && puntajeParcial >= mejoresHorarios.peek().puntajeTotal()) {
            return;
        }

        // Caso Base
        if (indexMateria == materiasConParalelos.size()) {
            List<HorarioClaseResponse> clases = mapToHorarioClase(combinacionActual);
            HorarioGeneradoResponse nuevoHorario = new HorarioGeneradoResponse(puntajeParcial, clases);
            
            mejoresHorarios.offer(nuevoHorario);
            if (mejoresHorarios.size() > 50) {
                mejoresHorarios.poll(); // Remove the worst
            }
            return;
        }

        // Recursividad
        for (ParaleloEstructuradoDTO paralelo : materiasConParalelos.get(indexMateria)) {
            if (tieneCruceHorario(combinacionActual, paralelo)) {
                continue;
            }

            combinacionActual.add(paralelo);
            backtrack(indexMateria + 1, materiasConParalelos, combinacionActual, mejoresHorarios, prioridades, startTime);
            combinacionActual.remove(combinacionActual.size() - 1);
        }
    }

    private boolean tieneCruceHorario(List<ParaleloEstructuradoDTO> horarioActual, ParaleloEstructuradoDTO nuevoParalelo) {
        for (ClaseBloqueDTO bloqueNuevo : nuevoParalelo.bloques()) {
            for (ParaleloEstructuradoDTO existente : horarioActual) {
                for (ClaseBloqueDTO bloqueExistente : existente.bloques()) {
                    if (bloqueExistente.dia().equalsIgnoreCase(bloqueNuevo.dia())) {
                        // Verifica cruce: (InicioA < FinB) && (FinA > InicioB)
                        if (bloqueExistente.horaInicio().isBefore(bloqueNuevo.horaFin()) &&
                            bloqueExistente.horaFin().isAfter(bloqueNuevo.horaInicio())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private double calcularPuntaje(List<ParaleloEstructuradoDTO> combinacion, List<String> prioridades) {
        if (combinacion.isEmpty() || prioridades == null) return 0.0;

        double puntajeTotal = 0;
        int[] multiplicadores = {10000, 1000, 100, 10, 1};

        for (int i = 0; i < prioridades.size() && i < multiplicadores.length; i++) {
            String prioridad = prioridades.get(i);
            double penalizacionBruta = 0;

            switch (prioridad) {
                case "EVITAR_PRIMERA_HORA":
                    penalizacionBruta = evaluarEvitarPrimeraHora(combinacion);
                    break;
                case "CONCENTRAR_MANANA":
                    penalizacionBruta = evaluarConcentrarManana(combinacion);
                    break;
                case "CONCENTRAR_TARDE":
                    penalizacionBruta = evaluarConcentrarTarde(combinacion);
                    break;
                case "MINIMIZAR_VENTANAS":
                    penalizacionBruta = evaluarMinimizarVentanas(combinacion);
                    break;
                case "TENER_DIAS_LIBRES":
                    penalizacionBruta = evaluarTenerDiasLibres(combinacion);
                    break;
                default:
                    penalizacionBruta = 0;
                    break;
            }

            puntajeTotal += (penalizacionBruta * multiplicadores[i]);
        }

        return puntajeTotal;
    }

    private double evaluarEvitarPrimeraHora(List<ParaleloEstructuradoDTO> combinacion) {
        double penalty = 0;
        Set<String> diasPenalizados = new HashSet<>();
        LocalTime sieteAM = LocalTime.of(7, 0);

        for (ParaleloEstructuradoDTO p : combinacion) {
            for (ClaseBloqueDTO b : p.bloques()) {
                if (!b.horaInicio().isAfter(sieteAM) && !diasPenalizados.contains(b.dia())) {
                    penalty += 1.0;
                    diasPenalizados.add(b.dia());
                }
            }
        }
        return penalty;
    }

    private double evaluarConcentrarManana(List<ParaleloEstructuradoDTO> combinacion) {
        double penalty = 0;
        LocalTime unaPM = LocalTime.of(13, 0);

        for (ParaleloEstructuradoDTO p : combinacion) {
            for (ClaseBloqueDTO b : p.bloques()) {
                if (b.horaInicio().isAfter(unaPM) || b.horaInicio().equals(unaPM)) {
                    penalty += 1.0;
                }
            }
        }
        return penalty;
    }

    private double evaluarConcentrarTarde(List<ParaleloEstructuradoDTO> combinacion) {
        double penalty = 0;
        LocalTime dosPM = LocalTime.of(14, 0);

        for (ParaleloEstructuradoDTO p : combinacion) {
            for (ClaseBloqueDTO b : p.bloques()) {
                if (b.horaInicio().isBefore(dosPM)) {
                    penalty += 1.0;
                }
            }
        }
        return penalty;
    }

    private double evaluarMinimizarVentanas(List<ParaleloEstructuradoDTO> combinacion) {
        Map<String, List<ClaseBloqueDTO>> clasesPorDia = new HashMap<>();
        for (ParaleloEstructuradoDTO p : combinacion) {
            for (ClaseBloqueDTO b : p.bloques()) {
                clasesPorDia.computeIfAbsent(b.dia(), k -> new ArrayList<>()).add(b);
            }
        }

        double totalHorasHuecasSemana = 0;
        for (List<ClaseBloqueDTO> bloquesDia : clasesPorDia.values()) {
            if (bloquesDia.size() <= 1) continue;

            bloquesDia.sort(Comparator.comparing(ClaseBloqueDTO::horaInicio));

            LocalTime primeraHora = bloquesDia.get(0).horaInicio();
            LocalTime ultimaHora = bloquesDia.get(bloquesDia.size() - 1).horaFin();

            double horasTotalesSpan = (ultimaHora.toSecondOfDay() - primeraHora.toSecondOfDay()) / 3600.0;
            double horasRealesClase = 0;

            for (ClaseBloqueDTO b : bloquesDia) {
                horasRealesClase += (b.horaFin().toSecondOfDay() - b.horaInicio().toSecondOfDay()) / 3600.0;
            }

            double horasHuecas = horasTotalesSpan - horasRealesClase;
            if (horasHuecas > 0) {
                totalHorasHuecasSemana += horasHuecas;
            }
        }

        int diasClase = clasesPorDia.size();
        if (diasClase == 0) return 0;
        return (totalHorasHuecasSemana / 1.75 / diasClase);
    }

    private double evaluarTenerDiasLibres(List<ParaleloEstructuradoDTO> combinacion) {
        Set<String> diasConClase = new HashSet<>();
        for (ParaleloEstructuradoDTO p : combinacion) {
            for (ClaseBloqueDTO b : p.bloques()) {
                diasConClase.add(b.dia());
            }
        }
        return diasConClase.size();
    }

    private List<ParaleloEstructuradoDTO> mapActualToParalelos(List<HorarioClaseResponse> clases) {
        if (clases == null || clases.isEmpty()) return new ArrayList<>();
        List<ParaleloEstructuradoDTO> result = new ArrayList<>();
        for (HorarioClaseResponse c : clases) {
            List<ClaseBloqueDTO> bloques = new ArrayList<>();
            if (c.dia() != null && c.horaInicio() != null && c.horaFin() != null) {
                bloques.add(new ClaseBloqueDTO(
                    c.dia(), 
                    LocalTime.parse(c.horaInicio()), 
                    LocalTime.parse(c.horaFin())
                ));
            }
            result.add(new ParaleloEstructuradoDTO(
                null, 
                null, 
                c.materia(), 
                c.paralelo(), 
                c.docente(), 
                c.aula(), 
                bloques,
                true // esMateriaActual
            ));
        }
        return result;
    }

    private List<HorarioClaseResponse> mapToHorarioClase(List<ParaleloEstructuradoDTO> paralelos) {
        List<HorarioClaseResponse> res = new ArrayList<>();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm");
        for (ParaleloEstructuradoDTO p : paralelos) {
            for (ClaseBloqueDTO b : p.bloques()) {
                res.add(new HorarioClaseResponse(
                        p.nombreMateria(),
                        p.paralelo(),
                        b.dia(),
                        b.horaInicio().format(dtf),
                        b.horaFin().format(dtf),
                        p.docente(),
                        p.aula(),
                        null, // creditos
                        p.esMateriaActual(),
                        p.idOferta()
                ));
            }
        }
        return res;
    }

    private List<ClaseBloqueDTO> parseHorarioJson(String horarioJson) {
        List<ClaseBloqueDTO> bloques = new ArrayList<>();
        try {
            JsonNode array = objectMapper.readTree(horarioJson);
            if (!array.isArray()) return bloques;

            for (JsonNode slot : array) {
                String dia = text(slot, "dia");
                String horaInicioStr = text(slot, "inicio");
                if (horaInicioStr == null) horaInicioStr = text(slot, "hora_inicio");

                String horaFinStr = text(slot, "fin");
                if (horaFinStr == null) horaFinStr = text(slot, "hora_fin");

                if (dia != null && horaInicioStr != null && horaFinStr != null) {
                    bloques.add(new ClaseBloqueDTO(
                            dia,
                            LocalTime.parse(horaInicioStr),
                            LocalTime.parse(horaFinStr)
                    ));
                }
            }
        } catch (Exception ignored) {}
        return bloques;
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) return null;
        String t = value.asText();
        return t == null || t.isBlank() ? null : t;
    }
}
