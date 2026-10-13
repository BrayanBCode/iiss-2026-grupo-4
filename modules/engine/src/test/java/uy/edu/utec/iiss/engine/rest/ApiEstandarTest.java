package uy.edu.utec.iiss.engine.rest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.engine.control.ControlService;
import uy.edu.utec.iiss.engine.inventario.InventarioEjemplo;
import uy.edu.utec.iiss.engine.inventario.RepositorioInventario;
import uy.edu.utec.iiss.engine.switches.ClienteSwitch;
import uy.edu.utec.iiss.engine.switches.EstadoSwitch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración del API del controlador (§5) con el contexto Spring
 * completo: filtro de API key, controladores, persistencia en un archivo
 * temporal, control y core reales. Solo los switches son un doble (sin red,
 * sin Mockito: un switch falso que anota las llamadas).
 * El broker MQTT apunta a un puerto cerrado: el engine tiene que arrancar y
 * responder igual (el entorno puede fallar).
 */
@SpringBootTest(properties = {
        "api.security.api-key=clave-test",
        "mqtt.broker.url=tcp://127.0.0.1:1",
        "entorno.url=",
        "control.tick-ms=0",
        "control.reconciliacion-segundos=0"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("API del controlador (§5) de punta a punta")
class ApiEstandarTest {

    static Path archivo;

    @DynamicPropertySource
    static void archivoTemporal(DynamicPropertyRegistry r) throws Exception {
        archivo = Files.createTempDirectory("ecowarm-it").resolve("inventario.json");
        r.add("inventario.archivo", archivo::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired ControlService control;
    @Autowired RepositorioInventario repositorio;
    @Autowired SwitchesFalsos switches;

    /** Switch falso: siempre DESCONOCIDO al consultar; anota "url|id|ACCION" de cada comando. */
    static class SwitchesFalsos implements ClienteSwitch {
        final List<String> comandos = new CopyOnWriteArrayList<>();
        final List<String> consultas = new CopyOnWriteArrayList<>();

        @Override
        public EstadoSwitch comandar(String url, String idSwitch, Accion accion) {
            comandos.add(url + "|" + idSwitch + "|" + accion);
            return accion == Accion.ON ? EstadoSwitch.ON : EstadoSwitch.OFF;
        }

        @Override
        public EstadoSwitch consultar(String url, String idSwitch) {
            consultas.add(url + "|" + idSwitch);
            return EstadoSwitch.DESCONOCIDO;
        }
    }

    @TestConfiguration
    static class Dobles {
        @Bean
        @Primary
        SwitchesFalsos switchesFalsos() {
            return new SwitchesFalsos();
        }
    }

    @BeforeEach
    void limpiar() throws Exception {
        Files.deleteIfExists(archivo);
    }

    /** Los switches se comandan en su propio hilo (CoreAdapter): se espera un poco a que salga el comando. */
    private void comando(String url, String id, Accion a) throws InterruptedException {
        esperarHasta(() -> switches.comandos.contains(url + "|" + id + "|" + a));
        assertTrue(switches.comandos.contains(url + "|" + id + "|" + a), "no se envió " + a + " a " + id + ": " + switches.comandos);
    }

    private static void esperarHasta(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (!condicion.getAsBoolean() && System.nanoTime() < limite) {
            Thread.sleep(10);
        }
    }

    private static MockHttpServletRequestBuilder conClave(MockHttpServletRequestBuilder b) {
        return b.header("X-API-Key", "clave-test").contentType(MediaType.APPLICATION_JSON);
    }

    // ---------------- autenticación ----------------

    @Test
    @DisplayName("Sin X-API-Key o con una incorrecta -> 401 con el cuerpo de error uniforme")
    void sinClave_401() throws Exception {
        mvc.perform(get("/sitio")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.mensaje").exists());
        mvc.perform(get("/control").header("X-API-Key", "otra")).andExpect(status().isUnauthorized());
    }

    // ---------------- §5.1 sitio ----------------

    @Test
    @DisplayName("GET /sitio antes de cargar nada -> 404")
    void sitioSinCargar_404() throws Exception {
        mvc.perform(conClave(get("/sitio"))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /sitio -> 200, queda persistido, y GET devuelve lo persistido")
    void putYGetSitio() throws Exception {
        mvc.perform(conClave(put("/sitio")).content(InventarioEjemplo.JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sitio").value("casa-rodriguez"))
                .andExpect(jsonPath("$.habitaciones").value(2));

        assertEquals(InventarioEjemplo.casaRodriguez(), repositorio.cargar().orElseThrow());
        mvc.perform(conClave(get("/sitio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("2.0"))
                .andExpect(jsonPath("$.sitio.potenciaContratadaKW").value(3.7))
                .andExpect(jsonPath("$.sitio.tarifa.punta.desde").value("17:00"))
                .andExpect(jsonPath("$.habitaciones[1].topicTermostato").value("ht-sim-room2/status/temperature:0"))
                .andExpect(jsonPath("$.habitaciones[1].temperaturaEsperada").value(20.0));
    }

    @Test
    @DisplayName("PUT /sitio inválido -> 400 con el detalle, y queda la configuración anterior")
    void putInvalido_400YQuedaElAnterior() throws Exception {
        mvc.perform(conClave(put("/sitio")).content(InventarioEjemplo.JSON)).andExpect(status().isOk());
        String duplicado = InventarioEjemplo.JSON.replace("sw-sim-room2", "sw-sim-room1").replace("\"potenciaKW\": 0.8", "\"potenciaKW\": 0");

        mvc.perform(conClave(put("/sitio")).content(duplicado))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errores", hasItem("idSwitch duplicado: 'sw-sim-room1'")))
                .andExpect(jsonPath("$.errores", hasItem(startsWith("habitación 'room2': potenciaKW"))));

        mvc.perform(conClave(get("/sitio"))).andExpect(jsonPath("$.habitaciones[1].idSwitch").value("sw-sim-room2"));
    }

    @Test
    @DisplayName("PUT /sitio con major de schemaVersion distinto, o con JSON roto -> 400")
    void putSchemaOJsonInvalido_400() throws Exception {
        mvc.perform(conClave(put("/sitio")).content(InventarioEjemplo.JSON.replace("\"2.0\"", "\"3.0\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(conClave(put("/sitio")).content("{ roto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(conClave(put("/sitio")).content(InventarioEjemplo.JSON.replace("3.7", "\"mucha\"")))
                .andExpect(status().isBadRequest());
    }

    // ---------------- §5.2 control ----------------

    @Test
    @DisplayName("GET /control arranca DETENIDO, con fechaHora, factorTiempo e iniciadoEn en null")
    void controlArrancaDetenido() throws Exception {
        mvc.perform(conClave(get("/control")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("DETENIDO"))
                .andExpect(jsonPath("$.fechaHora").isEmpty())
                .andExpect(jsonPath("$.factorTiempo").isEmpty())
                .andExpect(jsonPath("$.iniciadoEn").isEmpty());
    }

    @Test
    @DisplayName("INICIAR -> EN_EJECUCION con la hora del control; consulta los switches (§4) y los comanda (§3)")
    void iniciar() throws Exception {
        mvc.perform(conClave(put("/sitio")).content(InventarioEjemplo.JSON)).andExpect(status().isOk());

        mvc.perform(conClave(post("/control/comandos"))
                        .content("{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:03.120Z\",\"factorTiempo\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_EJECUCION"))
                .andExpect(jsonPath("$.factorTiempo").value(10.0))
                .andExpect(jsonPath("$.fechaHora", startsWith("2026-10-01T09:15:")))
                .andExpect(jsonPath("$.iniciadoEn").exists());

        esperarHasta(() -> switches.consultas.contains("http://switches:8081|sw-sim-room1"));
        assertTrue(switches.consultas.contains("http://switches:8081|sw-sim-room1"));
        comando("http://switches:8081", "sw-sim-room1", Accion.OFF);   // sin lecturas: OFF conocido
    }

    @Test
    @DisplayName("Con el control en ejecución, una lectura con déficit enciende el switch de su habitación")
    void lecturaEnEjecucion_enciende() throws Exception {
        mvc.perform(conClave(put("/sitio")).content(InventarioEjemplo.JSON)).andExpect(status().isOk());
        mvc.perform(conClave(post("/control/comandos"))
                .content("{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:03.120Z\"}")).andExpect(status().isOk());

        control.alRecibirLectura("room1", 18.0);

        comando("http://switches:8081", "sw-sim-room1", Accion.ON);
        mvc.perform(conClave(get("/diagnostico")))
                .andExpect(jsonPath("$.habitaciones[0].motivo").value("CALEFACCIONANDO"));
    }

    @Test
    @DisplayName("DETENIDO no comanda switches aunque lleguen lecturas; DETENER es idempotente")
    void detenido_noComanda() throws Exception {
        mvc.perform(conClave(put("/sitio")).content(InventarioEjemplo.JSON)).andExpect(status().isOk());
        mvc.perform(conClave(post("/control/comandos")).content("{\"accion\":\"DETENER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("DETENIDO"));
        mvc.perform(conClave(post("/control/comandos")).content("{\"accion\":\"DETENER\"}")).andExpect(status().isOk());

        control.alRecibirLectura("room1", 10.0);

        assertTrue(switches.comandos.isEmpty(), "comandó estando detenido: " + switches.comandos);
    }

    @Test
    @DisplayName("INICIAR sobre uno en ejecución re-sincroniza (200, no error)")
    void iniciarDosVeces_resincroniza() throws Exception {
        mvc.perform(conClave(post("/control/comandos"))
                .content("{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:00:00.000Z\"}")).andExpect(status().isOk());
        mvc.perform(conClave(post("/control/comandos"))
                        .content("{\"accion\":\"INICIAR\",\"fechaHora\":\"2027-01-01T00:00:00.000Z\",\"factorTiempo\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaHora", startsWith("2027-01-01T00:00:0")))
                .andExpect(jsonPath("$.factorTiempo").value(2.0));
    }

    @Test
    @DisplayName("Comandos inválidos -> 400: acción desconocida, sin fechaHora, fechaHora no ISO, factor <= 0 o no numérico")
    void comandosInvalidos_400() throws Exception {
        for (String cuerpo : new String[]{
                "{\"accion\":\"PAUSAR\"}",
                "{\"accion\":\"INICIAR\"}",
                "{\"accion\":\"INICIAR\",\"fechaHora\":\"ayer\"}",
                "{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:03.120Z\",\"factorTiempo\":0}",
                "{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:03.120Z\",\"factorTiempo\":-3}",
                "{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:03.120Z\",\"factorTiempo\":\"10\"}",
                "[]"}) {
            mvc.perform(conClave(post("/control/comandos")).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
        mvc.perform(conClave(get("/control"))).andExpect(jsonPath("$.estado").value("DETENIDO"));
    }

    @Test
    @DisplayName("PUT /sitio en caliente (EN_EJECUCION) se adopta sin reiniciar: la habitación nueva se comanda")
    void putEnCaliente() throws Exception {
        mvc.perform(conClave(put("/sitio")).content(InventarioEjemplo.JSON)).andExpect(status().isOk());
        mvc.perform(conClave(post("/control/comandos"))
                .content("{\"accion\":\"INICIAR\",\"fechaHora\":\"2026-10-01T09:15:03.120Z\"}")).andExpect(status().isOk());

        mvc.perform(conClave(put("/sitio")).content(InventarioEjemplo.JSON.replace("sw-sim-room2", "sw-sim-nuevo")))
                .andExpect(status().isOk());

        comando("http://switches:8081", "sw-sim-nuevo", Accion.OFF);
        mvc.perform(conClave(get("/control"))).andExpect(jsonPath("$.estado").value("EN_EJECUCION"));
    }

    // ---------------- ABM de habitaciones (fuera del estándar) ----------------

    @Test
    @DisplayName("ABM de habitaciones sobre el inventario: alta 201+Location, duplicado 409, baja 204, inexistente 404")
    void abmHabitaciones() throws Exception {
        mvc.perform(conClave(put("/sitio")).content(InventarioEjemplo.JSON)).andExpect(status().isOk());
        String nueva = """
                {"id":"room3","nombre":"Cocina","temperaturaEsperada":19.0,"potenciaKW":1.0,
                 "idTermostato":"ht-sim-room3","topicTermostato":"ht-sim-room3/status/temperature:0",
                 "idSwitch":"sw-sim-room3","urlSwitch":"http://switches:8081"}""";

        mvc.perform(conClave(post("/habitaciones")).content(nueva))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/habitaciones/room3"));
        mvc.perform(conClave(post("/habitaciones")).content(nueva)).andExpect(status().isConflict());
        mvc.perform(conClave(get("/sitio"))).andExpect(jsonPath("$.habitaciones[2].id").value("room3"));
        mvc.perform(conClave(delete("/habitaciones/room3"))).andExpect(status().isNoContent());
        mvc.perform(conClave(get("/habitaciones/room3"))).andExpect(status().isNotFound());
    }
}
