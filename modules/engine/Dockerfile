# Nos traemos el JDK de java y lo llamamos "build" a la etapa 1 (compilacion)
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build
# POM del padre
COPY pom.xml .
# POM de todos los módulos hijos (hace falta que estén todos para que Maven
# pueda resolver el <modules> del padre, aunque acá solo compilemos engine)
COPY modules/core/pom.xml modules/core/pom.xml
COPY modules/eventGenerator/pom.xml modules/eventGenerator/pom.xml
COPY modules/engine/pom.xml modules/engine/pom.xml
COPY modules/switch-stub/pom.xml modules/switch-stub/pom.xml
RUN mvn -q -B -pl modules/engine -am dependency:go-offline
# El engine depende del módulo core: hace falta su código fuente para compilar
COPY modules/core/src modules/core/src
COPY modules/engine/src modules/engine/src
RUN mvn -q -B -pl modules/engine -am package

# ETAPA 2 (ejecucion)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /build/modules/engine/target/engine.jar /app/engine.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/engine.jar"]
