package com.easyschedule.backend.academico.selecciontemporal.model;

import com.easyschedule.backend.academico.oferta_materia.model.OfertaMateria;
import com.easyschedule.backend.estudiante.model.Estudiante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;

@Entity
@Table(name = "seleccion_temporal", uniqueConstraints = {
        @UniqueConstraint(name = "uq_estudiante_oferta", columnNames = { "estudiante_id", "oferta_materia_id" })
})
public class SeleccionTemporal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @ManyToOne(optional = false)
    @JoinColumn(name = "oferta_materia_id", nullable = false)
    private OfertaMateria ofertaMateria;

    @Column(name = "fecha_seleccion", nullable = false)
    private OffsetDateTime fechaSeleccion;

    public SeleccionTemporal() {
        // Constructor sin parámetros requerido por JPA para instanciar la entidad.
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Estudiante getEstudiante() {
        return estudiante;
    }

    public void setEstudiante(Estudiante estudiante) {
        this.estudiante = estudiante;
    }

    public OfertaMateria getOfertaMateria() {
        return ofertaMateria;
    }

    public void setOfertaMateria(OfertaMateria ofertaMateria) {
        this.ofertaMateria = ofertaMateria;
    }

    public OffsetDateTime getFechaSeleccion() {
        return fechaSeleccion;
    }

    public void setFechaSeleccion(OffsetDateTime fechaSeleccion) {
        this.fechaSeleccion = fechaSeleccion;
    }
}
