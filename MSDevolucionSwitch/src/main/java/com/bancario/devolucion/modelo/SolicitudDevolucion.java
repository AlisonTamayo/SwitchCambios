package com.bancario.devolucion.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "solicituddevolucion")
@Getter
@Setter
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

    // Código de referencia bancario de 6 dígitos para buscar transacción original
    @Column(name = "codigoReferencia", length = 6)
    private String codigoReferencia;
}
