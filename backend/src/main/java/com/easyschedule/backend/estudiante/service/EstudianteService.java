package com.easyschedule.backend.estudiante.service;

import com.easyschedule.backend.auth.dto.RegistroRequest;
import com.easyschedule.backend.auth.dto.request.SignupRequest;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.repositories.UserRepository;
import com.easyschedule.backend.auth.service.AuthService;
import com.easyschedule.backend.estudiante.dto.EstudianteResponse;
import com.easyschedule.backend.estudiante.dto.EstudianteUpdateRequest;
import com.easyschedule.backend.estudiante.dto.PerfilUpdateRequest;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import com.easyschedule.backend.academico.malla.model.Malla;
import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import com.easyschedule.backend.shared.exception.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class EstudianteService {

    private static final Logger log = LoggerFactory.getLogger(EstudianteService.class);

    private final EstudianteRepository estudianteRepository;
    private final MallaRepository mallaRepository;
    private final AuthService authService;
    private final UserRepository userRepository;

    public EstudianteService(
        EstudianteRepository estudianteRepository,
        MallaRepository mallaRepository,
        AuthService authService,
        UserRepository userRepository
    ) {
        this.estudianteRepository = estudianteRepository;
        this.mallaRepository = mallaRepository;
        this.authService = authService;
        this.userRepository = userRepository;
    }

    public List<EstudianteResponse> findAll() {
        List<EstudianteResponse> estudiantes = estudianteRepository.findAll().stream().map(this::toResponse).toList();
        log.debug("[ESTUDIANTE_LISTA] consulta general completada | total={}", estudiantes.size());
        return estudiantes;
    }

    public EstudianteResponse findById(Long id) {
        log.debug("[ESTUDIANTE_BUSQUEDA] consulta por id iniciada | id={}", id);
        EstudianteResponse response = toResponse(getEstudianteOrThrow(id));
        log.debug("[ESTUDIANTE_BUSQUEDA] consulta por id finalizada | id={}", id);
        return response;
    }

    public EstudianteResponse update(Long id, EstudianteUpdateRequest request) {
        log.debug("[ESTUDIANTE_UPDATE] inicio actualización administrativa | id={} mallaId={}", id, request.mallaId());
        Estudiante estudiante = getEstudianteOrThrow(id);
        Malla malla = request.mallaId() == null ? null : getMallaOrThrow(request.mallaId());

        estudiante.setNombre(formatProperName(request.nombre()));
        estudiante.setApellido(formatProperName(request.apellido()));
        estudiante.setCarnetIdentidad(normalizeCarnetIdentidad(request.carnetIdentidad()));
        estudiante.setFechaNacimiento(request.fechaNacimiento());
        estudiante.setSemestreActual(request.semestreActual());
        estudiante.setUniversidadId(request.universidadId());
        estudiante.setCarreraId(request.carreraId());
        estudiante.setMalla(malla);

        if (estudiante.getUser() != null) {
            if (estudiante.getUsername() == null || estudiante.getUsername().isBlank()) {
                estudiante.setUsername(estudiante.getUser().getUsername());
            }
            if (estudiante.getCorreo() == null || estudiante.getCorreo().isBlank()) {
                estudiante.setCorreo(estudiante.getUser().getEmail());
            }
        }

        estudiante.setProfileCompleted(isProfileCompleted(estudiante));

        EstudianteResponse response = toResponse(estudianteRepository.save(estudiante));
        log.info("[ESTUDIANTE_UPDATE] estudiante actualizado correctamente | id={}", id);
        return response;
    }

    public void delete(Long id) {
        log.debug("[ESTUDIANTE_DELETE] inicio eliminación | id={}", id);
        Estudiante estudiante = getEstudianteOrThrow(id);
        estudianteRepository.delete(estudiante);
        log.info("[ESTUDIANTE_DELETE] estudiante eliminado correctamente | id={}", id);
    }

    @Transactional
    public EstudianteResponse register(RegistroRequest request) {
        log.debug("[ESTUDIANTE_REGISTRO] inicio creación de perfil estudiante | username={}", request.username());
        SignupRequest signupRequest = new SignupRequest();
        signupRequest.setUsername(request.username());
        signupRequest.setEmail(request.email());
        signupRequest.setPassword(request.password());

        authService.registerUser(signupRequest);

        User user = userRepository.findByUsername(request.username())
            .orElseThrow(() -> new IllegalStateException("No se pudo recuperar el usuario recien registrado"));

        Estudiante estudiante = new Estudiante();
        estudiante.setUsername(user.getUsername());
        estudiante.setCorreo(user.getEmail());
        estudiante.setFechaRegistro(OffsetDateTime.now());
        estudiante.setProfileCompleted(false);
        estudiante.setUser(user);

        Estudiante estudianteSaved = estudianteRepository.save(estudiante);
        log.info("[ESTUDIANTE_REGISTRO] Perfil de estudiante creado para el usuario: {} con ID: {}", user.getUsername(), estudianteSaved.getId());
        return toResponse(estudianteSaved);
    }

    private Estudiante getEstudianteOrThrow(Long id) {
        return estudianteRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Estudiante no encontrado con id: " + id));
    }

    private Malla getMallaOrThrow(Long id) {
        return mallaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Malla no encontrada con id: " + id));
    }

    private boolean isProfileCompleted(Estudiante estudiante) {
        return estudiante.getNombre() != null
            && !estudiante.getNombre().isBlank()
            && estudiante.getApellido() != null
            && !estudiante.getApellido().isBlank()
            && estudiante.getUsername() != null
            && !estudiante.getUsername().isBlank()
            && estudiante.getCorreo() != null
            && !estudiante.getCorreo().isBlank()
            && estudiante.getCarnetIdentidad() != null
            && !estudiante.getCarnetIdentidad().isBlank()
            && estudiante.getFechaNacimiento() != null;
    }

    private EstudianteResponse toResponse(Estudiante estudiante) {
        Long mallaId = estudiante.getMalla() != null ? estudiante.getMalla().getId() : null;
        String username = estudiante.getUsername();
        String email = estudiante.getCorreo();

        if ((username == null || username.isBlank()) && estudiante.getUser() != null) {
            username = estudiante.getUser().getUsername();
        }

        if ((email == null || email.isBlank()) && estudiante.getUser() != null) {
            email = estudiante.getUser().getEmail();
        }

        return new EstudianteResponse(
            estudiante.getId(),
            username,
            estudiante.getNombre(),
            estudiante.getApellido(),
            email,
            estudiante.getCarnetIdentidad(),
            estudiante.getFechaNacimiento(),
            estudiante.getFechaRegistro(),
            estudiante.getSemestreActual(),
            estudiante.getUniversidadId(),
            estudiante.getCarreraId(),
            mallaId,
            estudiante.isProfileCompleted(),
            estudiante.isTourCompleted()
        );
    }
    @Transactional
    public EstudianteResponse completeTour(String username) {
        log.debug("[TOUR] marcando tour como completado | identifier={}", username);
        Estudiante estudiante = getOrCreateByIdentifier(username);
        estudiante.setTourCompleted(true);
        Estudiante saved = estudianteRepository.save(estudiante);
        log.info("[TOUR] tour marcado como completado para el estudiante con ID: {}", saved.getId());
        return toResponse(saved);
    }

    public EstudianteResponse findByUsername(String username) {
        log.debug("[PERFIL] búsqueda por identificador iniciada | identifier={}", username);
        EstudianteResponse response = toResponse(getOrCreateByIdentifier(username));
        log.debug("[PERFIL] búsqueda por identificador finalizada | identifier={}", username);
        return response;
    }

    public boolean canAccessProfile(String identifier, Long userId) {
        boolean canAccess = userRepository.findByUsernameIgnoreCase(identifier)
            .or(() -> userRepository.findByEmailIgnoreCase(identifier))
            .map(User::getId)
            .filter((id) -> id.equals(userId))
            .isPresent();
        log.trace("[PERFIL] validación de acceso | identifier={} userId={} allowed={}", identifier, userId, canAccess);
        return canAccess;
    }

    @Transactional
    public EstudianteResponse updateProfile(String username, PerfilUpdateRequest request) {
        Long estudianteId = null;

        try {
            log.debug("[PERFIL-EDICION] inicio actualización de perfil | identifier={}", username);

            Estudiante estudiante = getOrCreateByIdentifier(username);
            estudianteId = estudiante.getId();

            User user = validarUsuarioAsociado(estudiante);

            PerfilDatosActualizados datos = prepararDatosActualizados(request);

            validarDisponibilidadDatos(estudiante, user, datos);

            List<String> camposModificados = detectarCambios(estudiante, user, datos);

            actualizarPerfil(estudiante, user, datos);

            userRepository.save(user);
            Estudiante estudianteActualizado = estudianteRepository.save(estudiante);

            log.info(
                "[PERFIL-EDICION] Perfil actualizado exitosamente para el estudiante con ID: {}. Campos modificados: {}",
                estudianteActualizado.getId(),
                camposModificados
            );

            return toResponse(estudianteActualizado);

        } catch (ResourceNotFoundException ex) {
            log.warn(
                "[PERFIL-EDICION] Fallo actualización estudiante ID: {}. Causa: {}",
                estudianteId == null ? "N/A" : estudianteId,
                ex.getMessage()
            );
            throw ex;

        } catch (ResponseStatusException ex) {
            log.warn(
                "[PERFIL-EDICION] Fallo actualización estudiante ID: {}. Causa: {}",
                estudianteId == null ? "N/A" : estudianteId,
                ex.getReason()
            );
            throw ex;

        } catch (RuntimeException ex) {
            log.error(
                "[PERFIL-EDICION] Fallo actualización estudiante ID: {}. Causa: {}",
                estudianteId == null ? "N/A" : estudianteId,
                ex.getMessage(),
                ex
            );
            throw ex;
        }
    }

    private User validarUsuarioAsociado(Estudiante estudiante) {
        User user = estudiante.getUser();

        if (user == null) {
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "El estudiante no tiene un usuario asociado"
            );
        }

        return user;
    }

    private PerfilDatosActualizados prepararDatosActualizados(PerfilUpdateRequest request) {
        return new PerfilDatosActualizados(
            request.username().trim(),
            request.email().trim().toLowerCase(Locale.ROOT),
            normalizeCarnetIdentidad(request.carnetIdentidad()),
            formatProperName(request.nombre()),
            formatProperName(request.apellido()),
            request.fechaNacimiento()
        );
    }


    private void validarDisponibilidadDatos(
        Estudiante estudiante,
        User user,
        PerfilDatosActualizados datos
    ) {

        if (!user.getUsername().equalsIgnoreCase(datos.username())
            && (Boolean.TRUE.equals(userRepository.existsByUsernameIgnoreCase(datos.username()))
            || estudianteRepository.existsByUsernameIgnoreCase(datos.username()))) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Error: El nombre de usuario ya está en uso"
            );
        }


        if (!user.getEmail().equalsIgnoreCase(datos.email())
            && (Boolean.TRUE.equals(userRepository.existsByEmailIgnoreCase(datos.email()))
            || estudianteRepository.existsByCorreoIgnoreCase(datos.email()))) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Error: El correo electrónico ya está registrado"
            );
        }


        String carnetActual = estudiante.getCarnetIdentidad() == null
            ? ""
            : estudiante.getCarnetIdentidad();


        if (!carnetActual.equalsIgnoreCase(datos.carnet())
            && estudianteRepository.existsByCarnetIdentidadIgnoreCase(datos.carnet())) {

            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Error: El carnet de identidad ya está en uso"
            );
        }
    }


    private void actualizarPerfil(
        Estudiante estudiante,
        User user,
        PerfilDatosActualizados datos
    ) {

        user.setUsername(datos.username());
        user.setEmail(datos.email());

        estudiante.setUsername(datos.username());
        estudiante.setCorreo(datos.email());
        estudiante.setNombre(datos.nombre());
        estudiante.setApellido(datos.apellido());
        estudiante.setCarnetIdentidad(datos.carnet());
        estudiante.setFechaNacimiento(datos.fechaNacimiento());
        estudiante.setProfileCompleted(isProfileCompleted(estudiante));
    }

    private List<String> detectarCambios(
        Estudiante estudiante,
        User user,
        PerfilDatosActualizados datos
    ) {

        List<String> cambios = new ArrayList<>();

        if (!equalsIgnoreCase(user.getUsername(), datos.username())) {
            cambios.add("username");
        }

        if (!equalsIgnoreCase(user.getEmail(), datos.email())) {
            cambios.add("correo");
        }

        if (!equalsNullable(estudiante.getNombre(), datos.nombre())) {
            cambios.add("nombre");
        }

        if (!equalsNullable(estudiante.getApellido(), datos.apellido())) {
            cambios.add("apellido");
        }

        if (!equalsIgnoreCase(estudiante.getCarnetIdentidad(), datos.carnet())) {
            cambios.add("carnetIdentidad");
        }

        if (!equalsNullable(estudiante.getFechaNacimiento(), datos.fechaNacimiento())) {
            cambios.add("fechaNacimiento");
        }

        return cambios;
    }

    



    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private String formatProperName(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return cleaned;
        }

        String[] parts = cleaned.split(" ");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].toLowerCase(Locale.ROOT);
            String formattedPart = part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1);

            if (i > 0) {
                result.append(' ');
            }
            result.append(formattedPart);
        }

        return result.toString();
    }

    private boolean equalsNullable(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private String normalizeCarnetIdentidad(String value) {
        if (value == null) {
            return null;
        }

        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private Estudiante getOrCreateByIdentifier(String identifier) {
        log.trace("[PERFIL] resolviendo estudiante por identificador | identifier={}", identifier);
        User user = userRepository.findByUsernameIgnoreCase(identifier)
            .or(() -> userRepository.findByEmailIgnoreCase(identifier))
            .orElseThrow(() -> new ResourceNotFoundException(
                "Usuario no encontrado con username o correo: " + identifier
            ));

        Optional<Estudiante> existing = estudianteRepository.findById(user.getId());
        if (existing.isPresent()) {
            Estudiante estudiante = existing.get();
            boolean changed = false;

            if (estudiante.getUser() == null) {
                estudiante.setUser(user);
                changed = true;
            }

            if (estudiante.getUsername() == null || !estudiante.getUsername().equalsIgnoreCase(user.getUsername())) {
                estudiante.setUsername(user.getUsername());
                changed = true;
            }

            if (estudiante.getCorreo() == null || !estudiante.getCorreo().equalsIgnoreCase(user.getEmail())) {
                estudiante.setCorreo(user.getEmail());
                changed = true;
            }

            log.trace("[PERFIL] estudiante existente reutilizado | userId={} changed={}", user.getId(), changed);
            return changed ? estudianteRepository.save(estudiante) : estudiante;
        }

        Estudiante estudiante = new Estudiante();
        estudiante.setUsername(user.getUsername());
        estudiante.setCorreo(user.getEmail());
        estudiante.setFechaRegistro(OffsetDateTime.now());
        estudiante.setProfileCompleted(false);
        estudiante.setUser(user);

        try {
            Estudiante saved = estudianteRepository.save(estudiante);
            log.trace("[PERFIL] nuevo estudiante creado desde identificador | userId={}", user.getId());
            return saved;
        } catch (RuntimeException ex) {
            log.debug("[PERFIL] fallo al crear estudiante, intentando recuperar registro existente | userId={} message={}", user.getId(), ex.getMessage());
            return estudianteRepository.findByUsernameIgnoreCase(user.getUsername())
                .or(() -> estudianteRepository.findByCorreoIgnoreCase(user.getEmail()))
                .orElseThrow(() -> ex);
        }
    }


    private record PerfilDatosActualizados(
        String username,
        String email,
        String carnet,
        String nombre,
        String apellido,
        LocalDate fechaNacimiento
    ) {}

}
