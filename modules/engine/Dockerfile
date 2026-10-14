# syntax=docker/dockerfile:1
# Nos traemos el JDK de java y lo llamamos "build" a la etapa 1 (compilacion)
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build
# POM del padre
COPY pom.xml .
# POM de los módulos hijos (hace falta que estén todos para que Maven
# pueda resolver el <modules> del padre: core y engine)
COPY modules/core/pom.xml modules/core/pom.xml
COPY modules/engine/pom.xml modules/engine/pom.xml
# --mount=type=cache: el repositorio de Maven (~/.m2) se conserva entre builds,
# así un build que se cortó por la red retoma sin volver a bajar todo.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl modules/engine -am dependency:go-offline
# El engine depende del módulo core: hace falta su código fuente para compilar
COPY modules/core/src modules/core/src
COPY modules/engine/src modules/engine/src
# Los tests NO corren en la imagen (los corre scripts/test.sh): así el build no
# necesita bajar el proveedor de JUnit de Surefire, que go-offline no trae.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl modules/engine -am package -DskipTests

# ETAPA 2 (ejecucion)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /build/modules/engine/target/engine.jar /app/engine.jar
# El inventario del sitio (PUT /sitio) se persiste acá: montarlo como volumen
# para que sobreviva a recrear el contenedor (estándar v2, §5.1).
ENV INVENTARIO_ARCHIVO=/app/data/inventario.json
VOLUME /app/data
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/engine.jar"]
