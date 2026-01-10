package com.switchbank.mscontabilidad.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "CuentaTecnica")
@Getter
@Setter
public class CuentaTecnica {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @Column(name = "codigoBic", nullable = false, unique = true, length = 20)
    private String codigoBic;

    @Column(name = "saldoDisponible", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoDisponible;

    @Column(name = "firmaIntegridad", nullable = false)
    private String firmaIntegridad;

    public CuentaTecnica() {}

    // Constructor para inicializar
    public CuentaTecnica(String codigoBic) {
        this.codigoBic = codigoBic;
        // CORRECCIÓN: Nacer con 2 decimales
        this.saldoDisponible = BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        this.firmaIntegridad = "INITIAL_HASH"; 
    }
}