# Propuesta de Evolución: Arquitectura Orientada a Eventos en AWS
**Fase 3: Migración hacia Asincronía y Alta Disponibilidad**

## 1. Análisis de Situación Actual (Síncrono)
Actualmente, el Switch opera bajo un modelo **Orquestación Síncrona (Blocking REST)**.
*   **Flujo:** Banco A -> (Espera) -> Switch -> (Espera) -> Banco B.
*   **Debilidad:** Si Banco B tarda 10 segundos, el hilo del Switch y la conexión del Banco A quedan "secuestrados" por 10 segundos. Esto consume recursos (Threads/Conexiones) y limita la escalabilidad (Efecto avalancha).

## 2. Propuesta: Desacoplamiento con Colas (SQS)
Para la migración a AWS, proponemos eliminar la espera activa utilizando **AWS SQS (Simple Queue Service)**. Esto permite recibir miles de transacciones por segundo sin importar si el Banco B es lento.

### Diseño del Nuevo Flujo (EDA - Event Driven Architecture)

#### Paso 1: Recepción (High Throughput)
*   **Banco A** envía `POST /transfers`.
*   **API Gateway + Lambda (Core):**
    1.  Valida firma y formato (Rápido).
    2.  Guarda estado `RECEIVED` en DB.
    3.  Publica mensaje en Cola **`SQS-PENDING-TRANSFERS`**.
    4.  Responde inmediato al Banco A: `202 Accepted` (En vez de 200 OK). "Tu orden fue recibida, te avisaremos".

#### Paso 2: Procesamiento (Workers)
*   **Componente Worker (ECS/Fargate):**
    1.  Lee mensaje de `SQS-PENDING-TRANSFERS`.
    2.  Enruta (Busca Banco B).
    3.  Intenta enviar a Banco B.
    *   **Caso Éxito:** Banco B responde 200. Worker actualiza DB a `COMPLETED` y envía evento a `SQS-NOTIFICATIONS`.
    *   **Caso Fallo/Timeout:** Worker calcula reintento (Backoff exponencial) y devuelve el mensaje a la cola con un *DelaySeconds*, o lo mueve a una **DLQ (Dead Letter Queue)** si falla 5 veces.

#### Paso 3: Notificación Asíncrona
*   **Notification Service:**
    1.  Lee de `SQS-NOTIFICATIONS`.
    2.  Llama al Webhook del Banco A para confirmar: "La transacción X ya finalizó".

## 3. Justificación Tecnológica (Por qué NO Kafka)

Aunque Apache Kafka es estándar industrial para streaming, para este caso de uso transaccional bancario (Punto a Punto), **AWS SQS** es superior por:

1.  **Simplicidad Operativa:** Kafka requiere gestionar Clusters, Zookeeper y Particiones. SQS es Serverless (solo usas endpoints).
2.  **Manejo de Reintentos (Retries):** SQS tiene soporte nativo para "Visibility Timeout" (volver a poner el mensaje en la cola si el worker falla) y DLQ automática. En Kafka, implementar "Retry queues" es un patrón complejo manual.
3.  **Costo/Beneficio en AWS:** SQS cobra por petición. Para un volumen inicial/medio bancario, es infinitamente más barato y fácil de mantener que un clúster MSK (Managed Kafka).
4.  **Confirmación Individual (ACK):** En transferencias bancarias, cada mensaje es crítico. SQS elimina el mensaje *solo cuando el consumidor confirma éxito*. Kafka trabaja por offsets de bloque, lo cual es más riesgoso si un worker falla a mitad de un bloque.

## 4. Diagrama Lógico

```text
[BANCO A] 
    | (POST)
    v
[API GATEWAY] --> [LAMBDA RECEPTOR] --> (DB: RECEIVED)
                            |
                    [SQS: PENDING QUEUE]
                            |
                    [WORKER SERVICE] <--- (Lee y Reintenta Auto)
                            |
          (POST) ----------------------- (TIMEOUT?)
          |                                  |
    [BANCO B]                      [Backoff / DLQ]
```

Esta arquitectura garantiza que ninguna transacción se pierda por lentitud de un banco y permite escalar los Workers horizontalmente según la carga.
