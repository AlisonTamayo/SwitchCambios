# 📨 Nota de Entrega para el Equipo de Núcleo (Orquestador)

Hola equipo, `ms-contabilidad` (v3.0) está listo y certificado. Aquí tienen los puntos de integración requeridos para que el flujo completo funcione.

## 1. Integración de Reversos (RF-07)
**Cambio Requerido:** El Orquestador debe exponer el endpoint de devoluciones y enrutarlo hacia Contabilidad.

-   **Nuevo Endpoint en Contabilidad**: `POST /api/v1/ledger/v2/switch/transfers/return`
-   **Acción**:
    1.  Recibir el mensaje `pacs.004` (Devolución) desde el Banco.
    2.  Hacer un "Pass-through" (Ruteo directo) hacia el endpoint de arriba en Contabilidad.
    3.  **Importante**: No intenten procesarlo como una transacción normal (`/movimientos`), deben usar la ruta específica de `/return` para que se valide la lógica de "Reverso" (mismo monto, anti-duplicidad, referenciaId).

## 2. Integración de Compensación (RF-05)
**Cambio Requerido:** El servicio de Compensación (`ms-compensacion`) necesita jalar la data para el cierre.

-   **Nuevo Endpoint en Contabilidad**: `GET /api/v1/ledger/range`
-   **Parámetros**: `?start=...&end=...`
-   **Acción**: Configurar el Cron Job de Compensación para que llame a este endpoint a las 16:00 (Cierre diario) y procese el JSON Array resultante.
    *   *Nota*: El JSON ahora trae un campo `tipo` que puede ser `REVERSAL`. Deben sumar (o restar) según corresponda en la lógica de clearing.

## 3. Manejo de Errores (Códigos HTTP)
Contabilidad ahora retorna códigos específicos que el Orquestador debe saber interpretar para responder al Banco:

*   **`400 Bad Request`**: "Saldo Insuficiente" o "Reverso Duplicado". -> **Acción**: Responder al Banco con `RJCT` (Rejected).
*   **`409 Conflict`**: "Error de Integridad (Hash)". -> **Acción**: Alerta Crítica (Detener operación y notificar a seguridad).

---

**URLs Internas (Docker) Confirmadas:**
-   Base URL: `http://ms-contabilidad:8083`

Atte: Equipo de Contabilidad Alison Tamayo - Melany Vinueza
