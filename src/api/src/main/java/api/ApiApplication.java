package api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del API REST de habitaciones (CRUD + comandos de la
 * Iteración 3). Por ahora solo levanta el servidor y la conexión a Postgres;
 * la entidad Habitacion, los repositorios y los controllers se agregan en
 * el siguiente paso.
 */
@SpringBootApplication
public class ApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
