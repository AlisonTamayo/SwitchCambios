package com.bancario.nucleo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * DTO para el callback de los bancos destino al Switch.
 * Basado en ISO 20022 pacs.002 (FIToFIPaymentStatusReport)
 * 
 * Este DTO es utilizado por los bancos para notificar el resultado
 * de una transferencia procesada al Switch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusReportDTO {

    /**
     * Header con metadatos del mensaje de respuesta
     */
    @NotNull
    private Header header;

    /**
     * Body con el resultado del procesamiento
     */
    @NotNull
    private Body body;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        /**
         * ID único del mensaje de respuesta (generado por el banco destino)
         */
        private String messageId;

        /**
         * Timestamp de creación de la respuesta (ISO 8601)
         */
        private String creationDateTime;

        /**
         * BIC del banco que está respondiendo
         */
        @NotBlank
        private String respondingBankId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        /**
         * El instructionId de la transacción original.
         * Este es el identificador que permite al Switch relacionar
         * la respuesta con la transacción procesada.
         */
        @NotNull
        private UUID originalInstructionId;

        /**
         * El messageId original (opcional)
         */
        private String originalMessageId;

        /**
         * Estado del procesamiento:
         * - COMPLETED: Transferencia procesada exitosamente
         * - REJECTED: Transferencia rechazada por el banco destino
         */
        @NotBlank
        private String status;

        /**
         * Código de razón ISO 20022 si fue rechazada (opcional).
         * Ejemplos: AC03 (cuenta inválida), AC06 (cuenta bloqueada), AM04 (fondos
         * insuficientes)
         */
        private String reasonCode;

        /**
         * Descripción legible del error si fue rechazada (opcional)
         */
        private String reasonDescription;

        /**
         * Timestamp de cuando se procesó la transacción (ISO 8601)
         */
        private String processedDateTime;
    }
}
