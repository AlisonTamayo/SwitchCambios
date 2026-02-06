# Informe de Estado Técnico: Switch Transaccional v2.0
**Fecha de Corte:** 04 de Febrero, 2026
**Versión del Sistema:** 2.0.0 (Pre-Producción)

---

## 1. Resumen Ejecutivo
El Switch Transaccional opera actualmente bajo una arquitectura de microservicios distribuidos, diseñada para garantizar la interoperabilidad financiera bajo estándares ISO 20022. El sistema maneja con éxito flujos asíncronos de transferencias, compensación neta diferida (DNS) y auditoría contable en tiempo real.

**Estado General:** ✅ Estabilizado y Funcional.
**Puntos Críticos Vigentes:** Manejo de reintentos avanzados en RabbitMQ y sincronización temporal exacta para el cierre de ciclos.

---

## 2. Arquitectura de Microservicios y Modelos de Datos

El ecosistema se compone de 5 núcleos funcionales. A continuación, se detalla la responsabilidad, el modelo de datos (Schema) y la lógica de negocio de cada uno.

### 2.1. MS-NÚCLEO (Core Transaction Engine)
**Rol:** Cerebro del sistema. Orquesta la recepción, validación y enrutamiento.
**Base de Datos:** PostgreSQL (`nucleo_db`)

#### Modelo de Datos Actual (`nucleo_db`)
*   **Tabla `Transaccion`**: Registro maestro de cada operación.
    *   `idInstruccion` (UUID, PK): Identificador único e inmutable (ISO: InstructionId).
    *   `monto`, `moneda`: Valores económicos (Decimal, Char3).
    *   `codigoBicOrigen`, `codigoBicDestino`: Identificadores de los participantes.
    *   `estado`: Máquina de estados (`RECEIVED` -> `QUEUED` -> `COMPLETED` / `FAILED`).
    *   `referenciaRed`: Hash único para control de duplicidad técnica.
*   **Tabla `RespaldoIdempotencia`**:
    *   `hashContenido`: Huella digital de la transacción para detectar reintentos (Replay Attacks).
    *   `cuerpoRespuesta`: Cache de la respuesta original para responder idénticamente ante reintentos.

#### Funcionamiento
1.  Recibe mensaje ISO 20022 (`pacs.008`).
2.  Verifica integridad y duplicidad (Idempotencia).
3.  Valida participantes contra `MS-Directorio`.
4.  Debita fondos en `MS-Contabilidad`.
5.  Encola el mensaje en **RabbitMQ** hacia el banco destino.
6.  Espera confirmación asíncrona (Callback) para marcar como `COMPLETED`.

---

### 2.2. MS-CONTABILIDAD (Central Ledger)
**Rol:** "La Verdad Financiera". Gestiona las Cuentas Técnicas de los bancos en el Banco Central (Switch).
**Base de Datos:** PostgreSQL (`contabilidad_db`)

#### Modelo de Datos Actual (`contabilidad_db`)
*   **Tabla `CuentaTecnica`**:
    *   `codigoBic` (Unique): Dueño de la cuenta.
    *   `saldoDisponible`: Fondos líquidos actuales. **No permite valores negativos.**
    *   `firmaIntegridad`: Hash criptográfico de la fila para evitar manipulación directa en BD.
*   **Tabla `Movimiento`** (Libro Mayor Inmutable):
    *   `idInstruccion` (UUID): Vínculo con el Núcleo.
    *   `tipo`: `DEBIT` (Resta) o `CREDIT` (Suma).
    *   `monto`: Valor afectado.
    *   `saldoResultante`: Snapshot del saldo post-operación (Trazabilidad).

#### Funcionamiento
*   Implementa principios ACID estrictos.
*   Bloqueo pesimista (`PESSIMISTIC_WRITE`) al actualizar saldos para evitar condiciones de carrera (Double Spending).

---

### 2.3. MS-COMPENSACIÓN (Clearing House)
**Rol:** Calculadora de posiciones netas. Determina quién debe a quién al final del ciclo.
**Base de Datos:** PostgreSQL (`compensacion_db`)

#### Modelo de Datos Actual (`compensacion_db`)
*   **Tabla `CicloCompensacion`**:
    *   `numero_ciclo`: Identificador lógico (ej. 194).
    *   `estado`: `ABIERTO` (Recibiendo ops) o `CERRADO` (Ya liquidado).
    *   `fecha_apertura`, `fecha_cierre`: Ventana temporal.
*   **Tabla `PosicionInstitucion`**:
    *   `codigo_bic`: Participante.
    *   `total_debitos`: Suma de lo enviado.
    *   `total_creditos`: Suma de lo recibido.
    *   `neto`: `(Créditos - Débitos)`. Si es positivo, el banco *recibe* dinero; si es negativo, *paga*.
*   **Tabla `ArchivoLiquidacion`**:
    *   `xml_contenido`: Reporte XML final generado para el sistema RTGS.
    *   `firma_jws`: Firma digital del reporte.

#### Funcionamiento
*   Recibe notificaciones "fire-and-forget" desde el Núcleo cada vez que una Tx es exitosa.
*   Acumula en memoria/BD en tiempo real.
*   Cierra ciclos automáticamente (Cron) o manualmente.

---

### 2.4. MS-DIRECTORIO (Participant Discovery)
**Rol:** Libreta de direcciones y reglas de seguridad.
**Base de Datos:** MongoDB (`directorio_db`)

#### Modelo de Datos Actual (Colección `directorio`)
*   Documento JSON flexible:
    *   `codigoBic`: Identificador principal.
    *   `urlDestino`: Endpoint (Webhook) donde el banco recibe notificaciones.
    *   `llavePublica`: Credencial para verificar firmas.
    *   `estadoOperativo`: `ONLINE`, `OFFLINE`, `MANTENIMIENTO`.
    *   `reglasEnrutamiento`: Mapeo de prefijos BIN (tarjetas) a BIC.

---

### 2.5. MS-DEVOLUCIONES (Dispute Resolution)
**Rol:** Manejo de excepciones y reversos (`pacs.004`).
**Base de Datos:** PostgreSQL (`devolucion_db`)

#### Modelo de Datos Actual (`devolucion_db`)
*   **Tabla `SolicitudDevolucion`**:
    *   `idInstruccionOriginal`: Qué transacción se quiere reversar.
    *   `codigoMotivo`: Razón ISO (ej. `AM05` Duplicado, `AC03` Cuenta Inexistente).
    *   `estado`: Estado del reclamo.

---

## 3. Estado Actual de la Infraestructura
*   **Middleware:** RabbitMQ (Intercambio de mensajes asíncronos).
    *   Exchanges Directos por BIC.
    *   Colas Durables y Dead Letter Queues (DLQ) para manejo de fallos robusto.
*   **Gateway:** Kong API Gateway (Control de tráfico y enrutamiento público).
*   **Cache:** Redis (Idempotencia distribuida).

---

## 4. Oportunidades de Mejora y Posibles Cambios de Modelo

Para una evolución hacia una "Versión 3.0" o Productiva Enterprise, se sugieren los siguientes cambios en el modelo:

1.  **Modelo de Compensación con "Snapshot" de Saldos:**
    *   *Actual:* `PosicionInstitucion` acumula incrementalmente.
    *   *Propuesta:* Vincular explícitamente los IDs de transacción al ciclo (`tabla detalle_ciclo`). Esto permitiría auditoría 100% exacta de qué transacciones compusieron un ciclo cerrado, eliminando la dependencia de "fechas aproximadas".

2.  **Unificación de Identidad de Transacción:**
    *   *Actual:* La transacción existe en Núcleo y su reflejo contable en Contabilidad de forma separada.
    *   *Propuesta:* Crear un ID global de trazabilidad (TraceID) que viaje no solo en headers HTTP sino que se persista en todas las tablas (`Transaccion`, `Movimiento`, `Posicion`).

3.  **Manejo de Multimoneda Nativo:**
    *   *Actual:* Modelo preparado (`String moneda`), pero lógica `hardcoded` a USD.
    *   *Propuesta:* Tablas de `TasaCambio` y `CuentaTecnica` compuestas (BIC + Moneda) para soportar liquidación multimoneda real.

4.  **Estado "SETTLED" (Liquidado) en Núcleo:**
    *   *Actual:* El estado final en Núcleo es `COMPLETED`.
    *   *Propuesta:* Añadir estado `SETTLED` que se active solo cuando Compensación confirme que el ciclo se cerró y el dinero "real" se movió en el Banco Central.

---
**Elaborado por:** Equipo de Arquitectura Switch - AI Assistant
