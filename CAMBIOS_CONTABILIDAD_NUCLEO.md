# Resumen de Cambios Técnicos - Switch Financiero

Este documento detalla las implementaciones críticas realizadas en los microservicios `MSNucleoSwitch` y `Switch-ms-contabilidad` para asegurar el cumplimiento normativo y robustez del sistema.

## 1. MSNucleoSwitch (Orquestador Transactional)

### ✅ Idempotencia Híbrida (RF-03)
**Objetivo**: Evitar duplicidad de transacciones sin depender exclusivamente de la caché.
- **Implementación**: `TransaccionService.java`
- **Lógica**:
    1. **Primary Check**: Se intenta obtener un lock en **Redis** (`SETNX`) con TTL de 24 horas.
    2. **Fallback Automático**: Si Redis no responde (Connection Refused/Timeout), el sistema captura la excepción y consulta la base de datos (`RespaldoIdempotenciaRepository`).
    3. **Resultado**: El sistema sigue operando incluso si el clúster de Redis se cae, garantizando Alta Disponibilidad (HA).

### ✅ Sondeo Activo / Active Polling (RF-04)
**Objetivo**: Resolver transacciones que quedan en estado incierto (`PENDING`) por timeouts de red.
- **Implementación**: `TransaccionService.obtenerTransaccion(UUID id)`
- **Lógica**:
    - Al consultar una transacción, si su estado es `PENDING` o `RECEIVED` **Y** han pasado más de **5 segundos** desde su creación:
    - El Switch realiza una petición HTTP `GET` al Banco Destino (`/status/{id}`).
    - Si el banco responde `COMPLETED` o `FAILED`, se actualiza y persiste el estado final en el Switch.

## 2. Switch-ms-contabilidad (Ledger)

### ✅ Ventana de Devoluciones (RF-07)
**Objetivo**: Limitar el tiempo para reversos financieros.
- **Implementación**: `LedgerService.revertirTransaccion`
- **Cambio Crítico**: La ventana de tiempo se ajustó estrictamente a **24 horas**.
- **Regla**: 
  ```java
  if (original.getFechaRegistro().isBefore(LocalDateTime.now().minusHours(24))) {
      throw new RuntimeException("La transacción original es mayor a 24 horas...");
  }
  ```

### 🔒 Integridad de Saldos (RF-01.1)
- **Implementación**: `LedgerService`
- **Mecanismo**: Cada cuenta técnica tiene un campo `firmaIntegridad`.
- **Validación**: Antes de cualquier débito o crédito, se recalcula el **SHA-256** del saldo y BIC. Si no coincide con la firma guardada, se bloquea la operación por "Alerta de Seguridad".

---

## 📝 Resumen Ejecutivo (Explicación Sencilla)

En palabras simples, hemos blindado el sistema para que sea **más resistente y seguro**:

1.  **No nos quedamos parados (Nucleo)**: Antes, si el sistema de memoria rápida (Redis) fallaba, todo el switch dejaba de funcionar. Ahora, hemos puesto una **"llanta de repuesto" (Base de Datos)**. Si la memoria rápida falla, el sistema usa automáticamente el respaldo en disco y sigue procesando sin errores.
2.  **No dejamos dudas (Nucleo)**: Si una transacción se queda "pensando" o colgada por más de 5 segundos, el sistema ya no se queda esperando. Ahora va y **le pregunta activamente al banco destino** "¿Qué pasó con esto?", y actualiza el estado para que nadie se quede con la duda si el dinero pasó o no.
3.  **Protegemos la contabilidad (Ledger)**: Hemos puesto una regla estricta de **24 horas** para las devoluciones. Si alguien intenta revertir una operación de hace dos días, el sistema lo bloqueará automáticamente. Además, cada movimiento lleva una firma digital; si alguien intenta cambiar un saldo manualmente en la base de datos, el sistema se dará cuenta y bloqueará la cuenta por seguridad.
