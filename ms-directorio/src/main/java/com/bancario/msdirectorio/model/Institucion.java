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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Institucion {

    @Id
    private String _id;

    private String nombre;

    private DatosTecnicos datosTecnicos;

    @Indexed
    private String estadoOperativo;

    private List<ReglaEnrutamiento> reglasEnrutamiento;

    private InterruptorCircuito interruptorCircuito;

    public enum Estado {
        ONLINE,
        OFFLINE,
        MANT 
    }
}