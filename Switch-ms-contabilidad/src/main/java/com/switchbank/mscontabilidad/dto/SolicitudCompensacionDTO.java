package com.switchbank.mscontabilidad.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class SolicitudCompensacionDTO {
    private Integer cicloId;
    private List<PosicionBancariaDTO> posiciones;

    @Data
    public static class PosicionBancariaDTO {
        private String bic;
        private BigDecimal totalDebitos; // Lo que el banco envió (y se reservó)
        private BigDecimal totalCreditos; // Lo que el banco recibió
        private BigDecimal posicionNeta; // El resultado final (+/-)
    }
}
