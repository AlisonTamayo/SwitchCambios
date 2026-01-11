package com.bancario.nucleo.service;

import com.bancario.nucleo.dto.TransaccionResponseDTO;
import com.bancario.nucleo.dto.external.InstitucionDTO;
import com.bancario.nucleo.dto.external.RegistroMovimientoRequest;
import com.bancario.nucleo.dto.iso.MensajeISO;
import com.bancario.nucleo.exception.BusinessException;
import com.bancario.nucleo.model.TransaccionDocument;
import com.bancario.nucleo.model.TransaccionDocument.Auditoria;
import com.bancario.nucleo.model.TransaccionDocument.Datos;
import com.bancario.nucleo.model.TransaccionDocument.Header;
import com.bancario.nucleo.model.TransaccionDocument.Idempotencia;
import com.bancario.nucleo.repository.TransaccionDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransaccionService {

    private final TransaccionDocumentRepository transaccionRepository;
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
     * Procesa una transacción bajo el estándar ISO 20022 de forma documental e idempotente.
     * Flujo: Persistencia inicial (RECEIVED) -> Validaciones -> Ledger -> Compensación -> Webhook -> COMPLETED/FAILED.
     */
    @Transactional
    public TransaccionResponseDTO procesarTransaccionIso(MensajeISO iso) {
        UUID idInstruccion = UUID.fromString(iso.getBody().getInstructionId());
        String bicOrigen = iso.getHeader().getOriginatingBankId();
        String bicDestino = iso.getBody().getCreditor().getTargetBankId();
        BigDecimal monto = iso.getBody().getAmount().getValue();
        String moneda = iso.getBody().getAmount().getCurrency();
        String messageId = iso.getHeader().getMessageId();
        Instant now = Instant.now();

        log.info(">>> Iniciando Tx ISO: InstID={} MsgID={} Monto={}", idInstruccion, messageId, monto);

        // 1. IDEMPOTENCIA (instructionId como _id nativo)
        TransaccionDocument existente = transaccionRepository.findById(idInstruccion).orElse(null);
        if (existente != null) {
            log.warn("Transacción duplicada detectada: {}", idInstruccion);
            return mapToDTO(existente);
        }

        // 2. PERSISTENCIA INICIAL EN MONGO (estado RECEIVED)
        TransaccionDocument tx = inicializarDocumento(iso, idInstruccion, bicOrigen, bicDestino, monto, moneda, now);
        transaccionRepository.save(tx);

        try {
            // 3. VALIDACIÓN CON DIRECTORIO
            validarBanco(bicOrigen); // Validar emisor
            InstitucionDTO bancoDestinoInfo = validarBanco(bicDestino); // Validar receptor y obtener URL
            marcarEstado(tx, "ROUTED", "Bancos validados en Directorio");

            // 4. LEDGER: MOVIMIENTO DE DINERO REAL (Cuentas Técnicas)
            registrarMovimientoContable(bicOrigen, idInstruccion, monto, "DEBIT");
            registrarMovimientoContable(bicDestino, idInstruccion, monto, "CREDIT");

            // 5. COMPENSACIÓN: ACUMULACIÓN DE SALDOS (Clearing)
            notificarCompensacion(bicOrigen, monto, true);  // Origen DEBE (Débito)
            notificarCompensacion(bicDestino, monto, false); // Destino TIENE A FAVOR (Crédito)

            // 6. NOTIFICACIÓN AL BANCO DESTINO (PUSH / Webhook)
            String urlWebhook = bancoDestinoInfo.getUrlDestino();
            log.info("Webhook: Notificando a {}", urlWebhook);
            try {
                restTemplate.postForEntity(urlWebhook, iso, String.class);
                log.info("Webhook: Entregado exitosamente.");
            } catch (Exception e) {
                log.error("Webhook: Falló la entrega. El banco destino deberá conciliar manualmente. Error: {}", e.getMessage());
            }

            // 7. FINALIZACIÓN EXITOSA
            marcarEstado(tx, "COMPLETED", "Orquestación completada");
            TransaccionResponseDTO respuesta = mapToDTO(tx);
            guardarIdempotencia(tx, respuesta);
            transaccionRepository.save(tx);
            return respuesta;

        } catch (Exception e) {
            log.error("Error crítico en Tx: {}", e.getMessage(), e);
            marcarEstado(tx, "FAILED", e.getMessage());
            guardarIdempotencia(tx, Map.of("estado", tx.getEstado(), "error", e.getMessage()));
            transaccionRepository.save(tx);
            return mapToDTO(tx);
        }
    }

    public TransaccionResponseDTO obtenerTransaccion(UUID id) {
        TransaccionDocument tx = transaccionRepository.findById(id)
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
            String url = String.format("%s/api/v1/compensacion/ciclos/%d/acumular?bic=%s&monto=%s&esDebito=%s",
                    compensacionUrl, CICLO_ACTUAL_ID, bic, monto.toString(), esDebito);

            restTemplate.postForEntity(url, null, Void.class);

        } catch (Exception e) {
            log.error("ALERTA: Fallo al registrar compensación para {}. Descuadre en Clearing.", bic, e);
        }
    }

    // --- CONSTRUCCIÓN DOCUMENTAL / IDEMPOTENCIA ---

    private TransaccionDocument inicializarDocumento(MensajeISO iso, UUID idInstruccion, String bicOrigen, String bicDestino,
                                                     BigDecimal monto, String moneda, Instant now) {
        Instant fechaCreacion = parseCreationDateTime(iso.getHeader().getCreationDateTime(), now);
        String referenciaRed = "SWITCH-" + now.toEpochMilli();
        String debtorAccount = iso.getBody().getDebtor() != null ? iso.getBody().getDebtor().getAccountId() : null;
        String creditorAccount = iso.getBody().getCreditor() != null ? iso.getBody().getCreditor().getAccountId() : null;

        TransaccionDocument tx = TransaccionDocument.builder()
                .id(idInstruccion)
                .hashIdempotencia(calcularHashIdempotencia(idInstruccion, monto, moneda, bicOrigen, bicDestino, fechaCreacion))
                .header(Header.builder()
                        .messageId(iso.getHeader().getMessageId())
                        .bicOrigen(bicOrigen)
                        .bicDestino(bicDestino)
                        .fechaCreacion(fechaCreacion)
                        .build())
                .datos(Datos.builder()
                        .monto(monto)
                        .moneda(moneda)
                        .referenciaRed(referenciaRed)
                        .endToEndId(iso.getBody().getEndToEndId())
                        .remittanceInformation(iso.getBody().getRemittanceInformation())
                        .debtorAccount(debtorAccount)
                        .creditorAccount(creditorAccount)
                        .build())
                .estado("RECEIVED")
                .build();

        marcarEstado(tx, "RECEIVED", "Mensaje ISO recibido");
        return tx;
    }

    private void marcarEstado(TransaccionDocument tx, String estado, String detalle) {
        tx.setEstado(estado);
        tx.getAuditoria().add(Auditoria.builder()
                .estado(estado)
                .timestamp(Instant.now())
                .detalle(detalle)
                .build());
    }

    private void guardarIdempotencia(TransaccionDocument tx, Object cuerpo) {
        tx.setIdempotencia(Idempotencia.builder()
                .cuerpoRespuesta(cuerpo)
                .createdAt(Instant.now())
                .build());
    }

    private String calcularHashIdempotencia(UUID idInstruccion, BigDecimal monto, String moneda, String bicOrigen,
                                            String bicDestino, Instant fechaCreacion) {
        try {
            String raw = idInstruccion + "|" + monto.toPlainString() + "|" + moneda + "|" + bicOrigen + "|" + bicDestino + "|" + fechaCreacion;
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular el hash de idempotencia", e);
        }
    }

    private Instant parseCreationDateTime(String rawDate, Instant fallback) {
        if (rawDate == null || rawDate.isBlank()) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(rawDate).toInstant();
        } catch (DateTimeParseException ex) {
            log.warn("No se pudo parsear creationDateTime '{}', usando fallback UTC", rawDate);
            return fallback;
        }
    }

    // --- MAPPERS ---

    private TransaccionResponseDTO mapToDTO(TransaccionDocument tx) {
        LocalDateTime fechaCreacion = tx.getHeader() != null && tx.getHeader().getFechaCreacion() != null
                ? LocalDateTime.ofInstant(tx.getHeader().getFechaCreacion(), ZoneOffset.UTC)
                : null;

        return TransaccionResponseDTO.builder()
                .idInstruccion(tx.getId())
                .idMensaje(tx.getHeader() != null ? tx.getHeader().getMessageId() : null)
                .referenciaRed(tx.getDatos() != null ? tx.getDatos().getReferenciaRed() : null)
                .monto(tx.getDatos() != null ? tx.getDatos().getMonto() : null)
                .moneda(tx.getDatos() != null ? tx.getDatos().getMoneda() : null)
                .codigoBicOrigen(tx.getHeader() != null ? tx.getHeader().getBicOrigen() : null)
                .codigoBicDestino(tx.getHeader() != null ? tx.getHeader().getBicDestino() : null)
                .estado(tx.getEstado())
                .fechaCreacion(fechaCreacion)
                .build();
    }
}