package com.bancario.msdirectorio.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Document(collection = "directorio")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Institucion {

    @Id
    private String _id; // Ejemplo: "PICHECPX"
    
    private String nombre;
    
    private DatosTecnicos datosTecnicos;
    @Indexed // Índice simple para filtrar bancos ONLINE rápido
    private String estadoOperativo;
    private List<ReglaEnrutamiento> reglasEnrutamiento;

    private InterruptorCircuito interruptorCircuito;
}