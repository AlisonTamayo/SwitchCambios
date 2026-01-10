package com.bancario.nucleo.dto.external;

import lombok.Data;

@Data
public class InstitucionDTO {
    private String codigoBic;
    private String nombre;
    private String urlDestino;
    private String estadoOperativo;
}