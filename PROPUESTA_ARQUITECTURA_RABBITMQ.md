# Propuesta de Arquitectura Orientada a Eventos (EDA) con RabbitMQ
## Switch Transaccional Financiero V3

---

## 1. Análisis: ¿Por qué cambiar a Eventos?

Actualmente, el Switch opera bajo un modelo **Sincrónico (Bloqueante)**.
*   **Problema:** Si *Bantec* (Banco Destino) tarda 10 segundos en responder, el hilo en `MS-Nucleo` se queda "congelado" esperando. Si entran 1000 transacciones y Bantec está lento, el Switch colapsa por agotamiento de recursos (Threads).
*   **Solución:** **Arquitectura Orientada a Eventos (EDA)**.
    *   Desacoplamos la recepción del procesamiento.
    *   "Aceptamos" la transferencia instantáneamente (`202 Accepted`).
    *   La procesamos en segundo plano a través de **Colas**.

---

## 2. Decisión Arquitectónica: Topología de Mediador (The Orchestrator)

Basado en la teoría analizada (Ver *Imagen 2: Topología de Mediador*), la arquitectura ideal para un Switch Financiero es la **Topología de Mediador**.

### ¿Por qué NO "Broker" (Coreografía)?
La topología de Broker (reacción en cadena) es peligrosa para dinero. Si el microservicio de Contabilidad emite un evento y nadie lo escucha, el dinero se "pierde".

### ¿Por qué SÍ "Mediador"?
Necesitamos un **Director de Orquesta** (`MS-Nucleo`) que tenga el control absoluto del estado de la transacción.
1.  El núcleo recibe la petición.
2.  El núcleo **envía comandos** a las colas específicas (`q.contabilidad`, `q.bancos`).
3.  El núcleo maneja los errores (Sagas/Compensación) si un paso falla.

---

## 3. Diseño de la Infraestructura RabbitMQ (Reglas de Oro)

Aplicando la **Regla de Oro de RabbitMQ**: *"Nunca escribir directo a la cola, siempre a un Exchange"*.

### A. El Exchange Principal (El Distribuidor)
Usaremos un **Topic Exchange** llamado `switch.tx.exchange`.
*   **Justificación:** Nos permite enrutamiento flexible usando *wildcards* (Ver *Imagen 1: Tipo C*). Podemos enviar mensajes a un banco específico (`tx.to.bantec`) o eventos de auditoría global (`tx.*.log`).

### B. Colas Externas (Comunicación con Bancos)
Tal como se solicitó, definimos 4 colas persistentes para "bufferizar" el tráfico hacia los socios. Esto protege al Switch si un banco se cae.

| Cola (Queue) | Binding Key (Routing Key) | Propósito |
| :--- | :--- | :--- |
| `q.out.arcbank` | `bank.arcbank` | Mensajes salientes hacia ArcBank |
| `q.out.bantec` | `bank.bantec` | Mensajes salientes hacia Bantec |
| `q.out.nexus` | `bank.nexus` | Mensajes salientes hacia Nexus |
| `q.out.ecusol` | `bank.ecusol` | Mensajes salientes hacia Ecusol |

**Funcionamiento:**
1.  `MS-Nucleo` termina su proceso interno.
2.  Publica mensaje al Exchange con Routing Key `bank.bantec`.
3.  RabbitMQ enruta el mensaje a `q.out.bantec`.
4.  Un **Dispatcher (Despachador)** lee de esa cola y hace el `POST` al Webhook de Bantec. *Si Bantec está caído, el mensaje se queda seguro en la cola para reintentar luego.*

### C. Colas Internas (Microservicios)
Para reducir la latencia interna del Switch.

| Cola (Queue) | Binding Key | Propósito |
| :--- | :--- | :--- |
| `q.internal.ledger` | `cmd.ledger` | Comandos de débito/crédito asíncronos para MS-Contabilidad. |
| `q.internal.clearing` | `cmd.clearing` | Acumulación de saldos para MS-Compensacion (No necesitamos esperar respuesta). |
| `q.internal.audit` | `log.#` | Auditoría ciega. Guarda todo lo que pasa sin bloquear la operación principal. |

---

## 4. Flujo de Vida de una Transacción (Propuesta V3)

### Paso 1: Recepción (API Gateway)
1.  **Banco Origen** envía `POST /transfer`.
2.  `MS-Nucleo` valida formato básico.
3.  **ACCIÓN:** Publica evento `tx.received` en RabbitMQ.
4.  **RESPUESTA:** Retorna `HTTP 202 Accepted` al Banco Origen (con un `instructionId`). *Tiempo total: 50ms (vs 2000ms actuales).*

### Paso 2: Orquestación (Procesamiento de Fondo)
1.  `MS-Nucleo` (Consumer) lee el evento.
2.  **Comando Síncrono:** Valida fondos y fraude (Esto debe ser rápido y estricto).
3.  **Comando Asíncrono:** Publica en `q.internal.ledger` para reservar fondos.

### Paso 3: Despacho al Banco Destino
1.  `MS-Nucleo` determina que el destino es **BANTEC**.
2.  Publica mensaje al Exchange con key `bank.bantec`.
3.  El mensaje cae en la cola `q.out.bantec`.
4.  **Worker de Salida:**
    *   Toma el mensaje.
    *   Intenta contactar al Webhook de Bantec.
    *   Si Bantec responde OK -> Publica evento `tx.completed`.
    *   Si Bantec falla -> Devuelve mensaje a la cola (Retry con backoff exponencial).

---

## 5. Pros y Contras de esta Arquitectura

### ✅ Ventajas (Pros)
1.  **Alta Resiliencia:** Si Bantec se cae (pasa a modo Offline), el Switch **NO** falla. Guarda las transferencias en `q.out.bantec` y las entrega cuando Bantec vuelva.
2.  **Escalabilidad:** Podemos tener 10 workers procesando la cola de Bantec y solo 1 para Ecusol si el volumen varía.
3.  **Desacoplamiento:** El banco Origen no se queda esperando con el spinner cargando 10 segundos. Recibe "Recibido" y luego consulta el estado (Polling).
4.  **Manejo de Picos:** Si es quincena y llegan 10,000 tx/seg, las colas absorben el impacto y el sistema las procesa a su ritmo (Backpressure).

### ❌ Desventajas (Contras)
1.  **Complejidad:** Requiere mantener un servidor RabbitMQ.
2.  **Eventual Consistency:** La respuesta no es inmediata. El Banco Origen debe estar preparado para recibir un estado "PENDING" y consultar después.
3.  **Depuración:** Es más difícil rastrear un error ya que el log está distribuido en varios pasos asíncronos.

---

## 6. Conclusión y Recomendación

Se recomienda adoptar la **Topología de Mediador** utilizando un **Topic Exchange** central.

**Para los Bancos (Integration Pattern):**
La implementación más segura para ellos es que el Switch actúe como buffer.
*   **Switch -> Banco Destino:** Usamos las 4 colas propuestas (`q.out.X`). Esto garantiza que nunca perderemos una transferencia enviada a ellos, incluso si sus servidores reinician.
