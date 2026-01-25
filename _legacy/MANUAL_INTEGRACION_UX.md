# Manual de Integración UX: Estados de Transferencia Síncronos

**Audiencia:** Equipos de Desarrollo de Frontend/Mobile de los Bancos Participantes.
**Objetivo:** Mejorar la experiencia del usuario final evitando la incertidumbre del mensaje "Enviado a la red".

## 1. El Problema de la Asincronía
En sistemas de pagos distribuidos (ISO 20022), la respuesta inicial del Switch (`HTTP 201 Created`) solo significa **"Hemos recibido tu orden y la estamos procesando"**.

**NO SIGNIFICA** que el dinero haya llegado al destino.

Si usted muestra "Transferencia Exitosa" basándose solo en el 201, corre el riesgo de:
*   Falsos positivos (el usuario cree que pagó, pero la transacción falla 3 segundos después).
*   Reclamos por dinero no recibido.

## 2. Patrón de Implementación Recomendado (Polling Síncrono)

Para ofrecer una experiencia "Síncrona" (como una compra con tarjeta), debe implementar el siguiente flujo en su Banca Móvil/Web:

### Paso 1: Envío Inicial
El usuario confirma la transferencia. Su backend envía el `pacs.008` al Switch.
*   **UI:** Muestre un spinner o pantalla de carga ("Procesando su pago...").
*   ** Backend:** Recibe `instructionId` del Switch.

### Paso 2: Bucle de Consulta (Polling)
Su Backend **NO** debe responder al Frontend todavía. Debe iniciar un ciclo de consulta al Switch.

**Endpoint:** `GET https://api-switch.redbancaria.com/api/v2/switch/transfers/{instructionId}`
**Header:** `apikey: SU_CLAVE_PUBLICA`

**Lógica de Reintento:**
*   Consultar cada **1.5 segundos**.
*   Máximo **10 intentos** (Total 15 segundos).

### Paso 3: Interpretación de Estados

| Estado Recibido | Significado para el Usuario | Acción en UI |
| :--- | :--- | :--- |
| **RECEIVED / PENDING** | El Switch está contactando al Banco Destino. | ⏳ Seguir esperando (Polling). |
| **COMPLETED** | El dinero ya está en la cuenta destino. | ✅ Mostrar: **"¡Transferencia Exitosa!"**. |
| **FAILED** | Hubo un error técnico o rechazo de negocio. | ❌ Mostrar: **"Transacción Rechazada"** + Motivo. |
| **TIMEOUT** | Se agotó el tiempo máximo sin confirmación. | ⚠️ Mostrar: **"En proceso de validación. Le notificaremos."** |

## 3. Diagrama de Secuencia (Pseudo-código)

```javascript
async function procesarTransferencia(datos) {
    // 1. Enviar al Switch
    const respuestaInicial = await switchClient.post('/transfers', datos);
    const id = respuestaInicial.id;

    // 2. Polling (Máx 10 intentos)
    for (let i = 0; i < 10; i++) {
        await sleep(1500); // Esperar 1.5s
        
        const estadoTx = await switchClient.get(`/transfers/${id}`);
        
        if (estadoTx.status === 'COMPLETED') {
            return { exito: true, mensaje: "Transferencia Finalizada" };
        }
        
        if (estadoTx.status === 'FAILED') {
            // Reversar el dinero al cliente inmediatamente
            await coreBancario.reversarDebito(id);
            return { exito: false, mensaje: "Fondos devueltos. Motivo: " + estadoTx.error };
        }
    }

    // 3. Fallback (Si tarda más de 15s)
    return { exito: false, warning: true, mensaje: "La operación está tardando. Le avisaremos por SMS." };
}
```

## 4. Preguntas Frecuentes

**¿Por qué no usar Webhooks?**
Los Webhooks son excelentes para procesos backend-to-backend, pero para una App Móvil que espera respuesta en 3 segundos, el Polling es más fácil de implementar y menos propenso a problemas de conectividad del lado del cliente.

**¿Qué pasa si recibo un TIMEOUT?**
El Switch tiene un mecanismo de *Auto-Curación*. Si usted recibe TIMEOUT, el Switch seguirá intentando resolverlo internamente. Si falla definitivamente, el Switch le notificará (o usted verá el estado FAILED en una consulta posterior) y deberá devolver el dinero al cliente.
