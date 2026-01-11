package com.bancario.msdirectorio.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class DatosTecnicos {
    private String urlDestino;
    private String llavePublica;
    private String formatoMensajeria;
    private String versionEsquema;
}