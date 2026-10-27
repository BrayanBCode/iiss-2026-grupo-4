package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
