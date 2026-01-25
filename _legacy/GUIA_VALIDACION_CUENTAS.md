# Guía de Validación de Cuentas - Switch Transaccional ISO 20022

## Problema Detectado
Actualmente, los bancos participantes están respondiendo `HTTP 200 OK` a las solicitudes de transferencia (`pacs.008`) **antes** de validar si la cuenta destino existe. Esto provoca:
1.  Falsos positivos de "Transacción Exitosa" en el Switch y Banco Origen.
2.  Descuadres contables (dinero debitado en origen pero no acreditado en destino).
3.  Fallo en procesos de devolución automática (no se puede devolver dinero de una cuenta inexistente).

## Solución Requerida: Validación Síncrona

El Banco Destino **debe validar la existencia y estado de la cuenta destino** antes de responder al Switch.

### Flujo Correcto de Recepción (Banco Destino)

Cuando el endpoint `POST /api/core/transferencias/recepcion` recibe una solicitud:

1.  **Parsear el mensaje:** Obtener `CreditorAccount` (Cuenta Destino).
2.  **Validar Existencia:** Consultar base de datos interna (Core Bancario).
    *   ¿Existe la cuenta?
    *   ¿Está activa?
    *   ¿Permite créditos?
3.  **Si la cuenta NO existe:**
    *   **NO** devolver `HTTP 200`.
    *   **Responder con Error HTTP 404 o 422**.
    *   **Cuerpo del Error (JSON):** Incluir código ISO `AC01`.

### Ejemplo de Respuesta Correcta ante Cuenta Inexistente

**HTTP Status:** `422 Unprocessable Entity` o `404 Not Found`

**Body:**
```json
{
  "code": "AC01",
  "message": "AC01 - Número de cuenta incorrecto o inexistente en Banco Destino",
  "timestamp": "2026-01-24T10:00:00Z"
}
```

### Tabla de Códigos de Rechazo Inmediato

Al implementar esta validación síncrona, el Switch detectará el error instantáneamente, anulará la operación y notificará al Banco Origen que **NO debe descontar el dinero** (o debe reversarlo inmediatamente).

| Escenario | Código ISO | Mensaje Sugerido | Status HTTP Requerido |
| :--- | :--- | :--- | :--- |
| Cuenta no existe | **AC01** | Incorrect Account Number | 404 / 422 |
| Cuenta cerrada | **AC04** | Closed Account Number | 422 |
| Cuenta bloqueada | **AG01** | Transaction Forbidden | 403 / 422 |
| Moneda inválida | **AC03** | Invalid Currency | 422 |

### ¿Qué pasa si la validación debe ser Asíncrona?

Si su Core Bancario es muy lento y no pueden validar en tiempo real (< 3 segundos):
1.  Responda `HTTP 200 OK` (Aceptado).
2.  Si luego falla la validación, **NO intente hacer una devolución tradicional** (sacar dinero de la cuenta).
3.  Debe iniciar una **Devolución Técnica** donde la cuenta de "origen" de la devolución sea una **Cuenta Contable Transitoria del Banco**, no la cuenta del cliente inexistente.
