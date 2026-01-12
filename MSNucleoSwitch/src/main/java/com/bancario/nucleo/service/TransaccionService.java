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

    private final StringRedisTemplate redisTemplate;
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

    private static final String ISO_PDNG = "PDNG"; 
    private static final String ISO_ACCP = "ACCP"; 
    private static final String ISO_ACSC = "ACSC"; 
    private static final String ISO_RJCT = "RJCT"; 

    
    private static final String RSN_OK = "OK";
    private static final String RSN_MS03 = "MS03"; 
    private static final String RSN_AM04 = "AM04"; 
    private static final String RSN_AC01 = "AC01"; 
    private static final String RSN_MS99 = "MS99"; 

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

        String fingerprint = idInstruccion.toString()
                + monto.toString()
                + moneda
                + bicOrigen
                + bicDestino
                + creationDateTime
                + cuentaOrigen
                + cuentaDestino;

        String fingerprintMd5 = generarMD5(fingerprint);

        log.info(">>> Iniciando Tx ISO: InstID={} MsgID={} Monto={}", idInstruccion, messageId, monto);

        String redisKey = "idem:" + idInstruccion;

        
        String redisValue = fingerprintMd5 + "|" + ISO_PDNG + "|INIT";

        
        try {
            Boolean claimed = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, redisValue, java.time.Duration.ofHours(24));

            if (!Boolean.TRUE.equals(claimed)) {
                String existing = redisTemplate.opsForValue().get(redisKey);

                if (existing == null) {
                    log.warn("Redis inconsistente. Fallback a Postgres (RespaldoIdempotencia). InstID={}", idInstruccion);
                    return obtenerDesdeRespaldoPostgres(idInstruccion, fingerprintMd5);
                }

                String[] parts = existing.split("\\|");
                String storedMd5 = parts[0];
                String storedIsoStatus = parts.length > 1 ? parts[1] : "UNKNOWN";
                String storedReason = parts.length > 2 ? parts[2] : "-";

                if (!storedMd5.equals(fingerprintMd5)) {
                    log.error("VIOLACIÓN DE INTEGRIDAD — InstructionId={} alterado", idInstruccion);
                    throw new SecurityException("Same InstructionId, different content fingerprint");
                }

                log.warn("Duplicado detectado. ISO_STATUS={} REASON={} InstID={}",
                        storedIsoStatus, storedReason, idInstruccion);

                return obtenerTransaccion(idInstruccion);
                
            }

            log.info("Redis CLAIM OK — Nueva transacción {}", idInstruccion);

        } catch (Exception ex) {
            log.error("Redis no disponible. Fallback a Postgres (RespaldoIdempotencia). InstID={}", idInstruccion, ex);
            return obtenerDesdeRespaldoPostgres(idInstruccion, fingerprintMd5);
        }

       

        Transaccion tx = new Transaccion();
        tx.setIdInstruccion(idInstruccion);
        tx.setIdMensaje(messageId);
        tx.setReferenciaRed("SWITCH-" + System.currentTimeMillis());
        tx.setMonto(monto);
        tx.setMoneda(moneda);
        tx.setCodigoBicOrigen(bicOrigen);
        tx.setCodigoBicDestino(bicDestino);
        tx.setEstado(ISO_ACCP);
        tx.setFechaCreacion(LocalDateTime.now());

        tx = transaccionRepository.save(tx);

       
        actualizarEstadoRedis(redisKey, fingerprintMd5, ISO_ACCP, RSN_OK);

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

            String urlWebhook = bancoDestinoInfo.getUrlDestino();
            log.info("Webhook: Notificando a {}", urlWebhook);
            try {
                restTemplate.postForEntity(urlWebhook, iso, String.class);
                log.info("Webhook: Entregado exitosamente.");
            } catch (Exception e) {
                log.error("Webhook: Falló la entrega. Error: {}", e.getMessage());
                
            }

         
            tx.setEstado(ISO_ACSC);

            guardarRespaldoIdempotencia(tx, fingerprintMd5, ISO_ACSC, RSN_OK);

            actualizarEstadoRedis(redisKey, fingerprintMd5, ISO_ACSC, RSN_OK);

        } catch (Exception e) {
            
            String isoStatus = ISO_RJCT;
            String reason = mapearReasonIso(e);

            log.error("Error crítico en Tx. ISO_STATUS={} REASON={} msg={}", isoStatus, reason, e.getMessage(), e);

            tx.setEstado(isoStatus);

            guardarRespaldoIdempotencia(tx, fingerprintMd5, isoStatus, reason);

            actualizarEstadoRedis(redisKey, fingerprintMd5, isoStatus, reason);
        }

        Transaccion saved = transaccionRepository.save(tx);
        return mapToDTO(saved);
    }

    public TransaccionResponseDTO obtenerTransaccion(UUID id) {
        Transaccion tx = transaccionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Transacción no encontrada"));
        return mapToDTO(tx);
    }

   
    private TransaccionResponseDTO obtenerDesdeRespaldoPostgres(UUID idInstruccion, String fingerprintMd5) {

        RespaldoIdempotencia backup = idempotenciaRepository.findById(idInstruccion)
                .orElseThrow(() -> new BusinessException(
                        "No existe respaldo (RespaldoIdempotencia) para InstructionId=" + idInstruccion
                ));

        if (!fingerprintMd5.equals(backup.getHashContenido())) {
            log.error("RespaldoIdempotencia mismatch. InstID={} hashDB={} hashREQ={}",
                    idInstruccion, backup.getHashContenido(), fingerprintMd5);
            throw new SecurityException("Same InstructionId, different content fingerprint (Postgres RespaldoIdempotencia)");
        }

       
        return obtenerTransaccion(idInstruccion);
    }

    private void actualizarEstadoRedis(String redisKey, String fingerprintMd5, String isoStatus, String reasonCode) {
        try {
            redisTemplate.opsForValue().set(
                    redisKey,
                    fingerprintMd5 + "|" + isoStatus + "|" + reasonCode,
                    java.time.Duration.ofHours(24)
            );
        } catch (Exception ignore) {
            log.warn("No se pudo actualizar Redis ({}). Key={}", isoStatus, redisKey);
        }
    }

   
    private String mapearReasonIso(Exception e) {
       

        if (e instanceof HttpClientErrorException.NotFound) {
            return RSN_MS03;
        }
        if (e instanceof HttpClientErrorException.BadRequest) {
            return RSN_AM04;
        }

        String msg = e.getMessage() == null ? "" : e.getMessage().toUpperCase();

        if (msg.contains("FONDOS") || msg.contains("INSUFICIENT")) return RSN_AM04;
        if (msg.contains("NO EXISTE") || msg.contains("NO ESTÁ OPERATIVO") || msg.contains("DIRECTORIO")) return RSN_MS03;

        return RSN_MS99;
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

    private void guardarRespaldoIdempotencia(Transaccion tx, String fingerprintMd5, String isoStatus, String reasonCode) {
        RespaldoIdempotencia respaldo = new RespaldoIdempotencia();
        respaldo.setIdInstruccion(tx.getIdInstruccion()); 
        respaldo.setHashContenido(fingerprintMd5);
        respaldo.setFechaExpiracion(LocalDateTime.now().plusDays(1));
        respaldo.setTransaccion(tx);

        respaldo.setCuerpoRespuesta(
                "{ \"pacs002\": { " +
                        "\"instructionId\": \"" + tx.getIdInstruccion() + "\", " +
                        "\"transactionStatus\": \"" + isoStatus + "\", " +
                        "\"reason\": \"" + reasonCode + "\" " +
                "} }"
        );

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