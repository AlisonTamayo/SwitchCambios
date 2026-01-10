package com.bancario.compensacion.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "CicloCompensacion")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CicloCompensacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @NotNull(message = "La fecha de corte es obligatoria")
    @Column(name = "fechaCorte", nullable = false)
    private LocalDate fechaCorte;

    @NotBlank(message = "El estado es obligatorio")
    @Size(max = 20)
    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Size(max = 100)
    @Column(name = "descripcion", length = 100)
    private String descripcion;

    public CicloCompensacion() {
    }

    public CicloCompensacion(Integer id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CicloCompensacion that = (CicloCompensacion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CicloCompensacion{" +
                "id=" + id +
                ", fechaCorte=" + fechaCorte +
                ", estado='" + estado + '\'' +
                ", descripcion='" + descripcion + '\'' +
                '}';
    }
}
