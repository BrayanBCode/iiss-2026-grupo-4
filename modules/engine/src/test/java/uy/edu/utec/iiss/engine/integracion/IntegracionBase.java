package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uy.edu.utec.iiss.engine.adaptador.CoreAdapter;
import uy.edu.utec.iiss.engine.adaptador.CoreAdapter.EstadoControlado;
import uy.edu.utec.iiss.engine.control.ControlService;
import uy.edu.utec.iiss.engine.inventario.Inventario;

/**
 * Base de los tests de integración: el engine completo (Spring, listener MQTT, core, cliente REST
 * de switches) contra un broker MQTT embebido y switches HTTP falsos, sin Docker.
 *
 * <ul>
 *   <li>Los estímulos entran como en producción: el sitio y el control por el API REST (§5), las
 *       lecturas por MQTT (§2).
 *   <li>Se verifica lo que ven los switches (§3/§4) y, para explicar el porqué, el motivo de la
 *       decisión en el {@link CoreAdapter}.
 *   <li>Cada test arranca un engine nuevo; el broker y los switches se comparten y se limpian.
 * </ul>
 *
 * Por defecto el control arranca el jueves 2026-10-01 a las 09:00 UTC, fuera de la franja punta.
 */
@SpringBootTest(
        properties = {
            "api.security.api-key=" + IntegracionBase.CLAVE,
            "entorno.url=",
            "control.zona-horaria=UTC",
            "control.tick-ms=100",
            "control.reconciliacion-segundos=1",
            "switch.espera-reintento-segundos=1",
            "switch.timeout-conexion-ms=500",
            "switch.timeout-lectura-ms=1000"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class IntegracionBase {

    static final String CLAVE = "clave-it";
    static final String FECHA_INICIO = "2026-10-01T09:00:00Z";
    static final double POTENCIA_SITIO = 3.7;
    static final Duration ESPERA = Duration.ofSeconds(5);

    protected static final BrokerMqttEmbebido BROKER = new BrokerMqttEmbebido();
    protected static final SwitchesHttpFalsos SWITCHES = new SwitchesHttpFalsos();

    @DynamicPropertySource
    static void entorno(DynamicPropertyRegistry r) throws Exception {
        BROKER.iniciar();
        Path archivo = Files.createTempDirectory("ecowarm-it").resolve("inventario.json");
        r.add("inventario.archivo", archivo::toString);
        r.add("mqtt.broker.url", BROKER::url);
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected CoreAdapter adaptador;
    @Autowired protected ControlService control;
    @Autowired protected ObjectMapper json;

    @BeforeEach
    void limpiarEntorno() {
        BROKER.iniciar(); // por si un test lo dejó caído
        SWITCHES.reiniciar();
    }

    // ------------------------------------------------------------------
    // Inventario
    // ------------------------------------------------------------------

    protected static String idSwitch(String idHabitacion) {
        return "sw-" + idHabitacion;
    }

    protected static String topic(String idHabitacion) {
        return "ht-" + idHabitacion + "/status/temperature:0";
    }

    protected static Inventario.Habitacion habitacion(
            String id, double temperaturaEsperada, double potenciaKW) {
        return new Inventario.Habitacion(
                id,
                "Hab " + id,
                temperaturaEsperada,
                potenciaKW,
                "ht-" + id,
                topic(id),
                idSwitch(id),
                SWITCHES.url());
    }

    protected static Inventario inventario(double potenciaKW, Inventario.Habitacion... habs) {
        return new Inventario(
                "2.0",
                new Inventario.Sitio(
                        "sitio-it",
                        "Sitio de integración",
                        potenciaKW,
                        new Inventario.Tarifa(new Inventario.Franja("17:00", "23:00", "HABILES"))),
                List.of(habs));
    }

    /** PUT /sitio (§5.1). */
    protected void cargarSitio(Inventario inv) throws Exception {
        mvc.perform(conClave(put("/sitio")).content(json.writeValueAsString(inv)))
                .andExpect(status().isOk());
    }

    protected void cargarSitio(double potenciaKW, Inventario.Habitacion... habs) throws Exception {
        cargarSitio(inventario(potenciaKW, habs));
    }

    /** PUT /habitaciones/{id} (§5.1). */
    protected void modificarHabitacion(Inventario.Habitacion h) throws Exception {
        mvc.perform(conClave(put("/habitaciones/" + h.id())).content(json.writeValueAsString(h)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Control (§5.2)
    // ------------------------------------------------------------------

    protected void iniciar() throws Exception {
        iniciar(FECHA_INICIO, 1);
    }

    protected void iniciar(String fechaHora, double factorTiempo) throws Exception {
        comando(
                "{\"accion\":\"INICIAR\",\"fechaHora\":\"%s\",\"factorTiempo\":%s}"
                        .formatted(fechaHora, factorTiempo));
    }

    protected void detener() throws Exception {
        comando("{\"accion\":\"DETENER\"}");
    }

    private void comando(String cuerpo) throws Exception {
        mvc.perform(conClave(post("/control/comandos")).content(cuerpo)).andExpect(status().isOk());
    }

    protected static MockHttpServletRequestBuilder conClave(MockHttpServletRequestBuilder b) {
        return b.header("X-API-Key", CLAVE).contentType(MediaType.APPLICATION_JSON);
    }

    // ------------------------------------------------------------------
    // Termostatos (§2)
    // ------------------------------------------------------------------

    /**
     * Publica la lectura cuando el engine ya está suscrito al topic y, con el control en ejecución,
     * espera a que el adaptador la registre.
     */
    protected void temperatura(String idHabitacion, double temperaturaC) {
        esperar(
                () -> BROKER.haySuscriptor(topic(idHabitacion)),
                () -> "el engine no se suscribió a " + topic(idHabitacion));
        BROKER.publicarTemperatura(topic(idHabitacion), temperaturaC);
        if (control.enEjecucion()) {
            esperar(
                    () -> Objects.equals(estado(idHabitacion).temperaturaActual(), temperaturaC),
                    () -> "el core no registró " + temperaturaC + " °C en " + idHabitacion);
        }
    }

    // ------------------------------------------------------------------
    // Verificaciones
    // ------------------------------------------------------------------

    protected EstadoControlado estado(String idHabitacion) {
        return adaptador.estados().stream()
                .filter(e -> e.idHabitacion().equals(idHabitacion))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no existe la habitación " + idHabitacion));
    }

    protected String motivo(String idHabitacion) {
        return estado(idHabitacion).motivo();
    }

    /** Espera a que el switch de la habitación quede en {@code estado} ("ON"/"OFF"). */
    protected void esperarSwitch(String idHabitacion, String estado) {
        esperar(
                () -> SWITCHES.estado(idSwitch(idHabitacion)).equals(estado),
                () ->
                        "el switch de %s quedó %s y se esperaba %s (motivo: %s); comandos: %s"
                                .formatted(
                                        idHabitacion,
                                        SWITCHES.estado(idSwitch(idHabitacion)),
                                        estado,
                                        motivo(idHabitacion),
                                        SWITCHES.comandos()));
    }

    /** Espera a que el core decida por el motivo dado (p. ej. "SIN_POTENCIA_DISPONIBLE"). */
    protected void esperarMotivo(String idHabitacion, String motivo) {
        esperar(
                () -> motivo.equals(motivo(idHabitacion)),
                () -> idHabitacion + " tiene motivo " + motivo(idHabitacion) + ", no " + motivo);
    }

    /** Verifica que la condición se mantiene durante todo el lapso (lo que NO tiene que pasar). */
    protected static void seMantiene(
            Duration lapso, BooleanSupplier condicion, Supplier<String> mensaje) {
        long limite = System.nanoTime() + lapso.toNanos();
        while (System.nanoTime() < limite) {
            assertTrue(condicion.getAsBoolean(), mensaje);
            dormir(50);
        }
    }

    /** kW encendidos según lo que reportan los switches. */
    protected static double potenciaEncendida(Inventario inv) {
        return inv.habitaciones().stream()
                .filter(h -> SWITCHES.estado(h.idSwitch()).equals("ON"))
                .mapToDouble(Inventario.Habitacion::potenciaKW)
                .sum();
    }

    /**
     * Mayor potencia que llegó a estar encendida a la vez, reproduciendo los comandos en el orden
     * en que los recibieron los switches (todos arrancan apagados). Detecta excesos transitorios
     * que el estado final no muestra.
     */
    protected static double picoDePotencia(Inventario inv) {
        Map<String, Double> potencia =
                inv.habitaciones().stream()
                        .collect(
                                Collectors.toMap(
                                        Inventario.Habitacion::idSwitch,
                                        Inventario.Habitacion::potenciaKW));
        Map<String, Boolean> encendido = new HashMap<>();
        double pico = 0;
        for (SwitchesHttpFalsos.Comando c : SWITCHES.comandos()) {
            encendido.put(c.idSwitch(), c.accion().equals("ON"));
            double ahora =
                    encendido.entrySet().stream()
                            .filter(Map.Entry::getValue)
                            .mapToDouble(e -> potencia.getOrDefault(e.getKey(), 0.0))
                            .sum();
            pico = Math.max(pico, ahora);
        }
        return pico;
    }

    protected static Map<String, String> estadosSwitches(Inventario inv) {
        return inv.habitaciones().stream()
                .collect(
                        Collectors.toMap(
                                Inventario.Habitacion::id, h -> SWITCHES.estado(h.idSwitch())));
    }

    protected static void esperar(BooleanSupplier condicion, Supplier<String> mensaje) {
        esperar(ESPERA, condicion, mensaje);
    }

    protected static void esperar(
            Duration hasta, BooleanSupplier condicion, Supplier<String> mensaje) {
        long limite = System.nanoTime() + hasta.toNanos();
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                fail(mensaje.get());
            }
            dormir(20);
        }
    }

    protected static void assertSwitch(String idHabitacion, String estado) {
        assertEquals(estado, SWITCHES.estado(idSwitch(idHabitacion)), "switch de " + idHabitacion);
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
