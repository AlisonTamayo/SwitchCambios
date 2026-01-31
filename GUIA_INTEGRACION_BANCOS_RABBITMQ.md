# 🐰 Guía de Integración de Bancos con RabbitMQ
## Switch Transaccional DIGICONECU - Sistema de Colas

---

## 📋 Resumen Ejecutivo

Este documento proporciona las instrucciones técnicas para que las entidades financieras participantes se integren con el sistema de mensajería asíncrona del Switch DIGICONECU utilizando **Amazon MQ (RabbitMQ)**.

**Beneficios de la integración:**
- ✅ **Alta disponibilidad**: Mensajes persistentes garantizan entrega incluso durante mantenimiento
- ✅ **Desacoplamiento**: Sin dependencia de disponibilidad instantánea
- ✅ **Resiliencia**: Reintentos automáticos con backoff exponencial
- ✅ **Auditoría**: Trazabilidad completa de mensajes

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

> ⚠️ **IMPORTANTE**: El puerto 5672 (sin encriptación) está **deshabilitado**. Es obligatorio usar el puerto 5671 con SSL/TLS.

### Usuarios por Entidad

| Entidad | Usuario RabbitMQ | Cola Asignada |
|---------|------------------|---------------|
| Nexus | `nexus` | `q.bank.NEXUS.in` |
| Bantec | `bantec` | `q.bank.BANTEC.in` |
| ArcBank | `arcbank` | `q.bank.ARCBANK.in` |
| Ecusol | `ecusol` | `q.bank.ECUSOL.in` |

---

## 🏗️ Arquitectura del Flujo (Direct Exchange)

### 🎯 Regla de Oro de RabbitMQ

> ⚠️ **IMPORTANTE**: Los productores NUNCA escriben directamente en una cola. Los mensajes se envían a un **Exchange**, que decide a dónde va el mensaje basándose en el **Routing Key**.

**Tipo de Exchange:** `DIRECT` (Coincidencia Exacta)
- Si el `routingKey = "BANTEC"`, el mensaje va **SOLO** a la cola enlazada con `"BANTEC"`
- El routing key lo define el **Banco Origen** en el campo `creditor.targetBankId`

```
┌──────────────────────────────────────────────────────────────────────────────┐
│           FLUJO DIRECT EXCHANGE - TRANSFERENCIA INTERBANCARIA               │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│   [BANCO ORIGEN - NEXUS]                                                     │
│        │  POST /api/v1/transacciones                                         │
│        │  {                                                                  │
│        │    "header": { "originatingBankId": "NEXUS" },                       │
│        │    "body": {                                                        │
│        │      "creditor": {                                                  │
│        │        "targetBankId": "BANTEC"  ◄── ROUTING KEY (obligatorio)       │
│        │      }                                                              │
│        │    }                                                                │
│        │  }                                                                  │
│        ▼                                                                     │
│   [SWITCH DIGICONECU]                                                        │
│        │  1. Valida mensaje ISO y cuentas                                    │
│        │  2. Extrae routingKey = creditor.targetBankId = "BANTEC"            │
│        │  3. Registra en Ledger                                              │
│        │  4. Publica: rabbitTemplate.convertAndSend(exchange, "BANTEC", msg) │
│        ▼                                                                     │
│   [DIRECT EXCHANGE: ex.transfers.tx]                                         │
│        │  Regla: routingKey == bindingKey → enruta                           │
│        │  Binding: "BANTEC" → q.bank.BANTEC.in                               │
│        ▼                                                                     │
│   [COLA: q.bank.BANTEC.in] ◄── Su banco consume de aquí                       │
│        │                                                                     │
│        │  5. Banco destino procesa el depósito                               │
│        ▼                                                                     │
│   [BANCO DESTINO - BANTEC]                                                   │
│        │                                                                     │
│        │  6. HTTP Webhook de confirmación al origen                          │
│        ▼                                                                     │
│   [BANCO ORIGEN - NEXUS] ◄── Recibe confirmación                              │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Bindings del Direct Exchange

| Routing Key | Cola Destino | Banco |
|-------------|--------------|-------|
| `NEXUS` | `q.bank.NEXUS.in` | Nexus |
| `BANTEC` | `q.bank.BANTEC.in` | Bantec |
| `ARCBANK` | `q.bank.ARCBANK.in` | ArcBank |
| `ECUSOL` | `q.bank.ECUSOL.in` | Ecusol |

### Responsabilidades

| Actor | Rol | Acción |
|-------|-----|--------|
| **Banco Origen** | Productor | Define el `routingKey` en `creditor.targetBankId` |
| **Switch DIGICONECU** | Mediador/Publicador | Valida formato del routing key (enum `BancoDestino`) y publica al Exchange |
| **RabbitMQ (Direct Exchange)** | Enrutador | Enruta por coincidencia exacta del routing key |
| **Banco Destino** | Consumidor | Consume mensajes de su cola asignada `q.bank.{SU_BANCO}.in` |

---

## ⚙️ Configuración Técnica

### 1. Dependencias (Maven/Gradle)

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

---

### 2. Configuración `application.properties`

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

> 📌 **Consejo de Seguridad:** Nunca hardcodee las credenciales. Use variables de entorno o AWS Secrets Manager.

---

### 3. DTO de Mensaje (Estructura del Payload)

El banco origen debe enviar mensajes con la siguiente estructura ISO 20022.

> ⚠️ **CAMPO OBLIGATORIO**: El campo `creditor.targetBankId` es el **ROUTING KEY** que determina a qué banco se enrutará la transacción. Si este campo está vacío o es inválido, la transacción será rechazada.

#### Estructura Java
```java
@Data
public class TransferenciaDTO {
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
        private String targetBankId;        // ⚠️ ROUTING KEY - BIC destino (OBLIGATORIO)
    }
}
```

#### Ejemplo de Mensaje JSON (Enviado por Banco Origen)

```json
{
  "header": {
    "messageId": "MSG-550e8400-e29b-41d4-a716-446655440000",
    "creationDateTime": "2026-01-30T20:30:00Z",
    "originatingBankId": "NEXUS"
  },
  "body": {
    "instructionId": "550e8400-e29b-41d4-a716-446655440000",
    "endToEndId": "REF-CLIENTE-001",
    "amount": {
      "currency": "USD",
      "value": 1500.00
    },
    "debtor": {
      "name": "Juan Pérez",
      "accountId": "123456789012",
      "accountType": "CHECKING"
    },
    "creditor": {
      "name": "María García",
      "accountId": "987654321098",
      "accountType": "SAVINGS",
      "targetBankId": "BANTEC"
    },
    "remittanceInformation": "Pago por servicios profesionales"
  }
}
```

#### Valores Válidos para `targetBankId` (Routing Key)

| Valor | Banco Destino | Cola RabbitMQ |
|-------|---------------|---------------|
| `NEXUS` | Nexus | `q.bank.NEXUS.in` |
| `BANTEC` | Bantec | `q.bank.BANTEC.in` |
| `ARCBANK` | ArcBank | `q.bank.ARCBANK.in` |
| `ECUSOL` | Ecusol | `q.bank.ECUSOL.in` |

> 🚨 **Error BE01**: Si `targetBankId` contiene un valor no válido, el Switch rechazará la transacción con el código `BE01 - Routing key inválido`.

---

### 4. Implementación del Consumer (Listener)

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
    private final WebhookClient webhookClient;

    /**
     * Listener para recibir transferencias del Switch.
     * 
     * IMPORTANTE: Reemplace "q.bank.NEXUS.in" con su cola asignada:
     * - Nexus:  q.bank.NEXUS.in
     * - Bantec: q.bank.BANTEC.in
     * - ArcBank: q.bank.ARCBANK.in
     * - Ecusol: q.bank.ECUSOL.in
     */
    @RabbitListener(queues = "q.bank.NEXUS.in")
    public void recibirTransferencia(TransferenciaDTO mensaje) {
        log.info("Recibida transferencia: {} por ${}", 
                 mensaje.getBody().getInstructionId(),
                 mensaje.getBody().getAmount().getValue());
        
        try {
            // ═══════════════════════════════════════════════════════════
            // PASO 1: Validar cuenta beneficiaria en su Core Bancario
            // ═══════════════════════════════════════════════════════════
            String cuentaDestino = mensaje.getBody().getCreditor().getAccountId();
            if (!coreService.existeCuenta(cuentaDestino)) {
                log.error("Cuenta no existe: {}", cuentaDestino);
                // Rechazar sin reintentar - Cuenta inválida
                throw new AmqpRejectAndDontRequeueException("AC03 - Cuenta no existe");
            }
            
            // ═══════════════════════════════════════════════════════════
            // PASO 2: Procesar el depósito en el Core Bancario
            // ═══════════════════════════════════════════════════════════
            coreService.procesarDeposito(
                cuentaDestino,
                mensaje.getBody().getAmount().getValue(),
                mensaje.getBody().getCreditor().getName(),
                mensaje.getBody().getInstructionId()
            );
            
            log.info("Depósito procesado exitosamente: {}", 
                     mensaje.getBody().getInstructionId());
            
            // ═══════════════════════════════════════════════════════════
            // PASO 3: Confirmar al Banco Origen vía Webhook HTTP
            // ═══════════════════════════════════════════════════════════
            webhookClient.confirmarTransaccion(
                mensaje.getHeader().getOriginatingBankId(),
                mensaje.getBody().getInstructionId(),
                "COMPLETED"
            );
            
        } catch (CuentaNoExisteException e) {
            // Error de negocio - No reintentar
            log.error("Error de cuenta: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("AC03 - " + e.getMessage(), e);
            
        } catch (SaldoInsuficienteException e) {
            // Error de negocio - No reintentar
            log.error("Error de saldo: {}", e.getMessage());
            throw new AmqpRejectAndDontRequeueException("AM04 - " + e.getMessage(), e);
            
        } catch (Exception e) {
            // Error técnico - Spring aplicará reintentos automáticos
            // Después de 4 intentos fallidos, el mensaje irá al DLQ
            log.error("Error técnico procesando transferencia", e);
            throw e;  // Permite que Spring maneje los reintentos
        }
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

### Monitoreo de DLQ,  si se alcanza

Se recomienda implementar un listener secundario para alertar sobre mensajes en DLQ:

```java
@RabbitListener(queues = "q.bank.NEXUS.dlq")
public void procesarMensajeFallido(TransferenciaDTO mensaje) {
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
| **WRITE** | `^(ex\.transfers\.tx\|q\.bank\.{SU_BANCO}\..*)$` | Puede publicar y gestionar sus colas |

> ⚠️ Cualquier intento de acceder a colas de otro banco resultará en error `403 ACCESS_REFUSED`.

