package com.bancario.msdirectorio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "InterruptorCircuito")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class InterruptorCircuito {

    @Id
    @Column(name = "codigoBic")
    private String codigoBic;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // La PK es también la FK
    @JoinColumn(name = "codigoBic")
    private Institucion institucion;

    @Column(name = "fallosConsecutivos", nullable = false)
    private Integer fallosConsecutivos;

    @Column(name = "ultimoFallo")
    private LocalDateTime ultimoFallo;

    @Column(name = "estaAbierto", nullable = false)
    private Boolean estaAbierto;

    // CONSTRUCTOR VACÍO (Requerimiento 7)
    public InterruptorCircuito() {
    }

    // CONSTRUCTOR SOLO CLAVE PRIMARIA (Requerimiento 8)
    public InterruptorCircuito(String codigoBic) {
        this.codigoBic = codigoBic;
    }

    // EQUALS & HASHCODE SOLO PK (Requerimiento 11)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InterruptorCircuito that = (InterruptorCircuito) o;
        return Objects.equals(codigoBic, that.codigoBic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigoBic);
    }

    // TOSTRING COMPLETO (Requerimiento 12)
    @Override
    public String toString() {
        return "InterruptorCircuito{" +
                "codigoBic='" + codigoBic + '\'' +
                ", fallosConsecutivos=" + fallosConsecutivos +
                ", ultimoFallo=" + ultimoFallo +
                ", estaAbierto=" + estaAbierto +
                '}';
    }
}