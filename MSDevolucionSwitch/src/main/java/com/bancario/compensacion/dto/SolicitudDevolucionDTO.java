package com.bancario.compensacion.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

@lombok.Getter
@lombok.Setter
@lombok.AllArgsConstructor
public class SolicitudDevolucionDTO {
    private UUID id;
    private UUID idInstruccionOriginal;
    private String codigoMotivo;
    private String estado;
    private OffsetDateTime fechaSolicitud;
}
