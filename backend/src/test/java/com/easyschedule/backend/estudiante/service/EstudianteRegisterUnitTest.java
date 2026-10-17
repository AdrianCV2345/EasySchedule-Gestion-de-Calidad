package com.easyschedule.backend.estudiante.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.easyschedule.backend.academico.malla.repository.MallaRepository;
import com.easyschedule.backend.auth.dto.RegistroRequest;
import com.easyschedule.backend.auth.dto.request.SignupRequest;
import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.auth.repositories.UserRepository;
import com.easyschedule.backend.auth.service.AuthService;
import com.easyschedule.backend.estudiante.dto.EstudianteResponse;
import com.easyschedule.backend.estudiante.model.Estudiante;
import com.easyschedule.backend.estudiante.repository.EstudianteRepository;
import com.easyschedule.backend.shared.exception.UserAlreadyExistsException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EstudianteRegisterUnitTest {

    @Mock
    private EstudianteRepository estudianteRepository;

    @Mock
    private MallaRepository mallaRepository;

    @Mock
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EstudianteService estudianteService;

    private RegistroRequest request;

    @BeforeEach
    void setUp() {
        request = new RegistroRequest("juan", "Password123!", "juan@mail.com");
    }

    @Test
    void givenValidRequest_whenRegister_thenCreatesIncompleteStudentProfile() {
        User user = user(7L, "juan", "juan@mail.com");
        Estudiante savedStudent = new Estudiante();
        savedStudent.setId(7L);
        savedStudent.setUsername("juan");
        savedStudent.setCorreo("juan@mail.com");
        savedStudent.setUser(user);

        when(userRepository.findByUsername("juan")).thenReturn(Optional.of(user));
        when(estudianteRepository.save(any(Estudiante.class))).thenReturn(savedStudent);

        EstudianteResponse response = estudianteService.register(request);

        ArgumentCaptor<SignupRequest> authRequest = ArgumentCaptor.forClass(SignupRequest.class);
        verify(authService).registerUser(authRequest.capture());
        assertEquals("juan", authRequest.getValue().getUsername());
        assertEquals("juan@mail.com", authRequest.getValue().getEmail());
        assertEquals("Password123!", authRequest.getValue().getPassword());
        verify(userRepository).findByUsername("juan");

        ArgumentCaptor<Estudiante> studentCaptor = ArgumentCaptor.forClass(Estudiante.class);
        verify(estudianteRepository).save(studentCaptor.capture());
        Estudiante student = studentCaptor.getValue();

        assertEquals("juan", student.getUsername());
        assertEquals("juan@mail.com", student.getCorreo());
        assertEquals(user, student.getUser());
        assertFalse(student.isProfileCompleted());
        assertNotNull(student.getFechaRegistro());
        assertEquals(7L, response.id());
        assertFalse(response.profileCompleted());
    }

    @Test
    void givenDuplicateUser_whenRegister_thenPropagatesExceptionWithoutSaving() {
        UserAlreadyExistsException exception = new UserAlreadyExistsException("Usuario duplicado");
        org.mockito.Mockito.doThrow(exception).when(authService).registerUser(any());

        UserAlreadyExistsException thrown = assertThrows(
            UserAlreadyExistsException.class,
            () -> estudianteService.register(request)
        );

        assertEquals(exception, thrown);
        verify(userRepository, never()).findByUsername(any());
        verify(estudianteRepository, never()).save(any());
    }

    @Test
    void givenRegisteredUserCannotBeRecovered_whenRegister_thenThrowsIllegalStateException() {
        when(userRepository.findByUsername("juan")).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> estudianteService.register(request)
        );

        assertEquals("No se pudo recuperar el usuario recien registrado", exception.getMessage());
        verify(authService).registerUser(any());
        verify(estudianteRepository, never()).save(any());
    }

    private User user(Long id, String username, String email) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        return user;
    }
}
