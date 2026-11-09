package uy.edu.utec.iiss.engine.integracion;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import uy.edu.utec.iiss.engine.inventario.Inventario;

/**
 * Prueba de estrés (T36): alguien prende a mano un switch en medio de una corrida acelerada. Con la
 * reconciliación de producción (15 s) y factor 60, el controlador lo tiene que volver a apagar en
 * segundos reales, no en los 15 s reales (15 min de control) que tardaba antes de escalarla con el
 * factor.
 */
@DisplayName("Integración: reconciliación escalada con el factor (T36)")
@TestPropertySource(properties = "control.reconciliacion-segundos=15")
class ReconciliacionEscaladaTest extends IntegracionBase {

    /** Martes 3/11 a las 10:00 en Montevideo, fuera de la punta: como el simulador. */
    static final String FECHA = "2026-11-03T13:00:00Z";

    /** Bastante menos que los 15 s reales de antes; deja margen para una máquina lenta. */
    static final Duration HASTA = Duration.ofSeconds(4);

    @Test
    @DisplayName(
            "A ×60, un switch prendido a mano que pasa la contratada vuelve a OFF en segundos"
                    + " reales")
    void switchPrendidoAMano_vuelveAOffEnSegundos() throws Exception {
        // 3 × 1,2 kW con 2,5 kW contratados: solo entran dos.
        Inventario limitante =
                inventario(
                        2.5,
                        habitacion("room1", 21.0, 1.2),
                        habitacion("room2", 21.0, 1.2),
                        habitacion("room3", 21.0, 1.2));
        cargarSitio(limitante);
        iniciar(FECHA, 60);
        temperatura("room1", 15.0);
        temperatura("room2", 16.0);
        temperatura("room3", 17.0);
        esperarSwitch("room1", "ON");
        esperarSwitch("room2", "ON");
        esperarMotivo("room3", "SIN_POTENCIA_DISPONIBLE");

        // Alguien lo prende a mano: 3,6 kW encendidos con 2,5 contratados.
        SWITCHES.fijar(idSwitch("room3"), "ON");

        esperar(
                HASTA,
                () -> SWITCHES.estado(idSwitch("room3")).equals("OFF"),
                () ->
                        "room3 sigue ON a mano después de "
                                + HASTA.toSeconds()
                                + " s reales; comandos: "
                                + SWITCHES.comandos());
        assertSwitch("room1", "ON");
        assertSwitch("room2", "ON");
    }

    @Test
    @DisplayName("Con factor 1 la reconciliación sigue siendo cada 15 s: no se consulta de más")
    void factorUno_noConsultaDeMas() throws Exception {
        cargarSitio(6.0, habitacion("room1", 21.0, 1.0));
        iniciar(FECHA, 1);
        temperatura("room1", 25.0);
        esperarSwitch("room1", "OFF");
        int consultas = SWITCHES.consultas().size();

        seMantiene(
                Duration.ofSeconds(3),
                () -> SWITCHES.consultas().size() == consultas,
                () -> "se consultó el switch antes de los 15 s: " + SWITCHES.consultas());
    }
}
