package com.bancario.nucleo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transacciones")
public class TransaccionDocument {

    @Id
    private UUID id; // instructionId as Mongo _id

    private String hashIdempotencia;

    private Header header;

    private Datos datos;

    private String estado; // RECEIVED, ROUTED, COMPLETED, FAILED

    private Idempotencia idempotencia;

    @Builder.Default
    private List<Auditoria> auditoria = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        private String messageId;
        private String bicOrigen;
        private String bicDestino;
        private Instant fechaCreacion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Datos {
        private BigDecimal monto;
        private String moneda;
        private String referenciaRed;
        private String endToEndId;
        private String remittanceInformation;
        private String debtorAccount;
        private String creditorAccount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Idempotencia {
        private Object cuerpoRespuesta;

        @Indexed(name = "ttl_idempotencia", expireAfterSeconds = 86400)
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Auditoria {
        private String estado;
        private Instant timestamp;
        private String detalle;
    }
}
