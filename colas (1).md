# Guia Tecnica de Integracion - Sistema de Colas (RabbitMQ)

## Resumen
Se ha implementado una arquitectura basada en mensajeria asincrona utilizando RabbitMQ en AWS (Amazon MQ). 
El objetivo es orquestar la comunicacion transaccional entre las entidades financieras del ecosistema garantizando la entrega, persistencia y ordenamiento de los mensajes.

El sistema utiliza el patron Direct Exchange con enrutamiento dinamico y Dead Letter Exchanges (DLX) para la gestion automatizada de fallos, asegurando que ninguna transaccion se pierda por indisponibilidad momentanea de los participantes.

---

## 1. Credenciales y Conectividad

| Parametro | Valor |
|-----------|-------|
| Consola de Gestion | https://b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws/ |
| Endpoint AMQPS | amqps://b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws:5671 |
| Virtual Host | / |
| Usuario | Asignado individualmente por entidad (Solicitar a DigiconEcu) |
| Protocolo | AMQP 0-9-1 sobre TLS 1.2 (SSL obligatorio) |

Nota de Seguridad: El puerto estandar 5672 esta deshabilitado. Es obligatorio el uso del puerto 5671 con cifrado SSL/TLS.

---

## 2. Arquitectura del Sistema

El flujo de informacion se divide en dos carriles:
1.  Ida (Push): Mensajeria asincrona via RabbitMQ para la solicitud de transferencia.
2.  Vuelta (Callback): Llamada HTTP POST (Webhook) para la confirmacion del estado.

```text
┌─────────────────────────────────────────────────────────────────────┐
│                        AWS (us-east-2)                              │
│                                                                     │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │               Amazon MQ (RabbitMQ Cluster)                  │   │
│   │                                                             │   │
│   │  [Exchange Principal]: ex.transfers.tx                      │   │
│   │          │                                                  │   │
│   │          ├──(Routing Key: NEXUS)──> [q.bank.NEXUS.in]       │   │
│   │          ├──(Routing Key: BANTEC)─> [q.bank.BANTEC.in]      │   │
│   │          └──(Routing Key: ...)────> [q.bank.ARCBANK.in]     │   │
│   │                                                             │   │
│   │  [Dead Letter Exchange]: ex.transfers.dlx (Errores)         │   │
│   │          │                                                  │   │
│   │          └──> [q.bank.NEXUS.dlq] (Auditoria Manual)         │   │
│   └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
         ↑                    ↑                    ↑
         │                    │                    │
    ┌────┴────┐          ┌────┴────┐          ┌────┴────┐
    │ BANTEC  │          │ SWITCH  │          │  NEXUS  │
    │ (Google)│          │ (Google)│          │  (AWS)  │
    └─────────┘          └─────────┘          └─────────┘


---

## 3. Justificacion Tecnica: Modelo de Seguridad y Gestion de Usuarios
Para cumplir con los estandares de seguridad de la informacion financiera (confidencialidad e integridad), se ha implementado un modelo de Aislamiento Logico Estricto basado en ACLs (Listas de Control de Acceso) internas de RabbitMQ.

### 3.1. Principio de Minimo Privilegio (Least Privilege)
La configuracion de usuarios no se basa en roles genericos, sino en expresiones regulares (Regex) que actuan como firewall a nivel de aplicacion:

Permiso de Lectura (Read): ^q\.bank\.MI_BANCO\..*

Esto garantiza matematicamente que el Banco A no puede consumir, ni siquiera accidentalmente, los mensajes destinados al Banco B. Cualquier intento de suscripcion a una cola ajena resultara en un error 403 ACCESS_REFUSED inmediato por parte del broker.

Permiso de Escritura (Write): ^(ex\.transfers\.tx|q\.bank\.MI_BANCO\..*)$

Permite al banco publicar en el Exchange transaccional (necesario para el flujo) y gestionar sus propias colas, pero impide inyectar mensajes directamente en las colas privadas de otros participantes.

3.2. Trazabilidad y Auditoria
Al utilizar credenciales nominadas (nexus_user, bantec_user), cada conexion, canal abierto y mensaje consumido queda registrado en los logs de auditoria de AWS CloudWatch asociado a una identidad especifica. Esto permite un analisis forense preciso en caso de incidentes, identificando inequivocamente el origen de cada accion.

3.3. Encriptacion en Transito
Se ha forzado el uso del protocolo AMQPS (Puerto 5671). Esto asegura que el payload de la transaccion (datos sensibles, montos, cuentas) viaje encriptado mediante TLS 1.2 desde el servidor on-premise/cloud del banco hasta la infraestructura de Amazon MQ, mitigando ataques de tipo Man-in-the-Middle.


4. Guia Bancos

A continuacion se detallan las configuraciones requeridas para integrar con el bus de mensajeria.

4.1. Configuracion application.yml
Es critico habilitar la capa SSL y configurar la politica de reintentos exponencial para manejar micro-cortes sin perder transacciones.

YAML
spring:
  rabbitmq:
    host: b-455e546c-be71-4fe2-ba0f-bd3112e6c220.mq.us-east-2.on.aws
    port: 5671  # Puerto Seguro Obligatorio
    username: ${RABBITMQ_USER}      # Credencial suministrada
    password: ${RABBITMQ_PASSWORD}  # Credencial suministrada
    virtual-host: /
    ssl:
      enabled: true
      algorithm: TLSv1.2
    listener:
      simple:
        acknowledge-mode: auto
        # Importante: Si false, el mensaje rechazado va al DLQ. Si true, vuelve a la cola infinita.
        default-requeue-rejected: false 
        retry:
          enabled: true
          max-attempts: 4         # Intento inicial + 3 reintentos
          initial-interval: 800ms # Primer delay
          multiplier: 2.5         # Factor de crecimiento (800ms -> 2s -> 5s)
          max-interval: 5000ms
4.2. Publicacion de Mensajes (Switch / Origen)
Las aplicaciones no deben enviar mensajes directamente a las colas (queues). Deben enviarlos al Exchange utilizando la Routing Key correspondiente al banco destino.

Tabla de Routing Keys:

Banco Nexus: NEXUS

Banco Bantec: BANTEC

ArcBank: ARCBANK

Ecusol: ECUSOL

Java
// Ejemplo Java: Envio al Switch
String exchange = "ex.transfers.tx";
String routingKey = "BANTEC"; // Variable segun el destino
TransferenciaDTO payload = new TransferenciaDTO(...);

rabbitTemplate.convertAndSend(exchange, routingKey, payload);
4.3. Consumo de Mensajes (Banco Destino)
El banco debe configurar su listener escuchando exclusivamente su cola de entrada .in.

Java
@Component
public class TransferenciaListener {

    @RabbitListener(queues = "q.bank.NEXUS.in") // Reemplazar con su cola asignada
    public void recibirTransferencia(TransferenciaDTO mensaje) {
        try {
            // 1. Logica de negocio (Core Bancario)
            servicioBancario.procesarDeposito(mensaje);
            
            // 2. Confirmacion al origen (Webhook)
            clienteHttp.confirmarTransaccion(mensaje.getOrigenUrl(), mensaje.getId());
            
        } catch (ExcepcionNegocio e) {
            // Si ocurre un error, lanzar excepcion.
            // Spring aplicara los reintentos configurados en el yml.
            // Si fallan los 4 intentos, el mensaje ira al DLQ.
            throw new AmqpRejectAndDontRequeueException("Error procesando tx", e);
        }
    }
}


## 5. Gestion de Errores y Dead Letter Queue (DLQ)
Para garantizar la resiliencia, se ha configurado un Time-To-Live (TTL) y una redireccion automatica de fallos.
Si un mensaje llega a la cola q.bank.NEXUS.in y no es procesado correctamente tras los 4 intentos de la aplicacion, o expira el TTL:
El mensaje es retirado de la cola principal.
Es republicado automaticamente en el exchange ex.transfers.dlx.
Se almacena finalmente en q.bank.NEXUS.dlq.
Accion requerida: Los equipos de operaciones deben monitorear las colas .dlq para identificar transacciones que requieren intervencion manual o reprocesamiento.
