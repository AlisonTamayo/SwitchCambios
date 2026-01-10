# 📚 DOCUMENTACIÓN TÉCNICA COMPLETA - ms-contabilidad

## 🎯 Propósito de este Documento

Este documento te permitirá **defender cada línea de código** del microservicio. Aquí encontrarás:
- ✅ Qué hace cada clase y método
- ✅ A quién llama y quién lo llama
- ✅ Por qué se tomaron decisiones técnicas específicas
- ✅ Respuestas a preguntas técnicas comunes

---

## 📊 ARQUITECTURA GENERAL

### Flujo de Datos Completo

```
HTTP Request → CuentaController → CuentaService → CuentaRepository → PostgreSQL
                      ↓                  ↓              ↓
                 Validación        Lógica Negocio   Persistencia
                      ↓                  ↓              ↓
              ManejadorExcepciones  Transacciones  Bloqueo Optimista
```

### Capas del Sistema

1. **Capa de Presentación (API REST)**: `CuentaController`
2. **Capa de Negocio**: `CuentaService`
3. **Capa de Persistencia**: `CuentaRepository`, `TransaccionRepository`
4. **Capa de Modelo**: `Cuenta`, `Transaccion`, `TipoOperacion`
5. **Capa de Transferencia**: `CuentaDTO`, `TransaccionRequestDTO`
6. **Capa de Mapeo**: `CuentaMapper`
7. **Capa de Manejo de Errores**: `ManejadorExcepciones`

---

## 🏗️ ANÁLISIS DETALLADO POR CLASE

---

## 1️⃣ CuentaController.java

### 📍 Ubicación
`com.switchbank.mscontabilidad.controlador.CuentaController`

### 🎯 Responsabilidad
**Punto de entrada HTTP** para todas las operaciones de cuentas. Expone 3 endpoints REST.

### 🔗 Dependencias Inyectadas
```java
private final CuentaService cuentaService;
```
- **Inyección por Constructor** (`@RequiredArgsConstructor` de Lombok)
- **Por qué**: Inmutabilidad, facilita testing, Spring recomienda constructor injection

### 📌 Anotaciones de Clase
```java
@RestController          // Marca como controlador REST (respuestas JSON automáticas)
@RequestMapping("/api/v1/cuentas")  // Base path para todos los endpoints
@RequiredArgsConstructor // Genera constructor con dependencias final
@Tag(name = "Controlador de Cuentas", description = "...") // Documentación Swagger
```

---

### 🔵 MÉTODO 1: obtenerCuenta()

```java
@GetMapping("/{id}")
public ResponseEntity<CuentaDTO> obtenerCuenta(@PathVariable Integer id)
```

#### ¿Qué hace?
Recupera los detalles de una cuenta por su ID.

#### ¿A quién llama?
1. `cuentaService.obtenerCuentaPorId(id)` → Delega al servicio

#### ¿Quién lo llama?
- Clientes HTTP externos (Postman, otros microservicios, frontend)

#### Flujo Completo
```
GET /api/v1/cuentas/1
    ↓
CuentaController.obtenerCuenta(1)
    ↓
CuentaService.obtenerCuentaPorId(1)
    ↓
CuentaRepository.findById(1)
    ↓
PostgreSQL: SELECT * FROM Cuenta WHERE id = 1
    ↓
CuentaMapper.aDTO(cuenta)
    ↓
ResponseEntity.ok(cuentaDTO)
```

#### Respuestas HTTP
- **200 OK**: Cuenta encontrada (devuelve `CuentaDTO`)
- **404 NOT FOUND**: Cuenta no existe (manejado por `ManejadorExcepciones`)

#### Preguntas Frecuentes
**P: ¿Por qué devuelves `ResponseEntity<CuentaDTO>` y no solo `CuentaDTO`?**
R: `ResponseEntity` permite controlar el código HTTP (200, 404, etc.) y headers personalizados.

**P: ¿Por qué usas `@PathVariable` en vez de `@RequestParam`?**
R: REST recomienda IDs en la ruta (`/cuentas/1`) en vez de query params (`/cuentas?id=1`).

---

### 🟢 MÉTODO 2: crearCuenta()

```java
@PostMapping
public ResponseEntity<CuentaDTO> crearCuenta(@RequestBody CuentaDTO cuentaDTO)
```

#### ¿Qué hace?
Crea una nueva cuenta bancaria en el sistema.

#### ¿A quién llama?
1. `cuentaService.crearCuenta(cuentaDTO)` → Delega al servicio

#### Flujo Completo
```
POST /api/v1/cuentas
Body: {"numeroCuenta": "123", "referenciaClienteId": "CLI-001", "saldo": 100.00}
    ↓
CuentaController.crearCuenta(cuentaDTO)
    ↓
CuentaService.crearCuenta(cuentaDTO)
    ↓
CuentaMapper.aEntidad(cuentaDTO)  // DTO → Entidad
    ↓
if (saldo == null) → saldo = 0.00  // Regla de negocio
    ↓
CuentaRepository.save(cuenta)
    ↓
PostgreSQL: INSERT INTO Cuenta (...)
    ↓
CuentaMapper.aDTO(cuentaGuardada)  // Entidad → DTO
    ↓
ResponseEntity.ok(cuentaDTO)
```

#### Respuestas HTTP
- **200 OK**: Cuenta creada exitosamente
- **400 BAD REQUEST**: Datos inválidos (ej. número de cuenta duplicado)

#### Preguntas Frecuentes
**P: ¿Por qué no usas `@Valid` en `@RequestBody`?**
R: `CuentaDTO` no tiene validaciones Jakarta (`@NotNull`, etc.). Si las tuviera, deberías agregar `@Valid`.

**P: ¿Qué pasa si envío un `saldo` negativo?**
R: Actualmente se permite. Deberías agregar `@PositiveOrZero` en `CuentaDTO.saldo` si quieres validarlo.

---

### 🟣 MÉTODO 3: realizarTransaccion()

```java
@PostMapping("/{id}/transacciones")
public ResponseEntity<CuentaDTO> realizarTransaccion(
    @PathVariable Integer id,
    @Valid @RequestBody TransaccionRequestDTO request)
```

#### ¿Qué hace?
Ejecuta un DÉBITO o CRÉDITO en una cuenta existente.

#### ¿A quién llama?
1. **Si es DÉBITO**: `cuentaService.debitar(id, monto)`
2. **Si es CRÉDITO**: `cuentaService.acreditar(id, monto)`

#### Flujo Completo (DÉBITO)
```
POST /api/v1/cuentas/1/transacciones
Body: {"monto": 50.00, "tipo": "DEBITO"}
    ↓
CuentaController.realizarTransaccion(1, request)
    ↓
if (tipo == DEBITO) → CuentaService.debitar(1, 50.00)
    ↓
CuentaRepository.findById(1)  // Busca cuenta
    ↓
if (saldo < monto) → throw SaldoInsuficienteException  // Validación
    ↓
cuenta.setSaldo(saldo - monto)  // 100 - 50 = 50
    ↓
CuentaRepository.save(cuenta)  // UPDATE Cuenta SET saldo = 50 WHERE id = 1
    ↓
registrarTransaccion(cuenta, 50, DEBITO)  // Auditoría
    ↓
TransaccionRepository.save(transaccion)  // INSERT INTO Transaccion (...)
    ↓
CuentaMapper.aDTO(cuentaActualizada)
    ↓
ResponseEntity(resultado, HttpStatus.CREATED)  // 201
```

#### Respuestas HTTP
- **201 CREATED**: Transacción exitosa
- **400 BAD REQUEST**: Saldo insuficiente o monto inválido
- **404 NOT FOUND**: Cuenta no existe

#### Preguntas Frecuentes
**P: ¿Por qué usas `if/else` en vez de un patrón Strategy?**
R: Para 2 casos (DÉBITO/CRÉDITO), `if/else` es más simple. Si tuvieras 5+ tipos, considera Strategy Pattern.

**P: ¿Por qué devuelves `HttpStatus.CREATED` (201) en vez de `OK` (200)?**
R: REST recomienda `201` para operaciones que crean recursos (en este caso, una `Transaccion`).

**P: ¿Qué pasa si dos usuarios debitan la misma cuenta al mismo tiempo?**
R: El campo `@Version` en `Cuenta` activa **bloqueo optimista**. La segunda transacción fallará con `OptimisticLockException`.

---

## 2️⃣ CuentaService.java

### 📍 Ubicación
`com.switchbank.mscontabilidad.servicio.CuentaService`

### 🎯 Responsabilidad
**Lógica de negocio** para operaciones de cuentas. Maneja transaccionalidad, validaciones y auditoría.

### 🔗 Dependencias Inyectadas
```java
private final CuentaRepository cuentaRepository;
private final TransaccionRepository transaccionRepository;
private final CuentaMapper cuentaMapper;
```

### 📌 Anotaciones de Clase
```java
@Service              // Marca como componente de servicio
@RequiredArgsConstructor  // Inyección por constructor
```

---

### 🔵 MÉTODO 1: obtenerCuentaPorId()

```java
@Transactional(readOnly = true)
public CuentaDTO obtenerCuentaPorId(Integer id)
```

#### ¿Qué hace?
Busca una cuenta por ID y la convierte a DTO.

#### ¿A quién llama?
1. `Objects.requireNonNull(id, ...)` → Validación null
2. `cuentaRepository.findById(id)` → Consulta BD
3. `cuentaMapper.aDTO(cuenta)` → Conversión Entidad → DTO
4. `orElseThrow(...)` → Lanza excepción si no existe

#### Anotaciones Clave
- **`@Transactional(readOnly = true)`**: 
  - **Por qué**: Optimización. Le dice a Hibernate que no habrá escrituras.
  - **Beneficio**: Mejora performance, evita flush innecesarios.

#### Preguntas Frecuentes
**P: ¿Por qué usas `Objects.requireNonNull()` si Spring ya valida?**
R: Defensa en profundidad. Si alguien llama al servicio directamente (sin pasar por el controller), falla rápido.

**P: ¿Qué es `Optional.map()`?**
R: Si `findById()` devuelve un `Optional<Cuenta>`, `map()` lo transforma a `Optional<CuentaDTO>`.

---

### 🟢 MÉTODO 2: crearCuenta()

```java
@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
public CuentaDTO crearCuenta(CuentaDTO cuentaDTO)
```

#### ¿Qué hace?
Crea una nueva cuenta, aplicando regla de negocio (saldo inicial = 0 si no se especifica).

#### ¿A quién llama?
1. `Objects.requireNonNull(cuentaDTO, ...)` → Validación
2. `cuentaMapper.aEntidad(cuentaDTO)` → DTO → Entidad
3. `if (saldo == null) → setSaldo(BigDecimal.ZERO)` → Regla de negocio
4. `cuentaRepository.save(cuenta)` → INSERT en BD
5. `cuentaMapper.aDTO(cuentaGuardada)` → Entidad → DTO

#### Anotaciones Clave
- **`propagation = Propagation.REQUIRED`**: 
  - Si ya hay una transacción activa, úsala. Si no, crea una nueva.
- **`isolation = Isolation.READ_COMMITTED`**: 
  - Evita lecturas sucias (dirty reads).
  - **Por qué**: Balance entre consistencia y performance.

#### Preguntas Frecuentes
**P: ¿Por qué no usas `@Transactional` sin parámetros?**
R: Los valores por defecto son `REQUIRED` y `DEFAULT` (depende de la BD). Ser explícito evita sorpresas.

**P: ¿Qué pasa si `numeroCuenta` ya existe?**
R: PostgreSQL lanza `DataIntegrityViolationException` (constraint UNIQUE). Spring lo convierte en `400 BAD REQUEST`.

---

### 🟣 MÉTODO 3: debitar()

```java
@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
public CuentaDTO debitar(Integer cuentaId, BigDecimal monto)
```

#### ¿Qué hace?
Resta dinero de una cuenta, validando saldo suficiente.

#### ¿A quién llama?
1. `Objects.requireNonNull(cuentaId, ...)` → Validación
2. `Objects.requireNonNull(monto, ...)` → Validación
3. `cuentaRepository.findById(cuentaId)` → Busca cuenta
4. `if (saldo.compareTo(monto) < 0)` → Validación negocio
5. `cuenta.setSaldo(saldo.subtract(monto))` → Actualiza saldo
6. `cuentaRepository.save(cuenta)` → UPDATE en BD
7. `registrarTransaccion(...)` → Auditoría
8. `cuentaMapper.aDTO(...)` → Conversión

#### Validaciones Críticas
```java
if (cuenta.getSaldo().compareTo(monto) < 0) {
    throw new SaldoInsuficienteException("...");
}
```
- **Por qué `compareTo()` en vez de `<`**: `BigDecimal` no soporta operadores. `compareTo()` devuelve:
  - `-1` si saldo < monto
  - `0` si saldo == monto
  - `1` si saldo > monto

#### Preguntas Frecuentes
**P: ¿Por qué usas `BigDecimal` en vez de `double`?**
R: **CRÍTICO**. `double` tiene errores de redondeo (ej. `0.1 + 0.2 = 0.30000000000000004`). En finanzas, usa SIEMPRE `BigDecimal`.

**P: ¿Qué pasa si dos usuarios debitan al mismo tiempo?**
R: El campo `@Version` previene "Lost Updates":
1. Usuario A lee cuenta (version=1, saldo=100)
2. Usuario B lee cuenta (version=1, saldo=100)
3. Usuario A debita 50 → UPDATE ... WHERE version=1 (version=2, saldo=50) ✅
4. Usuario B debita 30 → UPDATE ... WHERE version=1 ❌ (falla, version ya es 2)

---

### 🟠 MÉTODO 4: acreditar()

```java
@Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
public CuentaDTO acreditar(Integer cuentaId, BigDecimal monto)
```

#### ¿Qué hace?
Suma dinero a una cuenta (sin validación de saldo).

#### Diferencias con `debitar()`
- ✅ **NO valida saldo** (puedes acreditar cualquier monto)
- ✅ Usa `add()` en vez de `subtract()`

#### Preguntas Frecuentes
**P: ¿Por qué no validas que el monto sea positivo?**
R: La validación está en `TransaccionRequestDTO` con `@Positive`. Si alguien llama al servicio directamente, debería agregarse aquí también.

---

### 🔴 MÉTODO 5: registrarTransaccion() (PRIVADO)

```java
private void registrarTransaccion(Cuenta cuenta, BigDecimal monto, TipoOperacion tipo)
```

#### ¿Qué hace?
Crea un registro de auditoría en la tabla `Transaccion`.

#### ¿A quién llama?
1. `new Transaccion(cuenta, monto, tipo, UUID.randomUUID().toString())` → Crea entidad
2. `transaccionRepository.save(log)` → INSERT en BD

#### Preguntas Frecuentes
**P: ¿Por qué usas `UUID.randomUUID()`?**
R: Genera un identificador único para **idempotencia**. Si la misma transacción se reintenta, puedes detectar duplicados.

**P: ¿Por qué es `private`?**
R: Solo debe llamarse internamente después de actualizar el saldo. No debe exponerse públicamente.

---

## 3️⃣ ManejadorExcepciones.java

### 📍 Ubicación
`com.switchbank.mscontabilidad.excepcion.ManejadorExcepciones`

### 🎯 Responsabilidad
**Interceptor global de excepciones**. Convierte excepciones Java en respuestas HTTP JSON.

### 📌 Anotaciones de Clase
```java
@RestControllerAdvice  // Intercepta excepciones de todos los @RestController
```

---

### 🔵 MÉTODO 1: manejarSaldoInsuficiente()

```java
@ExceptionHandler(SaldoInsuficienteException.class)
public ResponseEntity<Map<String, Object>> manejarSaldoInsuficiente(SaldoInsuficienteException ex)
```

#### ¿Qué hace?
Captura `SaldoInsuficienteException` y devuelve `400 BAD REQUEST`.

#### Respuesta JSON
```json
{
  "codigo": 400,
  "mensaje": "Saldo insuficiente para realizar el débito",
  "marca_tiempo": "2025-12-25T17:00:00"
}
```

#### Preguntas Frecuentes
**P: ¿Por qué devuelves `Map<String, Object>` en vez de una clase `ErrorResponse`?**
R: Flexibilidad. Para producción, considera crear una clase `ErrorDTO` para consistencia.

---

### 🟢 MÉTODO 2: manejarCuentaNoEncontrada()

```java
@ExceptionHandler(CuentaNoEncontradaException.class)
public ResponseEntity<Map<String, Object>> manejarCuentaNoEncontrada(CuentaNoEncontradaException ex)
```

#### ¿Qué hace?
Captura `CuentaNoEncontradaException` y devuelve `404 NOT FOUND`.

---

### 🟣 MÉTODO 3: manejarExcepcionGeneral()

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> manejarExcepcionGeneral(Exception ex)
```

#### ¿Qué hace?
**Catch-all** para cualquier excepción no manejada. Devuelve `500 INTERNAL SERVER ERROR`.

#### Preguntas Frecuentes
**P: ¿No es peligroso exponer `ex.getMessage()` al cliente?**
R: **SÍ**. En producción, deberías:
1. Loggear el error completo
2. Devolver un mensaje genérico al cliente
3. Usar un ID de correlación para rastrear el error

---

## 4️⃣ Cuenta.java (Entidad)

### 📍 Ubicación
`com.switchbank.mscontabilidad.modelo.Cuenta`

### 🎯 Responsabilidad
**Entidad JPA** que representa una cuenta bancaria en la BD.

### 📌 Anotaciones de Clase
```java
@Entity              // Marca como entidad JPA
@Table(name = "Cuenta")  // Mapea a tabla "Cuenta"
@Getter              // Genera getters (Lombok)
@Setter              // Genera setters (Lombok)
```

### 🔗 Campos

#### 1. `id` (Primary Key)
```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Integer id;
```
- **`IDENTITY`**: PostgreSQL auto-incrementa el ID.

#### 2. `numeroCuenta` (Unique)
```java
@Column(name = "numerocuenta", nullable = false, unique = true)
private String numeroCuenta;
```
- **`unique = true`**: No puede haber dos cuentas con el mismo número.
- **`name = "numerocuenta"`**: Nombre en minúsculas para compatibilidad con PostgreSQL.

#### 3. `saldo` (BigDecimal)
```java
@Column(name = "saldo", nullable = false, precision = 19, scale = 2)
private BigDecimal saldo;
```
- **`precision = 19, scale = 2`**: Hasta 17 dígitos enteros y 2 decimales (ej. `99999999999999999.99`).

#### 4. `version` (Optimistic Locking)
```java
@Version
@Column(name = "version")
private Long version;
```
- **`@Version`**: Hibernate incrementa este campo en cada UPDATE.
- **Uso**: Previene "Lost Updates" en concurrencia.

### Preguntas Frecuentes
**P: ¿Por qué `equals()` y `hashCode()` solo usan `id`?**
R: Identidad de entidad. Dos cuentas son iguales si tienen el mismo ID, aunque otros campos difieran.

**P: ¿Por qué no usas `@AllArgsConstructor` de Lombok?**
R: JPA requiere un constructor vacío. Lombok generaría uno con todos los campos, rompiendo JPA.

---

## 5️⃣ Transaccion.java (Entidad)

### 📍 Ubicación
`com.switchbank.mscontabilidad.modelo.Transaccion`

### 🎯 Responsabilidad
**Registro de auditoría** de todas las transacciones (débitos y créditos).

### 🔗 Relaciones

#### Relación con `Cuenta`
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "cuentaid", nullable = false)
private Cuenta cuenta;
```
- **`LAZY`**: No carga la cuenta automáticamente (mejora performance).
- **`@JoinColumn`**: Crea FK `cuentaid` → `Cuenta.id`.

### 📌 Callbacks JPA

#### `@PrePersist`
```java
@PrePersist
protected void alCrear() {
    fechaCreacion = LocalDateTime.now();
}
```
- **Cuándo se ejecuta**: Antes de `INSERT`.
- **Uso**: Timestamp automático.

### Preguntas Frecuentes
**P: ¿Por qué `FetchType.LAZY` en vez de `EAGER`?**
R: `LAZY` evita el problema N+1. Solo carga la cuenta si accedes a `transaccion.getCuenta()`.

---

## 6️⃣ CuentaDTO.java

### 📍 Ubicación
`com.switchbank.mscontabilidad.dto.CuentaDTO`

### 🎯 Responsabilidad
**Data Transfer Object** para exponer datos de cuenta al cliente.

### 📌 Anotaciones de Clase
```java
@Data               // Genera getters, setters, equals, hashCode, toString
@Builder            // Patrón Builder (ej. CuentaDTO.builder().id(1).build())
@NoArgsConstructor  // Constructor vacío (requerido por Jackson)
@AllArgsConstructor // Constructor con todos los campos (requerido por Builder)
```

### Preguntas Frecuentes
**P: ¿Por qué no expones `version` en el DTO?**
R: Es un detalle de implementación (bloqueo optimista). El cliente no necesita saberlo.

**P: ¿Por qué usas DTO en vez de devolver la entidad directamente?**
R: 
1. **Seguridad**: No expones campos sensibles (ej. `version`).
2. **Desacoplamiento**: Puedes cambiar la entidad sin romper la API.
3. **Performance**: Evitas lazy loading issues.

---

## 7️⃣ TransaccionRequestDTO.java

### 📍 Ubicación
`com.switchbank.mscontabilidad.dto.TransaccionRequestDTO`

### 🎯 Responsabilidad
**DTO de entrada** para solicitudes de transacción.

### 📌 Validaciones Jakarta

```java
@NotNull(message = "El monto es obligatorio")
@Positive(message = "El monto debe ser positivo")
private BigDecimal monto;

@NotNull(message = "El tipo de operación es obligatorio (DEBITO, CREDITO)")
private TipoOperacion tipo;
```

### Preguntas Frecuentes
**P: ¿Cuándo se ejecutan estas validaciones?**
R: Cuando usas `@Valid` en el controller (`@Valid @RequestBody TransaccionRequestDTO`).

**P: ¿Qué pasa si envío `{"monto": -50, "tipo": "DEBITO"}`?**
R: Spring devuelve `400 BAD REQUEST` con mensaje: "El monto debe ser positivo".

---

## 8️⃣ CuentaMapper.java

### 📍 Ubicación
`com.switchbank.mscontabilidad.mapper.CuentaMapper`

### 🎯 Responsabilidad
**Conversión bidireccional** entre `Cuenta` (entidad) y `CuentaDTO`.

### 📌 Código
```java
@Mapper(componentModel = "spring")
public interface CuentaMapper {
    CuentaDTO aDTO(Cuenta cuenta);

    @Mapping(target = "version", ignore = true)
    Cuenta aEntidad(CuentaDTO cuentaDTO);
}
```

### Preguntas Frecuentes
**P: ¿Quién implementa esta interfaz?**
R: **MapStruct** genera la implementación en tiempo de compilación (`CuentaMapperImpl.java`).

**P: ¿Por qué `@Mapping(target = "version", ignore = true)`?**
R: El cliente no envía `version` en el DTO. Hibernate lo maneja automáticamente.

**P: ¿Qué hace `componentModel = "spring"`?**
R: Hace que MapStruct genere un `@Component`, permitiendo inyección de dependencias.

---

## 🔐 PREGUNTAS DE DEFENSA TÉCNICA

### Sobre Transaccionalidad

**P: ¿Qué pasa si `debitar()` falla después de actualizar el saldo pero antes de registrar la transacción?**
R: **Rollback automático**. `@Transactional` garantiza atomicidad. Si hay una excepción, ambas operaciones se revierten.

**P: ¿Por qué usas `READ_COMMITTED` en vez de `SERIALIZABLE`?**
R: `SERIALIZABLE` es más seguro pero más lento. `READ_COMMITTED` + bloqueo optimista (`@Version`) es suficiente para este caso.

---

### Sobre Concurrencia

**P: ¿Qué pasa si 1000 usuarios debitan la misma cuenta simultáneamente?**
R: 
1. Todos leen la cuenta (version=1, saldo=1000)
2. El primero actualiza (version=2, saldo=950) ✅
3. Los otros 999 fallan con `OptimisticLockException` ❌
4. El cliente debe reintentar la operación

**P: ¿Por qué no usas bloqueo pesimista (`SELECT ... FOR UPDATE`)?**
R: Bloqueo optimista es más escalable. Bloqueo pesimista bloquea la fila, reduciendo throughput.

---

### Sobre Validaciones

**P: ¿Por qué validas en el DTO Y en el servicio?**
R: **Defensa en profundidad**:
- DTO: Valida datos de entrada HTTP
- Servicio: Valida lógica de negocio (ej. saldo suficiente)

---

### Sobre Performance

**P: ¿Cómo optimizarías este código para 10,000 transacciones/segundo?**
R:
1. **Caché**: Redis para cuentas frecuentes
2. **Batch Processing**: Agrupa transacciones en lotes
3. **Sharding**: Particiona cuentas por rango de IDs
4. **Async**: Registra transacciones de auditoría de forma asíncrona

---

## 📊 DIAGRAMA DE SECUENCIA (Débito)

```
Cliente → CuentaController → CuentaService → CuentaRepository → PostgreSQL
   |            |                  |                |               |
   |--POST /1/transacciones------->|                |               |
   |            |                  |                |               |
   |            |--debitar(1,50)-->|                |               |
   |            |                  |--findById(1)-->|               |
   |            |                  |                |--SELECT------>|
   |            |                  |                |<--Cuenta------|
   |            |                  |<--Cuenta-------|               |
   |            |                  |                |               |
   |            |                  |--Validar Saldo |               |
   |            |                  |--setSaldo(50)  |               |
   |            |                  |--save(cuenta)->|               |
   |            |                  |                |--UPDATE------>|
   |            |                  |                |<--OK----------|
   |            |                  |<--Cuenta-------|               |
   |            |                  |                |               |
   |            |                  |--registrarTransaccion()------->|
   |            |                  |                |--INSERT------>|
   |            |                  |                |<--OK----------|
   |            |                  |                |               |
   |            |<--CuentaDTO------|                |               |
   |<--201 CREATED----------------|                |               |
```

---

## 🎓 CONCLUSIÓN

Este microservicio implementa:
✅ **Arquitectura en capas** (Controller → Service → Repository)
✅ **Transaccionalidad ACID** con Spring `@Transactional`
✅ **Bloqueo optimista** para concurrencia
✅ **Validaciones en múltiples niveles** (DTO + Servicio)
✅ **Auditoría completa** (tabla `Transaccion`)
✅ **Manejo centralizado de errores** (`@RestControllerAdvice`)
✅ **Documentación OpenAPI** automática

**Puntos fuertes**:
- Código limpio y bien estructurado
- Uso correcto de `BigDecimal` para finanzas
- Separación de responsabilidades

**Áreas de mejora** (para producción):
- Agregar seguridad (OAuth2/JWT)
- Implementar caché (Redis)
- Agregar métricas (Micrometer)
- Implementar circuit breaker (Resilience4j)
