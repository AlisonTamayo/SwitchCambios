# 🐰 Estructura del Sistema de Colas - RabbitMQ

## 📋 Resumen General

El sistema de mensajería utiliza **Amazon MQ (RabbitMQ)** para la comunicación asíncrona entre el Switch DIGICONECU y los bancos del ecosistema.

**Broker:** `switch-rabbitmq`  
**Región:** us-east-2  
**Protocolo:** AMQPS (TLS 1.2, puerto 5671)

---

## 🔄 Flujo de Transferencias Interbancarias

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│ BANCO NEXUS  │         │    SWITCH    │         │   RABBITMQ   │         │ BANCO BANTEC │
│   (Origen)   │         │  DIGICONECU  │         │   EXCHANGE   │         │  (Destino)   │
└──────┬───────┘         └──────┬───────┘         └──────┬───────┘         └──────┬───────┘
       │                        │                        │                        │
       │ 1. HTTP: "Transferir   │                        │                        │
       │    $500 a Bantec"      │                        │                        │
       │───────────────────────►│                        │                        │
       │                        │                        │                        │
       │                        │ 2. Switch procesa:     │                        │
       │                        │    - Valida cuentas    │                        │
       │                        │    - Registra en DB    │                        │
       │                        │    - Determina destino │                        │
       │                        │                        │                        │
       │                        │ 3. Publica mensaje     │                        │
       │                        │    routingKey="BANTEC" │                        │
       │                        │───────────────────────►│                        │
       │                        │                        │                        │
       │                        │                        │ 4. Enruta a cola       │
       │                        │                        │───────────────────────►│
       │                        │                        │   q.bank.BANTEC.in     │
       │                        │                        │                        │
       │                        │                        │        5. Bantec       │
       │                        │                        │           consume      │
       │                        │                        │           y procesa    │
       │                        │                        │                        │
       │◄─────────────────────────────────────────────────────────────────────────│
       │                    6. Webhook HTTP (confirmación)                        │
```

### Pasos del Flujo

| Paso | Actor | Acción |
|------|-------|--------|
| **1** | Banco Origen (Nexus) | Envía HTTP al **Switch** (no directamente al Exchange) |
| **2** | Switch DIGICONECU | Procesa la transacción: valida cuentas, registra en DB, determina destino |
| **3** | Switch DIGICONECU | Publica al Exchange `ex.transfers.tx` con `routingKey="BANTEC"` |
| **4** | RabbitMQ Exchange | Enruta automáticamente el mensaje a `q.bank.BANTEC.in` |
| **5** | Banco Destino (Bantec) | Consume el mensaje de su cola y procesa el depósito |
| **6** | Banco Destino (Bantec) | Hace webhook HTTP de confirmación al Banco Origen |

---

## 📦 ¿Qué es un Exchange?

**Exchange = Centro de distribución de correos**

Los bancos **NO se envían mensajes entre sí directamente**. Todo pasa por el Exchange, que lee el "destinatario" (routing key) y lo deposita en el buzón correcto (cola).

### Tipos de Exchange

| Tipo | Cómo enruta | Uso |
|------|-------------|-----|
| **direct** | Por coincidencia exacta de routing key | ✅ El que usamos |
| **fanout** | A TODAS las colas conectadas (broadcast) | No usado |
| **topic** | Por patrones (`*.banco.*`) | No usado |
| **headers** | Por headers del mensaje | No usado |

**Nuestro caso:** `ex.transfers.tx` es **direct** → si `routingKey = "BANTEC"`, el mensaje va a `q.bank.BANTEC.in`

---

## 🔀 Exchanges Configurados

| Exchange | Tipo | Propósito | Creado por |
|----------|------|-----------|------------|
| `ex.transfers.tx` | direct | **Exchange principal** - aquí publica el Switch | ✅ Nosotros |
| `ex.transfers.dlx` | direct | **Dead Letter Exchange** - mensajes fallidos | ✅ Nosotros |
| `amq.direct` | direct | Exchange por defecto | RabbitMQ |
| `amq.fanout` | fanout | Exchange por defecto | RabbitMQ |
| `amq.topic` | topic | Exchange por defecto | RabbitMQ |
| `amq.headers` | headers | Exchange por defecto | RabbitMQ |
| `(AMQP default)` | direct | Exchange sin nombre (legacy) | RabbitMQ |

---

## 📬 Colas Configuradas

### Colas de Entrada (`.in`)

| Cola | Estado | Propósito | Features |
|------|--------|-----------|----------|
| `q.bank.NEXUS.in` | ✅ running | Transferencias para Nexus | D, TTL, DLX, DLK |
| `q.bank.BANTEC.in` | ✅ running | Transferencias para Bantec | D, TTL, DLX, DLK |
| `q.bank.ARCBANK.in` | ✅ running | Transferencias para ArcBank | D, TTL, DLX, DLK |
| `q.bank.ECUSOL.in` | ✅ running | Transferencias para Ecusol | D, TTL, DLX, DLK |

### Colas de Error (`.dlq` - Dead Letter Queue)

| Cola | Estado | Propósito | Features |
|------|--------|-----------|----------|
| `q.bank.NEXUS.dlq` | ✅ running | Mensajes fallidos de Nexus | D |
| `q.bank.BANTEC.dlq` | ✅ running | Mensajes fallidos de Bantec | D |
| `q.bank.ARCBANK.dlq` | ✅ running | Mensajes fallidos de ArcBank | D |
| `q.bank.ECUSOL.dlq` | ✅ running | Mensajes fallidos de Ecusol | D |

### Significado de Features

| Feature | Significado |
|---------|-------------|
| **D** | Durable - sobrevive a reinicios del servidor |
| **TTL** | Time-To-Live - mensajes expiran después de X tiempo |
| **DLX** | Dead Letter Exchange - a dónde van los mensajes fallidos |
| **DLK** | Dead Letter Routing Key - cómo se enrutan los fallidos |

---

## 👤 Usuarios Configurados

| Usuario | Tag | Propósito |
|---------|-----|-----------|
| `mqadmin` | administrator | Usuario principal con control total |
| `nexus` | monitoring | Usuario del banco Nexus - acceso limitado a sus colas |
| `monitoring-AWS-OWNED...` | monitoring, protected | Usuario de AWS para CloudWatch - **NO MODIFICAR** |

### ⚠️ Usuarios Pendientes de Crear

Para completar la seguridad, se deben crear:

| Usuario | ACL Lectura | ACL Escritura |
|---------|-------------|---------------|
| `bantec` | `^q\.bank\.BANTEC\..*` | `^(ex\.transfers\.tx\|q\.bank\.BANTEC\..*)$` |
| `arcbank` | `^q\.bank\.ARCBANK\..*` | `^(ex\.transfers\.tx\|q\.bank\.ARCBANK\..*)$` |
| `ecusol` | `^q\.bank\.ECUSOL\..*` | `^(ex\.transfers\.tx\|q\.bank\.ECUSOL\..*)$` |
| `switch` | `^q\.bank\..*` | `^ex\.transfers\.tx$` |

---

## 🎯 Roles y Responsabilidades

| Actor | Rol | Acción en RabbitMQ |
|-------|-----|-------------------|
| **Switch DIGICONECU** | Orquestador central | **PUBLICA** mensajes al Exchange `ex.transfers.tx` |
| **Banco Nexus** | Origen/Destino | **CONSUME** de `q.bank.NEXUS.in` |
| **Banco Bantec** | Origen/Destino | **CONSUME** de `q.bank.BANTEC.in` |
| **Banco ArcBank** | Origen/Destino | **CONSUME** de `q.bank.ARCBANK.in` |
| **Banco Ecusol** | Origen/Destino | **CONSUME** de `q.bank.ECUSOL.in` |
| **RabbitMQ Exchange** | Buzón central | **ENRUTA** mensajes según routing key |

---

## 🔗 Credenciales de Acceso

| Parámetro | Valor |
|-----------|-------|
| **Consola Web** | `https://b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws/` |
| **Endpoint AMQPS** | `amqps://b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws:5671` |
| **Puerto** | 5671 (SSL obligatorio) |
| **Virtual Host** | `/` |
| **Credenciales** | AWS Secrets Manager → `rabbitmq-credentials` |

---

## 📊 Tabla de Routing Keys

| Banco Destino | Routing Key | Cola Destino |
|---------------|-------------|--------------|
| Nexus | `NEXUS` | `q.bank.NEXUS.in` |
| Bantec | `BANTEC` | `q.bank.BANTEC.in` |
| ArcBank | `ARCBANK` | `q.bank.ARCBANK.in` |
| Ecusol | `ECUSOL` | `q.bank.ECUSOL.in` |

---

**Última actualización:** 2026-01-29  
**Infraestructura:** Amazon MQ (RabbitMQ 3.13)  
**Región:** us-east-2
