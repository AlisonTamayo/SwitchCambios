package com.bancario.devolucion.dto;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class SolicitudCreacionDTO {
    private UUID id;
    private UUID idInstruccionOriginal;
    private String codigoMotivo;
    private String estado;
}
