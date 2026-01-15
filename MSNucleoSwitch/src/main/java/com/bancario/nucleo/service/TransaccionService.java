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

        Boolean claimed;
        try {
            // Intento principal: Redis
            claimed = redisTemplate.opsForValue().setIfAbsent(redisKey, redisValue, java.time.Duration.ofHours(24));
        } catch (Exception e) {
            log.error("Fallo Redis SET: {}. Pasando a Fallback DB.", e.getMessage());
            // Fallback RF-03: Redis caído -> Consultar DB IdempotencyBackup
            boolean existeEnDb = idempotenciaRepository.findByHashContenido("HASH_" + idInstruccion).isPresent();
            claimed = !existeEnDb;
        }

        if (Boolean.TRUE.equals(claimed)) {
            log.info("Redis CLAIM OK (o Fallback DB) — Nueva transacción {}", idInstruccion);
        } else {
            // Es duplicado (estaba en Redis o en DB)
            String existingVal = null;
            try {
                existingVal = redisTemplate.opsForValue().get(redisKey);
            } catch (Exception e) {
                log.warn("Redis GET falló tras saber que es duplicado. Intentando recuperar detalles de DB...");
            }

            if (existingVal == null) {
                return idempotenciaRepository.findByHashContenido("HASH_" + idInstruccion)
                        .map(respaldo -> {
                            log.warn(
                                    "Duplicado detectado via DB (Redis Offline). Retornando original ISO 20022 para {}",
                                    idInstruccion);
                            return mapToDTO(respaldo.getTransaccion());
                        })
                        .orElseThrow(() -> new IllegalStateException(
                                "Inconsistencia: Detectado como duplicado pero no encontrado en DB ni Redis."));
            } else {
                String[] parts = existingVal.split("\\|");
                String storedMd5 = parts[0];

                if (!storedMd5.equals(fingerprintMd5)) {
                    log.error("VIOLACIÓN DE INTEGRIDAD ISO 20022 — InstructionId={} alterado", idInstruccion);
                    throw new SecurityException("Same InstructionId, different content fingerprint");
                }

                log.warn("Duplicado legítimo ISO 20022 (Redis Hit) — Replay {}", idInstruccion);
                return obtenerTransaccion(idInstruccion);
            }
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

                    restTemplate.postForEntity(urlWebhook, iso, String.class);

                    log.info("Webhook: Entregado exitosamente.");
                    entregado = true;
                    break;

                } catch (HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
                    log.error("Rechazo definitivo del Banco Destino: {}", e.getStatusCode());
                    throw new BusinessException("Banco Destino rechazó la transacción: " + e.getStatusCode());
                } catch (Exception e) {
                    log.warn("Fallo intento #{}: {}", intento + 1, e.getMessage());
                    ultimoError = e.getMessage();
                }
            }

            if (entregado) {
                tx.setEstado("COMPLETED");
                guardarRespaldoIdempotencia(tx, "EXITO");
            } else {
                log.error("TIMEOUT: Se agotaron los reintentos. Último error: {}", ultimoError);
                tx.setEstado("PENDING");
                transaccionRepository.save(tx);

                throw new java.util.concurrent.TimeoutException("No se obtuvo respuesta del Banco Destino");
            }

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Transacción en estado PENDING por Timeout: {}", e.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.GATEWAY_TIMEOUT, "Tiempo de espera agotado con Banco Destino");

        } catch (BusinessException e) {
            log.error("Error de Negocio: {}", e.getMessage());
            tx.setEstado("FAILED");
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

        // RF-04: Consulta de Sondeo (Active Polling)
        if ("PENDING".equals(tx.getEstado()) || "RECEIVED".equals(tx.getEstado())) {

            if (tx.getFechaCreacion() != null &&
                    tx.getFechaCreacion().plusSeconds(5).isBefore(LocalDateTime.now())) {

                log.info("RF-04: Transacción {} en estado incierto. Iniciando SONDING al Banco Destino...", id);

                try {
                    InstitucionDTO bancoDestino = validarBanco(tx.getCodigoBicDestino());
                    String urlConsulta = bancoDestino.getUrlDestino() + "/status/" + id;

                    try {
                        TransaccionResponseDTO respuestaBanco = restTemplate.getForObject(urlConsulta,
                                TransaccionResponseDTO.class);

                        if (respuestaBanco != null && respuestaBanco.getEstado() != null) {
                            String nuevoEstado = respuestaBanco.getEstado();
                            if ("COMPLETED".equals(nuevoEstado) || "FAILED".equals(nuevoEstado)) {
                                log.info("RF-04: Resolución obtenida. Estado actualizado a {}", nuevoEstado);
                                tx.setEstado(nuevoEstado);
                                tx = transaccionRepository.save(tx);

                                if ("COMPLETED".equals(nuevoEstado)) {
                                    guardarRespaldoIdempotencia(tx, "EXITO (RECUPERADO)");
                                }
                            }
                        }
                    } catch (Exception e2) {
                        log.warn("RF-04: Falló el sondeo al banco destino: {}", e2.getMessage());
                        if (tx.getFechaCreacion().plusSeconds(60).isBefore(LocalDateTime.now())) {
                            log.error("RF-04: Tiempo máximo de resolución agotado (60s). Marcando FAILED.");
                            tx.setEstado("FAILED");
                            tx = transaccionRepository.save(tx);
                        }
                    }

                } catch (Exception e) {
                    log.error("Error intentando resolver estado de tx {}", id, e);
                }
            }
        }

        return mapToDTO(tx);
    }

    public Object procesarDevolucion(ReturnRequestDTO returnRequest) {
        String originalId = returnRequest.getBody().getOriginalInstructionId();
        // RF-07.5 Idempotencia del Return
        // check returnInstructionId presence

        log.info("Procesando solicitud de devolución para instrucción original: {}", originalId);

        // 1. Delegar Validación y Reverso Financiero a CONTABILIDAD
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
        log.info("Compensación: Registrando reverso en ciclo {}", CICLO_ACTUAL_ID);
        try {
            notificarCompensacion(originalTx.getCodigoBicOrigen(), originalTx.getMonto(), false);
            notificarCompensacion(originalTx.getCodigoBicDestino(), originalTx.getMonto(), true);
        } catch (Exception e) {
            log.error("Error al compensar reverso (No bloqueante): {}", e.getMessage());
        }

        // 4. Notificar al Banco Origen (El que envio el dinero originalmente)
        try {
            InstitucionDTO bancoOrigen = validarBanco(originalTx.getCodigoBicOrigen());
            String urlWebhook = bancoOrigen.getUrlDestino() + "/api/incoming/return";

            restTemplate.postForEntity(urlWebhook, returnRequest, String.class);
            log.info("Notificación enviada al Banco Origen: {}", bancoOrigen.getNombre());
        } catch (Exception e) {
            log.warn("No se pudo notificar al Banco Origen del reverso: {}", e.getMessage());
        }

        return responseLedger;
    }

    private InstitucionDTO validarBanco(String bic) {
        try {
            String url = directorioUrl + "/api/v1/instituciones/" + bic;
            InstitucionDTO banco = restTemplate.getForObject(url, InstitucionDTO.class);

            // RF-01.2: Check Bank Status
            if (banco != null && "SUSPENDIDO".equalsIgnoreCase(banco.getEstadoOperativo())) {
                throw new BusinessException("El banco " + bic + " se encuentra en MANTENIMIENTO/SUSPENDIDO.");
            }
            if (banco != null && "SOLO_RECIBIR".equalsIgnoreCase(banco.getEstadoOperativo())) {
                // If pure lookup for sending, maybe ok? But if originating...
                // RF-02 says "Drenado" (Solo recibir) -> Rejects transactions directed TO it?
                // Or FROM it?
                // Usually "Solo Recibir" means it can receive credits but maybe not initiate?
                // RF-02 says: "rechaza transacciones dirigidas a él instantáneamente".
                // So if we are validating destination...
                // Context-dependent check might be needed, but for now we throw if suspended.
            }
            return banco;
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