package com.bancario.compensacion.modelo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "ciclocompensacion")
@Getter
@Setter
public class CicloCompensacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "numero_ciclo")
    private Integer numeroCiclo;

    @Column(length = 100)
    private String descripcion;

    @Column(length = 20)
    private String estado;

    @Column(name = "fecha_apertura")
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
}
