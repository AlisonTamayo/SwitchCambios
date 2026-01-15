package com.bancario.compensacion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para la gestión de ciclos de compensación")
public class CicloDTO {

    @Schema(description = "ID del ciclo", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer id;

    @NotNull(message = "La fecha de corte no puede ser nula")
    @Schema(description = "Fecha de corte del ciclo", example = "2023-12-31")
    private LocalDate fechaCorte;

    @NotBlank(message = "El estado no puede estar vacío")
    @Schema(description = "Estado del ciclo (ABIERTO, CERRADO, ENVIADO)", example = "ABIERTO")
    private String estado;

    @Schema(description = "Descripción opcional del ciclo", example = "Corte fin de mes")
    private String descripcion;
}
