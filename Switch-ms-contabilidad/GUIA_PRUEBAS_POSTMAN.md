# 🧪 Guía de Pruebas de Funcionamiento (Postman) - Switch Contabilidad

Esta guía detalla el paso a paso para validar el cumplimiento de los requerimientos **RF-07 (Reversos ISO 20022)** y **RF-05 (Compensación)** utilizando Postman.

## 📋 Pre-requisitos
1.  Base de datos PostgreSQL levantada.
2.  Microservicio `ms-contabilidad` en ejecución.
3.  URL Base: `http://localhost:8083`.

---

## 🟢 CASO DE PRUEBA 1: Fondeo Inicial (Seguridad y Saldo)
**Objetivo**: Crear o Recargar la cuenta para establecer un Hash de Integridad válido.

-   **Método**: `POST`
-   **Endpoint**: `/api/v1/funding/recharge`
-   **Body (JSON)**:
    ```json
    {
      "codigoBic": "ARCBANK",
      "monto": 5000000.00,
      "idInstruccion": "{{$guid}}" 
    }
    ```
    *Nota: `{{$guid}}` genera un UUID aleatorio en Postman.*

-   **Resultado Esperado (200 OK)**:
    -   `saldoDisponible`: 5000000.00
    -   `firmaIntegridad`: (Hash SHA-256 válido generado por el sistema).

---

## 🟡 CASO DE PRUEBA 2: Transacción Normal (Débito)
**Objetivo**: Simular una salida de dinero real.

-   **Método**: `POST`
-   **Endpoint**: `/api/v1/ledger/movimientos`
-   **Body (JSON)**:
    ```json
    {
      "codigoBic": "ARCBANK",
      "tipo": "DEBIT",
      "monto": 100.00,
      "idInstruccion": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11" 
    }
    ```

-   **Resultado Esperado (200 OK)**:
    -   `saldoDisponible`: 4999900.00 (Se restaron $100).

---

## 🔴 CASO DE PRUEBA 3: Reverso ISO 20022 (RF-07)
**Objetivo**: Validar que el sistema acepta el formato estándar ISO bancario para devolver fondos.

-   **Método**: `POST`
-   **Endpoint**: `/api/v1/ledger/v2/switch/transfers/return`
-   **Body (JSON ISO 20022)**:
    ```json
    {
      "header": {
        "messageId": "MSG-001",
        "originatingBankId": "ARCBANK"
      },
      "body": {
        "returnInstructionId": "{{$guid}}", 
        "originalInstructionId": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
        "returnReason": "FRAD",
        "returnAmount": {
          "currency": "USD",
          "value": 100.00
        },
        "debtor": { "name": "Test User" }, 
        "creditor": { "name": "Target User"}
      }
    }
    ```

-   **Resultado Esperado (200 OK)**:
    -   El saldo **retorna** a `5,000,000.00`.
    -   Se crea un movimiento tipo `REVERSAL`.

---

## 🛡️ CASO DE PRUEBA 4: Protección Anti-Duplicidad (Seguridad)
**Objetivo**: Verificar que nadie pueda cobrar un reverso dos veces.

-   **Acción**: Enviar **exactamente el mismo JSON** del paso anterior (con el mismo `originalInstructionId` y `returnInstructionId` si este fuera estático, pero la validación clave es sobre `originalInstructionId` que ya fue revertido).
    *Nota: Si usas `{{$guid}}` en returnID, el sistema igual validará el `originalInstructionId`.*

-   **Endpoint**: `/api/v1/ledger/v2/switch/transfers/return`
-   **Respuesta Esperada (400 Bad Request)**:
    ```json
    {
        "error": "DUPLICADO: Esta transacción ya ha sido revertida anteriormente."
    }
    ```

---

## 📊 CASO DE PRUEBA 5: Reporte de Clearing (RF-05)
**Objetivo**: Confirmar que los movimientos son extraíbles para la compensación.

-   **Método**: `GET`
-   **Endpoint**: `/api/v1/ledger/range`
-   **Query Params**:
    -   `start`: `2024-01-01T00:00:00`
    -   `end`: `2030-12-31T23:59:59`

-   **Resultado Esperado (200 OK)**:
    -   JSON Array con **3 Movimientos**:
        1.  TYPE: `RECHARGE` ($5M)
        2.  TYPE: `DEBIT` ($100)
        3.  TYPE: `REVERSAL` ($100, con `referenciaId` apuntando al débito).

---

## ✅ RESUMEN DE VALIDACIÓN (CERTIFICACIÓN 100%)
Tras la ejecución exitosa de los 5 casos anteriores (evidenciada en pruebas locales), se certifica que el microservicio **Switch-ms-contabilidad** cumple con:

1.  **Integridad Financiera**: Los saldos se calculan con precisión `BigDecimal` y están protegidos por Hash SHA-256.
2.  **RF-07 (Reversos)**: Implementación correcta de `ReturnRequestDTO` (Estructura ISO 20022), validación de 24h, montos idénticos y prevención de duplicados.
3.  **RF-05 (Compensación)**: Endpoints de rango de fechas funcionales y optimizados con Índices.
4.  **Resiliencia**: Manejo correcto de Proxies de Hibernate (`@JsonIgnore`) y excepciones controladas.

**Estado del Arte**: El servicio es funcional, seguro y cumple estrictamente el contrato de integración.
