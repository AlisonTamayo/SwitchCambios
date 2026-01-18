# 🏦 Manual de Integración - Switch Transaccional V3

**Versión del API:** 2.0 (Compliance ISO 20022)  
**Entorno:** Producción (GCP)  
**Protocolo:** HTTP/1.1 REST + JSON

---

## 1. Datos de Conexión

Para conectarse al Switch, todos los bancos participantes deben configurar sus clientes HTTP (Core Bancario) con los siguientes parámetros base:

| Parámetro | Valor |
| :--- | :--- |
| **Host Base (Gateway)** | `http://34.16.106.7:8000` |
| **DNS (Opcional)** | `https://switch-digiconecu.duckdns.org` (Si usan SSL) |
| **Autenticación** | Header `apikey` (Obligatorio) |
| **Content-Type** | `application/json` |

### 🔐 Credenciales (API Keys)
Cada banco tiene una llave única que debe enviar en el Header `apikey`.

*   **ArcBank:** `ARCBANK_SECRET_KEY_2025_XYZ`
*   **Nexus Bank:** `NEXUS_SECRET_KEY_123`
*   **EcuSol:** `PUBLIC_KEY_ECUSOL_67890`
*   **BanTec:** `BANTEC_SECRET_KEY_2025`

---

## 2. Catálogo de Endpoints (Contrato V2)

### A. Realizar Transferencia (RF-01)
Inicia una transferencia interbancaria (Crédito / P2P).

*   **Método:** `POST`
*   **Endpoint:** `/api/v2/switch/transfers`
*   **Body (JSON ISO 20022 Simplificado):**

```json
{
  "header": {
    "messageId": "ARCBANK-20260118-0001",
    "creationDateTime": "2026-01-18T10:00:00Z",
    "originatingBankId": "ARCBANK"
  },
  "payload": {
    "instructionId": "INSTR-001",
    "endToEndId": "E2E-123456789",
    "amount": 150.00,
    "currency": "USD",
    "debtor": {
      "name": "Juan Perez",
      "account": "100200300",
      "bankId": "ARCBANK"
    },
    "creditor": {
      "name": "Maria Lopez",
      "account": "999888777",
      "bankId": "NEXUS_BANK"
    },
    "remittanceInformation": "Pago de Alquiler"
  }
}
```

### B. Validar Disponibilidad de Fondos (RF-01.1)
Consulta el saldo técnico disponible del banco en el Banco Central (Switch) antes de operar.

*   **Método:** `GET`
*   **Endpoint:** `/funding/{bankId}`
*   **Ejemplo:** `/funding/ARCBANK`

**Respuesta Exitosa (200 OK):**
```json
{
  "bic": "ARCBANK",
  "saldo": 500000.00,
  "moneda": "USD",
  "ultimoMovimiento": "2026-01-18T09:30:00"
}
```

### C. Consultar Estado de Transacción (RF-04)
Permite verificar el estado final de una transacción en caso de Timeout.

*   **Método:** `GET`
*   **Endpoint:** `/api/v2/switch/transfers/{instructionId}`
*   **Ejemplo:** `/api/v2/switch/transfers/INSTR-001`

**Respuesta:**
```json
{
  "instructionId": "INSTR-001",
  "status": "COMPLETED",  // Opciones: PENDING, COMPLETED, REJECTED, FAILED
  "description": "Procesada exitosamente"
}
```

### D. Solicitar Devolución / Reverso (RF-07)
Solicita devolver una transacción previamente completada (Post-Transaction Return).

*   **Método:** `POST`
*   **Endpoint:** `/api/v2/switch/transfers/return`
*   **Body:**

```json
{
  "originalInstructionId": "INSTR-001",
  "reasonCode": "DUPL",  // DUPL (Duplicado), FRAD (Fraude), CUST (Solicitud Cliente)
  "description": "Transferencia duplicada por error"
}
```

---

## 3. Códigos de Respuesta HTTP

| Código | Significado | Acción Recomendada |
| :--- | :--- | :--- |
| **200 OK** | Éxito | Procesar respuesta. |
| **201 Created** | Transacción Aceptada | Guardar comprobante. |
| **400 Bad Request** | Datos inválidos | Revisar JSON y campos obligatorios. |
| **401 Unauthorized** | API Key inválida | Verificar header `apikey`. |
| **404 Not Found** | Recurso no encontrado | Verificar URL o IDs. |
| **500 Server Error** | Error Interno Switch | Contactar soporte técnico. |
| **503 Unavailable** | Banco Destino Offline | Reintentar más tarde o usar Circuit Breaker. |

---

## 4. Soporte
Para problemas de conectividad o errores 500 recurrentes, reportar a:
*   **Equipo Infraestructura Switch**
*   **Monitor:** Torre de Control (Dashboard)
