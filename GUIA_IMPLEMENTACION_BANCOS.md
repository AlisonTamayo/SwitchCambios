# 🐰 Guía de Integración de Bancos con RabbitMQ
## Switch Transaccional DIGICONECU - Sistema de Colas Asíncrono

---

> [!IMPORTANT]
> **CAMBIO CRÍTICO (v2.0.0 - Febrero 2026):** El flujo de transferencias ha migrado de **síncrono** (HTTP directo) a **asíncrono** (RabbitMQ + Callback). Los bancos deben actualizar su integración siguiendo esta guía.

---

## 📋 Resumen Ejecutivo

Este documento proporciona las instrucciones técnicas para que las entidades financieras participantes se integren con el sistema de mensajería asíncrona del Switch DIGICONECU utilizando **Amazon MQ (RabbitMQ)**.

### ¿Qué cambió?

| Aspecto | Antes (Síncrono) | Ahora (Asíncrono) |
|---------|------------------|-------------------|
| **Banco Origen recibe** | HTTP 201 con estado final | **HTTP 202 Accepted** con estado `QUEUED` |
| **Banco Destino recibe** | HTTP POST directo del Switch | **Mensaje en cola RabbitMQ** |
| **Confirmación final** | En la misma respuesta HTTP | **Callback HTTP al Switch + Webhook al origen** |
| **Tiempo de respuesta** | 1-10 segundos (bloqueado) | **~100ms** (inmediato) |

### Beneficios de la integración:
- ✅ **Alta disponibilidad**: Mensajes persistentes garantizan entrega incluso durante mantenimiento
- ✅ **Desacoplamiento**: Sin dependencia de disponibilidad instantánea
- ✅ **Resiliencia**: Reintentos automáticos con backoff exponencial
- ✅ **Auditoría**: Trazabilidad completa de mensajes

---

## 📊 Diagrama del Flujo Asíncrono

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                          FLUJO ASÍNCRONO - 5 FASES                                          │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                              │
│   FASE 1-2: SOLICITUD Y ENCOLAMIENTO                                                        │
│   ─────────────────────────────────────────────────────────────────────────────────────────  │
│                                                                                              │
│   [BANCO ORIGEN]  ──── ① POST /api/v1/transacciones ────►  [SWITCH DIGICONECU]              │
│        │                                                          │                          │
│        │                                                          │ • Valida mensaje ISO     │
│        │                                                          │ • Registra DEBIT         │
│        │                                                          │ • Publica a RabbitMQ     │
│        │                                                          │ • Estado: QUEUED         │
│        │                                                          ▼                          │
│        │ ◄───────────── ② HTTP 202 Accepted ─────────────  (Respuesta inmediata ~100ms)     │
│        │                   { "estado": "QUEUED" }                                            │
│        │                                                                                     │
│   ─────────────────────────────────────────────────────────────────────────────────────────  │
│   FASE 3: ENRUTAMIENTO RABBITMQ                                                              │
│   ─────────────────────────────────────────────────────────────────────────────────────────  │
│                                                                                              │
│   [SWITCH] ────► [ex.transfers.tx] ────routingKey="BANTEC"────► [q.bank.BANTEC.in]          │
│                   (Direct Exchange)                              (Cola del Banco)           │
│                                                                         │                    │
│   ─────────────────────────────────────────────────────────────────────────────────────────  │
│   FASE 4: PROCESAMIENTO EN BANCO DESTINO                                                     │
│   ─────────────────────────────────────────────────────────────────────────────────────────  │
│                                                                         │                    │
│                                                                         ▼                    │
│                                                              [BANCO DESTINO]                │
│                                                                   │                          │
│                                                                   │ • @RabbitListener        │
│                                                                   │ • Valida cuenta          │
│                                                                   │ • Procesa depósito       │
│                                                                   │ • Construye callback     │
│                                                                   ▼                          │
│   ─────────────────────────────────────────────────────────────────────────────────────────  │
│   FASE 5: CALLBACK Y NOTIFICACIÓN                                                            │
│   ─────────────────────────────────────────────────────────────────────────────────────────  │
│                                                                   │                          │
│              [SWITCH DIGICONECU] ◄──── ④ POST /callback ─────────┘                          │
│                   │                                                                          │
│                   │ • Registra CREDIT en Ledger                                              │
│                   │ • Actualiza estado: COMPLETED o REJECTED                                 │
│                   │ • Notifica al banco origen vía webhook                                   │
│                   ▼                                                                          │
│   [BANCO ORIGEN] ◄───────────── ⑤ Webhook con resultado final ──────                        │
│        │                                                                                     │
│        ▼                                                                                     │
│   Notifica al cliente: "Tu transferencia fue completada" ✅                                  │
│                                                                                              │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🔐 Credenciales de Conexión

| Parámetro | Valor |
|-----------|-------|
| **Endpoint AMQPS** | `amqps://b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws:5671` |
| **Consola Web** | `https://b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws/` |
| **Puerto** | `5671` (SSL obligatorio) |
| **Virtual Host** | `/` |
| **Protocolo** | AMQP 0-9-1 sobre TLS 1.2 |
| **Usuario** | Asignado por DIGICONECU (ver tabla abajo) |
| **Contraseña** | Solicitar a DIGICONECU vía canal seguro |

> [!WARNING]
> El puerto 5672 (sin encriptación) está **deshabilitado**. Es obligatorio usar el puerto 5671 con SSL/TLS.

### Usuarios y Colas por Entidad

| Entidad | Usuario RabbitMQ | Cola Asignada | Dead Letter Queue |
|---------|------------------|---------------|-------------------|
| Nexus | `nexus` | `q.bank.NEXUS.in` | `q.bank.NEXUS.dlq` |
| Bantec | `bantec` | `q.bank.BANTEC.in` | `q.bank.BANTEC.dlq` |
| ArcBank | `arcbank` | `q.bank.ARCBANK.in` | `q.bank.ARCBANK.dlq` |
| Ecusol | `ecusol` | `q.bank.ECUSOL.in` | `q.bank.ECUSOL.dlq` |

---

## 🏗️ Arquitectura del Flujo (Direct Exchange)

### 🎯 Regla de Oro de RabbitMQ

> [!IMPORTANT]
> Los productores NUNCA escriben directamente en una cola. Los mensajes se envían a un **Exchange**, que decide a dónde va el mensaje basándose en el **Routing Key**.

**Tipo de Exchange:** `DIRECT` (Coincidencia Exacta)
- Si el `routingKey = "BANTEC"`, el mensaje va **SOLO** a la cola enlazada con `"BANTEC"`
- El routing key lo define el **Banco Origen** en el campo `creditor.targetBankId`

### Bindings del Direct Exchange

| Routing Key | Cola Destino | Banco |
|-------------|--------------|-------|
| `NEXUS` | `q.bank.NEXUS.in` | Nexus |
| `BANTEC` | `q.bank.BANTEC.in` | Bantec |
| `ARCBANK` | `q.bank.ARCBANK.in` | ArcBank |
| `ECUSOL` | `q.bank.ECUSOL.in` | Ecusol |

### Responsabilidades por Actor

| Actor | Rol | Acción |
|-------|-----|--------|
| **Banco Origen** | Productor | Define el `routingKey` en `creditor.targetBankId` |
| **Switch DIGICONECU** | Mediador/Publicador | Valida formato del routing key y publica al Exchange |
| **RabbitMQ (Direct Exchange)** | Enrutador | Enruta por coincidencia exacta del routing key |
| **Banco Destino** | Consumidor | Consume mensajes de su cola asignada `q.bank.{SU_BANCO}.in` |
| **Banco Destino** | Notificador | **NUEVO:** Envía callback HTTP al Switch con resultado |

---

## 👥 Cambios Requeridos por Rol

### 📤 Para Banco ORIGEN (quien envía dinero)

#### Cambio 1: Manejar HTTP 202 Accepted

El Switch ahora retorna `HTTP 202 Accepted` con estado `QUEUED` en lugar de esperar el resultado final.

```java
// ANTES: Esperaba HTTP 201 con estado COMPLETED
ResponseEntity<TransaccionResponseDTO> response = restTemplate.postForEntity(
    switchUrl + "/api/v1/transacciones", 
    mensajeIso, 
    TransaccionResponseDTO.class
);
if (response.getStatusCode() == HttpStatus.CREATED && 
    "COMPLETED".equals(response.getBody().getEstado())) {
    // Transferencia exitosa
}

// AHORA: Acepta HTTP 202 con estado QUEUED
ResponseEntity<TransaccionResponseDTO> response = restTemplate.postForEntity(
    switchUrl + "/api/v1/transacciones", 
    mensajeIso, 
    TransaccionResponseDTO.class
);
if (response.getStatusCode() == HttpStatus.ACCEPTED && 
    "QUEUED".equals(response.getBody().getEstado())) {
    // Transferencia encolada - esperando resultado final
    String instructionId = response.getBody().getIdInstruccion();
    // Iniciar polling o esperar webhook
}
```

#### Cambio 2: Implementar recepción de webhook (RECOMENDADO)

Exponer un endpoint para recibir la notificación del resultado final:

```java
@RestController
@RequestMapping("/api/incoming")
public class WebhookController {

    @PostMapping("/transfer-result")
    public ResponseEntity<?> recibirResultado(@RequestBody StatusReportDTO resultado) {
        log.info("Resultado recibido para instrucción: {}", 
                 resultado.getBody().getOriginalInstructionId());
        
        if ("COMPLETED".equals(resultado.getBody().getStatus())) {
            // Notificar al cliente: "Tu transferencia fue exitosa"
            notificarCliente(resultado.getBody().getOriginalInstructionId(), true);
        } else if ("REJECTED".equals(resultado.getBody().getStatus())) {
            // Notificar al cliente: "Tu transferencia fue rechazada"
            // El dinero ya fue devuelto automáticamente
            notificarCliente(resultado.getBody().getOriginalInstructionId(), false);
        }
        
        return ResponseEntity.ok().build();
    }
}
```

#### Cambio 3: Implementar Polling (ALTERNATIVA)

Si no puede recibir webhooks, implemente polling:

```java
// Polling cada 1.5 segundos, máximo 10 intentos
public void esperarResultado(String instructionId) {
    int intentos = 0;
    while (intentos < 10) {
        Thread.sleep(1500);
        
        TransaccionResponseDTO estado = restTemplate.getForObject(
            switchUrl + "/api/v1/transacciones/" + instructionId,
            TransaccionResponseDTO.class
        );
        
        if ("COMPLETED".equals(estado.getEstado())) {
            // ✅ Éxito
            return;
        }
        if ("REJECTED".equals(estado.getEstado()) || "FAILED".equals(estado.getEstado())) {
            // ❌ Fallo
            return;
        }
        // Estado aún QUEUED, seguir esperando
        intentos++;
    }
    // ⚠️ Timeout - mostrar "En proceso" al usuario
}
```

---

### 📥 Para Banco DESTINO (quien recibe dinero)

> [!CAUTION]
> Los bancos destino **DEBEN** implementar tanto el **Consumer de RabbitMQ** como el **Callback HTTP al Switch**. Sin estos componentes, las transferencias quedarán en estado `QUEUED` indefinidamente.

#### Paso 1: Agregar dependencia RabbitMQ

**Maven (`pom.xml`):**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**Gradle (`build.gradle`):**
```groovy
implementation 'org.springframework.boot:spring-boot-starter-amqp'
```

#### Paso 2: Configurar conexión RabbitMQ

**`application.properties`:**
```properties
# ========================================
# CONFIGURACION RABBITMQ (Amazon MQ)
# ========================================
spring.rabbitmq.host=b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws
spring.rabbitmq.port=5671
spring.rabbitmq.username=${RABBITMQ_USER}
spring.rabbitmq.password=${RABBITMQ_PASSWORD}
spring.rabbitmq.virtual-host=/

# SSL/TLS Obligatorio (puerto 5671)
spring.rabbitmq.ssl.enabled=true
spring.rabbitmq.ssl.algorithm=TLSv1.2

# Politica de reintentos
spring.rabbitmq.listener.simple.acknowledge-mode=auto
spring.rabbitmq.listener.simple.default-requeue-rejected=false
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=4
spring.rabbitmq.listener.simple.retry.initial-interval=800ms
spring.rabbitmq.listener.simple.retry.multiplier=2.5
spring.rabbitmq.listener.simple.retry.max-interval=5000ms
```

**Credenciales por banco:**
| Usuario | Password |
|---------|----------|
| `nexus` | `nexuspass` |
| `bantec` | `bantecpass` |
| `arcbank` | `arcbankpass` |
| `ecusol` | `ecusolpass` |

> [!TIP]
> Nunca hardcodee las credenciales. Use variables de entorno o AWS Secrets Manager.

#### Paso 3: Implementar Consumer + Callback

```java
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferenciaListener {

    private final CoreBancarioService coreService;
    private final RestTemplate restTemplate;
    
    // URL del endpoint de callback del Switch
    private static final String SWITCH_CALLBACK_URL = 
        "http://34.16.106.7:8000/api/v1/transacciones/callback";

    /**
     * Listener para recibir transferencias del Switch.
     * 
     * IMPORTANTE: Reemplace "q.bank.BANTEC.in" con su cola asignada:
     * - Nexus:  q.bank.NEXUS.in
     * - Bantec: q.bank.BANTEC.in
     * - ArcBank: q.bank.ARCBANK.in
     * - Ecusol: q.bank.ECUSOL.in
     */
    @RabbitListener(queues = "q.bank.BANTEC.in")  // ← CAMBIAR POR SU COLA
    public void recibirTransferencia(MensajeISO mensaje) {
        String instructionId = mensaje.getBody().getInstructionId();
        log.info("Recibida transferencia: {} por ${}", 
                 instructionId,
                 mensaje.getBody().getAmount().getValue());
        
        StatusReportDTO callback;
        
        try {
            // ═══════════════════════════════════════════════════════════
            // PASO 1: Validar cuenta beneficiaria en su Core Bancario
            // ═══════════════════════════════════════════════════════════
            String cuentaDestino = mensaje.getBody().getCreditor().getAccountId();
            if (!coreService.existeCuenta(cuentaDestino)) {
                log.error("Cuenta no existe: {}", cuentaDestino);
                // Enviar callback de RECHAZO
                callback = construirCallback(instructionId, "REJECTED", "AC03", "Cuenta no existe");
                enviarCallbackAlSwitch(callback);
                throw new AmqpRejectAndDontRequeueException("AC03 - Cuenta no existe");
            }
            
            // ═══════════════════════════════════════════════════════════
            // PASO 2: Procesar el depósito en el Core Bancario
            // ═══════════════════════════════════════════════════════════
            coreService.procesarDeposito(
                cuentaDestino,
                mensaje.getBody().getAmount().getValue(),
                mensaje.getBody().getCreditor().getName(),
                instructionId
            );
            
            log.info("Depósito procesado exitosamente: {}", instructionId);
            
            // ═══════════════════════════════════════════════════════════
            // PASO 3: NUEVO - Enviar callback EXITOSO al Switch
            // ═══════════════════════════════════════════════════════════
            callback = construirCallback(instructionId, "COMPLETED", null, null);
            enviarCallbackAlSwitch(callback);
            
        } catch (CuentaNoExisteException e) {
            // Error de negocio - No reintentar
            log.error("Error de cuenta: {}", e.getMessage());
            callback = construirCallback(instructionId, "REJECTED", "AC03", e.getMessage());
            enviarCallbackAlSwitch(callback);
            throw new AmqpRejectAndDontRequeueException("AC03 - " + e.getMessage(), e);
            
        } catch (CuentaBloqueadaException e) {
            // Error de negocio - No reintentar
            log.error("Cuenta bloqueada: {}", e.getMessage());
            callback = construirCallback(instructionId, "REJECTED", "AG01", e.getMessage());
            enviarCallbackAlSwitch(callback);
            throw new AmqpRejectAndDontRequeueException("AG01 - " + e.getMessage(), e);
            
        } catch (Exception e) {
            // Error técnico - Spring aplicará reintentos automáticos
            // Después de 4 intentos fallidos, el mensaje irá al DLQ
            log.error("Error técnico procesando transferencia", e);
            throw e;  // Permite que Spring maneje los reintentos
        }
    }
    
    /**
     * Construye el objeto de callback para enviar al Switch
     */
    private StatusReportDTO construirCallback(String instructionId, String status, 
                                              String reasonCode, String reasonDescription) {
        return StatusReportDTO.builder()
            .header(StatusReportDTO.Header.builder()
                .messageId("RESP-" + UUID.randomUUID().toString())
                .respondingBankId("BANTEC")  // ← CAMBIAR POR SU BIC
                .creationDateTime(LocalDateTime.now().toString())
                .build())
            .body(StatusReportDTO.Body.builder()
                .originalInstructionId(UUID.fromString(instructionId))
                .status(status)
                .reasonCode(reasonCode)
                .reasonDescription(reasonDescription)
                .processedDateTime(LocalDateTime.now().toString())
                .build())
            .build();
    }
    
    /**
     * NUEVO: Envía el resultado al Switch via callback HTTP
     */
    private void enviarCallbackAlSwitch(StatusReportDTO callback) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<StatusReportDTO> request = new HttpEntity<>(callback, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                SWITCH_CALLBACK_URL, 
                request, 
                String.class
            );
            
            log.info("Callback enviado al Switch. Status: {}, Response: {}", 
                     response.getStatusCode(), response.getBody());
                     
        } catch (Exception e) {
            log.error("Error enviando callback al Switch: {}", e.getMessage());
            // El Switch puede usar polling o el banco puede reintentar
            throw new RuntimeException("Error en callback: " + e.getMessage(), e);
        }
    }
}
```

---

## 📋 DTOs Requeridos

### StatusReportDTO (Para Callback)

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusReportDTO {
    private Header header;
    private Body body;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        private String messageId;           // ID único de la respuesta
        private String creationDateTime;    // Timestamp ISO 8601
        private String respondingBankId;    // BIC del banco que responde
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        private UUID originalInstructionId; // ID de la transacción original
        private String originalMessageId;   // MessageId original (opcional)
        private String status;              // COMPLETED o REJECTED
        private String reasonCode;          // Código ISO si rechazada (AC03, AG01, etc.)
        private String reasonDescription;   // Descripción del error si rechazada
        private String processedDateTime;   // Timestamp de procesamiento
    }
}
```

### MensajeISO (Estructura del Payload Recibido)

```java
@Data
public class MensajeISO {
    private Header header;
    private Body body;
    
    @Data
    public static class Header {
        private String messageId;           // ID único del mensaje
        private String creationDateTime;    // Timestamp ISO 8601
        private String originatingBankId;   // BIC del banco origen (quien envía)
    }
    
    @Data
    public static class Body {
        private String instructionId;       // UUID de la instrucción
        private String endToEndId;          // Referencia del cliente
        private Amount amount;
        private Debtor debtor;              // Ordenante
        private Creditor creditor;          // Beneficiario
        private String remittanceInformation; // Concepto
    }
    
    @Data
    public static class Amount {
        private String currency;            // "USD"
        private BigDecimal value;           // Monto
    }
    
    @Data
    public static class Debtor {
        private String name;
        private String accountId;
        private String accountType;         // CHECKING, SAVINGS
    }
    
    @Data
    public static class Creditor {
        private String name;
        private String accountId;
        private String accountType;
        private String targetBankId;        // ROUTING KEY - BIC destino
    }
}
```

---

## 🔄 Política de Reintentos

El sistema está configurado con **backoff exponencial**:

| Intento | Delay | Tiempo Acumulado |
|---------|-------|------------------|
| 1 (inicial) | 0ms | 0ms |
| 2 | 800ms | 800ms |
| 3 | 2,000ms | 2.8s |
| 4 | 5,000ms | 7.8s |

Después del **4to intento fallido**, el mensaje se mueve automáticamente a la **Dead Letter Queue (DLQ)**.

---

## ☠️ Dead Letter Queue (DLQ)

Los mensajes que fallan después de todos los reintentos se mueven a:

| Cola Principal | Cola DLQ |
|----------------|----------|
| `q.bank.NEXUS.in` | `q.bank.NEXUS.dlq` |
| `q.bank.BANTEC.in` | `q.bank.BANTEC.dlq` |
| `q.bank.ARCBANK.in` | `q.bank.ARCBANK.dlq` |
| `q.bank.ECUSOL.in` | `q.bank.ECUSOL.dlq` |

### Monitoreo de DLQ

Se recomienda implementar un listener secundario para alertar sobre mensajes en DLQ:

```java
@RabbitListener(queues = "q.bank.BANTEC.dlq")
public void procesarMensajeFallido(MensajeISO mensaje) {
    log.error("ALERTA: Mensaje en DLQ - InstructionId: {}", 
              mensaje.getBody().getInstructionId());
    
    // Enviar alerta al equipo de operaciones
    alertService.enviarAlerta(
        "Transferencia fallida requiere intervención manual",
        mensaje.getBody().getInstructionId()
    );
}
```

---

## 🔒 Permisos y Seguridad

Cada banco tiene permisos restringidos mediante ACLs:

| Permiso | Expresión Regular | Descripción |
|---------|-------------------|-------------|
| **READ** | `^q\.bank\.{SU_BANCO}\..*` | Solo puede leer de sus colas |
| **WRITE** | `^(ex\.transfers\.tx\|q\.bank\.{SU_BANCO}\..*)\$` | Puede publicar y gestionar sus colas |

> [!WARNING]
> Cualquier intento de acceder a colas de otro banco resultará en error `403 ACCESS_REFUSED`.

---

## 🧪 Endpoints para Pruebas

### 1. Enviar Transferencia (Banco Origen → Switch)

```bash
POST http://34.16.106.7:8000/api/v1/transacciones
Content-Type: application/json
apikey: SU_API_KEY

{
  "header": {
    "messageId": "MSG-TEST-001",
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
}
```

**Respuesta esperada (HTTP 202 Accepted):**
```json
{
  "idInstruccion": "550e8400-e29b-41d4-a716-446655440000",
  "estado": "QUEUED"
}
```

### 2. Enviar Callback (Banco Destino → Switch)

```bash
POST http://34.16.106.7:8000/api/v1/transacciones/callback
Content-Type: application/json

{
  "header": {
    "messageId": "RESP-001",
    "creationDateTime": "2026-02-01T10:05:00",
    "respondingBankId": "BANTEC"
  },
  "body": {
    "originalInstructionId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "COMPLETED",
    "processedDateTime": "2026-02-01T10:05:00"
  }
}
```

**Respuesta esperada (HTTP 200 OK):**
```json
{
  "idInstruccion": "550e8400-e29b-41d4-a716-446655440000",
  "estado": "COMPLETED"
}
```

### 3. Consultar Estado de Transacción (Polling)

```bash
GET http://34.16.106.7:8000/api/v1/transacciones/550e8400-e29b-41d4-a716-446655440000
```

### 4. Health Check del Callback

```bash
GET http://34.16.106.7:8000/api/v1/transacciones/callback/health
```

---

## ✅ Checklist de Implementación

### Para Banco ORIGEN

- [ ] Actualizar código para manejar HTTP 202 Accepted con estado QUEUED
- [ ] Implementar polling para consultar estado final
- [ ] (Recomendado) Exponer webhook para recibir notificaciones del Switch
- [ ] Actualizar UI para mostrar "En proceso..." mientras estado es QUEUED

### Para Banco DESTINO

- [ ] Agregar dependencia `spring-boot-starter-amqp`
- [ ] Configurar `application.properties` con credenciales RabbitMQ
- [ ] Implementar `@RabbitListener` para consumir de su cola asignada
- [ ] Implementar método `enviarCallbackAlSwitch()` para notificar resultado
- [ ] Crear DTOs: `MensajeISO` y `StatusReportDTO`
- [ ] (Recomendado) Implementar listener para DLQ y alertas

---

## 📞 Preguntas Frecuentes (FAQ)

### ¿Por qué cambiaron de HTTP directo a RabbitMQ?

**Respuesta:** Para desacoplar los servicios. Si un banco destino está caído, antes la transferencia fallaba inmediatamente. Ahora el mensaje queda en cola y el banco lo procesa cuando esté disponible.

### ¿Qué pasa si un banco no implementa el callback?

**Respuesta:** La transacción quedará en estado `QUEUED` indefinidamente. El banco origen no recibirá confirmación y deberá usar polling.

### ¿Cómo sabe el Switch a qué cola enviar el mensaje?

**Respuesta:** El campo `creditor.targetBankId` en el mensaje pacs.008 determina el routing key. Si `targetBankId = "BANTEC"`, el mensaje va a `q.bank.BANTEC.in`.

### ¿Qué pasa si RabbitMQ está caído?

**Respuesta:** La transferencia falla con error MS03. El banco origen recibe un error y puede reintentar.

### ¿Puedo seguir usando el webhook HTTP anterior?

**Respuesta:** No. El flujo HTTP directo fue reemplazado completamente por RabbitMQ. Debe migrar a la nueva arquitectura.

---

## 📝 Historial de Cambios

| Versión | Fecha | Cambio |
|---------|-------|--------|
| v2.0.0 | 2026-02-01 | Migración a flujo asíncrono con RabbitMQ y Callback |
| v1.0.0 | 2026-01-15 | Versión inicial con flujo síncrono HTTP |

---

**FIN DEL DOCUMENTO**
