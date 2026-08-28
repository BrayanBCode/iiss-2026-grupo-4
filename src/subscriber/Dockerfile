# Nos traemos el JDK de java y lo llamamos "build" a la etapa 1 (compilacion)
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /build
#POM del padre
COPY pom.xml .
#POM del hijo eventGenerator
COPY src/eventGenerator/pom.xml src/eventGenerator/pom.xml
#POM del hijo subscrite
COPY src/subscriber/pom.xml src/subscriber/pom.xml
#corremos con maven  (-q = reduce la salida de consola al minimo
 # -B = Ejecución por lotes (ideal para scripts) (saca las animaciones de la consola) hace un log plano
 #-pl = proyect list (lista los proyectos relacionados con el pom del subscriber))
 # -am = alse make (compila tambien las dependencias internas del modulo (si las tuviera))
 # dependency:go-offline = bajate(descarga) de Maven Central todas las dependencias que hacen falta para compilar subscriber (y lo que -am traiga)
RUN mvn -q -B -pl src/subscriber -am dependency:go-offline
# copia todo lo que esta dentro de src/subscriber/src del codigo, a la misma ruta en docker
COPY src/subscriber/src src/subscriber/src
# package = compila y empaqueta el codigo en un .jar
RUN mvn -q -B -pl src/subscriber -am package

#ETAPA 2 (ejecucion)
#Traemos el jre de JAVA en la version 25
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
# Aca copiamos la compilacion guardada en "build" hacia "app"
# En la ruta especificamos donde esta el archivo y donde queremos que se guarde en docker
COPY --from=build /build/src/subscriber/target/subscriber.jar /app/subscriber.jar
# ENTRYPOINT = lo que va a ejecutar docker para iniciar la ejecucion
ENTRYPOINT ["java","-jar","/app/subscriber.jar"]
