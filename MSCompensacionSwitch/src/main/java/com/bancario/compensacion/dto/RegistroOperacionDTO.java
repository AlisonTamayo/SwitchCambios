package com.bancario.compensacion.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class RegistroOperacionDTO {
    private UUID idInstruccion;
    private UUID idInstruccionOriginal; // Opcional, para reversos
    private String bicEmisor;
    private String bicReceptor;
    private BigDecimal monto;
    private String tipoOperacion; // PAGO, REVERSO
    private String codigoReferencia;
}
