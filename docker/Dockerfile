# Build reproducible: compila el suscriptor Java DENTRO de Docker, sin depender
# de IntelliJ ni de tener Java/Maven instalados en el host.
# Contexto de build: raíz del repo (ver docker-compose.yml -> context: ..)

# --- Etapa 1: compilación ---
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /build

# Dependencias (jar de Eclipse Paho) y código fuente
COPY lib ./lib
COPY src ./src

# Compila todos los .java del proyecto contra las libs de /lib.
# find soporta rutas con espacios/etc de forma segura vía -print0/xargs -0.
RUN mkdir -p out/classes && \
    find src -name "*.java" -print0 | xargs -0 javac -cp "lib/*" -d out/classes

# --- Etapa 2: imagen de ejecución (liviana, solo JRE) ---
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /build/out/classes ./classes
COPY lib ./lib

# Punto de entrada del suscriptor MQTT (package subscriber; class AppSubscriber)
ENTRYPOINT ["java", "-cp", "classes:lib/*", "subscriber.AppSubscriber"]
