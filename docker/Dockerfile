FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Copia el archivo JAR generado por IntelliJ desde la carpeta out
COPY out/artifacts/ing_soft_grupo4_jar/*.jar app.jar

# Ejecuta el suscriptor Java
ENTRYPOINT ["java", "-jar", "app.jar"]