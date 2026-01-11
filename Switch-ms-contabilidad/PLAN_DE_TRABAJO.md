# Plan de Implementación Compartido - Switch Contabilidad

Este documento sirve como hoja de ruta unificada para el desarrollo de las nuevas funcionalidades de Contabilidad.

**Estado Global**: 🟡 En Progreso
**Validación Reversos**: 24 horas (Actualizado)

---

## 📅 TURNO 1: Mel (Ahora)
**Objetivo**: Implementar estructura base y Pre-fondeo (RF-01.1).

### 1. Base y Modelos
- [ ] **Modificar `TipoMovimiento`**:
    - Agregar `RECHARGE` (Para recargas).
    - Agregar `REVERSAL` (Para reversos).

### 2. RF-01.1: Pre-fondeo (Gestión de Saldo)
- [ ] **Modificar `LedgerService`**:
    - Implementar `verificarSaldo(String bic, BigDecimal amount)` -> `boolean`.
    - Implementar `recargarSaldo(String bic, BigDecimal amount)` -> Crea movimiento `RECHARGE`.
- [ ] **Crear `FundingController`**:
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
- [ ] **Modificar `LedgerService`**:
    - Implementar `revertirTransaccion(UUID originalInstructionId)`.
    - **Regla de Negocio**: Verificar que la fecha de la transacción original NO sea mayor a **24 horas**.
    - **Lógica**: Crear movimiento contrario (`REVERSAL`) y actualizar saldos.
    - **Nota**: Usar `TipoMovimiento.REVERSAL`.
- [ ] **Modificar `LedgerController`**:
    - Agregar `POST /api/v1/ledger/reversos`.

### 4. RF-05: Soporte para Compensación
- [ ] **Modificar `LedgerService` y `Controller`**:
    - Implementar `obtenerMovimientosPorRango(start, end)`.
    - Endpoint: `GET /api/v1/ledger/range`.

### 📝 Resumen de tu Misión (Lo que te toca, Ali):
> "Tu trabajo es completar el ciclo. Tienes que hacer dos cosas principales:
> 1. **Los Reversos**: Si una transferencia falla después de haberse cobrado, necesitamos poder devolver la plata (`pacs.004`). Tienes que crear el endpoint para eso y asegurarte de que **no pasen más de 24 horas** desde la transacción original.
> 2. **Reporte para el Banco Central**: Necesitamos una forma de sacar todos los movimientos del día (el endpoint `/range`) para que el otro microservicio (Compensación) pueda hacer las cuentas finales (el Clearing) y decir cuánto debe cada banco."

---

## ✅ Lista de Verificación Final (Ambos)
- [ ] Probar flujo completo: Recarga -> Transacción (existente) -> Reverso -> Reporte.
