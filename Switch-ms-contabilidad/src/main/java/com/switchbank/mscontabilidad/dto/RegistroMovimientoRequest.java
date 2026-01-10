package com.switchbank.mscontabilidad.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RegistroMovimientoRequest {
    private String codigoBic;      // A quién afectamos
    private UUID idInstruccion;    // Referencia al Núcleo
    private BigDecimal monto;
    private String tipo;           // "CREDIT" o "DEBIT"
}