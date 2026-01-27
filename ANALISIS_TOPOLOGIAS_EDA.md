# Análisis Comparativo de Topologías EDA y Plan de Migración
## Switch Transaccional Scotiabank (V3)

Este documento analiza las dos principales topologías de Arquitectura Orientada a Eventos (EDA) propuestas por la teoría, selecciona la más adecuada para el Switch y define la hoja de ruta para transformar el sistema actual.

---

## 1. Comparativa: Broker vs. Mediador

Para el contexto financiero, la elección entre "Coreografía" (Broker) y "Orquestación" (Mediador) es crítica.

### Opción A: Topología de Broker (Coreografía)
*El "Teléfono Roto" o "Cadena de Relevos".*

*   **Funcionamiento:** `MS-Nucleo` recibe la solicitud y emite un evento `SolicitudCreada`. `MS-Contabilidad` lo escucha, debita, y emite `DebitoRealizado`. `MS-Compensacion` escucha ese y emite `Compensado`. Finalmente un Despachador envía al banco.
*   **Pros:** Desacoplamiento total, escalabilidad extrema.
*   **Contras (Fatales para el Switch):**
    *   **Pérdida de Visibilidad:** Nadie sabe el estado global de la transacción. Si falla el paso 3, ¿quién le avisa al paso 1 que haga rollback?
    *   **Rastreo Difícil:** Debuggear un error requiere correlacionar logs de 5 servicios dispersos.
    *   **Riesgo Financiero:** En banca, necesitamos ACID (Atomicidad). La coreografía es "Eventualmente Consistente", lo cual es arriesgado para saldos en tiempo real.

### Opción B: Topología de Mediador (Orquestación)
*El "Director de Orquesta".*

*   **Funcionamiento:** `MS-Nucleo` actúa como el cerebro central. Recibe la petición y tiene la lógica: *"Primero mando a la cola de Contabilidad. Si respondieron OK, entonces mando a la cola de Bantec. Si Bantec falla, ordeno a la cola de Contabilidad deshacer (Saga)"*.
*   **Pros:**
    *   **Control Centralizado:** `MS-Nucleo` sabe exactamente en qué estado está cada centavo.
    *   **Manejo de Errores (Sagas):** Es fácil implementar lógica de compensación (Reversos) si algo falla.
    *   **Simplicidad para el Cliente:** El Banco Origen solo habla con el Núcleo.
*   **Contras:**
    *   `MS-Nucleo` se convierte en un punto único de fallo (Single Point of Failure), pero se mitiga escalándolo horizontalmente (varias instancias de Nucleo).

---

## 2. El Veredicto: ¿Cuál es la mejor para el Switch?

**🏆 Ganador: Topología de Mediador (Orquestación)**

**Justificación:**
1.  **Seguridad Financiera:** Necesitas un responsable que garantice que si se debitó el dinero, llegue al destino o se devuelva. El Mediador ofrece esa garantía.
2.  **Lógica de Negocio Compleja:** El Switch maneja reglas de enrutamiento, validación de BINs y límites. Esa lógica pertenece a un orquestador central, no distribuida en eventos sueltos.
3.  **Estructura Actual:** Tu sistema actual ya tiene a `MS-Nucleo` como centro. Moverse a Mediador (pero asíncrono) es una evolución natural. Moverse a Broker requeriría reescribir todo desde cero.

---

## 3. Arquitectura Propuesta: El Núcleo como Hub de Eventos

El `MS-Nucleo` dejará de hacer llamadas HTTP directas (Rest Template síncrono) y pasará a **producir Comandos** hacia RabbitMQ.

### Estructura de Colas (El Sistema Nervioso)


### Estructura de Colas (El Sistema Nervioso)

Vamos a configurar un **Topic Exchange** principal: `switch.core.exchange`

#### 1. Colas de Negocio Core (Internas)
Estas desacoplan tus microservicios internos.

| Cola (Queue) | Routing Key | Función | ¿Por qué es necesaria? |
| :--- | :--- | :--- | :--- |
| `q.core.ledger` | `cmd.ledger.#` | Comandos para MS-Contabilidad. | Mueve el dinero en cuentas técnicas. Vital que sea asíncrono. |
| **`q.core.clearing`** | `cmd.clearing.#` | Comandos para MS-Compensacion. | Calcula la deuda neta. Si falla Ledger, Compensación sigue viva. |
| `q.core.audit` | `evt.audit.#` | Auditoría de tráfico. | Guarda logs sin bloquear la operación principal. |

#### 2. Colas de Salida a Bancos (Partners)
Buffers de seguridad para cada socio.

| Cola (Queue) | Routing Key | Función | ¿Por qué es necesaria? |
| :--- | :--- | :--- | :--- |
| `q.banco.arcbank` | `tx.out.arcbank` | Salida para ArcBank. | Buffer de seguridad. |
| `q.banco.bantec` | `tx.out.bantec` | Salida para Bantec. | Buffer de seguridad. |
| `q.banco.nexus` | `tx.out.nexus` | Salida para Nexus. | Buffer de seguridad. |
| `q.banco.ecusol` | `tx.out.ecusol` | Salida para Ecusol. | Buffer de seguridad. |

#### 3. Colas de Seguridad (DLQ - Dead Letter Queues)
**La red de protección**. Si algo falla N veces, cae aquí.

| Cola (Queue) | Routing Key | Función | ¿Por qué es necesaria? |
| :--- | :--- | :--- | :--- |
| **`q.dlq.bancos`** | (Automático) | Mensajes fallidos a bancos. | Si un banco rechaza 100 veces, el mensaje se guarda aquí para análisis manual. |
| **`q.dlq.core`** | (Automático) | Mensajes fallidos internos. | Si Ledger falla por error de código, el dinero se protege aquí. |

---

## 4. Plan de Acción: Implementación en 3 Fases

Para no romper el Switch actual ("Big Bang"), implementaremos los cambios gradualmente.

### FASE 1: Infraestructura y "Fire-and-Forget" (Riesgo Bajo)
*Objetivo: Instalar RabbitMQ y mover procesos que no necesitan respuesta inmediata.*

1.  **Infra:** Agregar `rabbitmq` al `docker-compose.yml`.
2.  **Código:** Agregar dependencias `spring-boot-starter-amqp` en `MS-Nucleo`.
3.  **Refactor (Auditoría):** Actualmente, cada log o auditoría puede estar escribiendo en disco o DB síncronamente.
    *   *Acción:* Crear la cola `q.core.audit`.
    *   *Cambio:* En lugar de guardar el log directo, el Núcleo envía un mensajito a RabbitMQ. Un consumidor ligero guarda en la DB.
    *   *Ganancia:* Reducción de latencia inmediata.

### FASE 2: Desacoplamiento de Servicios Internos (Riesgo Medio)
*Objetivo: Que la Contabilidad y Compensación no frenen al Núcleo.*

1.  **Compensación (Clearing):** El proceso de "Acumular saldo para el corte del día" no necesita ser en tiempo real estricto para la respuesta al usuario.
    *   *Acción:* Mover la llamada a `MS-Compensacion` a la cola `q.internal.clearing`.
    *   *Cambio:* El Núcleo envía el evento "Acumular $50 a Bantec" y sigue su vida. El servicio de Compensación lo procesa milisegundos después.

### FASE 3: Asincronía Total con Bancos (Riesgo Alto - Meta Final)
*Objetivo: Implementar las 4 colas principales de bancos.*

1.  **Receptores (Consumers):** Crear un componente `BankDispatcher` en `MS-Nucleo` (o un microservicio separado pequeño).
    *   Este componente escucha las colas `q.banco.*`.
    *   Toma el mensaje -> Hace el POST al Banco -> Espera respuesta.
2.  **Cambio en `TransaccionServicio`:**
    *   *Antes:* Recibe Tx -> Valida -> Llama a Bantec -> Espera -> Responde al Origen.
    *   *Ahora:* Recibe Tx -> Valida -> **Pone en Cola `q.banco.bantec`** -> Responde `202 ACCEPTED` al Origen inmediatamente.
3.  **El Reto del Polling:**
    *   Como el Switch responde "Aceptado" (pero no "Completado"), los bancos **TIENEN** que usar el endpoint de consulta de estado (`GET /status`) que diseñamos en el RF-04. Esta fase depende de que los bancos implementen bien su pantalla de carga.

---

## Resumen del Plan Técnico

1.  **Levantar RabbitMQ** (Docker).
2.  **Configurar Exchange y 4 Colas** en la clase de Configuración de Spring.
3.  **Crear el Productor** en `MS-Nucleo` (que inyecte `RabbitTemplate`).
4.  **Crear el Consumidor** (Listener) que lea de las colas y ejecute el `RestTemplate` actual.

Esta arquitectura convierte al Switch en un sistema **Elástico**: puede recibir 10,000 transacciones de golpe, encolarlas, y procesarlas a la velocidad que los bancos destino soporten, sin caerse.
