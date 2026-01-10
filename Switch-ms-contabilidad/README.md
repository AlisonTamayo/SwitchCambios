# ms-contabilidad

Microservicio de Contabilidad y Gestión de Saldos para el ecosistema bancario Switch.

## 📋 Descripción

`ms-contabilidad` es un microservicio crítico que gestiona cuentas bancarias y transacciones (débitos y créditos) con estrictas garantías de integridad financiera. Implementa bloqueo optimista para prevenir condiciones de carrera en operaciones concurrentes.

## 🏗️ Arquitectura

### Stack Tecnológico
- **Framework**: Spring Boot 3.2.5
- **Java**: 21
- **Base de Datos**: PostgreSQL (con tipos `NUMERIC` para precisión financiera)
- **ORM**: Spring Data JPA
- **Mapeo**: MapStruct 1.5.5.Final
- **Documentación API**: SpringDoc OpenAPI 2.5.0
- **Validación**: Jakarta Validation

### Principios de Diseño
- **Inyección por Constructor**: Uso de `@RequiredArgsConstructor` (Lombok)
- **Transaccionalidad**: `@Transactional` con estrategias READ_COMMITTED
- **Bloqueo Optimista**: `@Version` en entidad `Cuenta`
- **Tipos Seguros**: `BigDecimal` para montos (NUNCA `double`/`float`)
- **REST Compliance**: Recursos orientados a entidades, sin verbos en URIs

## 📁 Estructura del Proyecto

```
com.switchbank.mscontabilidad
├── modelo/                    # Entidades JPA
│   ├── Cuenta.java           # Entidad principal con @Version
│   ├── Transaccion.java      # Log de auditoría
│   └── TipoOperacion.java    # Enum (DEBITO, CREDITO)
├── repositorio/              # Repositorios JPA
│   ├── CuentaRepository.java
│   └── TransaccionRepository.java
├── servicio/                 # Lógica de negocio
│   └── CuentaService.java    # Validaciones y transaccionalidad
├── controlador/              # API REST
│   └── CuentaController.java # Endpoints documentados
├── dto/                      # Data Transfer Objects
│   ├── CuentaDTO.java
│   └── TransaccionRequestDTO.java
├── mapper/                   # MapStruct
│   └── CuentaMapper.java
└── excepcion/                # Manejo de errores
    ├── SaldoInsuficienteException.java
    ├── CuentaNoEncontradaException.java
    └── ManejadorExcepciones.java
```

## 🚀 API REST

### Base Path
```
/api/v1/cuentas
```

### Endpoints

#### 1. Obtener Cuenta
```http
GET /api/v1/cuentas/{id}
```
**Response 200:**
```json
{
  "id": 1,
  "numeroCuenta": "1234567890",
  "referenciaClienteId": "CLI-001",
  "saldo": 1500.50
}
```

#### 2. Crear Cuenta
```http
POST /api/v1/cuentas
Content-Type: application/json

{
  "numeroCuenta": "1234567890",
  "referenciaClienteId": "CLI-001",
  "saldo": 0.00
}
```

#### 3. Realizar Transacción (DÉBITO o CRÉDITO)
```http
POST /api/v1/cuentas/{id}/transacciones
Content-Type: application/json

{
  "monto": 100.00,
  "tipo": "DEBITO"  // o "CREDITO"
}
```

**Response 201:**
```json
{
  "id": 1,
  "numeroCuenta": "1234567890",
  "referenciaClienteId": "CLI-001",
  "saldo": 1400.50
}
```

**Errores:**
- `400 Bad Request`: Saldo insuficiente o validación fallida
- `404 Not Found`: Cuenta no existe
- `500 Internal Server Error`: Error técnico

## 🗄️ Modelo de Base de Datos

### Tabla: Cuenta
```sql
CREATE TABLE Cuenta (
    id SERIAL PRIMARY KEY,
    numeroCuenta VARCHAR(255) NOT NULL UNIQUE,
    referenciaClienteId VARCHAR(255) NOT NULL,
    saldo NUMERIC(19, 2) NOT NULL,
    version BIGINT
);
```

### Tabla: Transaccion
```sql
CREATE TABLE Transaccion (
    id BIGSERIAL PRIMARY KEY,
    cuentaId INTEGER NOT NULL REFERENCES Cuenta(id),
    monto NUMERIC(19, 2) NOT NULL,
    tipo VARCHAR(50) NOT NULL,
    referenciaUuid VARCHAR(255) NOT NULL,
    fechaCreacion TIMESTAMP
);
```

## 🔧 Configuración

### application.yml (Ejemplo)
```yaml
spring:
  application:
    name: ms-contabilidad
  datasource:
    url: jdbc:postgresql://localhost:5432/contabilidad_db
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

server:
  port: 8080

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
```

## 🐳 Docker

### Build
```bash
docker build -t ms-contabilidad:latest .
```

### Run
```bash
docker run -p 8083:8083 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/contabilidad_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  ms-contabilidad:latest
```

## 🛠️ Desarrollo Local

### Prerrequisitos
- Java 21
- Maven 3.9+
- PostgreSQL 14+

### Compilar
```bash
./mvnw clean package
```

### Ejecutar
```bash
./mvnw spring-boot:run
```

### Documentación API
Una vez iniciado, accede a:
```
http://localhost:8080/swagger-ui.html
```

## 🔗 Integración con Switch Transaccional

### Consideraciones de Integración

1. **Idempotencia**: Usa `referenciaUuid` en las transacciones para evitar duplicados.

2. **Compensación**: En caso de rollback distribuido, implementa endpoints de compensación:
   ```http
   POST /api/v1/cuentas/{id}/transacciones/compensar
   ```

3. **Circuit Breaker**: Configura Resilience4j para manejar fallos:
   ```yaml
   resilience4j:
     circuitbreaker:
       instances:
         contabilidad:
           failure-rate-threshold: 50
           wait-duration-in-open-state: 10s
   ```

4. **Eventos de Dominio**: Publica eventos tras cada transacción exitosa:
   - `CuentaDebitadaEvent`
   - `CuentaAcreditadaEvent`

5. **Correlación**: Propaga `X-Correlation-ID` en headers para trazabilidad.

### Ejemplo de Integración (Saga Pattern)
```java
// En el Orquestador de Switch
@Transactional
public void procesarTransferencia(TransferenciaDTO dto) {
    // 1. Debitar cuenta origen
    cuentaClient.realizarTransaccion(
        dto.getCuentaOrigenId(),
        new TransaccionRequestDTO(dto.getMonto(), TipoOperacion.DEBITO)
    );
    
    // 2. Acreditar cuenta destino
    cuentaClient.realizarTransaccion(
        dto.getCuentaDestinoId(),
        new TransaccionRequestDTO(dto.getMonto(), TipoOperacion.CREDITO)
    );
}
```

## 📝 Reglas de Negocio

1. **Saldo Inicial**: Las cuentas nuevas inician con saldo `0.00` si no se especifica.
2. **Validación de Débito**: Se rechaza si `saldo < monto`.
3. **Auditoría**: Toda transacción se registra en `Transaccion` con UUID único.
4. **Concurrencia**: El campo `version` previene "Lost Updates" mediante optimistic locking.

## 🧪 Testing

### Unit Tests
```bash
./mvnw test
```

### Integration Tests (con Testcontainers)
```bash
./mvnw verify
```

## 📄 Licencia

Proyecto interno - Switch Banking System

## 👥 Contacto

Para dudas sobre integración, contacta al equipo de Arquitectura.
