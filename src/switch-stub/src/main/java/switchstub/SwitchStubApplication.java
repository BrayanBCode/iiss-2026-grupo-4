package switchstub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del stub del switch (G1234-94, G1234-95, G1234-97).
 * Por ahora solo levanta el servidor embebido; el endpoint que recibe la
 * acción (encender/apagar) y loguea en consola se agrega en el siguiente
 * paso, junto con la lógica.
 */
@SpringBootApplication
public class SwitchStubApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwitchStubApplication.class, args);
    }
}
