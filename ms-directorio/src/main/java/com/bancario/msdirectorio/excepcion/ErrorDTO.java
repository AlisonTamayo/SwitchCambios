package com.bancario.msdirectorio.excepcion;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ErrorDTO {
    private String codigo;
    private String mensaje;
    private LocalDateTime fecha;
    private String path;
}
