package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import uy.edu.utec.iiss.engine.inventario.Inventario;

/**
 * Un termostato deja de funcionar: su última lectura vence y la habitación se apaga (no se
 * calefacciona a ciegas), sin afectar a las demás. La vigencia se baja a 2 s para el test (en
 * producción son 10 min).
 */
@TestPropertySource(properties = "control.vigencia-lectura-segundos=2")
@DisplayName("Integración: un termostato deja de funcionar")
class TermostatoCaidoTest extends IntegracionBase {

    @Test
    @DisplayName(
            "Sin lecturas nuevas, al vencer la última se apaga (LECTURA_VENCIDA); cuando vuelve,"
                    + " se retoma")
    void venceYSeRetoma() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        // el termostato no manda más nada
        esperarSwitch("living", "OFF");
        assertEquals("LECTURA_VENCIDA", motivo("living"));

        temperatura("living", 16.0); // vuelve
        esperarSwitch("living", "ON");
    }

    @Test
    @DisplayName("Mientras el termostato manda lecturas, la habitación no vence")
    void conLecturasNoVence() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        mantenerVivo(Duration.ofMillis(3500), "living", 16.0, () -> true);
        assertSwitch("living", "ON");
        assertEquals("CALEFACCIONANDO", motivo("living"));
    }

    @Test
    @DisplayName("Solo se apaga la habitación del termostato caído; las demás siguen controladas")
    void soloLaDelTermostatoCaido() throws Exception {
        cargarSitio(
                POTENCIA_SITIO, habitacion("living", 21.0, 1.0), habitacion("cocina", 20.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        temperatura("cocina", 15.0);
        esperarSwitch("living", "ON");
        esperarSwitch("cocina", "ON");

        // solo la cocina sigue publicando
        mantenerVivo(
                Duration.ofSeconds(5),
                "cocina",
                15.0,
                () -> !SWITCHES.estado("sw-living").equals("OFF"));
        assertSwitch("living", "OFF");
        assertEquals("LECTURA_VENCIDA", motivo("living"));
        assertSwitch("cocina", "ON");
    }

    @Test
    @DisplayName(
            "Con carga limitante, la potencia de la habitación sin termostato pasa a la que"
                    + " esperaba, sin superar la contratada")
    void liberaPotencia() throws Exception {
        Inventario inv =
                inventario(
                        POTENCIA_SITIO,
                        habitacion("living", 22.0, 1.5),
                        habitacion("dormitorio", 22.0, 1.5),
                        habitacion("estudio", 22.0, 1.5));
        cargarSitio(inv);
        iniciar();
        temperatura("living", 16.0);
        temperatura("dormitorio", 17.0);
        temperatura("estudio", 20.0);
        esperarSwitch("living", "ON");
        esperarSwitch("dormitorio", "ON");
        assertSwitch("estudio", "OFF");

        // se cae el termostato del living; dormitorio y estudio siguen publicando
        long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!SWITCHES.estado("sw-estudio").equals("ON") && System.nanoTime() < limite) {
            BROKER.publicarTemperatura(topic("dormitorio"), 17.0);
            BROKER.publicarTemperatura(topic("estudio"), 20.0);
            Thread.sleep(300);
        }
        assertSwitch("living", "OFF");
        assertSwitch("estudio", "ON");
        assertSwitch("dormitorio", "ON");
        assertTrue(picoDePotencia(inv) <= POTENCIA_SITIO, "comandos: " + SWITCHES.comandos());
    }

    @Test
    @DisplayName(
            "Con tiempo acelerado (×10) la vigencia se mide en tiempo de control: vence antes en"
                    + " tiempo real")
    void tiempoAcelerado() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar(FECHA_INICIO, 10);
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        // 2 s de vigencia de control = 0,2 s reales con factor 10
        esperarSwitch("living", "OFF");
        assertEquals("LECTURA_VENCIDA", motivo("living"));
    }

    /**
     * Publica una lectura de la habitación cada 300 ms durante el lapso, o hasta que la condición
     * deje de cumplirse.
     */
    private void mantenerVivo(
            Duration lapso, String idHabitacion, double temperaturaC, BooleanSupplier mientras)
            throws InterruptedException {
        long limite = System.nanoTime() + lapso.toNanos();
        while (System.nanoTime() < limite && mientras.getAsBoolean()) {
            BROKER.publicarTemperatura(topic(idHabitacion), temperaturaC);
            Thread.sleep(300);
        }
    }
}
