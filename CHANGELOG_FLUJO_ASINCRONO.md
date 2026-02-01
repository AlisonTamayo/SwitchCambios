# 📝 CHANGELOG - Migración a Flujo Asíncrono con RabbitMQ
## Switch Transaccional DIGICONECU - Febrero 2026

---

## 👥 Para los Integrantes del Equipo

Este documento explica **QUÉ cambios se hicieron**, **POR QUÉ** y **CÓMO mejoran** el sistema. Léanlo antes de revisar el código.

---

## 🎯 OBJETIVO DEL CAMBIO

### El Problema Anterior (Flujo Síncrono)

```
ANTES:
┌──────────────┐     HTTP POST      ┌──────────┐     HTTP POST      ┌──────────────┐
│ Banco Origen │ ──────────────────►│  Switch  │ ──────────────────►│ Banco Destino│
│              │                    │          │                    │              │
│              │ ◄──────────────────│          │ ◄──────────────────│              │
│              │    HTTP 200/201    │          │    HTTP 200/201    │              │
└──────────────┘                    └──────────┘                    └──────────────┘
       ▲                                                                   │
       │                                                                   │
       │               ⏳ BLOQUEADO 1-10 segundos                          │
       │                   esperando respuesta                             │
       └───────────────────────────────────────────────────────────────────┘
```

**Problemas:**
- ❌ El banco origen quedaba **BLOQUEADO** esperando respuesta
- ❌ Si el banco destino estaba caído, la transferencia **FALLABA**
- ❌ Si había timeout, no se sabía si la transferencia **se procesó o no**
- ❌ Alto **acoplamiento** entre servicios

### La Solución Implementada (Flujo Asíncrono)

```
AHORA:
┌──────────────┐  HTTP POST   ┌──────────┐  RabbitMQ   ┌──────────────┐
│ Banco Origen │ ────────────►│  Switch  │ ──────────► │ Banco Destino│
│              │              │          │             │              │
│              │ ◄────────────│          │             │              │
│              │  202 Accepted│          │ ◄───────────│              │
└──────────────┘   (~100ms)   │          │  Callback   │              │
       ▲                      │          │   HTTP      └──────────────┘
       │                      │          │
       │  Webhook (resultado) │          │
       └──────────────────────┴──────────┘
```

**Beneficios:**
- ✅ Banco origen recibe respuesta **INMEDIATA** (202 Accepted)
- ✅ Si banco destino está caído, mensaje **QUEDA EN COLA**
- ✅ Resultado llega **vía callback**, sin ambigüedad
- ✅ **Desacoplamiento** total entre servicios

---

## 📁 ARCHIVOS MODIFICADOS

### 1️⃣ `TransaccionServicio.java`

**Ubicación:** `MSNucleoSwitch/src/main/java/com/bancario/nucleo/servicio/`

**Cambios realizados:**

| Líneas | Cambio | Razón |
|--------|--------|-------|
| 54 | Inyección de `MensajeriaServicio` | Para poder publicar mensajes a RabbitMQ |
| 206-232 | Reemplazo del bloque de reintentos HTTP | Ahora publica a RabbitMQ en lugar de hacer HTTP directo |
| 216 | Estado cambia a `QUEUED` | Nuevo estado que indica "en cola, esperando procesamiento" |

**Código ANTES:**
```java
// Bloque de reintentos HTTP (60+ líneas)
int[] tiemposEspera = { 0, 800, 2000, 4000 };
for (int intento = 0; intento < tiemposEspera.length; intento++) {
    // ... HTTP directo al banco destino
    restTemplate.postForEntity(urlWebhook, request, String.class);
    // ... esperar respuesta
}
```

**Código AHORA:**
```java
// Publicación asíncrona a RabbitMQ (26 líneas)
log.info("RabbitMQ: Publicando transferencia a cola del banco destino: {}", bicDestino);
mensajeriaServicio.publicarTransferencia(bicDestino, iso);
tx.setEstado("QUEUED");
transaccionRepositorio.save(tx);
```

---

### 2️⃣ `TransaccionControlador.java`

**Ubicación:** `MSNucleoSwitch/src/main/java/com/bancario/nucleo/controlador/`

**Cambios realizados:**

| Líneas | Cambio | Razón |
|--------|--------|-------|
| 49-54 | Nuevo bloque para estado `QUEUED` | Retornar HTTP 202 Accepted en lugar de 201 Created |

**Código agregado:**
```java
// FLUJO ASÍNCRONO: HTTP 202 Accepted para transacciones encoladas
if ("QUEUED".equals(response.getEstado())) {
    log.info("Transacción {} encolada. Retornando HTTP 202 Accepted", response.getIdInstruccion());
    return new ResponseEntity<>(response, HttpStatus.ACCEPTED);
}
```

**¿Por qué HTTP 202?**
- HTTP 201 = "Recurso CREADO y listo"
- HTTP 202 = "Solicitud ACEPTADA, pero procesamiento en progreso"

---

## 📁 ARCHIVOS NUEVOS CREADOS

### 3️⃣ `StatusReportDTO.java` (NUEVO)

**Ubicación:** `MSNucleoSwitch/src/main/java/com/bancario/nucleo/dto/`

**Propósito:** DTO para recibir el resultado del procesamiento desde los bancos destino.

**Estructura:**
```java
@Data
public class StatusReportDTO {
    private Header header;  // respondingBankId, messageId, timestamp
    private Body body;      // originalInstructionId, status (COMPLETED/REJECTED)
}
```

**¿Por qué se creó?**
- Los bancos destino necesitan una estructura estándar para notificar el resultado
- Basado en ISO 20022 pacs.002 (FIToFIPaymentStatusReport)

---

### 4️⃣ `CallbackControlador.java` (NUEVO)

**Ubicación:** `MSNucleoSwitch/src/main/java/com/bancario/nucleo/controlador/`

**Propósito:** Endpoint para que los bancos destino envíen el resultado del procesamiento.

**Endpoint expuesto:**
```
POST /api/v1/transacciones/callback
```

**¿Por qué se creó?**
- El Switch necesita un punto de entrada para recibir los resultados
- Los bancos destino llaman aquí después de procesar la transferencia

---

### 5️⃣ `CallbackServicio.java` (NUEVO)

**Ubicación:** `MSNucleoSwitch/src/main/java/com/bancario/nucleo/servicio/`

**Propósito:** Procesar el callback del banco destino y completar el ciclo de la transacción.

**Responsabilidades:**
1. Buscar la transacción original por `instructionId`
2. Validar que esté en estado `QUEUED`
3. Si `COMPLETED`: Registrar CREDIT en Ledger, actualizar estado
4. Si `REJECTED`: Reversar DEBIT (devolver dinero al origen), actualizar estado
5. Notificar al banco origen vía webhook

**Flujo interno:**
```java
public TransaccionResponseDTO procesarCallback(StatusReportDTO statusReport) {
    // 1. Buscar transacción
    Transaccion tx = transaccionRepositorio.findById(instructionId);
    
    // 2. Validar estado
    if (!"QUEUED".equals(tx.getEstado())) throw new BusinessException(...);
    
    // 3. Procesar según resultado
    if ("COMPLETED".equals(status)) {
        procesarExito(tx, statusReport);  // Registra CREDIT
    } else if ("REJECTED".equals(status)) {
        procesarRechazo(tx, statusReport); // Reversa DEBIT
    }
    
    // 4. Notificar banco origen
    notificarBancoOrigen(tx, statusReport);
    
    return transaccionMapper.toDTO(saved);
}
```

---

## 📊 NUEVOS ESTADOS DE TRANSACCIÓN

| Estado | Significado | Cuándo |
|--------|-------------|--------|
| `RECEIVED` | Recibida, en validación | Al inicio del procesamiento |
| **`QUEUED`** ⭐ | **Publicada a RabbitMQ** | **NUEVO - Después de publicar a cola** |
| `COMPLETED` | Procesada exitosamente | Después de callback exitoso |
| `REJECTED` | Rechazada por banco destino | Después de callback con rechazo |
| `FAILED` | Error en validación | Si hay error antes de encolar |

---

## 🏗️ ARQUITECTURA ACTUALIZADA

### Componentes del Switch

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            MSNucleoSwitch                                   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         CONTROLADORES                               │    │
│  │  ┌─────────────────────┐   ┌─────────────────────┐                  │    │
│  │  │TransaccionControlador│   │ CallbackControlador │ ◄── NUEVO       │    │
│  │  │POST /transacciones  │   │POST /callback       │                  │    │
│  │  └──────────┬──────────┘   └──────────┬──────────┘                  │    │
│  └─────────────┼─────────────────────────┼─────────────────────────────┘    │
│                │                         │                                   │
│  ┌─────────────▼─────────────────────────▼─────────────────────────────┐    │
│  │                          SERVICIOS                                   │    │
│  │  ┌───────────────────┐   ┌─────────────────┐   ┌─────────────────┐  │    │
│  │  │TransaccionServicio│   │MensajeriaServicio│   │ CallbackServicio│ ◄── NUEVO
│  │  │                   │──►│                 │   │                 │  │    │
│  │  │ • Valida          │   │ • Publica a     │   │ • Procesa       │  │    │
│  │  │ • Registra DEBIT  │   │   RabbitMQ      │   │   resultado     │  │    │
│  │  │ • Encola mensaje  │   │                 │   │ • Registra      │  │    │
│  │  │                   │   │                 │   │   CREDIT        │  │    │
│  │  │                   │   │                 │   │ • Notifica      │  │    │
│  │  │                   │   │                 │   │   origen        │  │    │
│  │  └───────────────────┘   └─────────────────┘   └─────────────────┘  │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                │                                                             │
│                ▼                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                      RABBITMQ (Amazon MQ)                            │   │
│  │  ex.transfers.tx → q.bank.NEXUS.in, q.bank.BANTEC.in, ...           │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## ✅ REQUISITOS FUNCIONALES CUMPLIDOS

| RF | Descripción | Estado |
|----|-------------|--------|
| RF-01 | Transferencias interbancarias | ✅ Soportado (ahora asíncrono) |
| RF-02 | Validación de cuentas | ✅ Sin cambios |
| RF-03 | Consulta de estado | ✅ Sin cambios |
| RF-04 | Historial de transacciones | ✅ Sin cambios |
| RF-05 | **Mensajería asíncrona** | ✅ **IMPLEMENTADO** |
| RF-06 | **Callback de bancos** | ✅ **IMPLEMENTADO** |

---

## 🔧 REQUISITOS NO FUNCIONALES CUMPLIDOS

| RNF | Descripción | Cómo se cumple |
|-----|-------------|----------------|
| RNF-01 | Alta disponibilidad | RabbitMQ persiste mensajes si banco destino está caído |
| RNF-02 | Resiliencia | Dead Letter Queues para mensajes fallidos |
| RNF-03 | Desacoplamiento | Bancos no dependen de disponibilidad instantánea |
| RNF-04 | Rendimiento | Banco origen recibe respuesta en ~100ms |
| RNF-05 | Trazabilidad | `instructionId` permite rastrear todo el flujo |

---

## 📋 DOCUMENTACIÓN CREADA

| Documento | Propósito | Audiencia |
|-----------|-----------|-----------|
| `GUIA_INTEGRACION_BANCOS_RABBITMQ.md` | Guía paso a paso para implementar | Desarrolladores de bancos |
| `DIAGRAMA_FLUJO_RABBITMQ.md` | Diagrama visual del flujo completo | Todo el equipo |
| `CHANGELOG_FLUJO_ASINCRONO.md` | Este documento - explica los cambios | Equipo de desarrollo |

---

## 🧪 CÓMO PROBAR

### 1. Verificar que compila

```bash
cd MSNucleoSwitch
mvn compile
```

### 2. Probar endpoint de callback (sin RabbitMQ)

```bash
# Primero crear una transacción de prueba
# Luego simular el callback del banco destino

curl -X POST http://localhost:8080/api/v1/transacciones/callback \
  -H "Content-Type: application/json" \
  -d '{
    "header": {
      "messageId": "TEST-001",
      "respondingBankId": "BANTEC"
    },
    "body": {
      "originalInstructionId": "UUID-DE-TX-EXISTENTE",
      "status": "COMPLETED"
    }
  }'
```

### 3. Verificar logs

Buscar en los logs:
```
FLUJO ASÍNCRONO: Mensaje encolado exitosamente
  InstructionId: xxx
  Estado: QUEUED (esperando callback del banco destino)
  Cola destino: q.bank.BANTEC.in
```

---

## ⚠️ NOTAS IMPORTANTES

1. **El flujo anterior (HTTP directo) ya NO existe**
   - Todo pasa por RabbitMQ ahora

2. **Los bancos DEBEN implementar el listener y callback**
   - Sin esto, las transferencias quedan en estado `QUEUED` indefinidamente

3. **Backward compatibility**
   - Los bancos que no implementen RabbitMQ no podrán recibir transferencias
   - Deben migrar usando la guía

4. **Variables de entorno necesarias**
   ```yaml
   RABBITMQ_HOST: b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws
   RABBITMQ_PORT: 5671
   RABBITMQ_USER: <asignado>
   RABBITMQ_PASSWORD: <asignado>
   ```

---

## 👨‍💻 AUTOR DE LOS CAMBIOS

- **Fecha:** 2026-02-01
- **Versión:** 2.0.0
- **Tipo de cambio:** Breaking Change (requiere actualización de bancos)

---

## 📞 PREGUNTAS FRECUENTES

### ¿Por qué cambiamos de HTTP directo a RabbitMQ?

**Respuesta:** Para desacoplar los servicios. Si un banco destino está caído, antes la transferencia fallaba inmediatamente. Ahora el mensaje queda en cola y el banco lo procesa cuando esté disponible.

### ¿Qué pasa si un banco no implementa el callback?

**Respuesta:** La transacción quedará en estado `QUEUED` indefinidamente. El banco origen no recibirá confirmación.

### ¿Podemos volver al flujo síncrono?

**Respuesta:** Sí, pero no se recomienda. Habría que revertir los cambios en `TransaccionServicio.java`.

### ¿Cómo sabe el Switch a qué cola enviar el mensaje?

**Respuesta:** El campo `creditor.targetBankId` en el mensaje pacs.008 determina el routing key. Si `targetBankId = "BANTEC"`, el mensaje va a `q.bank.BANTEC.in`.

### ¿Qué pasa si RabbitMQ está caído?

**Respuesta:** La transferencia falla con error MS03. El banco origen recibe un error y puede reintentar.

---

**FIN DEL DOCUMENTO**
