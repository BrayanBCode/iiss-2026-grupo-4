package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.engine.adaptador.MotivoDescarte;

/**
 * Mensajes de termostato perdidos, rotos, repetidos o que no son para nadie. Un mensaje malo nunca
 * cambia la decisión: se conserva el último dato bueno.
 */
@DisplayName("Integración: mensajes perdidos, inválidos y repetidos")
class PerdidaMensajesTest extends IntegracionBase {

    @Test
    @DisplayName("Un payload que no es JSON o no trae tC se descarta y se conserva el último dato")
    void payloadInvalido() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        BROKER.publicar(topic("living"), "esto no es json");
        BROKER.publicar(topic("living"), "{\"id\":0,\"tF\":80.0}");
        BROKER.publicar(topic("living"), "{\"id\":0,\"tC\":\"veinte\"}");

        seMantiene(
                Duration.ofMillis(700),
                () ->
                        estado("living").temperaturaActual() == 16.0
                                && SWITCHES.estado("sw-living").equals("ON"),
                () -> "un payload inválido cambió la lectura: " + estado("living"));
        temperatura("living", 25.0); // el canal sigue sano
        esperarSwitch("living", "OFF");
    }

    @Test
    @DisplayName("Una lectura fuera del rango físico del sensor (-40..85 °C) se descarta")
    void lecturaFueraDeRango() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        BROKER.publicarTemperatura(topic("living"), 150.0); // apagaría si se creyera
        BROKER.publicarTemperatura(topic("living"), -60.0);

        seMantiene(
                Duration.ofMillis(700),
                () ->
                        estado("living").temperaturaActual() == 16.0
                                && SWITCHES.estado("sw-living").equals("ON"),
                () -> "una lectura imposible cambió la decisión: " + estado("living"));
    }

    @Test
    @DisplayName("Lecturas repetidas (QoS 1 puede duplicar) no generan comandos repetidos")
    void lecturasDuplicadas() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        for (int i = 0; i < 5; i++) {
            BROKER.publicarTemperatura(topic("living"), 16.0);
        }
        seMantiene(
                Duration.ofMillis(700),
                () -> SWITCHES.comandosA("sw-living").size() == 1,
                () -> "se repitieron comandos: " + SWITCHES.comandos());
    }

    @Test
    @DisplayName("Un mensaje en un topic que no está en el inventario se ignora")
    void topicDesconocido() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        BROKER.publicarTemperatura("ht-intruso/status/temperature:0", 30.0);
        BROKER.publicarTemperatura("otra/cosa", 30.0);

        seMantiene(
                Duration.ofMillis(500),
                () -> SWITCHES.comandos().size() == 1,
                () -> "un topic ajeno generó comandos: " + SWITCHES.comandos());
        assertEquals(16.0, estado("living").temperaturaActual());
    }

    @Test
    @DisplayName(
            "Se pierde la lectura que haría apagar: la habitación sigue con el último dato hasta"
                    + " que llega uno nuevo (o vence, ver TermostatoCaidoTest)")
    void lecturaPerdida() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        // El termostato publica 25 °C con el broker caído: ese mensaje no llega a nadie.
        BROKER.detener();
        BROKER.iniciar();

        seMantiene(
                Duration.ofMillis(700),
                () -> SWITCHES.estado("sw-living").equals("ON"),
                () -> "se apagó sin haber recibido la lectura");
        esperar(
                Duration.ofSeconds(10),
                () -> BROKER.haySuscriptor(topic("living")),
                () -> "el engine no volvió a suscribirse");
        temperatura("living", 25.0); // la siguiente lectura sí llega
        esperarSwitch("living", "OFF");
    }

    @Test
    @DisplayName(
            "Se usa el ts del termostato: exacto como instante de la lectura; una más vieja que la"
                    + " última o que la vigencia se descarta y se cuenta; sin ts se usa la hora de"
                    + " llegada")
    void tsDelTermostato() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        esperar(() -> BROKER.haySuscriptor(topic("living")), () -> "no se suscribió");
        Instant inicio = Instant.parse(FECHA_INICIO);
        // El ts llega en hora real: se convierte con fechaHora + (ts - iniciadoEn) x factor (factor
        // 1).
        Instant iniciadoEn = control.estado().iniciadoEn();
        esperar(
                () -> Instant.now().isAfter(iniciadoEn.plusMillis(300)),
                () -> "el reloj real no avanzó");

        // ts exacto: 250 ms reales después del INICIAR son 250 ms de control (no la hora de
        // llegada)
        Instant medido = iniciadoEn.plusMillis(250).truncatedTo(ChronoUnit.MILLIS);
        Instant esperado = inicio.plus(Duration.between(iniciadoEn, medido));
        BROKER.publicar(topic("living"), payload(16.0, tsReal(medido)));
        esperar(
                () -> esperado.equals(estado("living").ultimaLectura()),
                () -> "no se guardó el ts exacto: " + estado("living").ultimaLectura());
        esperarSwitch("living", "ON");

        // un minuto más vieja que la última: llegó fuera de orden, se descarta
        BROKER.publicar(topic("living"), payload(25.0, tsReal(iniciadoEn.minusSeconds(60))));
        seMantiene(
                Duration.ofMillis(700),
                () -> estado("living").temperaturaActual() == 16.0,
                () -> "se usó una lectura fuera de orden: " + estado("living"));
        assertEquals(
                Map.of(MotivoDescarte.FUERA_DE_ORDEN, 1), estado("living").lecturasDescartadas());

        // 11 min más vieja que ahora (vigencia 10 min): dato obsoleto, se descarta y se cuenta (D8)
        BROKER.publicar(topic("living"), payload(25.0, tsReal(iniciadoEn.minusSeconds(660))));
        esperar(
                () -> estado("living").lecturasDescartadas().containsKey(MotivoDescarte.TS_VENCIDO),
                () -> "no se contó la lectura vencida: " + estado("living").lecturasDescartadas());
        assertEquals(16.0, estado("living").temperaturaActual());

        // sin ts: vale igual, con la hora de llegada
        BROKER.publicar(topic("living"), "{\"id\":0,\"tC\":25.0}");
        esperarSwitch("living", "OFF");
        assertTrue(estado("living").ultimaLectura().isAfter(esperado));
    }

    /** Instante real como el ts del termostato: epoch en segundos con 3 decimales. */
    private static String tsReal(Instant instante) {
        return "%d.%03d".formatted(instante.getEpochSecond(), instante.getNano() / 1_000_000);
    }

    private static String payload(double temperaturaC, String ts) {
        return "{\"id\":0,\"tC\":%s,\"ts\":%s}".formatted(temperaturaC, ts);
    }
}
