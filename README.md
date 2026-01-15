# Microservicio de Compensación y Liquidación (Settlement Engine)

## Módulo G4: Implementación de Clearing, Continuidad Contable, Firma Digital y Monitoreo Operativo.

## 📌 Visión General
Este microservicio es el Motor de Cierre del Switch Transaccional. Su responsabilidad no es solo acumular sumas, sino actuar como el Garante de la Integridad Financiera del ecosistema.

Implementa un modelo de **Neteo Multilateral con Continuidad**, lo que significa que el sistema opera como un libro mayor ininterrumpido donde los saldos finales de un ciclo se convierten automáticamente en los saldos iniciales del siguiente, garantizando trazabilidad forense completa.

---

## ⚙️ Capacidades Clave (Cumplimiento RF)

### 1. Neteo Multilateral (RF-05)
Implementa el algoritmo de **Suma Cero**.
- Acumula débitos y créditos en tiempo real.
- Al cierre, valida matemáticamente que: `Σ (Posiciones Netas) == 0.00`.
- Si el sistema no cuadra al centavo, bloquea la generación de archivos (Fail-Safe).

### 2. Continuidad Contable (Requisito G4)
A diferencia de un sistema batch tradicional que "resetea" a cero:
- **Arrastre de Saldos:** Al cerrar el Ciclo N, el sistema crea atómicamente el Ciclo N+1.
- **Trazabilidad:** El Saldo Final de hoy se inyecta como Saldo Inicial de mañana.

### 3. Firma Digital JWS (RNF-SEC-04)
Para garantizar la **Validez Legal** y el **No Repudio** de los archivos de liquidación:
- Genera archivos XML compatibles con ISO 20022.
- Firma criptográficamente el contenido usando el estándar JWS (JSON Web Signature) con algoritmo RS256.
- Utiliza la librería certificada `nimbus-jose-jwt`.

### 4. Monitor Operativo (Dashboard)
Expone métricas en tiempo real para el tablero de control:
- Semáforo de estado del sistema (Verde/Rojo).
- Cronómetro de ciclos y volúmenes transaccionales.

---

## 🛠️ Stack Tecnológico
- **Core:** Java 21, Spring Boot 3.x
- **Persistencia:** PostgreSQL (Esquema relacional estricto).
- **Seguridad:** nimbus-jose-jwt (Criptografía asimétrica RSA).
- **Documentación:** OpenAPI 3 / Swagger.
- **Integración:** RESTful APIs (Nivel 2 Maturity Model).

---

## 🔌 API Reference (V1)

### 🟢 Dashboard & Monitoreo
Endpoints públicos para alimentar el Frontend de control.

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/v1/dashboard/monitor` | Devuelve el estado del semáforo (V/R), ciclo activo y hora de inicio. |
| `GET` | `/api/v1/compensacion/ciclos` | Historial completo de ciclos operativos (Auditoría). |

### ⚡ Operaciones Core (Uso Interno del Switch)
Endpoints de alta velocidad y seguridad para el motor transaccional.

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/v1/compensacion/ciclos/{id}/acumular` | **Clearing Real-Time:** Registra débitos/créditos. Invocado por MS-Nucleo. |
| `POST` | `/api/v1/compensacion/ciclos/{id}/cierre` | **Settlement Trigger:** Ejecuta validación suma cero, firma JWS y continuidad. |

> **Nota:** Se eliminaron los endpoints de creación manual (`POST /posiciones`) para garantizar la integridad de los datos. Las posiciones solo se crean por acumulación o continuidad automática.

---

## 🔐 Seguridad y Firmas
El servicio implementa un **Módulo de Seguridad (HSM Simulado)** en la clase `SeguridadService.java`.
- **Algoritmo:** RS256 (RSA Signature with SHA-256).
- **Key Rotation:** Preparado para inyección de llaves privadas vía variables de entorno o Vault.

---

## 🚀 Despliegue

### Requisitos de Base de Datos
El servicio requiere un esquema específico para manejar la continuidad. Asegúrese de ejecutar el script de inicialización (`init.sql`) que crea las tablas:
- `ciclocompensacion`
- `posicioninstitucion` (con columnas `saldo_inicial`, `neto`)
- `archivoliquidacion` (con columna `firma_jws`)

### Ejecución con Docker

```bash
# Construir imagen
./mvnw clean package -DskipTests
docker-compose build ms-compensacion

# Levantar servicio
docker-compose up -d ms-compensacion
```

---

## 🧪 Pruebas de Validación (Defensa)
1. **Integridad:** Realizar transacciones cruzadas y verificar que la suma de la columna `neto` en `posicioninstitucion` sea `0.00`.
2. **Cierre:** Ejecutar `POST .../cierre`. Verificar que:
   - El ciclo actual pasa a **CERRADO**.
   - Se crea un nuevo ciclo **ABIERTO** automáticamente.
   - Los saldos se arrastran a la columna `saldo_inicial` del nuevo ciclo.
3. **Evidencia:** Descargar el XML generado y verificar el tag `<Signature>` o la estructura JWS en el log.
