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
        List<MallaMateriaResponse> disponibles = mallaDisponibilidadService.getMateriasDisponibles(request.mallaId(), request.userId());
        validateSelectedMaterias(request.materiasSeleccionadas(), disponibles);
        List<List<ParaleloEstructuradoDTO>> materiasConParalelos = buildMateriasConParalelos(
            request.materiasSeleccionadas(), disponibles
        );

        if (materiasConParalelos.isEmpty()) {
            return Collections.emptyList();
        }

        PriorityQueue<HorarioGeneradoResponse> mejoresHorarios = new PriorityQueue<>(Collections.reverseOrder()); // Max-Heap for the worst score at top
        HorarioActualResponse actualResponse = horarioRecomendadoService.getHorarioActualByUserId(request.userId());
        List<ParaleloEstructuradoDTO> combinacionActual = mapActualToParalelos(actualResponse.clases());
        backtrack(
            0,
            materiasConParalelos,
            combinacionActual,
            mejoresHorarios,
            request.prioridades(),
            System.currentTimeMillis()
        );
        return sortResults(mejoresHorarios);
    }

    private void validateSelectedMaterias(
            List<MateriaSeleccionadaRequest> seleccionadas,
            List<MallaMateriaResponse> disponibles) {
        Set<Long> disponiblesIds = disponibles.stream()
            .map(MallaMateriaResponse::id)
            .collect(Collectors.toSet());

        for (MateriaSeleccionadaRequest seleccionada : seleccionadas) {
            if (disponiblesIds.contains(seleccionada.materiaId())) {
                continue;
            }
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "La materia con ID " + seleccionada.materiaId() + " no está disponible o no existe en la malla."
            );
        }
    }

    private List<List<ParaleloEstructuradoDTO>> buildMateriasConParalelos(
            List<MateriaSeleccionadaRequest> seleccionadas,
            List<MallaMateriaResponse> disponibles) {
        Map<Long, String> materiaNombres = disponibles.stream()
            .collect(Collectors.toMap(MallaMateriaResponse::id, MallaMateriaResponse::nombreMateria));

        List<List<ParaleloEstructuradoDTO>> resultado = new ArrayList<>();
        for (MateriaSeleccionadaRequest seleccionada : seleccionadas) {
            List<ParaleloEstructuradoDTO> paralelos = buildParalelos(seleccionada, materiaNombres);
            if (!paralelos.isEmpty()) {
                resultado.add(paralelos);
            }
        }
        return resultado;
    }

    private List<ParaleloEstructuradoDTO> buildParalelos(
            MateriaSeleccionadaRequest seleccionada,
            Map<Long, String> materiaNombres) {
        List<OfertaMateria> ofertas = ofertaMateriaRepository.findByMallaMateriaId(seleccionada.materiaId())
            .stream()
            .filter(oferta -> isSelectedParallel(seleccionada, oferta))
            .toList();

        List<ParaleloEstructuradoDTO> resultado = new ArrayList<>();
        for (OfertaMateria oferta : ofertas) {
            resultado.add(toParalelo(oferta, seleccionada.materiaId(), materiaNombres));
        }
        return resultado;
    }

    private boolean isSelectedParallel(MateriaSeleccionadaRequest seleccionada, OfertaMateria oferta) {
        if (!Objects.equals(oferta.getMallaMateriaId(), seleccionada.materiaId())) {
            return false;
        }
        return seleccionada.paralelos() == null
            || seleccionada.paralelos().isEmpty()
            || seleccionada.paralelos().contains(oferta.getParalelo());
    }

    private ParaleloEstructuradoDTO toParalelo(
            OfertaMateria oferta,
            Long materiaId,
            Map<Long, String> materiaNombres) {
        return new ParaleloEstructuradoDTO(
            oferta.getId(),
            oferta.getMallaMateriaId(),
            materiaNombres.getOrDefault(materiaId, "Desconocida"),
            oferta.getParalelo(),
            oferta.getDocente(),
            oferta.getAula(),
            parseHorarioJson(oferta.getHorarioJson()),
            false
        );
    }

    private List<HorarioGeneradoResponse> sortResults(
            PriorityQueue<HorarioGeneradoResponse> mejoresHorarios) {
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
        
        if (System.currentTimeMillis() - startTime > 1500) {
            return;
        }

        double puntajeParcial = calcularPuntaje(combinacionActual, prioridades);
        if (mejoresHorarios.size() == 50 && puntajeParcial >= mejoresHorarios.peek().puntajeTotal()) {
            return;
        }

        if (indexMateria == materiasConParalelos.size()) {
            addResult(combinacionActual, puntajeParcial, mejoresHorarios);
            return;
        }

        exploreParallels(indexMateria, materiasConParalelos, combinacionActual, mejoresHorarios, prioridades, startTime);
    }

    private void addResult(
            List<ParaleloEstructuradoDTO> combinacionActual,
            double puntaje,
            PriorityQueue<HorarioGeneradoResponse> mejoresHorarios) {
        mejoresHorarios.offer(new HorarioGeneradoResponse(puntaje, mapToHorarioClase(combinacionActual)));
        if (mejoresHorarios.size() > 50) {
            mejoresHorarios.poll();
        }
    }

    private void exploreParallels(
            int indexMateria,
            List<List<ParaleloEstructuradoDTO>> materiasConParalelos,
            List<ParaleloEstructuradoDTO> combinacionActual,
            PriorityQueue<HorarioGeneradoResponse> mejoresHorarios,
            List<String> prioridades,
            long startTime) {
        for (ParaleloEstructuradoDTO paralelo : materiasConParalelos.get(indexMateria)) {
            if (tieneCruceHorario(combinacionActual, paralelo)) continue;
            combinacionActual.add(paralelo);
            backtrack(indexMateria + 1, materiasConParalelos, combinacionActual, mejoresHorarios, prioridades, startTime);
            combinacionActual.remove(combinacionActual.size() - 1);
        }
    }

    private boolean tieneCruceHorario(List<ParaleloEstructuradoDTO> horarioActual, ParaleloEstructuradoDTO nuevoParalelo) {
        for (ClaseBloqueDTO bloqueNuevo : nuevoParalelo.bloques()) {
            for (ParaleloEstructuradoDTO existente : horarioActual) {
                if (existeCruceConBloques(horarioActual, bloqueNuevo)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean existeCruceConBloques(List<ParaleloEstructuradoDTO> horarioActual, ClaseBloqueDTO bloqueNuevo) {
        for (ParaleloEstructuradoDTO existente : horarioActual) {
            for (ClaseBloqueDTO bloqueExistente : existente.bloques()) {
                if (esMismoDia(bloqueExistente, bloqueNuevo) && hayCruceHorario(bloqueExistente, bloqueNuevo)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean esMismoDia(ClaseBloqueDTO bloqueExistente, ClaseBloqueDTO bloqueNuevo) {
        return bloqueExistente.dia().equalsIgnoreCase(bloqueNuevo.dia());
    }

    private boolean hayCruceHorario(ClaseBloqueDTO bloqueExistente, ClaseBloqueDTO bloqueNuevo) {
        return bloqueExistente.horaInicio().isBefore(bloqueNuevo.horaFin()) &&
            bloqueExistente.horaFin().isAfter(bloqueNuevo.horaInicio());
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
