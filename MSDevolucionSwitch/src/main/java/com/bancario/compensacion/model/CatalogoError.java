package com.bancario.compensacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "catalogoerror")
@lombok.Getter
@lombok.Setter
public class CatalogoError {

    @Id
    @Column(name = "codigo", nullable = false, length = 10)
    private String codigo;

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    private String descripcion;

    public CatalogoError() { }

    public CatalogoError(String codigo) {
        this.codigo = codigo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CatalogoError)) return false;
        CatalogoError that = (CatalogoError) o;
        return Objects.equals(this.codigo, that.codigo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.codigo);
    }

    @Override
    public String toString() {
        return "CatalogoError{" +
                "codigo='" + codigo + '\'' +
                ", descripcion='" + descripcion + '\'' +
                '}';
    }
}