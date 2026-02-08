package com.bancario.compensacion.listener;

import com.bancario.compensacion.dto.RegistroOperacionDTO;
import com.bancario.compensacion.servicio.CompensacionServicio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompensacionListener {

    private final CompensacionServicio compensacionServicio;

    @RabbitListener(queues = "q.switch.compensacion.in")
    public void recibirOperacion(RegistroOperacionDTO dto) {
        log.info("RabbitMQ: Recibida operación de compensación {} - Monto: {}", dto.getIdInstruccion(), dto.getMonto());
        try {
            compensacionServicio.registrarOperacion(dto);
            log.info("RabbitMQ: Operación procesada exitosamente.");
        } catch (Exception e) {
            log.error("RabbitMQ: Error procesando operación: {}", e.getMessage());
            // Throw exception to potentially trigger retry/DLQ if configured
            throw e;
        }
    }
}
