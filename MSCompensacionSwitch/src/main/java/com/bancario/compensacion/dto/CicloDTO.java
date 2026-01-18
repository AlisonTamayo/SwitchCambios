package com.bancario.compensacion.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class CicloDTO {
    private Integer id;
    private Integer numeroCiclo;
    private String descripcion;
    private String estado;
    private LocalDateTime fechaApertura;
    private LocalDateTime fechaCierre;
}
