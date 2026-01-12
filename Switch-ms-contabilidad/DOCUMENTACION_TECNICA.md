# 📚 DOCUMENTACIÓN TÉCNICA - Switch ms-contabilidad (v3.0 - Final)

## 🌟 Visión General
El **ms-contabilidad** es el núcleo fiduciario del Switch Transaccional. Su misión es mantener un registro **inmutable y auditable** de los fondos de los bancos participantes.

> **FILOSOFÍA**: "El Switch no crea dinero, solo administra la verdad de quién lo tiene."

---

## ✅ CUMPLIMIENTO DE REQUERIMIENTOS

### 1. RF-07: Devoluciones y Reversos (ISO 20022)
Este es el componente más complejo implementado. Permite anular una transacción exitosa por causas de fuerza mayor (Fraude, Error Operativo).

- **Endpoint**: `POST /api/v1/ledger/v2/switch/transfers/return`
- **Estándar**: ISO 20022 (pacs.004).
- **Entrada (`ReturnRequestDTO`)**:
  ```json
  {
    "header": { "messageId": "...", "originatingBankId": "..." },
    "body": {
      "returnInstructionId": "REV-001",  // ID del Banco
      "originalInstructionId": "TX-123", // ID de la Tx a anular
      "returnReason": "FRAD",            // Fraude
      "returnAmount": { "value": 100.00 }
    }
  }
  ```

#### 🛡️ Lógica de Protección (LedgerService):
1.  **Validación de Existencia**: Busca si `TX-123` existe.
2.  **Ventana de Tiempo**: Verifica que `TX-123` no tenga más de **24 horas** de antigüedad (Configurable a 48h).
3.  **Anti-Duplicidad (Critico)**: Consulta la BD: `¿Existe algún Movimiento tipo REVERSAL que tenga referenciaId == TX-123?`.
    -   **Si existe**: Lanza error `DUPLICADO`. Evita devolver el dinero dos veces.
4.  **Validación de Monto**: Asegura que el monto a devolver sea EXACTAMENTE igual al original.
5.  **Integridad**: Recalcula el Hash de la cuenta antes y después de mover el saldo.
6.  **Persistencia**: Guarda un nuevo movimiento con:
    -   `Tipo`: `REVERSAL`
    -   `ReferenciaId`: `TX-123` (Trazabilidad perfecta).

---

### 2. RF-05: Soporte a Compensación (Clearing)
El microservicio actúa como **Proveedor de Verdad** para el cierre del ciclo diario.

- **Endpoint**: `GET /api/v1/ledger/range?start=...&end=...`
- **Función**: Entrega la lista cruda de movimientos ("Sábana de datos") al microservicio de Compensación.
- **Flujo**:
    1.  Compensación pide datos de 09:00 a 16:00.
    2.  Contabilidad entrega:
        -   `Tx1 (Débito): -100`
        -   `Tx2 (Crédito): +50`
        -   `Tx3 (Reverso Tx1): +100`
    3.  Compensación calcula el neto (`-100 + 50 + 100 = +50`) y genera el archivo para el Banco Central.

---

### 3. RF-01.1: Pre-fondeo y Cuentas Técnicas
Sistema para gestionar la liquidez de los bancos en el Switch.

- **Endpoint**: `/api/v1/funding/recharge`
- **Seguridad**: Idempotencia estricta usando `idInstruccion`. Si envías la misma recarga 2 veces, solo se procesa una.
- **Hash de Seguridad**: Cada cuenta tiene una columna `firmaIntegridad` (SHA-256).
    -   `Hash = SHA256(BIC + Saldo)`
    -   Si un DBA intenta cambiar el saldo manualmente por SQL, el Hash no coincidirá y el sistema bloqueará la cuenta automáticamente en la siguiente lectura.

---

## 🏗️ ARQUITECTURA DEL CÓDIGO

### 📂 Controladores (API Interface)

#### `LedgerController`
Es la fachada principal.
-   Maneja las versiones de API (`/v1/...`, `/v2/...`).
-   Convierte las excepciones de negocio (`SaldoInsuficiente`, `Duplicado`) en respuestas HTTP correctas (`400 Bad Request`, `409 Conflict`).

#### `FundingController`
Fachada administrativa.
-   Permite inyectar dinero al sistema (Recargas).
-   Consulta de saldos de alta velocidad.

### 🧠 Servicio (`LedgerService`)
Aquí reside toda la inteligencia.
-   **Transaccionalidad**: Usa `@Transactional`. Si falla el guardado del movimiento, se hace Rollback del saldo. Todo o nada.
-   **Lógica de Negocio**: Contiene las reglas de 24h, validación de hashes y lógica de reversos.

### 💾 Modelos de Datos (Entidades JPA)

#### 1. Tabla: `CuentaTecnica`
| Campo | Descripción |
| :--- | :--- |
| `codigoBic` | ID del Banco dueño de la cuenta. |
| `saldoDisponible` | Dinero liquido actual. |
| `firmaIntegridad` | Hash de seguridad anti-manipulación. |

#### 2. Tabla: `Movimiento`
| Campo | Descripción |
| :--- | :--- |
| `idInstruccion` | UUID único de la operación. |
| `tipo` | `DEBIT`, `CREDIT`, `RECHARGE`, `REVERSAL`. |
| `monto` | Valor de la operación. |
| `referenciaId` | **Clave para RF-07**: Guarda el ID de la Tx original en caso de ser un Reverso. |
| `fechaRegistro` | Timestamp para el RF-05 (Clearing). |

---

## 🔄 RESUMEN DE FUNCIONAMIENTO COMPLETO

1.  **El dinero entra** vía `FundingController` (Recarga).
2.  **El dinero se mueve** vía `LedgerController` (Transferencias P2P).
    -   Se valida saldo.
    -   Se resta origen, suma destino virtualmente (si fuera interno) o solo se debita.
    -   Se actualizan Hashes.
3.  **El dinero regresa (si hay error)** vía `LedgerController` (Reverso/Devolución ISO 20022).
    -   Se valida tiempo y duplicidad.
    -   Se crea contra-asiento.
4.  **El dinero se reporta** vía endpoint `/range` para que Compensación haga el cierre oficial ante el Regulador.
