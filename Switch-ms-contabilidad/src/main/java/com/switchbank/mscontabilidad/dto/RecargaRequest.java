package com.switchbank.mscontabilidad.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RecargaRequest {
    private String bic;
    private BigDecimal monto;
    private UUID idInstruccion;
}
