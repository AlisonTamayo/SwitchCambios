package com.bancario.compensacion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO para los archivos de liquidación")
public class ArchivoDTO {

    @Schema(description = "ID del archivo", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Integer id;

    @NotNull(message = "El ID del ciclo es obligatorio")
    @Schema(description = "ID del ciclo asociado", example = "1")
    private Integer idCiclo;

    @NotBlank(message = "El nombre del archivo es obligatorio")
    @Schema(description = "Nombre del archivo generado", example = "LIQ_20231231.xml")
    private String nombre;

    @Schema(description = "Canal de envío", example = "SFTP")
    private String canalEnvio;

    @Schema(description = "Estado del envío", example = "ENVIADO")
    private String estado;

    @Schema(description = "Fecha de generación del archivo", example = "2023-12-31T23:59:59")
    private LocalDateTime fechaGeneracion;
}
