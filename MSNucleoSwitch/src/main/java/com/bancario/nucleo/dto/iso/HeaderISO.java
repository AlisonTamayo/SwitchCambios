package com.bancario.nucleo.dto.iso;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class HeaderISO {
    @NotBlank
    private String messageId;        // Trazabilidad técnica
    private String creationDateTime; // Fecha ISO
    @NotBlank
    private String originatingBankId; // BIC del banco que envía
}