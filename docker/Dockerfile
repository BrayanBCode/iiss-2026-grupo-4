# Build reproducible: compila el subscriber con Maven DENTRO de Docker.
# No depende de tener Java/Maven instalados en el host, ni de IntelliJ.
# Contexto de build: raíz del repo (ver docker-compose.yml -> context: ..)

# --- Etapa 1: build con Maven ---
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build

# Primero solo el pom.xml, para cachear la descarga de dependencias en una
# capa de Docker separada: si después solo cambia el código Java, esta capa
# no se vuelve a descargar.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B clean package

# --- Etapa 2: imagen de ejecución (liviana, solo JRE) ---
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# El fat jar generado por maven-shade-plugin ya incluye las dependencias
# (Paho), así que no hace falta copiar ninguna carpeta lib/.
COPY --from=build /build/target/subscriber.jar /app/subscriber.jar

ENTRYPOINT ["java", "-jar", "/app/subscriber.jar"]
