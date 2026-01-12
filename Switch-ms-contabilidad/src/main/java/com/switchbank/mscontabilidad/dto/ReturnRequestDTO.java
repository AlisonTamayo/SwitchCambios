package com.switchbank.mscontabilidad.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReturnRequestDTO {
    private Header header;
    private Body body;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        private String messageId;
        private String creationDateTime;
        private String originatingBankId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        private String returnInstructionId; // ID del reverso provisto por el banco (pacs.004)
        private String originalInstructionId; // ID de la transacción original a revertir
        private String returnReason; // Motivo (ej. MS03)
        private Amount returnAmount; // Monto a devolver
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Amount {
        private String currency;
        private BigDecimal value;
    }
}
