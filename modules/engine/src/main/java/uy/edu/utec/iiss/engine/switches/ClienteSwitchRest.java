package uy.edu.utec.iiss.engine.switches;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uy.edu.utec.iiss.core.model.Accion;

/**
 * Cliente REST de los switches según el estándar v2 (§3 comandar, §4 consultar), con el header
 * {@code X-API-Key} que exige el switch.
 *
 * <p><b>Timeouts en tiempo de control (T37).</b> Los configurados ({@code switch.timeout-*}) valen
 * con factor 1; con el control acelerado se dividen por el factor, con un piso de {@value
 * #TIMEOUT_MINIMO_MS} ms reales y nunca más que lo configurado. A ×60, esperar 10 s reales a un
 * switch son 10 min de control en los que el {@link uy.edu.utec.iiss.engine.adaptador.CoreAdapter},
 * que llama a los switches de a uno, no atiende a ningún otro (prueba de estrés {@code
 * EstresTest}). El adaptador además espacia los reintentos a un switch que viene fallando y lo
 * atiende después de los sanos.
 */
@Component
public class ClienteSwitchRest implements ClienteSwitch {

    static final String HEADER_API_KEY = "X-API-Key";

    /** Piso de los timeouts escalados: menos que esto ya no distingue lento de caído en una LAN. */
    static final int TIMEOUT_MINIMO_MS = 500;

    private static final Logger log = LoggerFactory.getLogger(ClienteSwitchRest.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAXIMO_EN_EL_LOG = 120;

    private final int timeoutConexionMs;
    private final int timeoutLecturaMs;
    // Se reemplaza entero en cada INICIAR (ajustarAlFactor): un pedido en curso termina con los
    // timeouts con que empezó y el siguiente usa los nuevos.
    private volatile RestTemplate http;
    private final String apiKey;

    public ClienteSwitchRest(
            @Value("${switch.api-key:}") String apiKey,
            @Value("${switch.timeout-conexion-ms:2000}") int timeoutConexionMs,
            @Value("${switch.timeout-lectura-ms:10000}") int timeoutLecturaMs) {
        this.timeoutConexionMs = timeoutConexionMs;
        this.timeoutLecturaMs = timeoutLecturaMs;
        this.http = cliente(timeoutConexionMs, timeoutLecturaMs);
        this.apiKey = apiKey;
    }

    private static RestTemplate cliente(int conexionMs, int lecturaMs) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(conexionMs);
        fabrica.setReadTimeout(lecturaMs);
        // Buffering: manda Content-Length en lugar de un cuerpo "chunked", que algunos servidores
        // no leen.
        return new RestTemplate(new BufferingClientHttpRequestFactory(fabrica));
    }

    @Override
    public void ajustarAlFactor(double factorTiempo) {
        int conexion = timeoutEscalado(timeoutConexionMs, factorTiempo);
        int lectura = timeoutEscalado(timeoutLecturaMs, factorTiempo);
        http = cliente(conexion, lectura);
        log.info(
                "Timeouts de los switches con factor {}: {} ms de conexión, {} ms de respuesta.",
                factorTiempo,
                conexion,
                lectura);
    }

    /**
     * {@code configurado / factor}, sin bajar de {@value #TIMEOUT_MINIMO_MS} ms ni pasar de lo
     * configurado: con factor 60 y 10 s, medio segundo real (30 s de control); con factor 1 o
     * menos, lo configurado. Si lo configurado ya es menor que el piso, se respeta.
     */
    static int timeoutEscalado(int configuradoMs, double factorTiempo) {
        long escalado = Math.round(configuradoMs / Math.max(1.0, factorTiempo));
        return (int) Math.min(configuradoMs, Math.max(TIMEOUT_MINIMO_MS, escalado));
    }

    @Override
    public EstadoSwitch comandar(String urlSwitch, String idSwitch, Accion accion) {
        String url =
                UriComponentsBuilder.fromUriString(urlSwitch)
                        .pathSegment("switches", idSwitch, "comandos")
                        .toUriString();
        String respuesta = pedir(idSwitch, url, HttpMethod.POST, Map.of("accion", accion.name()));
        EstadoSwitch estado = null;
        try {
            estado = estado(leerJson(idSwitch, "POST " + url, respuesta));
        } catch (SwitchNoDisponibleException e) {
            // Respondió 2xx (aceptó el comando) con un cuerpo que no es JSON: se toma como
            // aplicado, igual que con el cuerpo vacío. La reconciliación lo verifica después.
            log.warn("{}; se toma el comando {} como aplicado.", e.getMessage(), accion);
        }
        if (estado != null) {
            return estado;
        }
        return accion == Accion.ON ? EstadoSwitch.ON : EstadoSwitch.OFF;
    }

    @Override
    public EstadoSwitch consultar(String urlSwitch, String idSwitch) {
        String url =
                UriComponentsBuilder.fromUriString(urlSwitch)
                        .pathSegment("switches", idSwitch)
                        .toUriString();
        EstadoSwitch estado = estado(llamar(idSwitch, url, HttpMethod.GET, null));
        if (estado == null) {
            throw new SwitchNoDisponibleException(idSwitch, "respuesta sin 'estado' válido", null);
        }
        return estado;
    }

    private JsonNode llamar(String idSwitch, String url, HttpMethod metodo, Object cuerpo) {
        return leerJson(idSwitch, metodo + " " + url, pedir(idSwitch, url, metodo, cuerpo));
    }

    /** El pedido HTTP; una respuesta que no es 2xx o la falta de respuesta lanzan. */
    private String pedir(String idSwitch, String url, HttpMethod metodo, Object cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set(HEADER_API_KEY, apiKey);
        }
        try {
            return http.exchange(url, metodo, new HttpEntity<>(cuerpo, headers), String.class)
                    .getBody();
        } catch (RestClientException e) {
            throw new SwitchNoDisponibleException(
                    idSwitch, metodo + " " + url + " -> " + e.getMessage(), e);
        }
    }

    /**
     * La respuesta se lee como texto y se parsea acá, sin mirar el {@code Content-Type}: un switch
     * que manda un JSON válido como {@code text/plain} responde bien. Si no es JSON, el error dice
     * qué mandó (en la corrida del 2026-11-04 el simulador respondió {@code text/plain} en su
     * escenario de switch caído, y Spring solo decía "no suitable HttpMessageConverter").
     */
    static JsonNode leerJson(String idSwitch, String pedido, String respuesta) {
        if (respuesta == null || respuesta.isBlank()) {
            return null;
        }
        try {
            return JSON.readTree(respuesta);
        } catch (JsonProcessingException e) {
            String muestra = respuesta.strip();
            if (muestra.length() > MAXIMO_EN_EL_LOG) {
                muestra = muestra.substring(0, MAXIMO_EN_EL_LOG) + "…";
            }
            throw new SwitchNoDisponibleException(
                    idSwitch, pedido + " -> la respuesta no es JSON: \"" + muestra + "\"", e);
        }
    }

    private static EstadoSwitch estado(JsonNode respuesta) {
        if (respuesta == null || !respuesta.path("estado").isTextual()) {
            return null;
        }
        // Sin importar mayúsculas ni espacios: "on" o " OFF " son el mismo estado (F07).
        String texto = respuesta.path("estado").asText().strip().toUpperCase(Locale.ROOT);
        try {
            return EstadoSwitch.valueOf(texto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
