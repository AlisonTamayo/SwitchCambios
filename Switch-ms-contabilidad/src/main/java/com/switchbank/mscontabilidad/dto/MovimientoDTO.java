package com.switchbank.mscontabilidad.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MovimientoDTO {
    private Long id;
    private UUID idInstruccion;
    private String tipo;
    private BigDecimal monto;
    private BigDecimal saldoResultante;
    private LocalDateTime fechaRegistro;
    private UUID referenciaId;
    private String codigoBicCuenta; // Solo el BIC, no toda la cuenta
}
