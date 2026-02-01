package com.bancario.nucleo.controlador;

import com.bancario.nucleo.dto.StatusReportDTO;
import com.bancario.nucleo.dto.TransaccionResponseDTO;
import com.bancario.nucleo.servicio.CallbackServicio;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador para recibir callbacks de bancos destino.
 * 
 * Este endpoint es llamado por los bancos destino después de procesar
 * una transferencia recibida de su cola RabbitMQ.
 * 
 * Flujo:
 * 1. Banco destino consume mensaje de q.bank.{BIC}.in
 * 2. Banco destino procesa la transferencia
 * 3. Banco destino llama POST /api/v1/transacciones/callback con el resultado
 * 4. Switch actualiza el estado y notifica al banco origen
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/transacciones")
@RequiredArgsConstructor
@Tag(name = "Callback", description = "Endpoints para recibir resultados de bancos destino")
public class CallbackControlador {

    private final CallbackServicio callbackServicio;

    /**
     * Endpoint para recibir el resultado del procesamiento de una transferencia.
     * 
     * Los bancos destino deben llamar a este endpoint después de:
     * 1. Consumir el mensaje de su cola RabbitMQ
     * 2. Procesar la transferencia (validar cuenta, depositar fondos)
     * 
     * @param statusReport Resultado del procesamiento (pacs.002 StatusReport)
     * @return Transacción actualizada con el estado final
     */
    @PostMapping("/callback")
    @Operation(summary = "Recibir callback de banco destino", description = "Endpoint para que los bancos notifiquen el resultado del procesamiento de una transferencia")
    public ResponseEntity<TransaccionResponseDTO> recibirCallback(
            @Valid @RequestBody StatusReportDTO statusReport) {

        log.info("═══════════════════════════════════════════════════════════════════════════");
        log.info("POST /api/v1/transacciones/callback");
        log.info("  Banco que responde: {}", statusReport.getHeader().getRespondingBankId());
        log.info("  InstructionId: {}", statusReport.getBody().getOriginalInstructionId());
        log.info("  Status: {}", statusReport.getBody().getStatus());
        log.info("═══════════════════════════════════════════════════════════════════════════");

        TransaccionResponseDTO response = callbackServicio.procesarCallback(statusReport);

        // Retornar el estado apropiado
        if ("COMPLETED".equals(response.getEstado())) {
            return new ResponseEntity<>(response, HttpStatus.OK);
        } else if ("REJECTED".equals(response.getEstado())) {
            return new ResponseEntity<>(response, HttpStatus.OK);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Endpoint de health check para que los bancos verifiquen conectividad.
     */
    @GetMapping("/callback/health")
    @Operation(summary = "Health check del endpoint de callback")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Callback endpoint is healthy");
    }
}
