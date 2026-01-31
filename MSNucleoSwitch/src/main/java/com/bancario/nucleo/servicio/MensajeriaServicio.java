package com.bancario.nucleo.servicio;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bancario.nucleo.config.BancoDestino;
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
        String routingKey = validarYNormalizarRoutingKey(bicDestino);
        
        log.info("DIRECT EXCHANGE - Publicando transferencia");
        log.info("  Exchange: {}, RoutingKey: {}, InstructionId: {}", 
                 exchangeTransfers, routingKey, mensaje.getBody().getInstructionId());

        try {
            rabbitTemplate.convertAndSend(exchangeTransfers, routingKey, mensaje);
            log.info("Mensaje publicado exitosamente. Destino: q.bank.{}.in", routingKey);
        } catch (Exception e) {
            log.error("Error publicando mensaje a RabbitMQ: {}", e.getMessage(), e);
            throw new RuntimeException("Error en mensajería: " + e.getMessage(), e);
        }
    }

    private String validarYNormalizarRoutingKey(String bicDestino) {
        if (bicDestino == null || bicDestino.isBlank()) {
            throw new IllegalArgumentException(
                "BE01 - El routing key (creditor.targetBankId) es obligatorio. " +
                "El banco origen debe especificar el banco destino."
            );
        }

        String normalized = bicDestino.toUpperCase()
                .replace("_BANK", "")
                .replace("_BK", "")
                .trim();

        if (!BancoDestino.isValid(normalized)) {
            log.error("Routing key inválido: '{}'. Válidos: {}", 
                      normalized, BancoDestino.getAllRoutingKeys());
            throw new IllegalArgumentException(
                "BE01 - Routing key inválido: '" + normalized + "'. " +
                "Bancos destino válidos: " + BancoDestino.getAllRoutingKeys()
            );
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
