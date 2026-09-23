package switchstub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del stub del switch.
 * El endpoint que recibe la acción (ON/OFF) y loguea en consola está en
 * {@link SwitchController}.
 */
@SpringBootApplication
public class SwitchStubApplication {
    public static void main(String[] args) {
        SpringApplication.run(SwitchStubApplication.class, args);
    }
}
