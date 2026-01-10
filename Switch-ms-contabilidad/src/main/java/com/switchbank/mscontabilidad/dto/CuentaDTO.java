package com.switchbank.mscontabilidad.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class CuentaDTO {
    private UUID id;
    private String codigoBic;
    private BigDecimal saldoDisponible;
    private String firmaIntegridad;
}