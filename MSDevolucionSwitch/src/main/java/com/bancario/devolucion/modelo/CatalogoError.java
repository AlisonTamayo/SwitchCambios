package com.bancario.devolucion.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "catalogoerror")
@Getter
@Setter
public class CatalogoError {

    @Id
    @Column(name = "codigo", nullable = false, length = 10)
    private String codigo;

    @Column(name = "descripcion", nullable = false, columnDefinition = "text")
    private String descripcion;

    public CatalogoError() {
    }

    public CatalogoError(String codigo) {
        this.codigo = codigo;
    }
}
