package com.bancario.compensacion.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "archivoliquidacion")
@Getter
@Setter
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "ciclo"})
public class ArchivoLiquidacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_ciclo")
    private CicloCompensacion ciclo;

    private String nombre;
    
    @Column(name = "xml_contenido", columnDefinition = "TEXT")
    private String xmlContenido;

    @Column(name = "firma_jws", columnDefinition = "TEXT")
    private String firmaJws; 

    @Column(name = "canal_envio")
    private String canalEnvio;

    private String estado;

    @Column(name = "fecha_generacion")
    private LocalDateTime fechaGeneracion;
}