# Nos traemos el JDK de java y lo llamamos "build" a la etapa 1 (compilacion)
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build
# POM del padre
COPY pom.xml .
# POM de todos los módulos hijos (hace falta que estén todos para que Maven
# pueda resolver el <modules> del padre, aunque acá solo compilemos api)
COPY modules/eventGenerator/pom.xml modules/eventGenerator/pom.xml
COPY modules/subscriber/pom.xml modules/subscriber/pom.xml
COPY modules/api/pom.xml modules/api/pom.xml
COPY modules/switch-stub/pom.xml modules/switch-stub/pom.xml
RUN mvn -q -B -pl modules/api -am dependency:go-offline
COPY modules/api/src modules/api/src
RUN mvn -q -B -pl modules/api -am package

# ETAPA 2 (ejecucion)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /build/modules/api/target/api.jar /app/api.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/api.jar"]
