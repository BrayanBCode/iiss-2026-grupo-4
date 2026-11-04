package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    /** Payload del termostato (§2) con el ts en hora real: epoch en segundos con 3 decimales. */
    private static String payload(double temperaturaC, Instant ts) {
        Instant ms = ts.truncatedTo(ChronoUnit.MILLIS);
        return "{\"id\":0,\"tC\":%s,\"ts\":%d.%03d}"
                .formatted(temperaturaC, ms.getEpochSecond(), ms.getNano() / 1_000_000);
    }
}
