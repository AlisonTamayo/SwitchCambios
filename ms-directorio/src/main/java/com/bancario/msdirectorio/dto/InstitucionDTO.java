package com.bancario.msdirectorio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstitucionDTO {
    private String id;
    private String codigoBic;
    private String nombre;
    private String urlDestino;
    private String llavePublica;
    private String estadoOperativo;
    private List<ReglaDTO> reglasEnrutamiento;
    private CircuitBreakerDTO interruptorCircuito;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReglaDTO {
        private String prefijoBin;
        private String agente;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CircuitBreakerDTO {
        private boolean estaAbierto;
        private int fallosConsecutivos;

        private String ultimoFallo;
    }
}
