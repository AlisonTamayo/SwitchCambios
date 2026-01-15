package com.bancario.compensacion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para la posición neta de una institución")
public class PosicionDTO {

    @Schema(description = "ID de la posición", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer id;

    @NotNull(message = "El ID del ciclo es obligatorio")
    @Schema(description = "ID del ciclo asociado", example = "1")
    private Integer idCiclo;

    @NotBlank(message = "El código BIC es obligatorio")
    @Schema(description = "Código BIC de la institución", example = "BCOECUXXX")
    private String codigoBic;

    @Schema(description = "Total de débitos", example = "1500.50")
    private BigDecimal totalDebitos;

    @Schema(description = "Total de créditos", example = "2000.00")
    private BigDecimal totalCreditos;

    @Schema(description = "Monto neto", example = "499.50")
    private BigDecimal neto;
}
