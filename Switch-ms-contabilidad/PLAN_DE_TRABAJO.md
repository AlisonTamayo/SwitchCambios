# Plan de Implementación Compartido - Switch Contabilidad

Este documento sirve como hoja de ruta unificada para el desarrollo de las nuevas funcionalidades de Contabilidad.

**Estado Global**: � Completado
**Validación Reversos**: 24 horas (Actualizado)

---

## 📅 TURNO 1: Mel (Ahora)
**Objetivo**: Implementar estructura base y Pre-fondeo (RF-01.1).

### 1. Base y Modelos
- [x] **Modificar `TipoMovimiento`**:
    - Agregar `RECHARGE` (Para recargas).
    - Agregar `REVERSAL` (Para reversos).

### 2. RF-01.1: Pre-fondeo (Gestión de Saldo)
- [x] **Modificar `LedgerService`**:
    - Implementar `verificarSaldo(String bic, BigDecimal amount)` -> `boolean`.
    - Implementar `recargarSaldo(String bic, BigDecimal amount)` -> Crea movimiento `RECHARGE`.
- [x] **Crear `FundingController`**:
    - `POST /api/v1/funding/recharge`: Endpoint para administradores.
    - `GET /api/v1/funding/available/{bic}/{amount}`: Endpoint de consulta rápida.

> **Punto de Control (Completado)**:
> - [x] `TipoMovimiento` actualizado con `RECHARGE` y `REVERSAL`.
> - [x] `LedgerService` implementa `verificarSaldo` y `recargarSaldo`.
> - [x] `FundingController` creado y compilando.
> - [x] **Mejora**: Se implementó idempotencia estricta en `recargarSaldo` usando `idInstruccion`.
> - [x] **Nota para Ali**: El método `recargarSaldo` ahora requiere 3 parámetros: `(bic, monto, idInstruccion)`.

### 📝 Resumen para Ali (Lo que hizo Mel):
> "Hola Ali, ya dejé listo el sistema de **Pre-fondeo**. Básicamente, modifiqué los archivos para que el Switch pueda recibir dinero (Recargas) y validar si un banco tiene saldo antes de operar.
>
> Lo más importante es que agregué seguridad extra: ahora para recargar saldo hay que enviar un ID único (`idInstruccion`), así si el sistema se equivoca y manda la recarga dos veces, nosotros no duplicamos el dinero."

---

## 📅 TURNO 2: Ali (Tarde)
**Objetivo**: Implementar Reversos (RF-07) y Soporte a Clearing (RF-05).

### 3. RF-07: Devoluciones y Reversos
- [x] **Modificar `LedgerService`**:
    - Implementar `revertirTransaccion(UUID originalInstructionId)`.
    - **Regla de Negocio**: Verificar que la fecha de la transacción original NO sea mayor a **24 horas**.
    - **Lógica**: Crear movimiento contrario (`REVERSAL`) y actualizar saldos.
    - **Nota**: Usar `TipoMovimiento.REVERSAL`.
- [x] **Modificar `LedgerController`**:
    - Agregar `POST /api/v1/ledger/reversos`.

### 4. RF-05: Soporte para Compensación
- [x] **Modificar `LedgerService` y `Controller`**:
    - Implementar `obtenerMovimientosPorRango(start, end)`.
    - Endpoint: `GET /api/v1/ledger/range`.

### 📝 Resumen de tu Misión (Lo que te toca, Ali):
> "Tu trabajo es completar el ciclo. Tienes que hacer dos cosas principales:
> 1. **Los Reversos**: Si una transferencia falla después de haberse cobrado, necesitamos poder devolver la plata (`pacs.004`). Tienes que crear el endpoint para eso y asegurarte de que **no pasen más de 24 horas** desde la transacción original.
> 2. **Reporte para el Banco Central**: Necesitamos una forma de sacar todos los movimientos del día (el endpoint `/range`) para que el otro microservicio (Compensación) pueda hacer las cuentas finales (el Clearing) y decir cuánto debe cada banco."

---

## ✅ Lista de Verificación Final (Ambos)
- [x] Probar flujo completo: Recarga -> Transacción (existente) -> Reverso -> Reporte.

---

## 🔄 Ajustes de Integración (Melany + Alison)
**Objetivo**: Cerrar brechas de seguridad y trazabilidad detectadas post-análisis.

### 1. Prevención de Doble Reverso (Crítico)
- [x] **Base de Datos**: Se agregó columna `referenciaId` (UUID) en tabla `Movimiento`.
- [x] **Lógica**: Antes de revertir, se verifica si ya existe un movimiento `REVERSAL` vinculado al `originalInstructionId`.
- [x] **Repositorio**: Método `existsByTipoAndReferenciaId` creado.

### 2. Trazabilidad
- [x] **Link**: Ahora cada Reverso guarda el ID de la transacción que revirtió en `referenciaId`.

### 3. Reglas de Tiempo
- [ ] **Nota**: Se solicitó mantener la validación de **24 horas** temporalmente, aunque la norma RF-07 menciona 48h. Pendiente confirmación final.

### 4. Cumplimiento de Contrato API (ISO 20022)
- [x] **Nuevo DTO**: Creado `ReturnRequestDTO` para soportar la estructura anidada (Header/Body).
- [x] **Endpoint**: Actualizado a `/api/v1/ledger/v2/switch/transfers/return` (Ruta base + nueva ruta).
- [x] **Lógica**: Se utiliza el `returnInstructionId` enviado por el banco como ID de trazabilidad.

---

---

## 🏁 TURNO 3: Cierre y Entrega (Ali)

### 📝 Resumen de Finalización
> "Hola equipo, he finalizado la implementación del núcleo contable (`Switch-ms-contabilidad`). El microservicio ahora cumple estrictamente con los requisitos regulatorios asignados:
>
> 1.  **Cobertura de Reversos (RF-07)**:
>     *   **Funcionalidad**: Se permite la creación de contra-movimientos (`REVERSAL`) que anulan contablemente una operación previa.
>     *   **Integridad**: El sistema recalcula los Hashes de seguridad (`SHA-256`) tras el reverso, garantizando que la cadena de custodia del saldo no se rompa.
>     *   **Validaciones**: Se implementó la ventana de tiempo (24h según Plan) e inmutabilidad de reversos (no se puede revertir un reverso).
>     *   **Mapping**: El endpoint `/reversos` acepta el ID de la instrucción original, facilitando la trazabilidad.
>
> 2.  **Soporte a Compensación (RF-05)**:
>     *   **Extracción de Datos**: El nuevo endpoint `/range` filtra movimientos por fecha exacta.
>     *   **Uso**: Este endpoint expone la "verdad contable" necesaria para que el Módulo de Compensación realice el Neteo Multilateral sin acceder directamente a la base de datos de contabilidad (desacoplamiento).
>
> **Estado del Microservicio**: 🟢 LISTO PARA PRUEBAS DE FUNCIONAMIENTO
> El sistema ahora soporta el ciclo completo: `Fondeo -> Transacción -> (Opcional) Reverso -> Reporte de Clearing`."
