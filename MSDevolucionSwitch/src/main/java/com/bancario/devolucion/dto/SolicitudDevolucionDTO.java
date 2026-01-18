package com.bancario.devolucion.dto;

import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class SolicitudDevolucionDTO {
    private UUID id;
    private UUID idInstruccionOriginal;
    private String codigoMotivo;
    private String descripcionMotivo;
    private String estado;
    private OffsetDateTime fechaSolicitud;
}
