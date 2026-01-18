package com.bancario.devolucion.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CatalogoErrorDTO {
    private String codigo;
    private String descripcion;
}
