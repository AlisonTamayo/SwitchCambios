# 🐰 Guía de Integración de Bancos con RabbitMQ
## Switch Transaccional DIGICONECU - Sistema de Colas Asíncrono

---

## 📋 Resumen Ejecutivo

Este documento proporciona las instrucciones técnicas para que las entidades financieras participantes se integren con el sistema de mensajería **asíncrona** del Switch DIGICONECU utilizando **Amazon MQ (RabbitMQ)**.

**Beneficios de la integración:**
- ✅ **Alta disponibilidad**: Mensajes persistentes garantizan entrega incluso durante mantenimiento
- ✅ **Desacoplamiento**: Sin dependencia de disponibilidad instantánea del banco destino
- ✅ **Resiliencia**: Reintentos automáticos con backoff exponencial
- ✅ **Auditoría**: Trazabilidad completa de mensajes
- ✅ **Asincronía**: El banco origen recibe respuesta inmediata (202 Accepted)

---

## 🔄 Cambio de Arquitectura: Síncrono → Asíncrono

### ❌ Flujo ANTERIOR (Síncrono)
```
Banco Origen ──HTTP──► Switch ──HTTP──► Banco Destino
                          ◄──────────────── HTTP 200
                ◄──HTTP 201 Created────
                
⏳ Banco origen BLOQUEADO esperando respuesta (1-10 segundos)
```

### ✅ Flujo ACTUAL (Asíncrono)
```
Banco Origen ──HTTP──► Switch ──HTTP 202 Accepted──► Banco Origen (LIBRE!)
                          │
                          ▼ RabbitMQ
                    q.bank.BANTEC.in
                          │
                          ▼
                    Banco Destino consume
                          │
                          ▼ HTTP POST /callback
                       Switch
                          │
                          ▼ HTTP POST webhook
                    Banco Origen (recibe confirmación)
                    
✅ Banco origen recibe 202 INMEDIATAMENTE (~100ms)
✅ Confirmación llega después vía Webhook
```

---

## 🏗️ Arquitectura Completa del Flujo Asíncrono

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              FLUJO ASÍNCRONO COMPLETO - 5 PASOS                                                 │
├─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                                 │
│  ╔═══════════════════════╗                                                                                      │
│  ║     BANCO ORIGEN      ║                                                                                      │
│  ║       (NEXUS)         ║                                                                                      │
│  ║  Webhook configurado  ║                                                                                      │
│  ║  en el Directorio     ║                                                                                      │
│  ╚═══════════╤═══════════╝                                                                                      │
│              │                                                                                                  │
│              │ ① HTTP POST pacs.008 (ISO 20022)                                                                 │
│              │    { header: { originatingBankId: "NEXUS" },                                                     │
│              │      body: { creditor: { targetBankId: "BANTEC" } } }                                            │
│              ▼                                                                                                  │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════════╗         │
│  ║                                        SWITCH DIGICONECU                                          ║         │
│  ║                                                                                                   ║         │
│  ║   1. Valida mensaje ISO 20022                                                                     ║         │
│  ║   2. Valida bancos en Directorio                                                                  ║         │
│  ║   3. Registra DEBIT en Ledger (quita $ al banco origen)                                          ║         │
│  ║   4. Publica mensaje a cola: rabbitTemplate.convertAndSend("ex.transfers.tx", "BANTEC", msg)     ║         │
│  ║   5. Retorna HTTP 202 Accepted INMEDIATAMENTE                                                     ║         │
│  ║                                                                                                   ║         │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════════╝         │
│              │                                                                                                  │
│              │ ② HTTP 202 Accepted (INMEDIATO, ~100ms)                                                          │
│              │    { idInstruccion: "uuid", estado: "QUEUED", mensaje: "Transferencia encolada" }                │
│              ▼                                                                                                  │
│  ╔═══════════════════════╗                                                                                      │
│  ║     BANCO ORIGEN      ║                                                                                      │
│  ║       (NEXUS)         ║                                                                                      │
│  ║                       ║                                                                                      │
│  ║  ✅ LIBRE para hacer  ║                                                                                      │
│  ║     otras operaciones ║                                                                                      │
│  ╚═══════════════════════╝                                                                                      │
│                                                                                                                 │
│              ┌─────────────────────────────────────────────────────────────────────────────┐                    │
│              │                           RabbitMQ (Amazon MQ)                              │                    │
│              │                                                                             │                    │
│              │   ┌───────────────────────────┐                                             │                    │
│              │   │     ex.transfers.tx       │   (Direct Exchange)                         │                    │
│              │   └─────────────┬─────────────┘                                             │                    │
│              │                 │ routingKey = "BANTEC"                                     │                    │
│              │                 ▼                                                           │                    │
│              │   ┌───────────────────────────┐                                             │                    │
│              │   │    q.bank.BANTEC.in       │   ◄── Los bancos consumen de aquí          │                    │
│              │   └─────────────┬─────────────┘                                             │                    │
│              │                 │                                                           │                    │
│              └─────────────────┼───────────────────────────────────────────────────────────┘                    │
│                                │                                                                                │
│                                │ ③ @RabbitListener consume mensaje                                              │
│                                ▼                                                                                │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════════╗         │
│  ║                                     BANCO DESTINO (BANTEC)                                        ║         │
│  ║                                                                                                   ║         │
│  ║   @RabbitListener(queues = "q.bank.BANTEC.in")                                                    ║         │
│  ║   public void procesarTransferencia(MensajeISO mensaje) {                                         ║         │
│  ║       1. Extraer datos de la transferencia                                                        ║         │
│  ║       2. Validar cuenta destino existe                                                            ║         │
│  ║       3. Validar cuenta no bloqueada                                                              ║         │
│  ║       4. Procesar depósito en Core Bancario                                                       ║         │
│  ║       5. Enviar resultado AL SWITCH vía HTTP POST /callback                                       ║         │
│  ║   }                                                                                               ║         │
│  ║                                                                                                   ║         │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════════╝         │
│                                │                                                                                │
│                                │ ④ HTTP POST /api/v1/transacciones/callback                                     │
│                                │    { header: { respondingBankId: "BANTEC" },                                   │
│                                │      body: { originalInstructionId: "uuid", status: "COMPLETED" } }            │
│                                ▼                                                                                │
│  ╔═══════════════════════════════════════════════════════════════════════════════════════════════════╗         │
│  ║                                        SWITCH DIGICONECU                                          ║         │
│  ║                                                                                                   ║         │
│  ║   CallbackServicio.procesarCallback()                                                             ║         │
│  ║   1. Actualiza estado de tx a COMPLETED                                                           ║         │
│  ║   2. Registra CREDIT en Ledger (da $ al banco destino)                                           ║         │
│  ║   3. Busca webhook del banco origen en Directorio                                                 ║         │
│  ║   4. Envía HTTP POST con resultado al banco origen                                                ║         │
│  ║                                                                                                   ║         │
│  ╚═══════════════════════════════════════════════════════════════════════════════════════════════════╝         │
│                                │                                                                                │
│                                │ ⑤ HTTP POST pacs.002 (StatusReport) al Webhook del banco origen               │
│                                │    { body: { originalInstructionId: "uuid", status: "COMPLETED" } }            │
│                                ▼                                                                                │
│  ╔═══════════════════════╗                                                                                      │
│  ║     BANCO ORIGEN      ║                                                                                      │
│  ║       (NEXUS)         ║                                                                                      │
│  ║                       ║                                                                                      │
│  ║  ✅ Recibe resultado  ║                                                                                      │
│  ║  ✅ Notifica cliente  ║                                                                                      │
│  ║  ✅ TX completada     ║                                                                                      │
│  ╚═══════════════════════╝                                                                                      │
│                                                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔐 Credenciales de Conexión RabbitMQ

| Parámetro | Valor |
|-----------|-------|
| **Endpoint AMQPS** | `amqps://b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws:5671` |
| **Consola Web** | `https://b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws/` |
| **Puerto** | `5671` (SSL obligatorio) |
| **Virtual Host** | `/` |
| **Protocolo** | AMQP 0-9-1 sobre TLS 1.2 |
| **Usuario** | Asignado por DIGICONECU (ver tabla abajo) |
| **Contraseña** | Solicitar a DIGICONECU vía canal seguro |

> ⚠️ **IMPORTANTE**: El puerto 5672 (sin encriptación) está **deshabilitado**. Es obligatorio usar el puerto 5671 con SSL/TLS.

### Usuarios por Entidad

| Entidad | Usuario RabbitMQ | Cola Asignada |
|---------|------------------|---------------|
| Nexus | `nexus` | `q.bank.NEXUS.in` |
| Bantec | `bantec` | `q.bank.BANTEC.in` |
| ArcBank | `arcbank` | `q.bank.ARCBANK.in` |
| Ecusol | `ecusol` | `q.bank.ECUSOL.in` |

---

## 📦 Lo que debe implementar cada Banco

### Resumen de Responsabilidades

| # | Tarea | Protocolo | Descripción |
|---|-------|-----------|-------------|
| 1 | **Consumir de cola** | RabbitMQ | `@RabbitListener(queues = "q.bank.{BIC}.in")` |
| 2 | **Procesar transferencia** | Interno | Validar cuenta, depositar fondos |
| 3 | **Notificar al Switch** | HTTP POST | `POST /api/v1/transacciones/callback` |

---

## 🛠️ Implementación Paso a Paso

### Paso 1: Dependencias Maven

```xml
<!-- pom.xml -->
<dependencies>
    <!-- Spring AMQP para RabbitMQ -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>
    
    <!-- WebClient para HTTP al Switch -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>
    
    <!-- Jackson para serialización JSON -->
    <dependency>
        <groupId>com.fasterxml.jackson.datatype</groupId>
        <artifactId>jackson-datatype-jsr310</artifactId>
    </dependency>
</dependencies>
```

---

### Paso 2: Configuración `application.yml`

```yaml
spring:
  rabbitmq:
    # ═══════════════════════════════════════════════════════════════
    # CONEXIÓN A AMAZON MQ (RabbitMQ)
    # ═══════════════════════════════════════════════════════════════
    host: b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws
    port: 5671  # Puerto SSL OBLIGATORIO
    username: ${RABBITMQ_USER}      # bantec, nexus, arcbank, ecusol
    password: ${RABBITMQ_PASSWORD}  # Solicitar a DIGICONECU
    virtual-host: /
    
    # SSL/TLS Obligatorio
    ssl:
      enabled: true
      algorithm: TLSv1.2
    
    # ═══════════════════════════════════════════════════════════════
    # POLÍTICA DE REINTENTOS (Si falla el procesamiento)
    # ═══════════════════════════════════════════════════════════════
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false  # Si falla, va al DLQ
        retry:
          enabled: true
          max-attempts: 4
          initial-interval: 800ms
          multiplier: 2.5
          max-interval: 5000ms

# ═══════════════════════════════════════════════════════════════
# COLA ASIGNADA A SU BANCO (Cambiar según corresponda)
# ═══════════════════════════════════════════════════════════════
bank:
  code: BANTEC  # Cambiar: NEXUS, BANTEC, ARCBANK, ECUSOL
  queue:
    input: q.bank.BANTEC.in       # Cola principal
    dlq: q.bank.BANTEC.dlq        # Dead Letter Queue

# ═══════════════════════════════════════════════════════════════
# URL DEL SWITCH PARA CALLBACK
# ═══════════════════════════════════════════════════════════════
switch:
  url: http://34.16.106.7:8000    # Kong API Gateway
  callback:
    endpoint: /api/v1/transacciones/callback
```

---

### Paso 3: DTOs (Estructuras de Datos)

#### 3.1 DTO de Transferencia Entrante (pacs.008)

```java
package com.subanco.integracion.dto;

import lombok.Data;
import java.math.BigDecimal;

/**
 * Estructura del mensaje que llega desde RabbitMQ.
 * Representa una transferencia interbancaria (pacs.008).
 */
@Data
public class MensajeISO {
    private Header header;
    private Body body;
    
    @Data
    public static class Header {
        private String messageId;           // ID único del mensaje
        private String creationDateTime;    // Timestamp ISO 8601
        private String originatingBankId;   // BIC del banco origen (NEXUS, BANTEC, etc.)
    }
    
    @Data
    public static class Body {
        private String instructionId;       // UUID de la instrucción (CLAVE para tracking)
        private String endToEndId;          // Referencia del cliente
        private Amount amount;
        private Actor debtor;               // Ordenante (quien envía)
        private Actor creditor;             // Beneficiario (quien recibe)
        private String remittanceInformation; // Concepto
    }
    
    @Data
    public static class Amount {
        private String currency;            // "USD"
        private BigDecimal value;           // Monto
    }
    
    @Data
    public static class Actor {
        private String name;
        private String accountId;
        private String accountType;         // CHECKING, SAVINGS
        private String targetBankId;        // BIC destino (solo en creditor)
    }
}
```

#### 3.2 DTO de Respuesta al Switch (pacs.002 - StatusReport)

```java
package com.subanco.integracion.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO para notificar el resultado al Switch.
 * El banco debe enviar este DTO al endpoint /callback del Switch.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StatusReportDTO {
    private Header header;
    private Body body;
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Header {
        private String messageId;           // Nuevo ID para esta respuesta
        private String creationDateTime;    // Timestamp ISO 8601
        private String respondingBankId;    // BIC del banco que responde (ustedes)
    }
    
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Body {
        private UUID originalInstructionId;   // El instructionId de la tx original
        private String originalMessageId;     // El messageId original
        private String status;                // COMPLETED o REJECTED
        private String reasonCode;            // Solo si REJECTED: AC03, AM04, etc.
        private String reasonDescription;     // Descripción del error
        private String processedDateTime;     // Cuándo se procesó
    }
}
```

---

### Paso 4: Configuración RabbitMQ

```java
package com.subanco.integracion.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class RabbitMQConfig {

    /**
     * Converter JSON para mensajes RabbitMQ.
     * Permite deserializar automáticamente los mensajes a DTOs.
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(mapper);
    }
}
```

---

### Paso 5: Listener de Transferencias (⭐ COMPONENTE PRINCIPAL)

```java
package com.subanco.integracion.listener;

import com.subanco.integracion.dto.MensajeISO;
import com.subanco.integracion.dto.StatusReportDTO;
import com.subanco.integracion.service.CoreBancarioService;
import com.subanco.integracion.service.SwitchCallbackService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferenciaListener {

    private final CoreBancarioService coreService;
    private final SwitchCallbackService callbackService;

    @Value("${bank.code}")
    private String bankCode;

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * LISTENER PRINCIPAL - PROCESA TRANSFERENCIAS DESDE RABBITMQ
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * IMPORTANTE: Reemplace "q.bank.BANTEC.in" con su cola asignada:
     * - Nexus:   q.bank.NEXUS.in
     * - Bantec:  q.bank.BANTEC.in
     * - ArcBank: q.bank.ARCBANK.in
     * - Ecusol:  q.bank.ECUSOL.in
     */
    @RabbitListener(queues = "${bank.queue.input}")
    public void procesarTransferenciaEntrante(MensajeISO mensaje) {
        String instructionId = mensaje.getBody().getInstructionId();
        String bancoOrigen = mensaje.getHeader().getOriginatingBankId();
        
        log.info("═══════════════════════════════════════════════════════════════════════════");
        log.info("TRANSFERENCIA RECIBIDA via RabbitMQ");
        log.info("  InstructionId: {}", instructionId);
        log.info("  Banco Origen: {}", bancoOrigen);
        log.info("  Monto: {} {}", mensaje.getBody().getAmount().getValue(), 
                                   mensaje.getBody().getAmount().getCurrency());
        log.info("  Cuenta Destino: {}", mensaje.getBody().getCreditor().getAccountId());
        log.info("═══════════════════════════════════════════════════════════════════════════");

        StatusReportDTO resultado;
        
        try {
            // ═══════════════════════════════════════════════════════════════
            // PASO 1: Validar cuenta destino
            // ═══════════════════════════════════════════════════════════════
            String cuentaDestino = mensaje.getBody().getCreditor().getAccountId();
            
            if (!coreService.cuentaExiste(cuentaDestino)) {
                log.error("Cuenta destino no existe: {}", cuentaDestino);
                resultado = construirRespuestaRechazo(mensaje, "AC03", "Cuenta destino no existe");
                callbackService.notificarSwitch(resultado);
                return;
            }
            
            if (coreService.cuentaBloqueada(cuentaDestino)) {
                log.error("Cuenta destino bloqueada: {}", cuentaDestino);
                resultado = construirRespuestaRechazo(mensaje, "AC06", "Cuenta bloqueada");
                callbackService.notificarSwitch(resultado);
                return;
            }

            // ═══════════════════════════════════════════════════════════════
            // PASO 2: Procesar depósito en Core Bancario
            // ═══════════════════════════════════════════════════════════════
            coreService.procesarDeposito(
                cuentaDestino,
                mensaje.getBody().getAmount().getValue(),
                "Transferencia de " + bancoOrigen + " - Ref: " + instructionId
            );
            
            log.info("✅ Depósito procesado exitosamente");

            // ═══════════════════════════════════════════════════════════════
            // PASO 3: Notificar ÉXITO al Switch
            // ═══════════════════════════════════════════════════════════════
            resultado = construirRespuestaExito(mensaje);
            callbackService.notificarSwitch(resultado);
            
            log.info("✅ Callback enviado al Switch: COMPLETED");
            
        } catch (Exception e) {
            log.error("Error procesando transferencia: {}", e.getMessage(), e);
            resultado = construirRespuestaRechazo(mensaje, "MS03", e.getMessage());
            callbackService.notificarSwitch(resultado);
        }
    }

    /**
     * Construye respuesta de ÉXITO para el Switch
     */
    private StatusReportDTO construirRespuestaExito(MensajeISO mensaje) {
        return StatusReportDTO.builder()
                .header(StatusReportDTO.Header.builder()
                        .messageId(UUID.randomUUID().toString())
                        .creationDateTime(LocalDateTime.now().toString())
                        .respondingBankId(bankCode)
                        .build())
                .body(StatusReportDTO.Body.builder()
                        .originalInstructionId(UUID.fromString(mensaje.getBody().getInstructionId()))
                        .originalMessageId(mensaje.getHeader().getMessageId())
                        .status("COMPLETED")
                        .processedDateTime(LocalDateTime.now().toString())
                        .build())
                .build();
    }

    /**
     * Construye respuesta de RECHAZO para el Switch
     */
    private StatusReportDTO construirRespuestaRechazo(MensajeISO mensaje, 
                                                       String reasonCode, 
                                                       String reasonDescription) {
        return StatusReportDTO.builder()
                .header(StatusReportDTO.Header.builder()
                        .messageId(UUID.randomUUID().toString())
                        .creationDateTime(LocalDateTime.now().toString())
                        .respondingBankId(bankCode)
                        .build())
                .body(StatusReportDTO.Body.builder()
                        .originalInstructionId(UUID.fromString(mensaje.getBody().getInstructionId()))
                        .originalMessageId(mensaje.getHeader().getMessageId())
                        .status("REJECTED")
                        .reasonCode(reasonCode)
                        .reasonDescription(reasonDescription)
                        .processedDateTime(LocalDateTime.now().toString())
                        .build())
                .build();
    }
}
```

---

### Paso 6: Servicio para Callback al Switch

```java
package com.subanco.integracion.service;

import com.subanco.integracion.dto.StatusReportDTO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwitchCallbackService {

    private final RestTemplate restTemplate;

    @Value("${switch.url}")
    private String switchUrl;

    @Value("${switch.callback.endpoint}")
    private String callbackEndpoint;

    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * NOTIFICA EL RESULTADO DE LA TRANSFERENCIA AL SWITCH
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * Este método DEBE ser llamado después de procesar la transferencia.
     * El Switch espera este callback para:
     * 1. Actualizar el estado de la transacción
     * 2. Registrar los movimientos contables
     * 3. Notificar al banco origen
     */
    public void notificarSwitch(StatusReportDTO resultado) {
        String url = switchUrl + callbackEndpoint;
        
        log.info("Enviando callback al Switch: {}", url);
        log.info("  InstructionId: {}", resultado.getBody().getOriginalInstructionId());
        log.info("  Status: {}", resultado.getBody().getStatus());
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<StatusReportDTO> request = new HttpEntity<>(resultado, headers);
            
            restTemplate.postForEntity(url, request, String.class);
            
            log.info("✅ Callback enviado exitosamente al Switch");
            
        } catch (Exception e) {
            log.error("❌ Error enviando callback al Switch: {}", e.getMessage());
            // Considerar implementar cola local de reintentos
            throw new RuntimeException("Error notificando al Switch: " + e.getMessage(), e);
        }
    }
}
```

---

## 📡 Endpoint de Callback del Switch

Los bancos deben enviar el resultado de la transferencia a:

```
POST http://34.16.106.7:8000/api/v1/transacciones/callback
```

### Request Body (StatusReportDTO)

```json
{
  "header": {
    "messageId": "uuid-nuevo-generado-por-ustedes",
    "creationDateTime": "2026-02-01T10:30:00",
    "respondingBankId": "BANTEC"
  },
  "body": {
    "originalInstructionId": "uuid-de-la-transaccion-original",
    "originalMessageId": "messageId-original",
    "status": "COMPLETED",
    "processedDateTime": "2026-02-01T10:30:00"
  }
}
```

### Caso de Rechazo

```json
{
  "header": {
    "messageId": "uuid-nuevo-generado-por-ustedes",
    "creationDateTime": "2026-02-01T10:30:00",
    "respondingBankId": "BANTEC"
  },
  "body": {
    "originalInstructionId": "uuid-de-la-transaccion-original",
    "originalMessageId": "messageId-original",
    "status": "REJECTED",
    "reasonCode": "AC03",
    "reasonDescription": "Cuenta destino no existe",
    "processedDateTime": "2026-02-01T10:30:00"
  }
}
```

### Códigos de Rechazo ISO 20022

| Código | Descripción | Cuándo Usar |
|--------|-------------|-------------|
| `AC03` | Cuenta inválida | La cuenta destino no existe |
| `AC06` | Cuenta bloqueada | La cuenta está bloqueada o inactiva |
| `AM04` | Fondos insuficientes | (No aplica en destino, pero incluido) |
| `MS03` | Error interno | Error técnico en el procesamiento |
| `RC01` | Referencia inválida | El instructionId no es válido |

---

## ✅ Checklist de Implementación

| # | Tarea | Estado |
|---|-------|--------|
| 1 | Agregar dependencias Maven (`spring-boot-starter-amqp`) | ⬜ |
| 2 | Configurar `application.yml` con credenciales RabbitMQ | ⬜ |
| 3 | Crear DTOs (`MensajeISO`, `StatusReportDTO`) | ⬜ |
| 4 | Configurar `Jackson2JsonMessageConverter` | ⬜ |
| 5 | Implementar `TransferenciaListener` | ⬜ |
| 6 | Implementar `SwitchCallbackService` | ⬜ |
| 7 | Probar conexión a RabbitMQ | ⬜ |
| 8 | Probar callback al Switch | ⬜ |

---

## 🧪 Pruebas

### 1. Verificar Conexión a RabbitMQ

```bash
# El log debe mostrar:
# "Started consuming from queue: q.bank.BANTEC.in"
```

### 2. Enviar Transferencia de Prueba

Desde Postman o curl al Switch:

```bash
curl -X POST http://34.16.106.7:8000/api/v1/transacciones \
  -H "Content-Type: application/json" \
  -H "apikey: SU_API_KEY" \
  -d '{
    "header": {
      "messageId": "test-123",
      "creationDateTime": "2026-02-01T10:00:00",
      "originatingBankId": "NEXUS"
    },
    "body": {
      "instructionId": "550e8400-e29b-41d4-a716-446655440000",
      "amount": {
        "currency": "USD",
        "value": 100.00
      },
      "debtor": {
        "name": "Juan Pérez",
        "accountId": "1234567890"
      },
      "creditor": {
        "name": "María García",
        "accountId": "0987654321",
        "targetBankId": "BANTEC"
      }
    }
  }'
```

### 3. Verificar que llegó a la Cola

El log de su banco debe mostrar:
```
TRANSFERENCIA RECIBIDA via RabbitMQ
  InstructionId: 550e8400-e29b-41d4-a716-446655440000
  Banco Origen: NEXUS
  Monto: 100.00 USD
```

### 4. Verificar Callback

```
✅ Callback enviado al Switch: COMPLETED
```

---

## 📞 Soporte

Para dudas técnicas o solicitud de credenciales:

| Tipo | Contacto |
|------|----------|
| Credenciales RabbitMQ | Solicitar a DIGICONECU |
| Problemas de conexión | soporte@digiconecu.ec |
| Documentación técnica | Este documento |

---

**Versión:** 2.0.0  
**Última actualización:** 2026-02-01  
**Cambio principal:** Migración de flujo síncrono a asíncrono con callback HTTP
