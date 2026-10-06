package uy.edu.utec.iiss.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de core: la aplicación que corre el sistema. Desde acá
 * Spring Boot escanea todos los subpaquetes (rest, service, repository...).
 * Los endpoints HTTP viven aislados en el paquete "rest": el resto de core
 * NO debe importar nada de ahí (la dependencia va solo rest -> service).
 */
@SpringBootApplication
public class CoreApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
