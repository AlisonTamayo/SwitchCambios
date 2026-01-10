package com.bancario.msdirectorio.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "Institucion")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) 
public class Institucion {

    @Id
    @Column(name = "codigoBic", length = 11, nullable = false)
    private String codigoBic;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "urlDestino", nullable = false)
    private String urlDestino;

    @Column(name = "llavePublica", columnDefinition = "TEXT", nullable = false)
    private String llavePublica;

    @Enumerated(EnumType.STRING)
    @Column(name = "estadoOperativo", nullable = false)
    private EstadoOperativo estadoOperativo;

    // CONSTRUCTOR VACÍO (Requerimiento 7)
    public Institucion() {
    }

    // CONSTRUCTOR SOLO CLAVE PRIMARIA (Requerimiento 8)
    public Institucion(String codigoBic) {
        this.codigoBic = codigoBic;
    }

    // EQUALS & HASHCODE SOLO PK (Requerimiento 11)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Institucion that = (Institucion) o;
        return Objects.equals(codigoBic, that.codigoBic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codigoBic);
    }

    // TOSTRING COMPLETO (Requerimiento 12)
    @Override
    public String toString() {
        return "Institucion{" +
                "codigoBic='" + codigoBic + '\'' +
                ", nombre='" + nombre + '\'' +
                ", urlDestino='" + urlDestino + '\'' +
                ", llavePublica='" + llavePublica + '\'' +
                ", estadoOperativo=" + estadoOperativo +
                '}';
    }
}

// Enum auxiliar para el estado
enum EstadoOperativo {
    ONLINE, OFFLINE
}