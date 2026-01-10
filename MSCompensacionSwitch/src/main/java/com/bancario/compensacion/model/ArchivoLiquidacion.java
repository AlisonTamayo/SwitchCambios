package com.bancario.compensacion.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "ArchivoLiquidacion")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ArchivoLiquidacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @NotNull(message = "El ciclo de compensación es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idCiclo", nullable = false)
    private CicloCompensacion ciclo;

    @NotBlank(message = "El nombre del archivo es obligatorio")
    @Size(max = 100)
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Size(max = 20)
    @Column(name = "canalEnvio", length = 20)
    private String canalEnvio;

    @Size(max = 20)
    @Column(name = "estado", length = 20)
    private String estado;

    @Column(name = "fechaGeneracion")
    private LocalDateTime fechaGeneracion;

    public ArchivoLiquidacion() {
    }

    public ArchivoLiquidacion(Integer id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ArchivoLiquidacion that = (ArchivoLiquidacion) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ArchivoLiquidacion{" +
                "id=" + id +
                ", idCiclo=" + (ciclo != null ? ciclo.getId() : null) +
                ", nombre='" + nombre + '\'' +
                ", canalEnvio='" + canalEnvio + '\'' +
                ", estado='" + estado + '\'' +
                ", fechaGeneracion=" + fechaGeneracion +
                '}';
    }
}
