package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import uy.edu.utec.iiss.engine.inventario.Inventario;

/**
 * Pruebas de estrés, más duras que los escenarios de la clase y que {@code ecowarm-run}: switches
 * colgados, factores muy altos, sitios grandes y fallas combinadas, siempre cruzando el inicio de
 * la punta (17:00), que es donde un atraso se nota como consumo en punta.
 *
 * <p>Los switches usan los tiempos de producción (2 s de conexión, 10 s de respuesta, 5 s sin
 * reintentar) y la reconciliación de producción (15 s de control). La vigencia de las lecturas se
 * alarga para que no venzan durante las esperas y no tape lo que se mide.
 */
@DisplayName("Estrés: cruzar la punta con switches colgados, factor alto, 50 habitaciones")
@TestPropertySource(
        properties = {
            "control.tick-ms=1000",
            "control.reconciliacion-segundos=15",
            "control.vigencia-lectura-segundos=360000",
            "switch.timeout-conexion-ms=2000",
            "switch.timeout-lectura-ms=10000",
            "switch.espera-reintento-segundos=5",
            "mqtt.reconexion-max-segundos=1"
        })
class EstresTest extends IntegracionBase {

    static final ZoneId MONTEVIDEO = ZoneId.of("America/Montevideo");
    static final LocalTime PUNTA = LocalTime.of(17, 0);

    /** Martes 3/11/2026 a las hh:mm en Montevideo (UTC−3). */
    static String martes(int hora, int minuto) {
        return "2026-11-03T%02d:%02d:00Z".formatted(hora + 3, minuto);
    }

    @Test
    @DisplayName(
            "×60: 3 switches colgados (no responden) a las 16:55; el switch sano igual se apaga"
                    + " antes de las 17:00")
    void switchesColgados_elSanoSeApagaATiempo() throws Exception {
        Inventario sitio =
                inventario(
                        10.0,
                        habitacion("room1", 21.0, 1.0),
                        habitacion("room2", 21.0, 1.0),
                        habitacion("room3", 21.0, 1.0),
                        habitacion("room4", 21.0, 1.0));
        cargarSitio(sitio);
        iniciar(martes(16, 54), 60); // 6 min de control = 6 s reales hasta la punta
        for (int i = 1; i <= 4; i++) {
            temperatura("room" + i, 16.0);
        }
        for (int i = 1; i <= 4; i++) {
            esperarSwitch("room" + i, "ON");
        }
        for (int i = 1; i <= 3; i++) {
            SWITCHES.lento(idSwitch("room" + i), 60_000); // colgado: más que el timeout de 10 s
        }

        LocalTime apagado = horaDelControlAlApagar("room4", Duration.ofSeconds(60));
        assertTrue(
                !apagado.isAfter(PUNTA),
                "room4 (switch sano) se apagó a las " + apagado + ", ya en punta");
    }

    @Test
    @DisplayName("×600: un switch se apaga antes de las 17:00 aunque cada tick son 6 s de control")
    void factor600_cortaATiempo() throws Exception {
        cortaATiempoConFactor(600);
    }

    @Test
    @DisplayName(
            "×3600: un switch se apaga antes de las 17:00 aunque cada tick son 36 s de control")
    void factor3600_cortaATiempo() throws Exception {
        cortaATiempoConFactor(3600);
    }

    private void cortaATiempoConFactor(double factor) throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar(martes(10, 0), 1); // fuera de la punta, sin apuro: suscribirse y prender
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");
        // Re-INICIAR a 4 min de la punta con el factor alto (0,4 s reales a ×600).
        iniciar(martes(16, 56), factor);
        LocalTime apagado = horaDelControlAlApagar("living", Duration.ofSeconds(10));
        assertTrue(
                !apagado.isAfter(PUNTA),
                "a ×" + factor + " se apagó a las " + apagado + ", ya en punta");
    }

    @Test
    @DisplayName(
            "×60 con 50 habitaciones: prende sin pasar la contratada y a las 17:00 ya apagó todas")
    void cincuentaHabitaciones_cruzandoLaPunta() throws Exception {
        Inventario.Habitacion[] habs = new Inventario.Habitacion[50];
        for (int i = 0; i < 50; i++) {
            habs[i] = habitacion("room" + (i + 1), 21.0, 0.5);
        }
        Inventario sitio = inventario(20.0, habs); // 25 kW de demanda, 20 contratados
        cargarSitio(sitio);
        iniciar(martes(16, 52), 60); // 8 s reales hasta la punta
        for (Inventario.Habitacion h : habs) {
            esperar(() -> BROKER.haySuscriptor(topic(h.id())), () -> "no se suscribió a " + h.id());
        }
        for (Inventario.Habitacion h : habs) {
            BROKER.publicarTemperatura(topic(h.id()), 16.0);
        }
        esperar(
                () -> potenciaEncendida(sitio) >= 20.0 - 0.5,
                () -> "no aprovechó la contratada: " + potenciaEncendida(sitio) + " kW");
        assertTrue(
                picoDePotencia(sitio) <= 20.0 + 1e-9,
                "pico de " + picoDePotencia(sitio) + " kW con 20 contratados");

        LocalTime ultimoApagado = LocalTime.MIN;
        long limite = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (potenciaEncendida(sitio) > 0) {
            assertTrue(System.nanoTime() < limite, "no apagó todo: " + estadosSwitches(sitio));
            ultimoApagado = horaDelControl();
            Thread.onSpinWait();
        }
        assertTrue(
                !ultimoApagado.isAfter(PUNTA),
                "la última habitación se apagó a las " + ultimoApagado + ", ya en punta");
    }

    @Test
    @DisplayName(
            "×60, fallas combinadas: broker caído y un switch que da 503 a las 16:57; el sano se"
                    + " apaga antes de las 17:00 y al volver todo se retoma")
    void fallasCombinadas_cruzandoLaPunta() throws Exception {
        cargarSitio(
                POTENCIA_SITIO, habitacion("living", 21.0, 1.0), habitacion("cocina", 21.0, 1.0));
        iniciar(martes(16, 55), 60); // 5 s reales hasta la punta
        temperatura("living", 16.0);
        temperatura("cocina", 16.0);
        esperarSwitch("living", "ON");
        esperarSwitch("cocina", "ON");

        BROKER.detener();
        SWITCHES.caido(idSwitch("cocina"), true);

        LocalTime apagado = horaDelControlAlApagar("living", Duration.ofSeconds(30));
        assertTrue(!apagado.isAfter(PUNTA), "living se apagó a las " + apagado + ", ya en punta");

        // Vuelven: el switch de cocina se apaga (sigue la punta).
        SWITCHES.caido(idSwitch("cocina"), false);
        BROKER.iniciar();
        esperar(
                Duration.ofSeconds(15),
                () -> SWITCHES.estado(idSwitch("cocina")).equals("OFF"),
                () -> "cocina sigue ON en punta después de volver: " + SWITCHES.comandos());
    }

    // ------------------------------------------------------------------

    private LocalTime horaDelControl() {
        Instant ahora = control.estado().fechaHora();
        return ahora.atZone(MONTEVIDEO).toLocalTime();
    }

    /**
     * Espera (sin dormir, para no perder resolución a factores altos) a que el switch quede OFF y
     * devuelve la hora del control en ese momento.
     */
    private LocalTime horaDelControlAlApagar(String idHabitacion, Duration hasta) {
        long limite = System.nanoTime() + hasta.toNanos();
        while (!SWITCHES.estado(idSwitch(idHabitacion)).equals("OFF")) {
            assertTrue(
                    System.nanoTime() < limite,
                    () ->
                            idHabitacion
                                    + " no se apagó; hora del control "
                                    + horaDelControl()
                                    + ", motivo "
                                    + motivo(idHabitacion)
                                    + ", comandos "
                                    + SWITCHES.comandos());
            Thread.onSpinWait();
        }
        return horaDelControl();
    }
}
