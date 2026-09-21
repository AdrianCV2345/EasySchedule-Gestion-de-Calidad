package com.easyschedule.backend.estudiante.model;

import com.easyschedule.backend.auth.models.User;
import com.easyschedule.backend.academico.malla.model.Malla;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "student_profiles")
public class Estudiante {

    @Id
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String username;

    @Column(name = "correo", nullable = false, unique = true, length = 50)
    private String correo;

    @Column(nullable = true, length = 100)
    private String nombre;

    @Column(nullable = true, length = 100)
    private String apellido;

    @Column(name = "carnet_identidad", nullable = true, unique = true, length = 30)
    private String carnetIdentidad;

    @Column(name = "fecha_nacimiento", nullable = true)
    private LocalDate fechaNacimiento;

    @Column(name = "fecha_registro", nullable = false)
    private OffsetDateTime fechaRegistro;

    @Column(name = "semestre_actual", nullable = true)
    private Short semestreActual;

    @Column(name = "universidad_id")
    private Long universidadId;

    @Column(name = "carrera_id")
    private Long carreraId;

    @ManyToOne(optional = true)
    @JoinColumn(name = "malla_id", nullable = true)
    private Malla malla;

    @Column(name = "profile_completed", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean profileCompleted;

    @Column(name = "tour_completed", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean tourCompleted;

    @MapsId
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    public Estudiante() {
        // Constructor sin parámetros requerido por JPA para instanciar la entidad.
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getCarnetIdentidad() {
        return carnetIdentidad;
    }

    public void setCarnetIdentidad(String carnetIdentidad) {
        this.carnetIdentidad = carnetIdentidad;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) {
        this.fechaNacimiento = fechaNacimiento;
    }

    public OffsetDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(OffsetDateTime fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    public Short getSemestreActual() {
        return semestreActual;
    }

    public void setSemestreActual(Short semestreActual) {
        this.semestreActual = semestreActual;
    }

    public Long getUniversidadId() {
        return universidadId;
    }

    public void setUniversidadId(Long universidadId) {
        this.universidadId = universidadId;
    }

    public Long getCarreraId() {
        return carreraId;
    }

    public void setCarreraId(Long carreraId) {
        this.carreraId = carreraId;
    }

    public Malla getMalla() {
        return malla;
    }

    public void setMalla(Malla malla) {
        this.malla = malla;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isProfileCompleted() {
        return profileCompleted;
    }

    public void setProfileCompleted(boolean profileCompleted) {
        this.profileCompleted = profileCompleted;
    }

    public boolean isTourCompleted() {
        return tourCompleted;
    }

    public void setTourCompleted(boolean tourCompleted) {
        this.tourCompleted = tourCompleted;
    }

}
