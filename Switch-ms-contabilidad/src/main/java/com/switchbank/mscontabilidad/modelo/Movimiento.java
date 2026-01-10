package com.switchbank.mscontabilidad.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "Movimiento")
@Getter
@Setter
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "idCuenta", nullable = false)
    private CuentaTecnica cuenta;

    @Column(name = "idInstruccion", nullable = false)
    private UUID idInstruccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 10)
    private TipoMovimiento tipo;

    @Column(name = "monto", nullable = false, precision = 18, scale = 2)
    private BigDecimal monto;

    @Column(name = "saldoResultante", nullable = false, precision = 18, scale = 2)
    private BigDecimal saldoResultante;

    @Column(name = "fechaRegistro", nullable = false)
    private LocalDateTime fechaRegistro;

    public Movimiento() {}
}