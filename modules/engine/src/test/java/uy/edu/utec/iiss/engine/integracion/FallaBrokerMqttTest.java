package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import uy.edu.utec.iiss.engine.control.EstadoControl;
import uy.edu.utec.iiss.engine.mqtt.LecturaMqttListener;

/**
 * El broker MQTT se cae y se recupera (endurecimiento de lo diseñado en la iteración 4). Mientras
 * no está, el control sigue con lo último que sabía; cuando vuelve, el engine se reconecta solo,
 * restablece las suscripciones (también las de habitaciones agregadas durante la caída) y retoma.
 *
 * <p>La espera entre reintentos se baja a 1 s para que el test no dure de más. Con la reconexión
 * automática de Paho (espera que se duplica hasta 128 s, compartida entre clientes) {@link
 * #caidaLarga_seReconectaEnSegundos} falla.
 */
@TestPropertySource(properties = "mqtt.reconexion-max-segundos=1")
@DisplayName("Integración: caída y recuperación del broker MQTT")
class FallaBrokerMqttTest extends IntegracionBase {

    static final Duration RECONEXION = Duration.ofSeconds(5);

    @Autowired LecturaMqttListener mqtt;

    @Test
    @DisplayName(
            "El broker cae: el control conserva la última decisión y el API sigue respondiendo")
    void durante_laCaida() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        BROKER.detener();
        esperar(() -> !mqtt.conectado(), () -> "el engine no notó la caída del broker");

        mvc.perform(conClave(get("/diagnostico")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mqttConectado").value(false))
                .andExpect(jsonPath("$.control").value("EN_EJECUCION"));
        int comandos = SWITCHES.comandos().size();
        seMantiene(
                Duration.ofMillis(1500),
                () ->
                        SWITCHES.estado("sw-living").equals("ON")
                                && SWITCHES.comandos().size() == comandos,
                () -> "la caída del broker cambió los switches: " + SWITCHES.comandos());
        assertEquals("CALEFACCIONANDO", motivo("living"));
    }

    @Test
    @DisplayName(
            "Caída larga (8 s): al volver el broker el engine se reconecta en segundos, se"
                    + " re-suscribe y vuelve a controlar")
    void caidaLarga_seReconectaEnSegundos() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        BROKER.detener();
        Thread.sleep(
                8000); // con una espera que se duplica (1, 2, 4, 8 s) el próximo intento sería a
        // los 15 s
        BROKER.iniciar();

        esperar(
                RECONEXION,
                () -> mqtt.conectado() && BROKER.haySuscriptor(topic("living")),
                () -> "el engine no se reconectó ni se re-suscribió en " + RECONEXION);
        temperatura("living", 25.0);
        esperarSwitch("living", "OFF");
        mvc.perform(conClave(get("/diagnostico")))
                .andExpect(jsonPath("$.mqttConectado").value(true));
    }

    @Test
    @DisplayName("Una habitación agregada con el broker caído se suscribe cuando el broker vuelve")
    void habitacionAgregadaDuranteLaCaida() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");

        BROKER.detener();
        esperar(() -> !mqtt.conectado(), () -> "el engine no notó la caída del broker");
        mvc.perform(
                        conClave(post("/habitaciones"))
                                .content(json.writeValueAsString(habitacion("cocina", 20.0, 1.0))))
                .andExpect(status().isCreated());
        BROKER.iniciar();

        esperar(
                RECONEXION,
                () ->
                        BROKER.haySuscriptor(topic("living"))
                                && BROKER.haySuscriptor(topic("cocina")),
                () -> "faltan suscripciones tras la reconexión");
        temperatura("cocina", 15.0);
        esperarSwitch("cocina", "ON");
    }

    @Test
    @DisplayName(
            "Como el simulador (×60, ts en hora del control): el broker se cae unos segundos y al"
                + " volver las lecturas siguen valiendo, sin descartes (corrida del 2026-11-04)")
    void caidaCorta_tsEnHoraDelControl_noVencenLasLecturas() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar("2026-11-03T13:00:00Z", 60); // martes 10:00 en Montevideo
        esperar(() -> BROKER.haySuscriptor(topic("living")), () -> "no se suscribió");
        BROKER.publicar(topic("living"), payloadEnHoraDelControl(16.0));
        esperarSwitch("living", "ON");

        BROKER.detener(); // 3 s reales = 3 min de control: menos que la vigencia
        Thread.sleep(3000);
        BROKER.iniciar();
        esperar(
                RECONEXION,
                () -> mqtt.conectado() && BROKER.haySuscriptor(topic("living")),
                () -> "no se reconectó");

        BROKER.publicar(topic("living"), payloadEnHoraDelControl(25.0));
        esperarSwitch("living", "OFF");
        assertEquals("SATISFECHA", motivo("living"));
        assertEquals(Map.of(), estado("living").lecturasDescartadas());
    }

    /** Lectura con el ts como lo escribe el simulador: la hora del control, no la real. */
    private String payloadEnHoraDelControl(double temperaturaC) {
        EstadoControl c = control.estado();
        long transcurrido = Duration.between(c.iniciadoEn(), Instant.now()).toMillis();
        Instant ts =
                c.fechaHora()
                        .plusMillis(Math.round(transcurrido * c.factorTiempo()))
                        .truncatedTo(ChronoUnit.MILLIS);
        return "{\"id\":0,\"tC\":%s,\"ts\":%d.%03d}"
                .formatted(temperaturaC, ts.getEpochSecond(), ts.getNano() / 1_000_000);
    }

    @Test
    @DisplayName(
            "INICIAR con el broker caído funciona (sin lecturas, todo apagado) y el control arranca"
                    + " de verdad cuando el broker aparece")
    void iniciarConElBrokerCaido() throws Exception {
        BROKER.detener();
        esperar(() -> !mqtt.conectado(), () -> "el engine no notó la caída del broker");
        SWITCHES.fijar("sw-living", "ON");
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();

        esperarSwitch("living", "OFF"); // sin lecturas no se calefacciona
        assertEquals("SIN_LECTURA", motivo("living"));

        BROKER.iniciar();
        esperar(
                RECONEXION,
                () -> BROKER.haySuscriptor(topic("living")),
                () -> "no se suscribió al volver el broker");
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");
    }
}
