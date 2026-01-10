package com.bancario.compensacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "solicituddevolucion")
@lombok.Getter
@lombok.Setter
public class SolicitudDevolucion {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "idinstruccionoriginal", nullable = false)
    private UUID idInstruccionOriginal;

    @Column(name = "codigomotivo", nullable = false, length = 10)
    private String codigoMotivo;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado;

    @Column(name = "fechasolicitud", nullable = false)
    private OffsetDateTime fechaSolicitud;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "codigomotivo", referencedColumnName = "codigo", insertable = false, updatable = false)
    private CatalogoError motivo;

    public SolicitudDevolucion() { }

    public SolicitudDevolucion(UUID id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SolicitudDevolucion)) return false;
        SolicitudDevolucion that = (SolicitudDevolucion) o;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public String toString() {
        return "SolicitudDevolucion{" +
                "id=" + id +
                ", idInstruccionOriginal=" + idInstruccionOriginal +
                ", codigoMotivo='" + codigoMotivo + '\'' +
                ", estado='" + estado + '\'' +
                ", fechaSolicitud=" + fechaSolicitud +
                '}';
    }
}