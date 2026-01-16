# Guía de Pruebas Paso a Paso (Validación Final)

Siga estos pasos secuenciales para validar el funcionamiento del 100% del sistema. 
Esta guía usa el **Frontend (http://localhost:5173)** para gestión visual y **PowerShell/Terminal** para simular tráfico bancario (ya que el Switch no tiene UI para iniciar transferencias, pues es un servicio backend).

---

### Paso 1: Configuración de Bancos (Frontend)

1.  Abra el navegador en `http://localhost:5173/bancos`.
2.  **Registrar BANCO_A**:
    *   Clic en "Nuevo Banco".
    *   **BIC:** `BANCO_A`
    *   **Nombre:** `Banco Origen S.A.`
    *   **Url Webhook:** `http://host.docker.internal:9999/webhook/a` (URL simulada)
    *   **Llave:** `KEY_A`
    *   Clic en **Registrar**.
3.  **Registrar BANCO_B**:
    *   Clic en "Nuevo Banco".
    *   **BIC:** `BANCO_B`
    *   **Nombre:** `Banco Destino Corp`
    *   **Url Webhook:** `http://host.docker.internal:9999/webhook/b`
    *   **Llave:** `KEY_B`
    *   Clic en **Registrar**.

*(Verificación: Ambos bancos deben aparecer en la lista con estado ONLINE).*

---

### Paso 2: Activación y Fondeo (Frontend)

1.  Vaya a `http://localhost:5173/contabilidad`.
2.  Busque la tarjeta de `BANCO_A`.
    *   Si dice "Sin Cuenta", clic en **Activar Cuenta Técnica**.
3.  Busque la tarjeta de `BANCO_B`.
    *   Clic en **Activar Cuenta Técnica**.
4.  **Fondear BANCO_A**:
    *   Clic en **Recargar Fondos** en la tarjeta de `BANCO_A`.
    *   Monto: `5000000` (5 Millones).
    *   Aceptar.
5.  *(Opcional)* Fondear `BANCO_B` con `1000`.

*(Verificación: El saldo de BANCO_A debe mostrar $5,000,000.00 y el hash SHA-256 debe estar visible).*

---

### Paso 3: Simular Transferencia Exitosas (Terminal)

Como somos el Switch, necesitamos que el "Banco A" nos envíe una petición. Usaremos `curl` para similar esto.

1.  Abra una terminal (PowerShell) y ejecute:

```powershell
$body = @{
    header = @{
        messageId = "MSG-TEST-001"
        creationDateTime = "2024-01-20T10:00:00Z"
        originatingBankId = "BANCO_A"
    }
    body = @{
        instructionId = "TX-UUID-100"
        amount = @{ currency = "USD"; value = 100.00 }
        debtor = @{ accountId = "CTA-123"; name = "Juan" }
        creditor = @{ targetBankId = "BANCO_B"; accountId = "CTA-456"; name = "Maria" }
    }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Uri "http://localhost:8082/api/v1/transacciones" -Method Post -Body $body -ContentType "application/json"
```

2.  **Verifique la respuesta**: Debería recibir un JSON con `estado: "COMPLETED"` (o `TIMEOUT` si el webhook falla, pero el dinero se habrá movido).

---

### Paso 4: Validar Ledger y Trazabilidad (Frontend)

1.  Vaya a `http://localhost:5173/transacciones`.
    *   Clic en **Buscar**.
    *   Debería ver la transacción `TX-UUID-100` en la lista.
2.  Vaya a `http://localhost:5173/contabilidad`.
    *   **BANCO_A:** Saldo debe ser `$ 4,999,900.00` (menos $100).
    *   **BANCO_B:** Saldo debe haber aumentado $100 (si lo fondeó, +100).

*(Esto confirma RF-01 y RF-01.1)*

---

### Paso 5: Prueba de Idempotencia (RF-03)

1.  En la terminal, **ejecute exactamente el mismo comando del Paso 3** (mismo `TX-UUID-100`).
2.  Debería recibir la misma respuesta inmediatamente.
3.  Vaya a `http://localhost:5173/contabilidad` y refresque.
    *   **El saldo NO debe haber bajado otros $100**. Debe mantenerse igual que en el Paso 4.

---

### Paso 6: Prueba de Mantenimiento (RF-02)

1.  Vaya a `http://localhost:5173/bancos`.
2.  En la fila de `BANCO_B` (Destino), clic en el botón de **Apagado/Maintenance** para cambiar su estado (Debe quedar en OFFLINE o MANT).
3.  Intente enviar una nueva transferencia (cambie el ID):

```powershell
$body = @{
    header = @{ messageId = "MSG-TEST-002"; creationDateTime = "2024-01-20T10:00:00Z"; originatingBankId = "BANCO_A" }
    body = @{
        instructionId = "TX-UUID-200-MANT"
        amount = @{ currency = "USD"; value = 50.00 }
        creditor = @{ targetBankId = "BANCO_B" }
    }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Uri "http://localhost:8082/api/v1/transacciones" -Method Post -Body $body -ContentType "application/json"
```

4.  **Resultado esperado:** Error 409 o similar indicando "El banco se encuentra en MANTENIMIENTO/SUSPENDIDO".

---

### Paso 7: Compensación (Clearing)

1.  Vaya a `http://localhost:5173/compensacion`.
2.  Debería ver "Ciclo Actual #1".
3.  En la tabla de posiciones, debería ver:
    *   **BANCO_A:** Débito $100.
    *   **BANCO_B:** Crédito $100.
4.  Clic en **EJECUTAR CIERRE**.
5.  Acepte la confirmación.
6.  La página se recargará. Ahora debe mostrar **"Ciclo Actual #2"** (Abierto) y el Ciclo #1 debe aparecer en el Historial como "Cerrado".

---

**¡Si completa estos pasos, el sistema cumple el 100% de la funcionalidad básica y crítica!**
