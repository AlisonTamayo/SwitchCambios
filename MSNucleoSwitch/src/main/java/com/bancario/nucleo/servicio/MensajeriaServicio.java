package com.bancario.nucleo.servicio;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bancario.nucleo.dto.iso.MensajeISO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MensajeriaServicio {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange.transfers:ex.transfers.tx}")
    private String exchangeTransfers;

    public void publicarTransferencia(String bicDestino, MensajeISO mensaje) {
        String routingKey = normalizarRoutingKey(bicDestino);
        
        log.info("Publicando transferencia a RabbitMQ: Exchange={}, RoutingKey={}, InstructionId={}",
                exchangeTransfers, routingKey, mensaje.getBody().getInstructionId());

        try {
            rabbitTemplate.convertAndSend(exchangeTransfers, routingKey, mensaje);
            log.info("Mensaje publicado exitosamente al banco: {}", routingKey);
        } catch (Exception e) {
            log.error("Error publicando mensaje a RabbitMQ: {}", e.getMessage(), e);
            throw new RuntimeException("Error en mensajería: " + e.getMessage(), e);
        }
    }

    private String normalizarRoutingKey(String bicDestino) {
        if (bicDestino == null || bicDestino.isBlank()) {
            throw new IllegalArgumentException("BIC destino no puede ser nulo o vacío");
        }

        String normalized = bicDestino.toUpperCase()
                .replace("_BANK", "")
                .replace("_BK", "")
                .trim();

        if (!normalized.matches("NEXUS|BANTEC|ARCBANK|ECUSOL")) {
            log.warn("BIC destino desconocido: {} -> normalizado a: {}", bicDestino, normalized);
        }

        return normalized;
    }

    public boolean isDisponible() {
        try {
            rabbitTemplate.getConnectionFactory().createConnection().isOpen();
            return true;
        } catch (Exception e) {
            log.error("RabbitMQ no disponible: {}", e.getMessage());
            return false;
        }
    }
}
