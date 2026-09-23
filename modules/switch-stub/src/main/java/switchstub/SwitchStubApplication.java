package switchstub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del stub del switch (G1234-94, G1234-95, G1234-97).
 * El endpoint que recibe la acción (ON/OFF) y loguea en consola está en
 * {@link SwitchController}.
 */
@SpringBootApplication
public class SwitchStubApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwitchStubApplication.class, args);
    }
}
