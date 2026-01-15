# Guía de Pruebas Postman (End-to-End)

Esta guía detalla los pasos para validar el funcionamiento completo del Switch Transaccional usando Postman.

## Pre-requisitos
*   Entorno Docker corriendo (`docker-compose up`).
*   Microservicios activos en puertos:
    *   Gateway/Frontend proxy: 8080 (o 5173 para UI)
    *   Directorio: 8081
    *   Núcleo: 8082
    *   Contabilidad: 8083
    *   Compensación: 8084
    *   Devolución: 8085

---

## Escenario 1: Configuración Inicial de Bancos (RF-02)

**Objetivo:** Registrar dos bancos (Origen y Destino) y configurar sus claves.

1.  **Registrar Banco A (Origen)**
    *   **Método:** `POST`
    *   **URL:** `http://localhost:8081/api/v1/instituciones`
    *   **Body (JSON):**
        ```json
        {
            "codigoBic": "BANCO_A",
            "nombre": "Banco A S.A.",
            "urlDestino": "https://webhook.site/uuid-banco-a",
            "llavePublica": "KEY_A",
            "estadoOperativo": "ONLINE"
        }
        ```
    *   **Validación:** HTTP 201 Created. Verificar que aparece en `GET /instituciones`.

2.  **Registrar Banco B (Destino)**
    *   **Método:** `POST`
    *   **URL:** `http://localhost:8081/api/v1/instituciones`
    *   **Body (JSON):**
        ```json
        {
            "codigoBic": "BANCO_B",
            "nombre": "Banco B Corp",
            "urlDestino": "https://webhook.site/uuid-banco-b",
            "llavePublica": "KEY_B",
            "estadoOperativo": "ONLINE"
        }
        ```

---

## Escenario 2: Fondeo de Cuentas Técnicas (RF-01.1)

**Objetivo:** Depositar fondos en el Ledger para permitir transacciones.

1.  **Crear Cuenta Técnica Banco A**
    *   **Método:** `POST`
    *   **URL:** `http://localhost:8083/api/v1/cuentas`
    *   **Body:** `{ "codigoBic": "BANCO_A" }`

2.  **Fondeo Banco A ($1,000,000)**
    *   **Método:** `POST`
    *   **URL:** `http://localhost:8083/api/v1/ledger/fondos` (o endpoint de recarga)
    *   **Body:**
        ```json
        {
            "codigoBic": "BANCO_A",
            "monto": 1000000,
            "idInstruccion": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
        }
        ```
    *   **Nota:** Use un UUID único para `idInstruccion`.

3.  **Repetir para Banco B** (Crear cuenta y fondear).

---

## Escenario 3: Transferencia Exitosa (RF-01)

**Objetivo:** Transferir $100 de Banco A a Banco B.

1.  **Iniciar Transacción**
    *   **Método:** `POST`
    *   **URL:** `http://localhost:8082/api/v1/transacciones`
    *   **Body (ISO 20022 - pacs.008):**
        ```json
        {
            "header": {
                "messageId": "MSG-001",
                "creationDateTime": "2023-10-27T10:00:00Z",
                "originatingBankId": "BANCO_A"
            },
            "body": {
                "instructionId": "TX-UUID-UNIQUE-001",
                "amount": { "currency": "USD", "value": 100.00 },
                "debtor": { "accountId": "CTA-123", "name": "Juan Perez" },
                "creditor": { "targetBankId": "BANCO_B", "accountId": "CTA-456", "name": "Maria Lopez" },
                "remittanceInfo": "Pago Factura"
            }
        }
        ```
    *   **Validación:** Response HTTP 200. Body debe incluir `estado: "COMPLETED"` (si el Webhook de Banco B responde 200).
    *   *Nota:* Para pruebas reales, puede usar [Webhook.site](https://webhook.site) como `urlDestino` del Banco B para ver que el request llega. Configure el Banco B para usar esa URL.

---

## Escenario 4: Control de Idempotencia (RF-03)

**Objetivo:** Verificar que re-enviar la misma transacción no duplica el débito.

1.  **Re-enviar Transacción Escenario 3**
    *   **Acción:** Enviar exactamente el mismo JSON ("TX-UUID-UNIQUE-001") inmediatamente.
    *   **Validación:** Response HTTP 200. El log del sistema debe decir "Redis Hit" o "Duplicado detectado".
    *   **Verificación Saldo:** Consultar saldo de Banco A (`GET /cuentas/BANCO_A`). Solo debe haberse descontado $100 una vez.

---

## Escenario 5: Fondos Insuficientes (RF-01.1 / AM04)

**Objetivo:** Intentar enviar más dinero del disponible.

1.  **Enviar Transacción Grande**
    *   **Body:** Cambiar `value: 99999999` y `instructionId: "TX-FAIL-FUNDS"`.
    *   **Validación:** Response HTTP 4xx/5xx (Dependiendo de la configuración de error). Mensaje debe indicar "FONDOS INSUFICIENTES" o código `AM04`.
    *   **Estado:** La transacción debe quedar en `FAILED`.

---

## Escenario 6: Mantenimiento (RF-02)

**Objetivo:** Verificar rechazo cuando un banco está en Mantenimiento.

1.  **Poner Banco B en Mantenimiento**
    *   **Método:** `PATCH`
    *   **URL:** `http://localhost:8081/api/v1/instituciones/BANCO_B/operaciones?nuevoEstado=MANT`
2.  **Enviar Transacción hacia Banco B**
    *   **Validación:** Response Error. "El banco BANCO_B se encuentra en MANTENIMIENTO".
3.  **Restaurar Banco B**
    *   **Método:** `PATCH` con `nuevoEstado=ONLINE`.

---

## Escenario 7: Devolución (RF-07)

**Objetivo:** Revertir la transacción del Escenario 3.

1.  **Solicitar Devolución (pacs.004)**
    *   **Método:** `POST`
    *   **URL:** `http://localhost:8082/api/v1/transacciones/devolucion`
    *   **Body:**
        ```json
        {
            "header": { "messageId": "RET-001", ... },
            "body": {
                "originalInstructionId": "TX-UUID-UNIQUE-001",
                "returnAmount": { "currency": "USD", "value": 100.00 },
                "returnReason": "AC04"
            }
        }
        ```
    *   **Validación:** Response HTTP 200. Estado `REVERSED`.
    *   **Verificación Saldo:** El saldo de Banco A debe haber recuperado los $100.

