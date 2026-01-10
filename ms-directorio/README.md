# Microservicio de Directorio y Topología (ms-directorio)

Este microservicio actúa como el **"DNS Bancario"** y Proveedor Maestro del Switch Transaccional. Es el componente crítico encargado de gestionar a los participantes de la red, la seguridad y las reglas de enrutamiento.

El Núcleo Transaccional (Switch) depende 100% de este servicio para saber a qué URL enviar el dinero.

## 🚀 Tecnologías

- **Lenguaje:** Java 21 (Eclipse Temurin)
- **Framework:** Spring Boot 3.x
- **Base de Datos:** PostgreSQL 15 (Dockerizada)
- **Contenedorización:** Docker & Docker Compose
- **Documentación:** OpenAPI / Swagger UI

## 📋 Prerrequisitos

- Tener **Docker Desktop** instalado y corriendo.
- No es necesario tener Java ni Maven instalados en tu máquina local (Docker se encarga de todo).

## 🛠️ Instalación y Ejecución

Este proyecto utiliza *Multi-Stage Building*, por lo que un solo comando compila el código, genera el JAR y levanta la base de datos.

1. Abre una terminal en la raíz del proyecto.
2. Ejecuta el siguiente comando:

   docker-compose up --build

Espera a ver el log de Spring Boot indicando que la aplicación ha iniciado (puede tardar unos minutos la primera vez mientras descarga las dependencias).

🔌 Acceso y Puertos (Local / Docker interno)
- API REST: http://localhost:8081  (mapeo al puerto interno 8080)
- Swagger UI: http://localhost:8081/swagger-ui/index.html
- Base de Datos (Postgres): localhost:5432 (Docker interno 5432)

Credenciales BD:
- user: postgres
- password: admin
- db: bd_directorio

## 🧪 Guía de Pruebas (Datos Semilla)

Para configurar la red bancaria inicial con los 4 bancos del proyecto, utiliza el Swagger UI o Postman con los siguientes JSON.

1. Registrar Bancos (POST /api/v1/instituciones)  
   Ejecuta este endpoint 4 veces, una con cada bloque:

   Banco Nexus

       {
         "codigoBic": "NEXUS_BANK",
         "nombre": "Banco Nexus",
         "urlDestino": "http://nexus-core:8080/api/v1/transacciones/recibir",
         "llavePublica": "public_key_nexus_12345",
         "estadoOperativo": "ONLINE"
       }

   Cooperativa Ecusol

       {
         "codigoBic": "ECUSOL_BK",
         "nombre": "Cooperativa Ecusol",
         "urlDestino": "http://ecusol-core:8080/api/v1/payments/webhook",
         "llavePublica": "public_key_ecusol_67890",
         "estadoOperativo": "ONLINE"
       }

   ArcBank

       {
         "codigoBic": "ARCBANK_01",
         "nombre": "ArcBank Digital",
         "urlDestino": "http://arcbank-service:3000/api/inbound",
         "llavePublica": "public_key_arcbank_aabbc",
         "estadoOperativo": "ONLINE"
       }

   Bantec

       {
         "codigoBic": "BANTEC_FIN",
         "nombre": "Financiera Bantec",
         "urlDestino": "http://bantec-core:8080/api/tx/incoming",
         "llavePublica": "public_key_bantec_ddeef",
         "estadoOperativo": "ONLINE"
       }

2. Crear Reglas de Enrutamiento (POST /api/v1/reglas)  
   Asigna los rangos de tarjetas (BIN) a cada banco:

   Nexus (400001):

       {
         "prefijoBin": "400001",
         "institucion": { "codigoBic": "NEXUS_BANK" }
       }

   Ecusol (500001):

       {
         "prefijoBin": "500001",
         "institucion": { "codigoBic": "ECUSOL_BK" }
       }

   ArcBank (450099):

       {
         "prefijoBin": "450099",
         "institucion": { "codigoBic": "ARCBANK_01" }
       }

   Bantec (550088):

       {
         "prefijoBin": "550088",
         "institucion": { "codigoBic": "BANTEC_FIN" }
       }

3. Probar el Lookup (GET /api/v1/lookup/{bin})  
   Este es el endpoint que usará el Switch.

   Ejemplo:
    - GET /api/v1/lookup/500001

   Resultado esperado:
    - Debe devolver el objeto JSON completo de la Cooperativa Ecusol con su URL de destino.