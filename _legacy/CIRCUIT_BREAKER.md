# Circuit Breaker - Implementación con Resilience4j (RNF-AVA-02)

Este documento detalla la implementación técnica del patrón **Circuit Breaker** utilizando la librería estándar de industria **Resilience4j**. Esta solución robustece el middleware protegiéndolo de fallos en cascada y cumpliendo estrictamente con los requisitos de disponibilidad definidos.

---

## 🏗 Arquitectura y Tecnología

A diferencia de implementaciones manuales distribuidas, hemos integrado **Resilience4j** directamente en el núcleo del Switch (`MSNucleoSwitch`). Esto permite:
*   Gestión de estado en memoria de alto rendimiento.
*   Transiciones de estado atómicas y thread-safe.
*   Configuración centralizada vía `application.properties`.

El Circuit Breaker "envuelve" las llamadas HTTP salientes hacia los webhooks de los bancos participantes.

---

## ⚙️ Configuración y Parámetros

La configuración implementada cumple con las reglas de negocio del **RNF-AVA-02**:

| Parámetro | Valor Configurado | Descripción / Requisito |
| :--- | :---: | :--- |
| **Tam. Ventana Deslizante** | `5` | Analiza las últimas 5 peticiones (`COUNT_BASED`). |
| **Umbral de Fallo** | `100%` | Si las 5 fallan, se abre el circuito (Requisito: "más de 4 fallos consecutivos"). |
| **Umbral de Latencia** | `4000 ms` | Una llamada que tarde > 4s se considera "lenta" y cuenta como fallo. |
| **Tiempo en Estado ABIERTO** | `30 s` | Tiempo mínimo de bloqueo antes de intentar recuperación. |
| **Transición Automática** | `true` | Pasa automáticamente a `HALF_OPEN` tras los 30s para probar recuperación. |
| **Excepciones Registradas** | `5xx`, `Timeout`, `IOError` | Solo errores técnicos cuentan como fallo. Errores de negocio (4xx) se ignoran. |

### Extracto de Configuración (`application.properties`)
```properties
resilience4j.circuitbreaker.configs.default.slidingWindowSize=5
resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls=5
resilience4j.circuitbreaker.configs.default.failureRateThreshold=100
resilience4j.circuitbreaker.configs.default.waitDurationInOpenState=30s
resilience4j.circuitbreaker.configs.default.slowCallDurationThreshold=4000ms
resilience4j.circuitbreaker.configs.default.recordExceptions[0]=org.springframework.web.client.HttpServerErrorException
resilience4j.circuitbreaker.configs.default.recordExceptions[1]=java.util.concurrent.TimeoutException
```

---

## 🔄 Flujo de Funcionamiento

### 1. Estado CERRADO (Normal)
*   El tráfico fluye libremente hacia los bancos.
*   Resilience4j monitorea cada llamada:
    *   **Éxito:** Petición OK (< 4s).
    *   **Fallo:** Retorno 5xx, Timeout o Latencia > 4s.
*   Si se detectan **5 fallos consecutivos**, el estado cambia a **ABIERTO**.

### 2. Estado ABIERTO (Bloqueo)
*   **Acción Inmediata:** Cualquier intento de enviar una transacción al banco afectado es interceptado **antes** de realizar la conexión.
*   **Excepción:** Se lanza `CallNotPermittedException`.
*   **Manejo:** El Switch captura esta excepción y genera un error de negocio `MS03 - Technical Failure`, informando al origen inmediatamente sin latencia.
*   **Duración:** El bloqueo persiste durante **30 segundos**.

### 3. Estado HALF-OPEN (Recuperación)
*   Pasados los 30s, el circuito permite pasar **3 peticiones de prueba** (Probe).
*   **Si tienen éxito:** El circuito se CIERRA y vuelve a normalidad.
*   **Si fallan:** El circuito vuelve a ABRIRSE por otros 30s.

---

## 💻 Integración en Código (`TransaccionService.java`)

La lógica se implementa programáticamente usando el `CircuitBreakerRegistry`:

```java
// 1. Obtener instancia del CB para el banco destino específico
CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(bicDestino);

try {
    // 2. Ejecutar la llamada HTTP protegida
    cb.executeRunnable(() -> {
        restTemplate.postForEntity(urlWebhook, iso, String.class);
    });

} catch (CallNotPermittedException e) {
    // 3. Manejo de Circuito Abierto (Fail Fast)
    throw new BusinessException("MS03 - El Banco Destino está NO DISPONIBLE (Circuit Breaker Activo).");
}
```

---

## ✅ Matriz de Cumplimiento RNF-AVA-02

| Requisito | Estado | Evidencia |
| :--- | :---: | :--- |
| **Detectar 5 fallos consecutivos** | ✅ Completo | `minimumNumberOfCalls=5`, `failureRateThreshold=100` |
| **Detectar latencia > 4s** | ✅ Completo | `slowCallDurationThreshold=4000ms` |
| **Bloquear tráfico (Fail Fast)** | ✅ Completo | Captura de `CallNotPermittedException` |
| **Tiempo de espera 30s** | ✅ Completo | `waitDurationInOpenState=30s` |
| **Recuperación Automática** | ✅ Completo | `automaticTransitionFromOpenToHalfOpenEnabled=true` |
| **Identificación de Errores** | ✅ Completo | Filtro específico de excepciones (5xx vs 4xx) |

### Conclusión
La implementación con **Resilience4j** ofrece una solución más robusta, configurable y mantenible que la lógica manual previa, garantizando la protección del ecosistema Switch ante fallos de participantes.
