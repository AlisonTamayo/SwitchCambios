package com.bancario.compensacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@lombok.Getter
@lombok.Setter
public class CreateSolicitudDevolucionRequest {

    @NotNull
    private UUID id; 

    @NotNull
    private UUID idInstruccionOriginal;

    @NotBlank
    @Size(max = 10)
    private String codigoMotivo;

    @NotBlank
    @Pattern(regexp = "RECEIVED|REVERSED|FAILED")
    private String estado;
}
