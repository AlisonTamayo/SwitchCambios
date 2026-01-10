package com.bancario.nucleo.dto.iso;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

@Data
public class BodyISO {
    @NotBlank
    private String instructionId; // CLAVE DE IDEMPOTENCIA
    
    private String endToEndId;    // Referencia Cliente
    
    @Valid
    @NotNull
    private AmountISO amount;
    
    @Valid
    @NotNull
    private ActorISO debtor;   // Quien envía
    
    @Valid
    @NotNull
    private ActorISO creditor; // Quien recibe (Incluye targetBankId)
    
    private String remittanceInformation; // Motivo
}