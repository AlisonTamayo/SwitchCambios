# Guía de Demostración en Vivo: Defensa del Switch Transaccional

**Objetivo:** Demostrar funcionalmente el cumplimiento de los requerimientos analizados (RF-01 a RF-07) utilizando las interfaces gráficas reales (ArcBank, Bantec y Switch Dashboard).

**URLs del Entorno:**
*   **ArcBank (Banco A):** `https://arcbank-bank.duckdns.org`
*   **Bantec (Banco B):** `https://bantec-bank.duckdns.org`
*   **Switch Dashboard (Admin):** `http://localhost:5173` (o la URL desplegada del Frontend Switch)

---

## Escenario 1: El Camino Feliz (RF-01 Switching P2P)
**Qué demuestra:** Capacidad de orquestar movimiento de fondos en tiempo real con validaciones de seguridad.

**Pasos:**
1.  **Estado Inicial:**
    *   Abra **ArcBank** e inicie sesión (Usuario: `user1`). Verifique Saldo (ej. $500).
    *   Abra **Bantec** e inicie sesión (Usuario: `user2`). Verifique Saldo (ej. $100).
    *   Abra **Switch Dashboard > Transacciones**.
2.  **Acción:**
    *   En **ArcBank**, inicie Nueva Transferencia:
        *   **Destino:** `BANTEC`
        *   **Cuenta:** `222222` (Cuenta válida de user2)
        *   **Monto:** `$50.00`
        *   **Concepto:** "Demo Defensa"
    *   Confirmar envío.
3.  **Verificación:**
    *   **ArcBank UI:** Debe mostrar "Transferencia Exitosa". Saldo baja a $450.
    *   **Bantec UI:** Refresque. Saldo sube a $150.
    *   **Switch Dashboard:** Aparece una nueva fila:
        *   Estado: `COMPLETED`
        *   Monto: `50.00`
        *   Tiempo: `< 2s`

---

## Escenario 2: Directorio Dinámico y Circuit Breaker (RF-02 / RNF-AVA-02)
**Qué demuestra:** Gestión de topología y protección de la red ante fallos.

**Pasos:**
1.  **Configuración de Fallo:**
    *   Vaya a **Switch Dashboard > Gestión de Bancos**.
    *   Busque "BANTEC".
    *   Cambie el selector de Estado de `ONLINE` a **`OFFLINE`**.
    *   Observe que la etiqueta cambia a rojo.
2.  **Intento Fallido:**
    *   Regrese a **ArcBank**.
    *   Intente repetir la transferencia de $50.00 a Bantec.
3.  **Resultado Esperado:**
    *   **ArcBank UI:** Debe mostrar un error inmediato (Fail Fast):
        *   *"Error Procesando Transferencia: El banco destino no está disponible (MS03)."*
    *   **Switch Dashboard:** La transacción aparece como `FAILED` (o ni siquiera se crea si el rechazo es pre-routing).
4.  **Recuperación:**
    *   Vaya a **Switch Dashboard** y ponga a Bantec de nuevo en `ONLINE`.

---

## Escenario 3: Validación de Reglas de Negocio (RF-06 / Security)
**Qué demuestra:** Normalización de errores y protección anti-fraude.

**Variante A: Cuenta Inexistente**
1.  En **ArcBank**, envíe dinero a Bantec pero use la cuenta: `999999` (No existe).
2.  **Resultado:**
    *   **Switch:** Recibe error del Bantec.
    *   **ArcBank UI:** Muestra *"Rechazado: Cuenta Cerrada o No Existe (AC04)"*. (Traducción de errores RF-06).

**Variante B: Intento de Lavado (Límite Excedido)**
1.  En **ArcBank**, intente enviar **$15,000.00** USD.
2.  **Resultado:**
    *   **ArcBank UI:** Error inmediato.
    *   **Mensaje:** *"Monto excede el límite permitido (Max: 10,000 USD) (CH03)"*.
    *   Nota: Esto prueba la regla que inyectamos en el Core.

---

## Escenario 4: Motor de Compensación (RF-05)
**Qué demuestra:** Cierre de ciclo y generación de archivos de liquidación (Suma Cero).

**Pasos:**
1.  Vaya a **Switch Dashboard > Compensación**.
2.  Observe la tabla de "Ciclo Actual (Abierto)".
    *   Verá las posiciones de los bancos:
        *   **ArcBank:** `-50.00` (Deudor)
        *   **Bantec:** `+50.00` (Acreedor)
    *   **Neto Total:** `0.00` (Suma Cero).
3.  **Acción:**
    *   Haga clic en el botón **"Cerrar Ciclo"** (o "Simular Cut-off").
4.  **Verificación:**
    *   El ciclo pasa al historial de "Cerrados".
    *   Se habilita un botón para descargar **XML / PDF**.
    *   Abra el PDF y muestre el reporte firmado.

---

## Escenario 5: Auditoría y Trazabilidad (RF-07 y Ledger)
**Qué demuestra:** Inmutabilidad y capacidad de reversión.

1.  Vaya a **Switch Dashboard > Contabilidad**.
2.  Busque el movimiento por ID (copie el ID de la primera transacción).
3.  Muestre el detalle:
    *   Debe verse el registro `DEBIT` en la cuenta de ArcBank.
    *   Debe verse el registro `CREDIT` en la cuenta de Bantec.
    *   Muestre el **Hash de Integridad** (si es visible) para probar seguridad.

---

## Resumen para la Defensa
Siguiendo este guion, usted demuestra:
1.  **Funcionalidad:** El dinero se mueve (Escenario 1).
2.  **Resiliencia:** El sistema maneja fallos (Escenario 2).
3.  **Seguridad:** El sistema bloquea operaciones ilegales (Escenario 3).
4.  **Finanzas:** El sistema cuadra contablemente (Escenario 4).
