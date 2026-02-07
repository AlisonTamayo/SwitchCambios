package com.bancario.compensacion.modelo;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "detalleCompensacion")
@Data
public class DetalleCompensacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDetalle;

    @Column(name = "idInstruccion")
    private UUID idInstruccion;

    @Column(name = "idInstruccionOriginal")
    private UUID idInstruccionOriginal;

    @ManyToOne
    @JoinColumn(name = "idCiclo")
    private CicloCompensacion ciclo;

    @Column(name = "tipoOperacion", length = 10)
    private String tipoOperacion; // PAGO, REVERSO

    @Column(name = "bicEmisor", length = 20)
    private String bicEmisor;

    @Column(name = "bicReceptor", length = 20)
    private String bicReceptor;

    @Column(name = "monto", precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "estadoLiquidacion", length = 20)
    private String estadoLiquidacion; // INCLUIDO, EXCLUIDO

    // Código de referencia bancario de 6 dígitos para devoluciones
    @Column(name = "codigoReferencia", length = 6)
    private String codigoReferencia;
}
