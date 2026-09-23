# Nos traemos el JDK de java y lo llamamos "build" a la etapa 1 (compilacion)
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build
# POM del padre
COPY pom.xml .
# POM de todos los módulos hijos (hace falta que estén todos para que Maven
# pueda resolver el <modules> del padre, aunque acá solo compilemos switch-stub)
COPY modules/eventGenerator/pom.xml modules/eventGenerator/pom.xml
COPY modules/subscriber/pom.xml modules/subscriber/pom.xml
COPY modules/api/pom.xml modules/api/pom.xml
COPY modules/switch-stub/pom.xml modules/switch-stub/pom.xml
# -pl = project list (compila solo switch-stub) / -am = also make (sus dependencias internas, si tuviera)
RUN mvn -q -B -pl modules/switch-stub -am dependency:go-offline
# copia el código fuente del módulo
COPY modules/switch-stub/src modules/switch-stub/src
# package = compila y empaqueta (spring-boot-maven-plugin arma el jar ejecutable con Tomcat embebido)
RUN mvn -q -B -pl modules/switch-stub -am package

# ETAPA 2 (ejecucion)
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /build/modules/switch-stub/target/switch-stub.jar /app/switch-stub.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/switch-stub.jar"]
