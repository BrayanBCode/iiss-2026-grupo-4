package uy.edu.utec.iiss.engine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del engine: API REST, lecturas MQTT, comandos a los switches y persistencia del
 * inventario. La lógica de decisión está en el módulo core, que no depende del engine; acá solo la
 * usa {@code adaptador.CoreAdapter}.
 */
@SpringBootApplication
public class EngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(EngineApplication.class, args);
    }
}
