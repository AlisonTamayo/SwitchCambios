# Manual de Integración Técnica para Bancos Participantes
**Switch Transaccional ISO 20022 - Versión V3 (Defensa)**

---

## 📌 Tabla de Contenidos
1.  [Conceptos Generales y Conectividad](#1-conceptos-generales-y-conectividad)
2.  [Flujo 1: Envío de Transferencias (Banco Origen)](#2-flujo-1-envío-de-transferencias-banco-origen)
3.  [UX: Guía de Estados y Polling (Para Banca Móvil)](#3-ux-guía-de-estados-y-polling-para-banca-móvil)
4.  [Flujo 2: Recepción de Transferencias (Banco Destino)](#4-flujo-2-recepción-de-transferencias-banco-destino)
5.  [Validación de Cuentas (Anti-Dinero Fantasma)](#5-validación-de-cuentas-anti-dinero-fantasma)
6.  [Flujo 3: Procesamiento de Devoluciones (Returns)](#6-flujo-3-procesamiento-de-devoluciones-returns)

---

## 1. Conceptos Generales y Conectividad

El Switch opera como un orquestador centralizado. Para conectarse, su institución debe cumplir con:

### Requisitos Previos
*   **Registro BIC:** Debe tener un código BIC activo (ej. `ARCBANK`, `BANTEC`).
*   **Fondeo:** Su cuenta técnica en el Switch debe tener saldo (Modelo Pre-fondeado).
*   **Seguridad:** Todas las peticiones deben incluir el header `apikey` con su llave pública.

### Entornos
| Entorno | Base URL | Descripción |
| :--- | :--- | :--- |
| **Producción** | `http://34.16.106.7:8000` | Gateway Principal (Kong) |
| **Simulación** | `http://localhost:8000` | Entorno Local / Docker |

---

## 2. Flujo 1: Envío de Transferencias (Banco Origen)

Para enviar una transferencia, utilice el endpoint estándar `pacs.008`.

**Endpoint:** `POST /api/v2/switch/transfers`
**Header:** `apikey: <SU_LLAVE>`

**Cuerpo del Mensaje (Ejemplo):**
```json
{
  "header": {
    "messageId": "MSG-ARC-2026-001",
    "originatingBankId": "ARCBANK"
  },
  "body": {
    "instructionId": "uuid-v4-unico-generado-por-ustedes",
    "amount": { "currency": "USD", "value": 50.00 },
    "debtor": { "accountId": "123456" },
    "creditor": { "targetBankId": "BANTEC", "accountId": "987654" },
    "remittanceInformation": "Pago de servicios"
  }
}
```

### Respuestas HTTP
*   **201 Created:** Mensaje aceptado preliminarmente (⚠️ NO confirma éxito final).
*   **422 Unprocessable:** Error de negocio (Fondos insuficientes, Moneda inválida).
*   **503 Unavailable:** Circuit Breaker abierto (Banco destino caído).

---

## 3. UX: Guía de Estados y Polling (Para Banca Móvil)

**PROBLEMA CRÍTICO:** La respuesta `201 Created` es asíncrona. Si usted le dice a su cliente "Éxito" inmediatamente, puede crear falsos positivos.

**SOLUCIÓN (Patrón Optimistic UI + Polling):**
Su Backend debe implementar una espera activa antes de confirmar al Frontend.

### Algoritmo de Implementación
1.  Envía la transacción (`POST`). Recibe 201.
2.  Inicia un bucle `while` de 10 intentos (esperando 1.5s entre cada uno).
3.  En cada intento, consulta: `GET /api/v2/switch/transfers/{instructionId}`.
4.  Analiza el campo `estado`:

| Estado | Significado | Acción en App Móvil |
| :--- | :--- | :--- |
| **RECEIVED / PENDING** | Switch contactando destino. | ⏳ Mostrar Spinner "Procesando..." |
| **COMPLETED** | Dinero acreditado en destino. | ✅ Pantalla Verde "¡Transferencia Exitosa!" |
| **FAILED / REJECTED** | Rechazo técnico o de negocio. | ❌ Pantalla Roja + Devolver dinero al cliente. |
| **TIMEOUT** (Tras 15s) | Red lenta. | ⚠️ "Operación en proceso. Le notificaremos." |

---

## 4. Flujo 2: Recepción de Transferencias (Banco Destino)

Ustedes deben exponer un **Webhook** para recibir dinero.

**Ruta Sugerida:** `/api/core/transfers/reception`
**Método:** `POST`

### Contrato de Servicio
El Switch les enviará el mismo JSON `pacs.008`. Su sistema dispone de **4 segundos** para:
1.  Existencia de cuenta.
2.  Abonar el dinero.
3.  Responder `200 OK`.

**⚠️ IMPORTANTE:** Si su sistema tarda >5s o responde `500`, el Switch marcará la operación como fallida y se iniciará un proceso de reverso en contra suya.

---

## 5. Validación de Cuentas (Anti-Dinero Fantasma)

Es **OBLIGATORIO** que su Webhook de Recepción valide la cuenta destino de forma síncrona.

**Escenario:** Llega transferencia para cuenta `999999` (No existe).
**Acción Incorrecta:** Responder 200 OK y luego intentar depositar (Fallo).
**Acción Correcta:** Responder `404 Not Found` (o 422) inmediatamente.

**Tabla de Rechazos:**
*   Cuenta Inexistente -> `404` (ISO AC01)
*   Cuenta Cerrada -> `422` (ISO AC04)
*   Cuenta Bloqueada -> `422` (ISO AG01)

Esto permite que el Switch notifique al Banco Origen instantáneamente para que reembolse al cliente.

---

## 6. Flujo 3: Procesamiento de Devoluciones (Returns)

Si una transferencia exitosa (`COMPLETED`) debe ser revertida (ej. fraude post-validación, error operativo), el Banco Destino debe iniciar un `pacs.004`.

**Endpoint:** `POST /api/v2/switch/transfers/return`
**Reglas:**
*   Debe hacerse dentro de las 48 horas.
*   El monto debe ser **exactamente igual** al original (No parciales).
*   Debe referenciar el `originalInstructionId`.

**Ejemplo de Solicitud:**
```json
{
  "header": { "originatingBankId": "BANTEC", "messageId": "RET-001" },
  "body": {
    "originalInstructionId": "uuid-original-de-la-transferencia",
    "returnReason": "AC04" (Cuenta cerrada posteriormente),
    "returnAmount": { "currency": "USD", "value": 50.00 }
  }
}
```

El Switch procesará esto, debitará su cuenta técnica y devolverá el dinero al Banco Origen.

---

## 7. Apéndice A: Restricciones Operativas (Reglas de Negocio)

Para evitar rechazos inmediatos, su Core Bancario debe aplicar estas reglas **antes** de enviar la transacción al Switch:

| Regla | Valor Permitido | Código de Rechazo |
| :--- | :--- | :--- |
| **Moneda** | Únicamente `USD` (Dólar Estadounidense) | `AC03` - Invalid Currency |
| **Monto Máximo** | `$10,000.00` por transacción | `CH03` - Requested Limit Exceeded |
| **Identificadores** | `instructionId` debe ser UUID v4 único | `RC01` - Format Error |

---

## 8. Apéndice B: Catálogo Maestro de Errores (ISO 20022)

Utilice esta tabla para mapear los errores del Switch a mensajes amigables para su usuario en la App Móvil.

| Código ISO | Descripción Técnica | Mensaje Sugerido al Usuario | Acción del Banco |
| :--- | :--- | :--- | :--- |
| **AC01** | Incorrect Account Number | "El número de cuenta destino no existe." | No reintentar. |
| **AC03** | Invalid Currency | "Moneda no permitida. Solo se aceptan Dólares." | Corregir request. |
| **AC04** | Closed Account Number | "La cuenta destino está cerrada." | No reintentar. |
| **AG01** | Transaction Forbidden | "Transacción no permitida / Cuenta bloqueada." | Contactar soporte. |
| **AM04** | Insufficient Funds | "Fondos insuficientes en su cuenta." | Verificar saldo. |
| **CH03** | Limit Exceeded | "El monto excede el límite permitido ($10k)." | Reducir monto. |
| **DUPL** | Duplicate Payment | "Esta transferencia ya fue procesada." | Consultar estado. |
| **MS03** | Technical Failure | "Error en la red interbancaria. Intente más tarde." | Reintentar en 5 min. |
| **RC01** | Syntax Error | "Error interno de formato." | Revisar desarrollo IT. |

