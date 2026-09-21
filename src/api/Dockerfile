# Nos traemos el JDK de java y lo llamamos "build" a la etapa 1 (compilacion)
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build
# POM del padre
COPY pom.xml .
# POM de todos los módulos hijos (hace falta que estén todos para que Maven
# pueda resolver el <modules> del padre, aunque acá solo compilemos api)
COPY src/eventGenerator/pom.xml src/eventGenerator/pom.xml
COPY src/subscriber/pom.xml src/subscriber/pom.xml
COPY src/api/pom.xml src/api/pom.xml
COPY src/switch-stub/pom.xml src/switch-stub/pom.xml
RUN mvn -q -B -pl src/api -am dependency:go-offline
COPY src/api/src src/api/src
RUN mvn -q -B -pl src/api -am package

# ETAPA 2 (ejecucion)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /build/src/api/target/api.jar /app/api.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/api.jar"]
