package com.bancario.nucleo.service;

import com.bancario.nucleo.dto.TransaccionResponseDTO;
import com.bancario.nucleo.dto.external.InstitucionDTO;
import com.bancario.nucleo.dto.external.RegistroMovimientoRequest;
import com.bancario.nucleo.dto.iso.MensajeISO;
import com.bancario.nucleo.exception.BusinessException;
import com.bancario.nucleo.model.RespaldoIdempotencia;
import com.bancario.nucleo.model.Transaccion;
import com.bancario.nucleo.repository.RespaldoIdempotenciaRepository;
import com.bancario.nucleo.repository.TransaccionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransaccionService {

    private final TransaccionRepository transaccionRepository;
    private final RespaldoIdempotenciaRepository idempotenciaRepository;
    private final RestTemplate restTemplate;

    // --- URLs DE LOS MICROSERVICIOS ---
    @Value("${service.directorio.url:http://ms-directorio:8081}")
    private String directorioUrl;

    @Value("${service.contabilidad.url:http://ms-contabilidad:8083}")
    private String contabilidadUrl;

    @Value("${service.compensacion.url:http://ms-compensacion:8084}")
    private String compensacionUrl;

    // Para este prototipo, asumimos que siempre estamos en el Ciclo 1 (Ciclo Diario Abierto)
    private static final Integer CICLO_ACTUAL_ID = 1;

    /**
     * Procesa una transacción bajo el estándar ISO 20022.
     * Flujo: Validación -> Contabilidad (Ledger) -> Compensación (Clearing) -> Notificación (Webhook).
     */
    @Transactional
    public TransaccionResponseDTO procesarTransaccionIso(MensajeISO iso) {
        // 1. EXTRACCIÓN DE DATOS ISO
        UUID idInstruccion = UUID.fromString(iso.getBody().getInstructionId());
        String bicOrigen = iso.getHeader().getOriginatingBankId();
        String bicDestino = iso.getBody().getCreditor().getTargetBankId();
        BigDecimal monto = iso.getBody().getAmount().getValue();
        String moneda = iso.getBody().getAmount().getCurrency();
        String messageId = iso.getHeader().getMessageId();

        log.info(">>> Iniciando Tx ISO: InstID={} MsgID={} Monto={}", idInstruccion, messageId, monto);

        // 2. IDEMPOTENCIA
        if (transaccionRepository.existsById(idInstruccion)) {
            log.warn("Transacción duplicada detectada: {}", idInstruccion);
            return obtenerTransaccion(idInstruccion);
        }

        // 3. PERSISTENCIA INICIAL (Estado RECEIVED)
        Transaccion tx = new Transaccion();
        tx.setIdInstruccion(idInstruccion);
        tx.setIdMensaje(messageId);
        tx.setReferenciaRed("SWITCH-" + System.currentTimeMillis());
        tx.setMonto(monto);
        tx.setMoneda(moneda);
        tx.setCodigoBicOrigen(bicOrigen);
        tx.setCodigoBicDestino(bicDestino);
        tx.setEstado("RECEIVED");
        tx.setFechaCreacion(LocalDateTime.now());
        
        tx = transaccionRepository.save(tx);

        try {
            // 4. VALIDACIÓN CON DIRECTORIO
            validarBanco(bicOrigen); // Validar emisor
            InstitucionDTO bancoDestinoInfo = validarBanco(bicDestino); // Validar receptor y obtener URL

            // 5. LEDGER: MOVIMIENTO DE DINERO REAL (Cuentas Técnicas)
            // 5.1 Debitar al Origen
            log.info("Ledger: Debitando {} a {}", monto, bicOrigen);
            registrarMovimientoContable(bicOrigen, idInstruccion, monto, "DEBIT");

            // 5.2 Acreditar al Destino
            log.info("Ledger: Acreditando {} a {}", monto, bicDestino);
            registrarMovimientoContable(bicDestino, idInstruccion, monto, "CREDIT");

            // 6. COMPENSACIÓN: ACUMULACIÓN DE SALDOS (Clearing)
            // Esto actualiza la tabla de posiciones netas para el cierre del día
            log.info("Clearing: Registrando posiciones en Ciclo {}", CICLO_ACTUAL_ID);
            notificarCompensacion(bicOrigen, monto, true);  // Origen DEBE (Débito)
            notificarCompensacion(bicDestino, monto, false); // Destino TIENE A FAVOR (Crédito)

            // 7. NOTIFICACIÓN AL BANCO DESTINO (PUSH / Webhook)
            String urlWebhook = bancoDestinoInfo.getUrlDestino();
            log.info("Webhook: Notificando a {}", urlWebhook);
            try {
                restTemplate.postForEntity(urlWebhook, iso, String.class);
                log.info("Webhook: Entregado exitosamente.");
            } catch (Exception e) {
                // Si falla el webhook, no revertimos el dinero (ya está en Ledger), pero alertamos.
                log.error("Webhook: Falló la entrega. El banco destino deberá conciliar manualmente. Error: {}", e.getMessage());
            }

            // 8. FINALIZACIÓN EXITOSA
            tx.setEstado("COMPLETED");
            guardarRespaldoIdempotencia(tx, "EXITO");

        } catch (Exception e) {
            log.error("Error crítico en Tx: {}", e.getMessage());
            tx.setEstado("FAILED");
            // Aquí, si falló en el paso 5.2, el sistema debería tener una reversa automática.
            // Para el alcance actual, el estado FAILED indica intervención manual requerida si hubo débito parcial.
        }

        Transaccion saved = transaccionRepository.save(tx);
        return mapToDTO(saved);
    }

    public TransaccionResponseDTO obtenerTransaccion(UUID id) {
        Transaccion tx = transaccionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Transacción no encontrada"));
        return mapToDTO(tx);
    }

    // --- MÉTODOS PRIVADOS DE INTEGRACIÓN ---

    private InstitucionDTO validarBanco(String bic) {
        try {
            String url = directorioUrl + "/api/v1/instituciones/" + bic;
            return restTemplate.getForObject(url, InstitucionDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException("El banco " + bic + " no existe o no está operativo.");
        } catch (Exception e) {
            throw new BusinessException("Error de comunicación con Directorio (" + bic + "): " + e.getMessage());
        }
    }

    private void registrarMovimientoContable(String bic, UUID idTx, BigDecimal monto, String tipo) {
        try {
            RegistroMovimientoRequest req = RegistroMovimientoRequest.builder()
                    .codigoBic(bic)
                    .idInstruccion(idTx)
                    .monto(monto)
                    .tipo(tipo)
                    .build();

            String url = contabilidadUrl + "/api/v1/ledger/movimientos";
            restTemplate.postForEntity(url, req, Object.class);

        } catch (HttpClientErrorException.BadRequest e) {
            throw new BusinessException("Error contable para " + bic + ": Fondos insuficientes o integridad violada.");
        } catch (Exception e) {
            throw new BusinessException("Error crítico con Contabilidad: " + e.getMessage());
        }
    }

    /**
     * Llama al microservicio de Compensación para acumular los montos en el ciclo diario.
     */
    private void notificarCompensacion(String bic, BigDecimal monto, boolean esDebito) {
        try {
            // URL: POST /api/v1/compensacion/ciclos/{id}/acumular?bic=...&monto=...&esDebito=...
            String url = String.format("%s/api/v1/compensacion/ciclos/%d/acumular?bic=%s&monto=%s&esDebito=%s",
                    compensacionUrl, CICLO_ACTUAL_ID, bic, monto.toString(), esDebito);
            
            restTemplate.postForEntity(url, null, Void.class);
            
        } catch (Exception e) {
            // Un fallo en compensación es grave administrativamente, pero no debe detener la transacción en tiempo real.
            log.error("ALERTA: Fallo al registrar compensación para {}. Descuadre en Clearing.", bic, e);
        }
    }

    private void guardarRespaldoIdempotencia(Transaccion tx, String resultado) {
        RespaldoIdempotencia respaldo = new RespaldoIdempotencia();
        // Usamos constructor vacío para que @MapsId funcione correctamente
        respaldo.setHashContenido("HASH_" + tx.getIdInstruccion()); 
        respaldo.setCuerpoRespuesta("{ \"estado\": \"" + resultado + "\" }");
        respaldo.setFechaExpiracion(LocalDateTime.now().plusDays(1));
        respaldo.setTransaccion(tx);
        idempotenciaRepository.save(respaldo);
    }

    // --- MAPPERS ---

    private TransaccionResponseDTO mapToDTO(Transaccion tx) {
        return TransaccionResponseDTO.builder()
                .idInstruccion(tx.getIdInstruccion())
                .idMensaje(tx.getIdMensaje())
                .referenciaRed(tx.getReferenciaRed())
                .monto(tx.getMonto())
                .moneda(tx.getMoneda())
                .codigoBicOrigen(tx.getCodigoBicOrigen())
                .codigoBicDestino(tx.getCodigoBicDestino())
                .estado(tx.getEstado())
                .fechaCreacion(tx.getFechaCreacion())
                .build();
    }
}