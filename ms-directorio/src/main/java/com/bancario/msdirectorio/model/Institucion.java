package com.bancario.msdirectorio.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.*;
import java.util.List;
import java.util.ArrayList;

@Document(collection = "directorio") 
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Institucion {

    @Id
    private String codigoBic; 
    private String nombre;
    private String urlDestino;
    private String llavePublica;
    private EstadoOperativo estadoOperativo;

    private InterruptorCircuito interruptorCircuito; 
    private List<ReglaEnrutamiento> reglasEnrutamiento = new ArrayList<>();
}

enum EstadoOperativo {
    ONLINE, OFFLINE
}