# Microservicio de Compensación 🏦

## 📌 Descripción General
El **Microservicio de Compensación** es un componente crítico del **Switch Transaccional**. Su función principal es gestionar el proceso de "Settlement" o liquidación neta entre las instituciones financieras participantes del sistema. 

A diferencia de la transacción en tiempo real (que ocurre en el Core del Switch), este microservicio se encarga de consolidar los movimientos, calcular posiciones deudoras/acreedoras y generar los archivos normativos para el Banco Central o entidad regulatoria.

---

## 🏗️ Papel en el Switch Transaccional
Dentro de la arquitectura del Switch, este microservicio actúa en la fase **Post-Procesamiento**:

1.  **Corte de Ciclo:** Permite definir ventanas de tiempo (diarias, por turnos, etc.) para agrupar transacciones.
2.  **Cálculo de Posición Neta:** Determina cuánto dinero debe enviar o recibir cada institución (BIC) basándose en los débitos y créditos acumulados.
3.  **Gestión de Archivos:** Genera y registra los archivos de liquidación (ej. XML, planos) que se envían a la cámara de compensación.
4.  **Auditoría y Estado:** Mantiene la trazabilidad del estado de la liquidación (ABIERTO, CERRADO, ENVIADO).

---

## 🛠️ Tecnologías Utilizadas
- **Java 21** & **Spring Boot 3.5.x**
- **Spring Data JPA:** Persistencia con PostgreSQL.
- **Hibernate Validator:** Validaciones de integridad bancaria.
- **OpenAPI 3 / Swagger:** Documentación interactiva de la API.
- **Lombok:** Reducción de código boilerplate para logs y DTOs.
- **Docker & Docker Compose:** Contenerización y orquestación.

---

## 📂 Estructura del Proyecto
- `model`: Entidades JPA con mappers manuales y lógica de integridad.
- `dto`: Objetos de transferencia de datos validados y documentados.
- `repository`: Interfaces de acceso a datos.
- `service`: Lógica de negocio centralizada (sin interfaces Impl para mayor agilidad).
- `controller`: Endpoints REST bajo estándar bancario.
- `exception`: Manejo global de errores y respuestas estandarizadas.

---

## 🚀 Instalación y Ejecución

### Requisitos Previos
- Docker y Docker Compose
- Maven (o usar el `./mvnw` incluido)

### Pasos para Ejecutar
1. **Construir el proyecto:**
   ```powershell
   ./mvnw clean package -DskipTests
   ```
2. **Levantar contenedores:**
   ```powershell
   docker-compose up --build
   ```
3. **Acceder a la documentación:**
   - Swagger UI: [http://localhost:8084/swagger-ui.html](http://localhost:8081/swagger-ui.html)
   - API Docs: [http://localhost:8084/v3/api-docs](http://localhost:8081/v3/api-docs)

---

## 🔌 API Endpoints (V1)

### Ciclos de Compensación
- `GET /api/v1/compensacion/ciclos`: Lista todos los ciclos.
- `POST /api/v1/compensacion/ciclos`: Crea un nuevo ciclo de corte.
- `GET /api/v1/compensacion/ciclos/{id}`: Detalle de un ciclo específico.

### Posiciones Netas
- `POST /api/v1/compensacion/posiciones`: Registra la posición de una institución.
- `GET /api/v1/compensacion/ciclos/{cicloId}/posiciones`: Consulta posiciones de un ciclo.

### Archivos de Liquidación
- `POST /api/v1/compensacion/archivos`: Registra la generación de un archivo.
- `GET /api/v1/compensacion/ciclos/{cicloId}/archivos`: Lista archivos generados por ciclo.

---

## 🔐 Configuración de Seguridad y DB
El microservicio utiliza variables de entorno para su configuración dinámica (ver `docker-compose.yml`):
- `SPRING_DATASOURCE_URL`: Conexión de base de datos.
- `SPRING_DATASOURCE_PASSWORD`: Credencial configurada como `admin`.

---

## 🤝 Unión con otros Microservicios
Este microservicio suele comunicarse de forma asíncrona o mediante procesos Batch con el **Microservicio Core** del Switch:
1. El Core notifica el fin de una transacción exitosa.
2. El Microservicio de Compensación acumula estos datos en su tabla de `PosicionInstitucion` bajo un `CicloCompensacion` activo.
3. Al finalizar el día, se cierra el ciclo y se activan los procesos de generación de archivos.
