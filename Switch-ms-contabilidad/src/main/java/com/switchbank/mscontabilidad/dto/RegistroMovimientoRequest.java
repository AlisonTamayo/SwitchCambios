package com.switchbank.mscontabilidad.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RegistroMovimientoRequest {
    private String codigoBic;
    private UUID idInstruccion;
    private BigDecimal monto;
    private String tipo;
}