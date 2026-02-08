package com.bancario.compensacion.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ArchivoDTO {
    private Integer id;
    private String nombre;
    private String xmlContenido;

    private String canalEnvio;
    private String estado;
    private LocalDateTime fechaGeneracion;
}
