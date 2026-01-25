# Guía de Integración Técnica - Switch Transaccional (ISO 20022)
**Versión:** 1.0.0  
**Audiencia:** Equipos de Desarrollo de Bancos Participantes (Ecusol, Nexus, ArcBank, Bantec)

## 1. Información de Conectividad

Para interactuar con el Switch, los bancos deben apuntar a la siguiente dirección base. 
**Importante:** Actualmente operamos sobre HTTP para evitar problemas de certificados autofirmados en entornos de desarrollo/pruebas.

| Entorno | URL Base del Switch (Gateway) | Dirección IP | Puerto |
| :--- | :--- | :--- | :--- |
| **Producción / Pruebas** | `http://34.16.106.7:8000` | `34.16.106.7` | `8000` |

---

## 2. Requisitos Previos

Antes de intentar una transacción, el banco debe asegurarse de:

1.  **Registro en el Directorio:** El banco debe estar dado de alta en el microservicio `ms-directorio` (Dashboard Administrativo) con:
    *   **BIC (Bank Identifier Code):** Identificador único de 8-11 caracteres (ej. `ECUSOLBK`, `NEXUSBK`).
    *   **URL de Webhook:** La dirección pública donde el Switch notificará las transferencias entrantes (ver sección 4).
    *   **Estado:** `ONLINE`.
2.  **Fondeo (Liquidez):** La cuenta técnica del banco en el Switch debe tener saldo suficiente. Si el saldo es insuficiente, el Switch rechazará la transacción con error `AC01`.

---

## 3. Flujo 1: Enviar Transferencia (Banco -> Switch)

Para enviar dinero a otro banco, realice una petición `POST` al siguiente endpoint:

*   **Endpoint:** `/api/v2/switch/transfers`
*   **Método:** `POST`
*   **Headers:**
    *   `Content-Type: application/json`
    *   `X-Bank-Id: <SU_BIC_AQUI>` (Ej. `ECUSOLBK`)

### Estructura del JSON (ISO 20022 Simplificado)

```json
{
  "header": {
    "messageId": "MSG-ECU-123456789",       // ID único del mensaje (trazabilidad)
    "creationDateTime": "2026-01-20T10:00:00Z" // Formato ISO 8601
  },
  "body": {
    "instructionId": "a1b2c3d4-e5f6-7890-a1b2-c3d4e5f67890", // UUID V4 VÁLIDO (Obligatorio)
    "endToEndId": "E2E-REF-CLIENTE-001",    // Referencia visible para el cliente
    "amount": {
      "currency": "USD",
      "value": 150.00
    },
    "debtorAgent": {
      "bic": "ECUSOLBK"                    // BIC del Banco Origen (Ustedes)
    },
    "debtor": {
      "name": "Juan Perez",
      "account": "1234567890"              // Cuenta de Débito
    },
    "creditorAgent": {
      "bic": "NEXUSBK"                     // BIC del Banco Destino
    },
    "creditor": {
      "name": "Maria Lopez",
      "account": "0987654321"              // Cuenta Crédito
    }
  }
}
```

### Respuestas Esperadas

*   **200 OK:** La transacción fue aceptada y procesada exitosamente.
*   **400 Bad Request:** Error de formato, validación o saldo insuficiente (`AC01`).
*   **503 Service Unavailable:** El banco destino no está respondiendo o el Switch está saturado.

---

## 4. Flujo 2: Recibir Transferencia (Switch -> Banco)

Para recibir dinero, ustedes deben exponer un **Webhook Público**. Esta URL debe ser registrada en el Directorio del Switch.

*   **Recomendación de Ruta:** `/api/core/transfers/reception` (o similar).
*   **Método que recibirán:** `POST`
*   **Payload:** El Switch les enviará exactamente la misma estructura JSON descrita arriba.

### Responsabilidad del Banco Receptor
Cuando su sistema reciba este POST, debe:
1.  Validar que la cuenta `creditor.account` exista.
2.  Acreditar el dinero al cliente inmediatamente.
3.  **Responder con HTTP 200 OK en menos de 4 segundos.**

**IMPORTANTE:** Si su sistema tarda más de 5 segundos o devuelve un error (500, 404), el Switch marcará la transacción como fallida e iniciará un proceso de **REVERSO (Devolución automática)**.

---

## 5. (NUEVO) Requisito para RF-04: Consulta de Estado

Para cumplir con la normativa de consistencia, **SU BANCO DEBE EXPONER UN ENDPOINT DE CONSULTA**.
El Switch llamará a este endpoint si no recibe respuesta de su parte en una transferencia anterior.

*   **Endpoint que USTEDES deben crear:** `/status/{instructionId}` (Relativo a su URL base)
*   **Método:** `GET`
*   **Respuesta Esperada:**
```json
{
  "estado": "COMPLETED" // O "FAILED", "PENDING"
}
```

**Si no implementan esto, el Switch asumirá que cualquier transacción dudosa falló y les descontará el dinero (Reverso).**

## 6. Checklist para el Éxito (Nexus y Ecusol)

Para evitar errores comunes (`500 Internal Server Error`, `Timeouts`), verifique:

*   ✅ **Formato de Fecha:** Use cadenas ISO estándar (`yyyy-MM-dd'T'HH:mm:ss`). No envíe objetos complejos de fecha.
*   ✅ **UUIDs Válidos:** El campo `instructionId` **DEBE** ser un UUID válido (formato 8-4-4-4-12 caracteres). Si envían un string aleatorio, fallará.
*   ✅ **Manejo de Respuestas:** Su API debe devolver un JSON simple al confirmar la recepción, no HTML ni texto plano.
*   ✅ **Webhooks Unificados:** Si usan el mismo sistema para recibir transferencias y reversos, asegurese de que su lógica pueda distinguir (o manejar ambos) sin lanzar error 404.
*   ✅ **Logs:** Monitoree sus logs de entrada. Si el Switch recibe un `404 Not Found` de su parte, asumirá que su sistema está caído.


