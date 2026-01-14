# Informe de Consolidación e Integración: Núcleo + Contabilidad

**Fecha:** 2026-01-14
**Branch Actual**: `feature/union`
**Estado**: Integración Estable (RF-01 y RF-07 Completados)

## 1. Resumen Ejecutivo
Se ha completado la integración crítica entre el **Microservicio Núcleo (Orquestador)**, **Contabilidad (Ledger)** y **Compensación**. El sistema ahora soporta el flujo completo de transferencias interbancarias con garantías financieras y el flujo de reversos (devoluciones) orquestado, cumpliendo estrictamente con los tiempos y estados definidos en la documentación funcional.

---

## 2. Implementaciones Realizadas

### A. RF-07: Devoluciones y Reversos (Orquestación Completa)
Se resolvió el "Faltante Crítico" mediante un modelo híbrido donde el Núcleo orquesta y Contabilidad valida.

**Flujo Implementado:**
1. **Delegación Financiera**: El Núcleo recibe la petición `pacs.004` y consulta primero a `ms-contabilidad`.
   - *Validación*: Contabilidad verifica fondos, existencia de la transacción original y que no supere las **48 horas** (Corregido de 24h a 48h en `LedgerService`).
   - *Integridad*: Contabilidad verifica hashes y evita duplicados.
2. **Actualización de Estado (Switch)**: Si Contabilidad aprueba, el Núcleo actualiza la transacción original a `REVERSED`.
3. **Compensación Inversa**: El Núcleo notifica a `ms-compensacion` invirtiendo los roles:
   - Banco Origen: recibe Crédito (Recupera fondos).
   - Banco Destino: recibe Débito (Devuelve fondos).
4. **Notificación**: El Núcleo envía automáticamente un Webhook al Banco Origen informando del éxito de la devolución.

**Archivos Clave Modificados:**
- `MSNucleoSwitch/../TransaccionService.java`: Método `procesarDevolucion`.
- `Switch-ms-contabilidad/../LedgerService.java`: Ajuste de ventana de tiempo (48h).

### B. RF-01: Switching y Política de Reintentos
Se implementó la lógica de robustez en el envío de transferencias (`pacs.008`).

**Características:**
- **Reintentos Deterministas**: 4 intentos exactos con delays de `0ms`, `800ms`, `2s`, `4s`.
- **Manejo de timeouts**: Si tras el 4to intento no hay respuesta, la transacción pasa a estado `PENDING` (no FAILED) y se responde con `HTTP 504`, permitiendo resolución posterior vía sondeo.
- **Estados Finales**: `COMPLETED` (2xx), `FAILED` (4xx/5xx definitivo), `PENDING` (Timeout).

---

## 3. Estado Actual del Código

### Microservicio Núcleo (`com.bancario.nucleo`)
El `TransaccionService` actúa ahora como el **Source of Truth** del estado de la transacción, pero confía plenamente en `ms-contabilidad` para la integridad de los saldos. 

**Interacciones Externas:**
- `POST /api/v1/ledger/movimientos`: Para registrar débitos/créditos en tiempo real.
- `POST /api/v1/compensacion/...`: Para acumular neteo.
- `GET /api/v1/instituciones/{bic}`: Para obtener URL del banco destino.

### Microservicio Contabilidad (`com.switchbank.mscontabilidad`)
Mantiene la integridad de las Cuentas Técnicas.
- **Regla Crítica**: No permite alterar saldos si el hash de integridad no coincide.
- **Endpoint Reverso**: `/v2/switch/transfers/return` encapsula toda la lógica de validación de reversibilidad.

---

## 4. Guía para el Equipo "Núcleo + Directorio"

⚠️ **ATENCIÓN INTEGRACIÓN NUCLEO-DIRECTORIO** ⚠️

Este módulo ya está estable. Si van a realizar cambios en la integración con el Directorio de Instituciones, por favor sigan estas reglas para **NO ROMPER** la integridad financiera:

1. **NO TOCAR el flujo de `procesarTransaccionIso`**:
   - La lógica de llamadas a `registrarMovimientoContable` y `notificarCompensacion` es crítica. No cambiar el orden ni los parámetros.
   
2. **Punto de Entrada Permitido**:
   - Pueden modificar libremente el método `private InstitucionDTO validarBanco(String bic)` en `TransaccionService.java`.
   - Actualmente hace un `restTemplate.getForObject` simple. Si implementan caché, seguridad adicional o lógica de fallback para el Directorio, háganlo DENTRO de ese método.

3. **DTOs**:
   - Si el `InstitucionDTO` cambia (ej. nuevos campos de seguridad), actualicen la clase Java correspondiente sin cambiar la firma del método de validación.

## 5. Deuda Técnica Conocida
- **RNF-AVA-02 (Circuit Breaker)**: Aún no implementado. El sistema hace reintentos, pero no "abre el circuito" si un banco cae definitivamente. Pendiente para fase de optimización final.
