package uy.edu.utec.iiss.engine.registro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uy.edu.utec.iiss.engine.inventario.Inventario;
import uy.edu.utec.iiss.engine.inventario.InventarioInvalidoException;
import uy.edu.utec.iiss.engine.inventario.InventarioService;
import uy.edu.utec.iiss.engine.mqtt.LecturaMqttListener;

/**
 * Arranque del engine y registro ante el entorno (estándar v2, §6).
 *
 * <ol>
 *   <li>Recupera el inventario persistido (si hay) y lo pone en uso.
 *   <li>Si hay entorno configurado ({@code entorno.url}): se registra con {@code POST
 *       {entorno}/controladores}, reintentando con espera creciente (2, 4, 8 ... hasta 30 s) porque
 *       el entorno puede tardar en levantar. El broker MQTT es el que devuelve el registro (no se
 *       hardcodea).
 *   <li>Sin entorno (instalación real): usa {@code mqtt.broker.url}.
 *   <li>Si después del registro todavía no hay sitio, lo pide al entorno ({@code GET
 *       {entorno}/sitio}, la vía "pull"). La vía "push" (PUT /sitio) funciona siempre.
 * </ol>
 */
@Component
public class RegistroEntorno {

    private static final Logger log = LoggerFactory.getLogger(RegistroEntorno.class);
    private static final String HEADER_API_KEY = "X-API-Key";
    private static final long ESPERA_MAXIMA_MS = 30_000;

    private final InventarioService inventarioService;
    private final LecturaMqttListener mqtt;
    private final ObjectMapper json;
    private final RestTemplate http;
    private final String entornoUrl;
    private final String entornoApiKey;
    private final String etiqueta;
    private final String baseUrl;
    private final String apiKeyPropia;
    private final String brokerPorDefecto;

    private volatile boolean registrado;

    public RegistroEntorno(
            InventarioService inventarioService,
            LecturaMqttListener mqtt,
            ObjectMapper json,
            @Value("${entorno.url:}") String entornoUrl,
            @Value("${entorno.api-key:}") String entornoApiKey,
            @Value("${controlador.etiqueta:ecowarm-grupo4}") String etiqueta,
            @Value("${controlador.base-url:http://localhost:8080}") String baseUrl,
            @Value("${api.security.api-key}") String apiKeyPropia,
            @Value("${mqtt.broker.url:tcp://localhost:1883}") String brokerPorDefecto) {
        this.inventarioService = inventarioService;
        this.mqtt = mqtt;
        this.json = json;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(3000);
        // Holgado a propósito: antes de responder el registro, el entorno nos carga el sitio y nos
        // da INICIAR.
        fabrica.setReadTimeout(30_000);
        // Buffering: manda Content-Length en lugar de un cuerpo "chunked", que algunos servidores
        // no leen.
        this.http = new RestTemplate(new BufferingClientHttpRequestFactory(fabrica));
        this.entornoUrl = sinBarraFinal(entornoUrl);
        this.entornoApiKey = entornoApiKey;
        this.etiqueta = etiqueta;
        this.baseUrl = baseUrl;
        this.apiKeyPropia = apiKeyPropia;
        this.brokerPorDefecto = brokerPorDefecto;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void arrancar() {
        inventarioService.cargarGuardado();
        if (entornoUrl.isBlank()) {
            log.info(
                    "Sin entorno configurado (ENTORNO_URL): se usa el broker {}.",
                    brokerPorDefecto);
            mqtt.conectar(brokerPorDefecto);
            return;
        }
        Thread t = new Thread(this::registrarConReintentos, "registro-entorno");
        t.setDaemon(true);
        t.start();
    }

    private void registrarConReintentos() {
        long espera = 2_000;
        while (!registrado && !Thread.currentThread().isInterrupted()) {
            try {
                registrar();
                return;
            } catch (Exception e) {
                log.warn(
                        "No se pudo registrar ante el entorno {}: {}. Reintento en {} s.",
                        entornoUrl,
                        e.getMessage(),
                        espera / 1000);
                try {
                    Thread.sleep(espera);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                espera = Math.min(espera * 2, ESPERA_MAXIMA_MS);
            }
        }
    }

    /** Un intento de registro. Lanza si el entorno no respondió 2xx. */
    void registrar() {
        Map<String, String> cuerpo =
                Map.of("etiqueta", etiqueta, "baseUrl", baseUrl, "apiKey", apiKeyPropia);
        JsonNode respuesta =
                http.exchange(
                                entornoUrl + "/controladores",
                                HttpMethod.POST,
                                new HttpEntity<>(cuerpo, headers()),
                                JsonNode.class)
                        .getBody();
        registrado = true;
        log.info(
                "Registrado ante el entorno {} como '{}' (baseUrl {}).",
                entornoUrl,
                etiqueta,
                baseUrl);

        mqtt.conectar(
                brokerInformado(respuesta)
                        .orElseGet(
                                () -> {
                                    log.warn(
                                            "El registro no informó broker MQTT; se usa {}.",
                                            brokerPorDefecto);
                                    return brokerPorDefecto;
                                }));

        if (inventarioService.actual().isEmpty()) {
            pedirSitio();
        }
    }

    static Optional<String> brokerInformado(JsonNode respuesta) {
        if (respuesta == null) {
            return Optional.empty();
        }
        JsonNode m = respuesta.path("mqtt");
        if (!m.path("host").isTextual()
                || m.path("host").asText().isBlank()
                || !m.path("puerto").canConvertToInt()) {
            return Optional.empty();
        }
        return Optional.of("tcp://" + m.path("host").asText() + ":" + m.path("puerto").asInt());
    }

    private void pedirSitio() {
        try {
            JsonNode sitio =
                    http.exchange(
                                    entornoUrl + "/sitio",
                                    HttpMethod.GET,
                                    new HttpEntity<>(headers()),
                                    JsonNode.class)
                            .getBody();
            inventarioService.reemplazar(json.treeToValue(sitio, Inventario.class));
            log.info("Sitio obtenido del entorno (GET {}/sitio).", entornoUrl);
        } catch (InventarioInvalidoException e) {
            log.warn("El sitio que ofrece el entorno no es válido: {}", e.errores());
        } catch (Exception e) {
            log.info(
                    "El entorno no ofrece el sitio por GET ({}); se espera el PUT /sitio.",
                    e.getMessage());
        }
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (entornoApiKey != null && !entornoApiKey.isBlank()) {
            h.set(HEADER_API_KEY, entornoApiKey);
        }
        return h;
    }

    public boolean registrado() {
        return registrado;
    }

    private static String sinBarraFinal(String url) {
        String u = url == null ? "" : url.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }
}
