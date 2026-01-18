package com.bancario.compensacion.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class PosicionDTO {
    private Integer id;
    private Integer idCiclo;
    private String codigoBic;
    private BigDecimal saldoInicial;
    private BigDecimal totalDebitos;
    private BigDecimal totalCreditos;
    private BigDecimal neto;
}
