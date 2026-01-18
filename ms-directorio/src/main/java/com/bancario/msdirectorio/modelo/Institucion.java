package com.bancario.msdirectorio.modelo;

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
    private String id;

    @Indexed(unique = true)
    private String codigoBic;

    private String nombre;

    private String urlDestino;
    private String llavePublica;

    @Indexed
    private String estadoOperativo;

    private List<ReglaEnrutamiento> reglasEnrutamiento;

    private InterruptorCircuito interruptorCircuito;

    public enum Estado {
        ONLINE,
        OFFLINE,
        MANT,
        SUSPENDIDO,
        SOLO_RECIBIR
    }
}
