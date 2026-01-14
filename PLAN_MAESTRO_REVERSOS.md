# 🚀 Plan Maestro: Implementación del "Botón de Arrepentimiento" (Solicitud de Reverso)

Este documento explica de forma sencilla y ejecutiva qué haremos, cómo funcionará y qué reglas regirán la funcionalidad de devoluciones para los bancos (**Arcbank / Bantec**).

---

## 🎯 ¿Qué vamos a hacer?
Vamos a dotar al Banco de la capacidad de **revertir una transferencia saliente** en caso de error o fraude detectado.

Técnicamente, implementaremos una **función de "Solicitar Devolución"** conectada al Switch iso 20022.

---

## 🖥️ 1. La Experiencia en Pantalla (Frontend)

El operador bancario (o el cliente, según el canal) verá lo siguiente:

### A. El Botón Inteligente
En la sección **Historial de Transferencias**, junto a cada movimiento enviado:
*   Se añadirá un botón llamado: **`↩️ Solicitar Devolución`**.

> **⚠️ La Regla de Oro (24 Horas):**
> *   El sistema verificará la fecha/hora de la transferencia.
> *   **Si tiene MENOS de 24 horas**: El botón aparecerá habilitado y visible. ✅
> *   **Si tiene MÁS de 24 horas**: El botón desaparecerá mágicamente (o se deshabilitará). ❌
> *   *¿Por qué?*: Porque el Switch rechaza automáticamente cualquier reclamo antiguo. No queremos dar falsas esperanzas.

### B. La Ventanita de Confirmación (Modal)
Al pulsar el botón, no se envía nada todavía. Se abre un diálogo preguntando:

**"¿Estás seguro de solicitar el reverso de estos $100.00?"**
*   **Seleccione el Motivo**: (Menú desplegable obligatorio)
    *   🚨 Fue un Fraude (`FRAD`)
    *   🔁 Fue un Error Técnico (`TECH`)
    *   👯‍♀️ Fue un Pago Duplicado (`DUPL`)
*   **Botón Final**: `[ Confirmar y Enviar ]`

---

## ⚙️ 2. Lo que pasa "Por Detrás" (Backend)

Una vez confirmado, el sistema del Banco se pone a trabajar:

1.  **Redacción de la Carta Digital (JSON Builder)**:
    *   El sistema toma los datos de la transacción original (ID, Monto, Fechas, Nombres).
    *   Los empaqueta en un formato estricto llamado **ISO 20022 (`pacs.004`)**. Es como llenar un formulario oficial.

2.  **Envío al Correo Central (POST al Switch)**:
    *   El sistema envía este paquete a la dirección única del Switch:
    *   `POST http://[IP_KONG]:8000/api/v1/devoluciones`

---

## 📡 3. La Respuesta (Feedback Inmediato)

El sistema del Banco se queda esperando unos milisegundos a ver qué dice el Switch:

### ✅ Escenario A: ¡Éxito! (HTTP 200)
*   **El Switch dice**: "Aprobado. El dinero ya está de vuelta en tu Cuenta Técnica".
*   **El Banco hace**:
    1.  Muestra un mensaje verde: *"Devolución Exitosa"*.
    2.  Automáticamente **abona el dinero** a la cuenta del cliente afectado.
    3.  Marca la transacción localmente como "REVERSADA".

### ❌ Escenario B: Rechazo (HTTP 400/409)
*   **El Switch dice**: "Rechazado. (Motivo: Ya se devolvió antes / Pasaron más de 24h)".
*   **El Banco hace**:
    1.  Muestra un mensaje rojo: *"No se pudo procesar la devolución: [Motivo del Switch]"*.
    2.  No le devuelve nada al cliente.

---

## 📋 Resumen para el Equipo de Desarrollo

| Componente | Tarea Clave |
| :--- | :--- |
| **Frontend** | Ocultar botón si `Now - TxDate > 24h`. Modal con Motivos. |
| **Backend** | Construir JSON ISO 20022 con `originalInstructionId` correcto. |
| **Integración** | Apuntar a `:8000/api/v1/devoluciones`. |
| **Base de Datos** | Manejar el estado `REVERSADA` en la tabla local. |

---

*Este plan asegura que tanto el operador como el sistema cumplan con las reglas del Switch sin fricción.*
