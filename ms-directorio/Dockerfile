# ==========================================
# ETAPA 1: BUILD (Construir el JAR con MAVEN y JAVA 21)
# ==========================================
# CAMBIO IMPORTANTE: Usamos la imagen base con terminación '-21'
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copiamos archivos de configuración
COPY pom.xml .
COPY src ./src

# Compilamos el proyecto
RUN mvn clean package -DskipTests

# ==========================================
# ETAPA 2: RUN (Ejecutar la App con JAVA 21)
# ==========================================
# CAMBIO IMPORTANTE: Usamos la imagen ligera de Java 21
FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copiamos el JAR generado en la etapa anterior
# Asegúrate que el nombre coincida con lo que genera tu pom.xml
COPY --from=build /app/target/ms-directorio-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]