# Plan de Despliegue en AWS: Ecosistema Switch Transaccional

## 1. Análisis de Interdependencia y Flujo

### Comunicación entre Microservicios
*   **Mecanismo:** El análisis del código revela que `MSNucleoSwitch` utiliza `RestTemplate` para la comunicación síncrona.
*   **Topología:**
    *   `MSNucleo` -> `MSDirectorio` (Validación y Lookup)
    *   `MSNucleo` -> `MSContabilidad` (Ledger y Saldos)
    *   `MSNucleo` -> `MSCompensacion` (Clearing)
*   **Consistencia de Flujo (Integridad):**
    *   **MD5:** Generado en `MSNucleo` (TransaccionService).
    *   **BIN Checking:** Validado en `MSNucleo` llamando a `MSDirectorio`.
    *   **Pre-fondeo:** Validado en `MSContabilidad` antes del débito.
    *   **Conclusión:** La lógica está correctamente desacoplada pero orquestada centralmente por el Núcleo.

---

## 2. Plan de 'Separación para Conquistar'

### Repositorios Individuales: Archivos a Modificar

Para desplegar en AWS usando Docker Swarm o ECS, cada repositorio debe ser autónomo.

#### A. MSNucleoSwitch (`application.properties`)
Cambiar `localhost` por variables de entorno inyectables:
```properties
service.directorio.url=${SERVICIO_DIRECTORIO_URL:http://ms-directorio:8081}
service.contabilidad.url=${SERVICIO_CONTABILIDAD_URL:http://ms-contabilidad:8083}
service.compensacion.url=${SERVICIO_COMPENSACION_URL:http://ms-compensacion:8084}
```

#### B. Dockerfile Estándar (Optimizado para AWS - Java)
Usar `eclipse-temurin:17-jre-alpine` para imágenes ligeras (<200MB).

```dockerfile
# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV TZ=America/Guayaquil
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 3. Integración del Frontend en el Ecosistema

### Flujo de Datos
*   El Frontend **NO** debe atacar directamente al puerto 8082/8083.
*   **Todo el tráfico debe pasar por el Puerto 8000 de KONG.**
*   El Frontend debe configurarse para apuntar a: `http://{AWS_ELB_IP}:8000`.

### Configuración Nginx (Reverse Proxy + SPA)

El Nginx del Frontend debe servir los estáticos y redirigir `/api` a Kong para evitar CORS si están en el mismo dominio.

**Archivo `nginx.conf` recomendado para AWS:**
```nginx
server {
    listen 80;
    
    # Servir Frontend React
    location / {
        root /usr/share/nginx/html;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # Proxy hacia el Gateway (Kong) - Opcional si se usa URL absoluta en React
    location /api/ {
        proxy_pass http://kong-gateway:8000/;
        proxy_set_header Host $host;
    }
}
```

---

## 4. Dimensionamiento AWS EC2

### Requisitos de Recursos
*   **Carga:** 6 Microservicios Java (heap min 256MB c/u) + Postgres + Redis + Kong + Frontend.
*   **Estimación RAM:** ~4 GB solo para aplicaciones + 2 GB para BDs y Sistema. Total min: 6-8 GB.
*   **Instancia Recomendada:** **t3.large** (2 vCPU, 8 GB RAM) o **t3.xlarge** para producción holgada.
    *   *Nota:* Una `t3.medium` (4GB) se quedará corta y provocará OOM Kills.

### Security Groups (Firewall)
| Puerto | Protocolo | Origen | Descripción |
| :--- | :--- | :--- | :--- |
| **80** | TCP | 0.0.0.0/0 | Acceso Web Frontend |
| **8000** | TCP | 0.0.0.0/0 | API Publica (Kong) |
| **22** | TCP | Mi_IP_Casa | SSH (Admin) |
| **5432** | TCP | sg-interno | Base de Datos (Solo interna) |
| **8081-8085** | TCP | sg-interno | Microservicios (Solo interno) |

---

## 5. Checklist de Verificación Final ✅

1.  **Idempotencia:** Redis debe configurarse con `appendonly yes` en `redis.conf` si se desea persistencia estricta ante reinicios del contenedor.
2.  **Resiliencia:** Confirmar que `application.properties` en `MSNucleo` tenga `resilience4j.circuitbreaker` habilitado.
3.  **Deploy:**
    1.  Clonar cada repo en `/opt/switch/`.
    2.  Crear una red Docker (`docker network create switch-net`).
    3.  Levantar BDs primero.
    4.  Levantar Kong.
    5.  Levantar Micros.
    6.  Levantar Frontend.

Este plan asegura un despliegue ordenado, seguro y escalable en la nube de Amazon.
