package com.easyschedule.backend.academico.malla.service;

import com.easyschedule.backend.academico.malla.dto.MallaImportRequest;
import com.easyschedule.backend.academico.malla.dto.MallaImportResponse;
import com.easyschedule.backend.academico.malla.dto.MateriaImportRequest;
import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.model.MallaMateria;
import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import com.easyschedule.backend.academico.materia.model.Materia;
import com.easyschedule.backend.academico.materia.model.Prerequisito;
import com.easyschedule.backend.academico.materia.repository.MateriaRepository;
import com.easyschedule.backend.academico.malla.repository.MallaMateriaRepository;
import com.easyschedule.backend.academico.materia.repository.PrerequisitoRepository;
import com.easyschedule.backend.academico.carrera.repository.CarreraRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MallaImportService {

    private static final Logger logger = LoggerFactory.getLogger(MallaImportService.class);

    private final MallaRepository mallaRepository;
    private final MateriaRepository materiaRepository;
    private final MallaMateriaRepository mallaMateriaRepository;
    private final PrerequisitoRepository prerequisitoRepository;
    private final CarreraRepository carreraRepository;

    public MallaImportService(MallaRepository mallaRepository,
                              MateriaRepository materiaRepository,
                              MallaMateriaRepository mallaMateriaRepository,
                              PrerequisitoRepository prerequisitoRepository,
                              CarreraRepository carreraRepository) {
        this.mallaRepository = mallaRepository;
        this.materiaRepository = materiaRepository;
        this.mallaMateriaRepository = mallaMateriaRepository;
        this.prerequisitoRepository = prerequisitoRepository;
        this.carreraRepository = carreraRepository;
    }

    @Transactional
    public MallaImportResponse importarMalla(MallaImportRequest request) {
        logger.info("Iniciando importación de malla");
        validarRequest(request);
        validarCarrera(request.carreraId());
        String version = obtenerVersion(request);
        validarMallaExistente(
            request.carreraId(),
            version
        );

        Malla malla = crearMalla(request,version);
        Map<String,MallaMateria> materias = crearMateriasDeMalla(request.materias(),malla);
        int prerequisitos = crearPrerequisitos(request.materias(),materias);
        return construirRespuesta(
            malla,
            request.materias().size(),
            prerequisitos
        );
    }

    private void validarRequest(MallaImportRequest request) {
        if (request.nombre() == null || request.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la malla es requerido");
        }

        if (request.carreraId() == null) {
            throw new IllegalArgumentException("El ID de la carrera es requerido");
        }

        if (request.materias() == null || request.materias().isEmpty()) {
            throw new IllegalArgumentException("Debe proporcionar al menos una materia");
        }
    }

    private void validarCarrera(Long carreraId) {
        if (!carreraRepository.existsById(carreraId)) {
            throw new IllegalArgumentException("La carrera con ID " + carreraId + " no existe");
        }
    }

    private String obtenerVersion(MallaImportRequest request) {
        return request.version() != null ? request.version() : "1.0";
    }

    private void validarMallaExistente(Long carreraId, String version) {
        if (mallaRepository.existsByCarreraIdAndVersionAndActiveTrue(carreraId, version)) {
            throw new IllegalArgumentException("Ya existe una malla activa para esta carrera con versión " + version + ". Use una versión diferente.");
        }
    }

    private Malla crearMalla(MallaImportRequest request, String version) {
        Malla malla = new Malla();

        malla.setNombre(request.nombre());
        malla.setVersion(version);
        malla.setCarreraId(request.carreraId());
        malla.setActive(true);

        return mallaRepository.save(malla);
    }

    private Map<String, MallaMateria> crearMateriasDeMalla(List<MateriaImportRequest> materias, Malla malla) {
        Map<String, MallaMateria> materiasMap = new HashMap<>();

        for (MateriaImportRequest matReq : materias) {
            validarMateria(matReq);

            Materia materia = obtenerOCrearMateria(matReq);

            MallaMateria mallaMateria = new MallaMateria();
            mallaMateria.setMalla(malla);
            mallaMateria.setMateria(materia);
            mallaMateria.setSemestreSugerido(matReq.semestre().shortValue());

            mallaMateria = mallaMateriaRepository.save(mallaMateria);

            materiasMap.put(matReq.codigo(), mallaMateria);
        }

        return materiasMap;
    }

    private void validarMateria(MateriaImportRequest matReq) {
        if (matReq.codigo() == null || matReq.codigo().isBlank()) {
            throw new IllegalArgumentException("El código de la materia es requerido");
        }

        if (matReq.nombre() == null || matReq.nombre().isBlank()) {
            throw new IllegalArgumentException("El nombre de la materia es requerido");
        }

        if (matReq.semestre() == null || matReq.semestre() < 1) {
            throw new IllegalArgumentException("El semestre sugerido debe ser mayor a 0");
        }
    }

    private Materia obtenerOCrearMateria(MateriaImportRequest matReq) {
        return materiaRepository.findByCodigo(matReq.codigo())
            .orElseGet(() -> {
                Materia nueva = new Materia();

                nueva.setCodigo(matReq.codigo());
                nueva.setNombre(matReq.nombre());
                nueva.setCreditos(matReq.creditos() != null ? matReq.creditos().shortValue() : 0);
                nueva.setActive(true);

                return materiaRepository.save(nueva);
            });
    }

    private int crearPrerequisitos(List<MateriaImportRequest> materias, Map<String, MallaMateria> materiasMap) {
        int prerequisitosCount = 0;

        for (MateriaImportRequest matReq : materias) {
            if (matReq.prerequisitos() == null || matReq.prerequisitos().isEmpty()) {
                continue;
            }

            MallaMateria mallaMateria = materiasMap.get(matReq.codigo());

            for (String prereqCodigo : matReq.prerequisitos()) {
                MallaMateria prereq = materiasMap.get(prereqCodigo);

                if (prereq == null) {
                    throw new IllegalArgumentException("La materia con codigo '" + prereqCodigo + "' no existe en la malla actual");
                }

                if (prereq.getId().equals(mallaMateria.getId())) {
                    throw new IllegalArgumentException("La materia '" + matReq.codigo() + "' no puede ser prerequisito de si misma");
                }

                crearPrerequisitoSiNoExiste(mallaMateria, prereq);

                prerequisitosCount++;
            }
        }

        return prerequisitosCount;
    }

    private void crearPrerequisitoSiNoExiste(MallaMateria mallaMateria, MallaMateria prereq) {
        boolean exists = prerequisitoRepository.existsByMallaMateria_IdAndPrerequisito_Id(
            mallaMateria.getId(),
            prereq.getId()
        );

        if (!exists) {
            Prerequisito pre = new Prerequisito();

            pre.setMallaMateria(mallaMateria);
            pre.setPrerequisito(prereq);

            prerequisitoRepository.save(pre);
        }
    }

    private MallaImportResponse construirRespuesta(Malla malla, int totalMaterias, int prerequisitos) {
        return new MallaImportResponse(
            malla.getId(),
            malla.getNombre(),
            totalMaterias,
            prerequisitos,
            "Malla importada exitosamente"
        );
    }    

    


}
