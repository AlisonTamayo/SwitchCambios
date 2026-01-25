# Análisis de Cumplimiento: Core del Switch (Real-Time Processing)

## 1. Validador de Reglas (Validation Engine)

### Estado: ⚠️ PARCIALMENTE CUMPLIDO

**Análisis:**
*   **Schema Validation:** ✅ **CUMPLE.** Se utiliza `Jackson` y DTOs tipados (`MensajeISO`) para asegurar la estructura JSON correcta. Las anotaciones `@Valid` en el controlador rechazan estructuras irreconocibles.
*   **Reglas de Negocio:** ❌ **NO CUMPLE.**
    *   No existe validación de **Monto Máximo**. El sistema intentará procesar una transferencia de $100,000,000 si el banco origen la envía.
    *   No existe validación de **Moneda**. El sistema acepta "XYZ" como moneda si cumple el formato de string, delegando el error al Banco Destino o Contabilidad.

**Impacto:** Riesgo de seguridad, fraude y errores contables aguas abajo.

**Acción Correctiva:** Implementar bloque `validarReglasNegocio(monto, moneda)` antes de la persistencia inicial.

---

## 2. Control de Idempotencia (Idempotency Handler)

### Estado: ✅ CUMPLE TOTALMENTE

**Análisis:**
*   **Caché Redis:** ✅ Implementado. Se usa `redisTemplate.setIfAbsent` con clave `idem:{UUID}`.
*   **Ventana de Tiempo:** ✅ Implementado. TTL configurado a **24 horas**.
*   **Integridad:** ✅ Implementado. Se genera un **Fingerprint MD5** de los datos críticos (Monto, Cuentas, Fechas) para detectar si un mismo ID se reutiliza con datos diferentes (Violación de Integridad).
*   **Fallback:** ✅ Implementado. Si Redis falla, el sistema consulta la base de datos SQL (`RespaldoIdempotenciaRepositorio`), garantizando la operación en fallos de infraestructura.

---

## 3. Motor de Enrutamiento (Routing Engine)

### Estado: ✅ CUMPLE TOTALMENTE

**Análisis:**
*   **Directorio de Participantes:** ✅ Implementado. Consulta al microservicio `ms-directorio`.
*   **Traducción BIN:** ✅ Implementado (`validarEnrutamientoBin`). Resuelve el Banco Destino basándose en los primeros 6 dígitos de la cuenta.
*   **Verificación de Estado:** ✅ **CORREGIDO.** Se implementó validación exhaustiva de estados: `OFFLINE`, `SUSPENDIDO`, `MANT`, `SOLO_RECIBIR`.
*   **Circuit Breaker:** ✅ Implementado. Utiliza `Resilience4j` para dejar de enviar peticiones a un banco que falla repetidamente, protegiendo el hilo de ejecución del Switch.

---

## Resumen del Plan de Trabajo (Parte 1)

1.  **PRIORIDAD ALTA:** Mover las reglas de negocio (Límite y Moneda) al núcleo del Switch para rechazar transacciones inválidas tempranamente (`Fail Fast`).
2.  **PRIORIDAD MEDIA:** Configurar los límites como parámetros dinámicos (variables de entorno o base de datos) para no hardcodearlos en Java.
