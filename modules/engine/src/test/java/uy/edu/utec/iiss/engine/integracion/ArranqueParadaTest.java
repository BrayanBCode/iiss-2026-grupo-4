package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Situaciones iniciales y arranque / parada del controlador (§5.2): qué hace con la calefacción que
 * encuentra al INICIAR y qué deja al DETENER.
 */
@DisplayName("Integración: situaciones iniciales y arranque / parada")
class ArranqueParadaTest extends IntegracionBase {

    // ---------------- situaciones iniciales ----------------

    @Test
    @DisplayName("Sin INICIAR no se toca ningún switch, aunque lleguen lecturas")
    void sinIniciarNoSeComanda() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        temperatura("living", 15.0);

        seMantiene(
                Duration.ofMillis(700),
                () -> SWITCHES.comandos().isEmpty() && SWITCHES.consultas().isEmpty(),
                () -> "se habló con los switches sin INICIAR: " + SWITCHES.comandos());
    }

    @Test
    @DisplayName("Al INICIAR consulta el estado real de cada switch (§4) antes de comandar")
    void iniciarConsultaLosSwitches() throws Exception {
        cargarSitio(
                POTENCIA_SITIO, habitacion("living", 21.0, 1.0), habitacion("cocina", 19.0, 1.0));
        iniciar();

        esperar(
                () -> SWITCHES.consultas().containsAll(List.of("sw-living", "sw-cocina")),
                () -> "no consultó todos los switches: " + SWITCHES.consultas());
    }

    @Test
    @DisplayName(
            "Calefacción encendida de antes y todavía sin lecturas: se apaga al INICIAR (no se"
                    + " calefacciona a ciegas)")
    void encendidaSinLecturas_seApaga() throws Exception {
        SWITCHES.fijar("sw-living", "ON");
        SWITCHES.fijar("sw-cocina", "ON");
        cargarSitio(
                POTENCIA_SITIO, habitacion("living", 21.0, 1.0), habitacion("cocina", 19.0, 1.0));
        iniciar();

        esperarSwitch("living", "OFF");
        esperarSwitch("cocina", "OFF");
        assertEquals("SIN_LECTURA", motivo("living"));
    }

    @Test
    @DisplayName(
            "Calefacción encendida de antes en una habitación fría y otra caliente: con la primera"
                    + " lectura queda encendida la fría y apagada la caliente")
    void encendidasDeAntes_seAjustanConLaPrimeraLectura() throws Exception {
        SWITCHES.fijar("sw-living", "ON");
        SWITCHES.fijar("sw-cocina", "ON");
        cargarSitio(
                POTENCIA_SITIO, habitacion("living", 21.0, 1.0), habitacion("cocina", 19.0, 1.0));
        iniciar();
        temperatura("living", 17.0);
        temperatura("cocina", 23.0);

        esperarSwitch("living", "ON");
        esperarSwitch("cocina", "OFF");
        assertEquals("CALEFACCIONANDO", motivo("living"));
        assertEquals("SATISFECHA", motivo("cocina"));
    }

    @Test
    @DisplayName("Las lecturas anteriores a INICIAR no cuentan: arranca SIN_LECTURA")
    void lecturasAnterioresAIniciar_noCuentan() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        temperatura("living", 15.0);
        Thread.sleep(300); // que la lectura llegue mientras está detenido
        iniciar();

        esperarMotivo("living", "SIN_LECTURA");
        assertSwitch("living", "OFF");
    }

    @Test
    @DisplayName("INICIAR sin sitio configurado no falla; el sitio que llega después se controla")
    void iniciarSinSitio() throws Exception {
        iniciar();
        seMantiene(
                Duration.ofMillis(300),
                () -> SWITCHES.comandos().isEmpty(),
                () -> "comandó sin sitio: " + SWITCHES.comandos());

        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");
    }

    @Test
    @DisplayName("INICIAR en plena franja punta: no enciende nada, y apaga lo que estaba encendido")
    void iniciarEnFranjaPunta() throws Exception {
        SWITCHES.fijar("sw-living", "ON");
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar("2026-10-01T20:00:00Z", 1);
        temperatura("living", 15.0);

        esperarMotivo("living", "TARIFA_PUNTA");
        esperarSwitch("living", "OFF");
    }

    // ---------------- parada ----------------

    @Test
    @DisplayName(
            "DETENER: deja de comandar; lo que estaba encendido queda como está (§5.2 no exige"
                    + " apagar)")
    void detenerDejaDeComandar() throws Exception {
        cargarSitio(
                POTENCIA_SITIO, habitacion("living", 21.0, 1.0), habitacion("cocina", 19.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        temperatura("cocina", 15.0);
        esperarSwitch("living", "ON");
        esperarSwitch("cocina", "ON");

        detener();
        int comandosAlDetener = SWITCHES.comandos().size();
        temperatura("living", 25.0); // haría apagar, pero ya está detenido

        seMantiene(
                Duration.ofMillis(1500), // más que la reconciliación (1 s)
                () -> SWITCHES.comandos().size() == comandosAlDetener,
                () -> "comandó detenido: " + SWITCHES.comandos());
        assertSwitch("living", "ON");
        assertSwitch("cocina", "ON");
    }

    @Test
    @DisplayName("DETENER es idempotente y sin sitio no falla")
    void detenerIdempotente() throws Exception {
        detener();
        iniciar();
        detener();
        detener();
        assertTrue(SWITCHES.comandos().isEmpty());
    }

    @Test
    @DisplayName(
            "Reiniciar tras DETENER retoma el control: corrige lo que cambió mientras estaba"
                    + " detenido")
    void reiniciarRetomaElControl() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        detener();
        SWITCHES.fijar("sw-living", "OFF"); // alguien lo apagó a mano

        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");
    }

    @Test
    @DisplayName(
            "Con el control en marcha, un cambio manual en un switch se corrige al reconciliar")
    void cambioManualSeCorrige() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        SWITCHES.fijar("sw-living", "OFF");
        esperarSwitch("living", "ON"); // reconciliación cada 1 s
    }
}
