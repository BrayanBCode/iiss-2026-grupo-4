package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import uy.edu.utec.iiss.engine.inventario.Inventario;
import uy.edu.utec.iiss.engine.mqtt.LecturaMqttListener;

/**
 * Escenarios del checklist de la corrida contra el simulador (docs/tests/corrida-simulador) que no
 * cubrían los demás tests de integración, armados como los corre el simulador de la cátedra: cada
 * escenario es un {@code PUT /sitio}, un INICIAR con {@code factorTiempo} 60 y {@code fechaHora} en
 * UTC, y un DETENER; entre escenarios el simulador reinicia sus switches (corrida del 2026-10-28,
 * tarjeta T25).
 */
@DisplayName("Integración: escenarios del simulador (T25)")
@TestPropertySource(properties = "mqtt.reconexion-max-segundos=1")
class EscenariosSimuladorTest extends IntegracionBase {

    /** Martes 3/11 a las 10:00 en Montevideo, fuera de la punta: como el simulador. */
    static final String FECHA_SIMULADOR = "2026-11-03T13:00:00Z";

    static final double FACTOR_SIMULADOR = 60;

    @Autowired LecturaMqttListener mqtt;

    @Test
    @DisplayName(
            "Escenario limitante, DETENER, switches reiniciados y otro sitio holgado con menos"
                    + " habitaciones: se consultan los switches, se controla el sitio nuevo y no se"
                    + " toca lo que quedó afuera")
    void secuenciaDeEscenarios() throws Exception {
        // Escenario A, limitante: 3 × 1,2 kW con 2,5 kW contratados, solo entran dos.
        Inventario limitante =
                inventario(
                        2.5,
                        habitacion("room1", 21.0, 1.2),
                        habitacion("room2", 21.0, 1.2),
                        habitacion("room3", 21.0, 1.2));
        cargarSitio(limitante);
        iniciar(FECHA_SIMULADOR, FACTOR_SIMULADOR);
        temperatura("room1", 15.0);
        temperatura("room2", 16.0);
        temperatura("room3", 17.0);
        esperarSwitch("room1", "ON");
        esperarSwitch("room2", "ON");
        esperarMotivo("room3", "SIN_POTENCIA_DISPONIBLE");
        assertSwitch("room3", "OFF");
        assertTrue(
                picoDePotencia(limitante) <= 2.5,
                "se superó la contratada: " + picoDePotencia(limitante) + " kW");
        detener();

        // Entre escenarios el simulador reinicia sus switches.
        SWITCHES.fijar("sw-room1", "DESCONOCIDO");
        SWITCHES.fijar("sw-room2", "DESCONOCIDO");
        SWITCHES.fijar("sw-room3", "DESCONOCIDO");
        int comandosARoom3 = SWITCHES.comandosA("sw-room3").size();
        int consultas = SWITCHES.consultas().size();

        // Escenario B, holgado y sin room3.
        Inventario holgado =
                inventario(6.0, habitacion("room1", 21.0, 1.0), habitacion("room2", 21.0, 1.0));
        cargarSitio(holgado);
        esperar(
                () -> !BROKER.haySuscriptor(topic("room3")),
                () -> "sigue suscrito al termostato de room3");
        iniciar(FECHA_SIMULADOR, FACTOR_SIMULADOR);
        esperar(
                () ->
                        SWITCHES.consultas()
                                .subList(consultas, SWITCHES.consultas().size())
                                .containsAll(List.of("sw-room1", "sw-room2")),
                () -> "no consultó los switches al INICIAR (§4): " + SWITCHES.consultas());
        temperatura("room1", 15.0);
        temperatura("room2", 16.0);
        esperarSwitch("room1", "ON");
        esperarSwitch("room2", "ON");
        assertEquals("CALEFACCIONANDO", motivo("room2"));

        assertEquals(
                comandosARoom3,
                SWITCHES.comandosA("sw-room3").size(),
                "comandó el switch de una habitación que ya no está en el sitio");
        assertEquals("DESCONOCIDO", SWITCHES.estado("sw-room3"));
        assertTrue(potenciaEncendida(holgado) <= 6.0);
    }

    @Test
    @DisplayName(
            "Broker caído más que la vigencia: las lecturas vencen y se apaga (LECTURA_VENCIDA);"
                    + " al volver se re-suscribe y retoma")
    void brokerCaidoMasQueLaVigencia() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        // A ×300 la vigencia de 600 s de control son 2 s reales.
        iniciar(FECHA_INICIO, 300);
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        BROKER.detener();
        esperarMotivo("living", "LECTURA_VENCIDA");
        esperarSwitch("living", "OFF");

        BROKER.iniciar();
        esperar(
                Duration.ofSeconds(10),
                () -> mqtt.conectado() && BROKER.haySuscriptor(topic("living")),
                () -> "no se reconectó ni se re-suscribió");
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");
        assertEquals("CALEFACCIONANDO", motivo("living"));
    }

    @Test
    @DisplayName(
            "Termostato con el reloj adelantado (ts en el futuro): la lectura vale con la hora de"
                    + " llegada, no se descarta y no bloquea las siguientes")
    void tsEnElFuturo() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        esperar(() -> BROKER.haySuscriptor(topic("living")), () -> "no se suscribió");

        Instant adelantado = Instant.now().plusSeconds(120);
        BROKER.publicar(topic("living"), payload(16.0, adelantado));
        esperarSwitch("living", "ON");
        Instant guardada = estado("living").ultimaLectura();
        assertTrue(
                !guardada.isAfter(control.estado().fechaHora().plusSeconds(10)),
                "se guardó la hora del termostato adelantado: " + guardada);

        // La siguiente, con un ts correcto, no se toma como fuera de orden.
        BROKER.publicar(topic("living"), payload(25.0, Instant.now()));
        esperarSwitch("living", "OFF");
        assertTrue(estado("living").lecturasDescartadas().isEmpty());
    }

    @Test
    @DisplayName(
            "Crecer de 5 a 20 habitaciones y achicar a 5 en caliente (E15/E16): se suscribe y deja"
                    + " de escuchar lo que corresponde, nunca supera la contratada y no comanda lo"
                    + " que salió del sitio")
    void crecerYAchicarElSitio() throws Exception {
        // Los números del escenario de la clase: 5 habitaciones holgadas (4,8 kW con 6 kW
        // contratados) que crecen a 20 (25,3 kW con 12,7 kW contratados) y vuelven a 5.
        Inventario.Habitacion[] veinte = new Inventario.Habitacion[20];
        for (int i = 0; i < 5; i++) {
            veinte[i] = habitacion("room" + (i + 1), 21.0, 0.96);
        }
        for (int i = 5; i < 20; i++) {
            veinte[i] = habitacion("room" + (i + 1), 21.0, i < 15 ? 1.4 : 1.3);
        }
        Inventario cinco = inventario(6.0, Arrays.copyOfRange(veinte, 0, 5));
        Inventario grande = inventario(12.7, veinte);

        cargarSitio(cinco);
        iniciar(); // factor 1: las lecturas no vencen mientras se publican las 20
        for (int i = 0; i < 5; i++) {
            temperatura("room" + (i + 1), 18.0);
        }
        for (int i = 0; i < 5; i++) {
            esperarSwitch("room" + (i + 1), "ON"); // holgado: entran todas
        }

        // E15: crecer a 20, limitante.
        cargarSitio(grande);
        for (int i = 5; i < 20; i++) {
            temperatura("room" + (i + 1), 17.0);
        }
        esperar(
                () -> potenciaEncendida(grande) >= 12.7 - 1.4,
                () -> "no aprovechó la contratada: " + potenciaEncendida(grande) + " kW");
        seMantiene(
                Duration.ofMillis(1500),
                () -> potenciaEncendida(grande) <= 12.7 + 1e-9,
                () -> "superó la contratada: " + potenciaEncendida(grande) + " kW");
        assertTrue(
                picoDePotencia(grande) <= 12.7 + 1e-9,
                "pico de " + picoDePotencia(grande) + " kW con 12,7 contratados");

        // E16: achicar a 5. Las 15 que salen dejan de escucharse y de comandarse.
        Map<String, Integer> comandosAntes = new HashMap<>();
        for (int i = 5; i < 20; i++) {
            comandosAntes.put(
                    idSwitch("room" + (i + 1)),
                    SWITCHES.comandosA(idSwitch("room" + (i + 1))).size());
        }
        cargarSitio(cinco);
        esperar(
                () -> {
                    for (int i = 5; i < 20; i++) {
                        if (BROKER.haySuscriptor(topic("room" + (i + 1)))) {
                            return false;
                        }
                    }
                    return true;
                },
                () -> "sigue suscrito a termostatos que salieron del sitio");
        for (int i = 0; i < 5; i++) {
            temperatura("room" + (i + 1), 18.0);
            esperarSwitch("room" + (i + 1), "ON");
        }
        seMantiene(
                Duration.ofMillis(1500),
                () -> {
                    for (Map.Entry<String, Integer> e : comandosAntes.entrySet()) {
                        if (SWITCHES.comandosA(e.getKey()).size() != e.getValue()) {
                            return false;
                        }
                    }
                    return true;
                },
                () -> "comandó switches que ya no están en el sitio: " + SWITCHES.comandos());
    }

    /** Payload del termostato (§2) con el ts en hora real: epoch en segundos con 3 decimales. */
    private static String payload(double temperaturaC, Instant ts) {
        Instant ms = ts.truncatedTo(ChronoUnit.MILLIS);
        return "{\"id\":0,\"tC\":%s,\"ts\":%d.%03d}"
                .formatted(temperaturaC, ms.getEpochSecond(), ms.getNano() / 1_000_000);
    }
}
