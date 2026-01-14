package com.bancario.nucleo.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.bancario.nucleo.dto.TransaccionResponseDTO;
import com.bancario.nucleo.dto.ReturnRequestDTO;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class TransaccionService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    private final TransaccionRepository transaccionRepository;
    private final RespaldoIdempotenciaRepository idempotenciaRepository;
    private final RestTemplate restTemplate;

    @Value("${service.directorio.url:http://ms-directorio:8081}")
    private String directorioUrl;

    @Value("${service.contabilidad.url:http://ms-contabilidad:8083}")
    private String contabilidadUrl;

    @Value("${service.compensacion.url:http://ms-compensacion:8084}")
    private String compensacionUrl;

    private static final Integer CICLO_ACTUAL_ID = 1;

    @Transactional
    public TransaccionResponseDTO procesarTransaccionIso(MensajeISO iso) {
        UUID idInstruccion = UUID.fromString(iso.getBody().getInstructionId());
        String bicOrigen = iso.getHeader().getOriginatingBankId();
        String bicDestino = iso.getBody().getCreditor().getTargetBankId();
        BigDecimal monto = iso.getBody().getAmount().getValue();
        String moneda = iso.getBody().getAmount().getCurrency();
        String messageId = iso.getHeader().getMessageId();
        String creationDateTime = iso.getHeader().getCreationDateTime();
        String cuentaOrigen = iso.getBody().getDebtor().getAccountId();
        String cuentaDestino = iso.getBody().getCreditor().getAccountId();

        String fingerprint = idInstruccion.toString() + monto.toString() + moneda + bicOrigen + bicDestino
                + creationDateTime + cuentaOrigen + cuentaDestino;
        String fingerprintMd5 = generarMD5(fingerprint);

        log.info(">>> Iniciando Tx ISO: InstID={} MsgID={} Monto={}", idInstruccion, messageId, monto);

        String redisKey = "idem:" + idInstruccion;
        String redisValue = fingerprintMd5 + "|PROCESSING|-";

        Boolean claimed = redisTemplate.opsForValue().setIfAbsent(redisKey, redisValue, java.time.Duration.ofHours(24));

        if (Boolean.TRUE.equals(claimed)) {
            log.info("Redis CLAIM OK — Nueva transacción {}", idInstruccion);
        } else {
            String existing = redisTemplate.opsForValue().get(redisKey);

            if (existing == null) {
                throw new IllegalStateException("Redis inconsistente");
            }

            String[] parts = existing.split("\\|");
            String storedMd5 = parts[0];
            String storedStatus = parts[1];

            if (!storedMd5.equals(fingerprintMd5)) {
                log.error("VIOLACIÓN DE INTEGRIDAD ISO 20022 — InstructionId={} alterado", idInstruccion);
                throw new SecurityException(
                        "Same InstructionId, different content fingerprint");
            }

            log.warn("Duplicado legítimo ISO 20022 — Replay {}", idInstruccion);

            return obtenerTransaccion(idInstruccion);
        }

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
            validarBanco(bicOrigen);
            InstitucionDTO bancoDestinoInfo = validarBanco(bicDestino);

            log.info("Ledger: Debitando {} a {}", monto, bicOrigen);
            registrarMovimientoContable(bicOrigen, idInstruccion, monto, "DEBIT");

            log.info("Ledger: Acreditando {} a {}", monto, bicDestino);
            registrarMovimientoContable(bicDestino, idInstruccion, monto, "CREDIT");

            log.info("Clearing: Registrando posiciones en Ciclo {}", CICLO_ACTUAL_ID);
            notificarCompensacion(bicOrigen, monto, true);
            notificarCompensacion(bicDestino, monto, false);

            // 6. Forwarding con Política de Reintentos Determinista (RF-01)
            // Intentos: Inmediato, 800ms, 2s, 4s
            int[] tiemposEspera = { 0, 800, 2000, 4000 };
            boolean entregado = false;
            String ultimoError = "";

            for (int intento = 0; intento < tiemposEspera.length; intento++) {
                if (tiemposEspera[intento] > 0) {
                    try {
                        Thread.sleep(tiemposEspera[intento]);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }

                try {
                    String urlWebhook = bancoDestinoInfo.getUrlDestino();
                    log.info("Intento #{}: Enviando a {}", intento + 1, urlWebhook);

                    // Configurar timeout de 3s para este request (Idealmente via RequestFactory,
                    // aqui asumimos que el RestTemplate base tiene un timeout razonable o confiamos
                    // en el catch)
                    restTemplate.postForEntity(urlWebhook, iso, String.class);

                    log.info("Webhook: Entregado exitosamente.");
                    entregado = true;
                    break; // Salir del loop si éxito (2xx)

                } catch (HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
                    // 4xx/5xx -> Rechazo definitivo (No reintentar)
                    log.error("Rechazo definitivo del Banco Destino: {}", e.getStatusCode());
                    throw new BusinessException("Banco Destino rechazó la transacción: " + e.getStatusCode());
                } catch (Exception e) {
                    // Timeout o Error de Conexión -> Reintentar
                    log.warn("Fallo intento #{}: {}", intento + 1, e.getMessage());
                    ultimoError = e.getMessage();
                }
            }

            if (entregado) {
                tx.setEstado("COMPLETED");
                guardarRespaldoIdempotencia(tx, "EXITO");
            } else {
                // Se agotaron los reintentos -> TIMEOUT
                log.error("TIMEOUT: Se agotaron los reintentos. Último error: {}", ultimoError);
                tx.setEstado("PENDING"); // Estado UNKNOWN/PENDING según doc
                transaccionRepository.save(tx);

                // Responder 504 Gateway Timeout
                // Nota: Al lanzar excepción aquí, el GlobalExceptionHandler debería mapearlo a
                // 504.
                // Si no hay un manejador especifico, lanzamos una Runtime que indica timeout.
                throw new java.util.concurrent.TimeoutException("No se obtuvo respuesta del Banco Destino");
            }

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Transacción en estado PENDING por Timeout: {}", e.getMessage());
            // No cambiamos a FAILED, se queda en RECEIVED/PENDING para resolución posterior
            // (Sondeo)
            // Pero para responder al cliente ahora:
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.GATEWAY_TIMEOUT, "Tiempo de espera agotado con Banco Destino");

        } catch (BusinessException e) {
            log.error("Error de Negocio: {}", e.getMessage());
            tx.setEstado("FAILED");
            // Revertir contabilidad si es necesario (Saga), pero por ahora marcamos FAILED.
        } catch (Exception e) {
            log.error("Error crítico en Tx: {}", e.getMessage());
            tx.setEstado("FAILED");
        }

        Transaccion saved = transaccionRepository.save(tx);
        return mapToDTO(saved);
    }

    public TransaccionResponseDTO obtenerTransaccion(UUID id) {
        Transaccion tx = transaccionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Transacción no encontrada"));
        return mapToDTO(tx);
    }

    public Object procesarDevolucion(ReturnRequestDTO returnRequest) {
        String originalId = returnRequest.getBody().getOriginalInstructionId();
        log.info("Procesando solicitud de devolución para instrucción original: {}", originalId);

        // 1. Delegar Validación y Reverso Financiero a CONTABILIDAD
        // Contabilidad valida 48h, duplicados, estados y saldos
        String urlLedger = contabilidadUrl + "/api/v1/ledger/v2/switch/transfers/return";
        Object responseLedger;

        try {
            responseLedger = restTemplate.postForObject(urlLedger, returnRequest, Object.class);
            log.info("Contabilidad: Reverso financiero EXITOSO para {}", originalId);
        } catch (HttpClientErrorException e) {
            log.error("Contabilidad rechazó el reverso: {}", e.getResponseBodyAsString());
            throw new BusinessException("Rechazo de Contabilidad: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error de comunicación con Contabilidad: {}", e.getMessage());
            throw new BusinessException("Error de comunicación con Contabilidad: " + e.getMessage());
        }

        // 2. Actualizar Estado en NUCLEO (Switch)
        Transaccion originalTx = transaccionRepository.findById(UUID.fromString(originalId))
                .orElseThrow(() -> new BusinessException("Transacción original no encontrada en Switch"));

        originalTx.setEstado("REVERSED");
        transaccionRepository.save(originalTx);

        // 3. Ajustar COMPENSACION (Neteo Inverso)
        // Original: Origen -> DEBIT, Destino -> CREDIT
        // Reverso : Origen -> CREDIT, Destino -> DEBIT
        log.info("Compensación: Registrando reverso en ciclo {}", CICLO_ACTUAL_ID);
        try {
            notificarCompensacion(originalTx.getCodigoBicOrigen(), originalTx.getMonto(), false); // false = Credit
                                                                                                  // (Devolución)
            notificarCompensacion(originalTx.getCodigoBicDestino(), originalTx.getMonto(), true); // true = Debit
                                                                                                  // (Quitar fondos)
        } catch (Exception e) {
            log.error("Error al compensar reverso (No bloqueante): {}", e.getMessage());
        }

        // 4. Notificar al Banco Origen (El que envio el dinero originalmente)
        try {
            InstitucionDTO bancoOrigen = validarBanco(originalTx.getCodigoBicOrigen());
            String urlWebhook = bancoOrigen.getUrlDestino() + "/api/incoming/return"; // Endpoint estandar sugerido

            // Enviamos el ReturnRequest tal cual
            restTemplate.postForEntity(urlWebhook, returnRequest, String.class);
            log.info("Notificación enviada al Banco Origen: {}", bancoOrigen.getNombre());
        } catch (Exception e) {
            log.warn("No se pudo notificar al Banco Origen del reverso: {}", e.getMessage());
            // No fallamos el proceso completo por esto
        }

        return responseLedger;
    }

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

    private void notificarCompensacion(String bic, BigDecimal monto, boolean esDebito) {
        try {
            String url = String.format("%s/api/v1/compensacion/ciclos/%d/acumular?bic=%s&monto=%s&esDebito=%s",
                    compensacionUrl, CICLO_ACTUAL_ID, bic, monto.toString(), esDebito);

            restTemplate.postForEntity(url, null, Void.class);

        } catch (Exception e) {
            log.error("ALERTA: Fallo al registrar compensación para {}. Descuadre en Clearing.", bic, e);
        }
    }

    private void guardarRespaldoIdempotencia(Transaccion tx, String resultado) {
        RespaldoIdempotencia respaldo = new RespaldoIdempotencia();
        respaldo.setHashContenido("HASH_" + tx.getIdInstruccion());
        respaldo.setCuerpoRespuesta("{ \"estado\": \"" + resultado + "\" }");
        respaldo.setFechaExpiracion(LocalDateTime.now().plusDays(1));
        respaldo.setTransaccion(tx);
        idempotenciaRepository.save(respaldo);
    }

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

    private String generarMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (Exception e) {
            throw new RuntimeException("Error generando MD5", e);
        }
    }
}