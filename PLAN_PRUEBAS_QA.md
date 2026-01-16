# Plan de Pruebas de Calidad (QA) - Switch Transaccional v1.1

**Rol:** QA Automation Engineer  
**Objetivo:** Validar robustez, integridad y cumplimiento normativo en entorno local.

---

## 🧪 1. Prueba de Idempotencia (Anti-Duplicidad)
**Objetivo:** Verificar que el sistema no procese dos veces la misma transacción financiera.

### Pasos (Postman)
1.  **Preparación:** Genera un UUID único para `instructionId` (ej. `aaaa-1111`).
2.  **Ejecución 1 (Original):**
    *   **Endpoint:** `POST /api/v1/transacciones`
    *   **Body:**
        ```json
        {
          "header": { "messageId": "msg-001", "creationDateTime": "2026-01-16T10:00:00Z", "originatingBankId": "BANCO_A" },
          "body": {
            "instructionId": "aaaa-1111",
            "amount": { "value": 100.00, "currency": "USD" },
            "debtor": { "accountId": "100001" },
            "creditor": { "targetBankId": "BANCO_B", "accountId": "200001" }
          }
        }
        ```
    *   **Resultado Esperado:** `200 OK` (Estado: `COMPLETED` o `RECEIVED`).
3.  **Ejecución 2 (Replay Exacto):**
    *   Envía **exactamente el mismo JSON** nuevamente.
    *   **Resultado Esperado:** `200 OK`. El cuerpo de la respuesta debe ser idéntico al anterior.
4.  **Validación en Base de Datos (SQL):**
    ```sql
    -- En BD Contabilidad
    SELECT COUNT(*) FROM movimientacuenta 
    WHERE id_instruccion = 'aaaa-1111';
    -- RESULTADO ESPERADO: 1 (No 2)
    ```

---

## 🛡️ 2. Prueba de Integridad (MD5 Check)
**Objetivo:** Verificar que el sistema detecte manipulaciones de datos en reintentos (mismo ID, distinto contenido).

### Pasos
1.  **Ejecución:**
    *   Usa el mismo `instructionId` de la prueba anterior (`aaaa-1111`).
    *   **Modificación Maliciosa:** Cambia el monto a `100.50` en el JSON.
    *   Envía la petición.
2.  **Resultado Esperado:**
    *   **Código HTTP:** `401 Unauthorized` o `409 Conflict`.
    *   **Mensaje de Error:** "Security Exception: Same InstructionId, different content fingerprint".
    *   **Log del Servidor:** Debe aparecer "VIOLACIÓN DE INTEGRIDAD ISO 20022".

---

## ⚡ 3. Prueba de Circuit Breaker (Resilience4j)
**Objetivo:** Validar que el Switch deje de enviar tráfico a un banco caído tras 5 fallos.

### Pasos
1.  **Simulación de Caída:**
    *   Detén el contenedor simulado del banco destino (o usa un puerto incorrecto en la DB de Directorio).
    *   `docker stop simulador-banco-b` (o equivalente).
2.  **Bombardeo de Peticiones:**
    *   Envía 5 transacciones seguidas hacia `BANCO_B`.
    *   **Observación:** Cada una tardará ~3s (Timeout) o fallará con "Connection Refused".
3.  **Disparo del Circuito:**
    *   Envía la **6ta petición**.
    *   **Resultado Esperado:** Fallo **INMEDIATO** (vs los 3s anteriores).
    *   **Error:** `MS03 - El Banco Destino está NO DISPONIBLE (Circuit Breaker Activo)`.
4.  **Verificación BD Directorio:**
    ```sql
    -- En BD Directorio (MongoDB o SQL mapeado)
    SELECT * FROM interruptor_circuito WHERE codigo_bic = 'BANCO_B';
    -- esta_abierto: true
    -- fallos_consecutivos: >= 5
    ```

---

## 🔄 4. Prueba de Ciclo de Compensación (Clearing)
**Objetivo:** Validar la continuidad del negocio al cerrar un ciclo.

### Script de Validación SQL

```sql
-- 1. Ver estado antes del cierre
SELECT * FROM ciclocompensacion ORDER BY numero_ciclo DESC LIMIT 2;

-- 2. Ejecutar Cierre vía API
-- POST http://localhost:8084/api/v1/compensacion/ciclos/{ID_ACTUAL}/cierre

-- 3. Ver estado DESPUÉS del cierre (Validación)
SELECT * FROM ciclocompensacion ORDER BY numero_ciclo DESC LIMIT 2;

-- CRITERIOS DE ACEPTACIÓN:
-- Fila 1 (Nuevo): estado = 'ABIERTO', fecha_apertura = (hace segundos)
-- Fila 2 (Viejo): estado = 'CERRADO', fecha_cierre = (hace segundos)
```

---

## 🚏 5. Prueba de Validación de BIN (Enrutamiento)
**Objetivo:** Verificar que el sistema rechace cuentas que no coinciden con el banco destino declarado.

### Pasos
1.  **Contexto:**
    *   Supongamos que el Banco A (`BANCO_A`) es dueño del BIN `111111`.
2.  **Ataque de Enrutamiento:**
    *   Envía una transacción dirigida al **Banco B** (`targetBankId: "BANCO_B"`).
    *   Pero usa una cuenta destino del **Banco A**: `accountId: "1111119999"`.
3.  **Resultado Esperado:**
    *   **Código HTTP:** `4xx Client Error`.
    *   **Error:** `BE01 - Routing Error: La cuenta destino no pertenece al banco indicado.`
    *   **Nivel de Bloqueo:** La transacción **NO** debe llegar a Contabilidad ni crearse en DB.

---

**Nota:** Este plan está diseñado para ser ejecutado secuencialmente en tu entorno local Dockerizado.
