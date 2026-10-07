package uy.edu.utec.iiss.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del engine: la aplicación que corre el sistema (MQTT, base
 * de datos, REST y comandos a los switches). Desde acá Spring Boot escanea
 * todos los subpaquetes (rest, service, repository...).
 *
 * Reglas de dependencia entre paquetes:
 *   - rest/ es la puerta de entrada HTTP: nada fuera de rest importa de ahí
 *     (la dependencia va solo rest -> service).
 *   - core/ es la lógica de decisión pura (sin Spring ni I/O): no importa
 *     nada del resto del engine. El engine la usa, nunca al revés.
 */
@SpringBootApplication
public class EngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(EngineApplication.class, args);
    }
}
