package com.easyschedule.backend.academico.seleccion.service;

import com.easyschedule.backend.academico.carrera.model.Carrera;
import com.easyschedule.backend.academico.carrera.repository.CarreraRepository;
import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import com.easyschedule.backend.academico.seleccion.dto.SeleccionRequest;
import com.easyschedule.backend.academico.seleccion.dto.SeleccionResponse;
import com.easyschedule.backend.academico.universidad.model.Universidad;
import com.easyschedule.backend.academico.universidad.repository.UniversidadRepository;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import com.easyschedule.backend.shared.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
public class SeleccionService {

    private static final String CARRERA_NO_ENCONTRADA_MESSAGE = "Carrera no encontrada";

    private final EstudianteRepository estudianteRepository;
    private final UniversidadRepository universidadRepository;
    private final CarreraRepository carreraRepository;
    private final MallaRepository mallaRepository;

    public SeleccionService(
        EstudianteRepository estudianteRepository,
        UniversidadRepository universidadRepository,
        CarreraRepository carreraRepository,
        MallaRepository mallaRepository
    ) {
        this.estudianteRepository = estudianteRepository;
        this.universidadRepository = universidadRepository;
        this.carreraRepository = carreraRepository;
        this.mallaRepository = mallaRepository;
    }

    public SeleccionResponse getSeleccionByUserId(Long userId) {
        Estudiante estudiante = getEstudianteOrThrow(userId);
        if (estudiante.getUniversidadId() == null || estudiante.getCarreraId() == null || estudiante.getMalla() == null) {
            return new SeleccionResponse(null, null, null, null, null, null);
        }

        Universidad universidad = universidadRepository.findByIdAndActiveTrue(estudiante.getUniversidadId())
            .orElseThrow(() -> new ResourceNotFoundException("Universidad no encontrada"));
        Carrera carrera = carreraRepository.findByIdAndActiveTrue(estudiante.getCarreraId())
            .orElseThrow(() -> new ResourceNotFoundException(CARRERA_NO_ENCONTRADA_MESSAGE));
        Malla malla = mallaRepository.findByIdAndActiveTrue(estudiante.getMalla().getId())
            .orElseThrow(() -> new ResourceNotFoundException("Malla no encontrada"));

        return new SeleccionResponse(
            universidad.getId(),
            universidad.getNombre(),
            carrera.getId(),
            carrera.getNombre(),
            malla.getId(),
            buildMallaLabel(malla)
        );
    }

    @Transactional
    public SeleccionResponse saveSeleccionByUserId(Long userId, SeleccionRequest request) {
        Universidad universidad = universidadRepository.findByIdAndActiveTrue(request.universidadId())
            .orElseThrow(() -> new ResourceNotFoundException("Universidad no encontrada"));
        Malla malla = mallaRepository.findByIdAndActiveTrue(request.mallaId())
            .orElseThrow(() -> new ResourceNotFoundException("Malla no encontrada"));
        Carrera carreraDeMalla = carreraRepository.findByIdAndActiveTrue(malla.getCarreraId())
            .orElseThrow(() -> new ResourceNotFoundException(CARRERA_NO_ENCONTRADA_MESSAGE));
        Carrera carreraSolicitada = request.carreraId() == null ? null : carreraRepository.findByIdAndActiveTrue(request.carreraId())
            .orElseThrow(() -> new ResourceNotFoundException(CARRERA_NO_ENCONTRADA_MESSAGE));

        if (!carreraDeMalla.getUniversidadId().equals(universidad.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La malla no pertenece a la universidad seleccionada");
        }

        if (carreraSolicitada != null) {
            if (!carreraSolicitada.getUniversidadId().equals(universidad.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La carrera no pertenece a la universidad seleccionada");
            }

            if (!Objects.equals(carreraSolicitada.getId(), carreraDeMalla.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La malla no pertenece a la carrera seleccionada");
            }
        }

        Estudiante estudiante = getEstudianteOrThrow(userId);
        boolean cambioMalla = estudiante.getMalla() == null || !Objects.equals(estudiante.getMalla().getId(), malla.getId());
        estudiante.setUniversidadId(universidad.getId());
        estudiante.setCarreraId(carreraDeMalla.getId());
        estudiante.setMalla(malla);
        if (cambioMalla) {
            estudiante.setSemestreActual((short) 1);
        }
        estudianteRepository.save(estudiante);

        return new SeleccionResponse(
            universidad.getId(),
            universidad.getNombre(),
            carreraDeMalla.getId(),
            carreraDeMalla.getNombre(),
            malla.getId(),
            buildMallaLabel(malla)
        );
    }

    private Estudiante getEstudianteOrThrow(Long userId) {
        return estudianteRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Estudiante no encontrado para el usuario autenticado"));
    }

    private String buildMallaLabel(Malla malla) {
        String nombre = malla.getNombre() == null ? "" : malla.getNombre().trim();
        if (!nombre.isEmpty()) {
            return nombre;
        }
        return "Malla " + malla.getVersion();
    }
}
