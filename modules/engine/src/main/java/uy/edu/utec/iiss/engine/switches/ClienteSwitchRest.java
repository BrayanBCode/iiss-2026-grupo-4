package uy.edu.utec.iiss.engine.switches;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
 * <p>Timeouts cortos a propósito: un switch caído no puede trabar el lazo de control (el {@link
 * uy.edu.utec.iiss.engine.adaptador.CoreAdapter} además espacia los reintentos a un switch que
 * viene fallando).
 */
@Component
public class ClienteSwitchRest implements ClienteSwitch {

    static final String HEADER_API_KEY = "X-API-Key";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAXIMO_EN_EL_LOG = 120;

    private final RestTemplate http;
    private final String apiKey;

    public ClienteSwitchRest(
            @Value("${switch.api-key:}") String apiKey,
            @Value("${switch.timeout-conexion-ms:2000}") int timeoutConexionMs,
            @Value("${switch.timeout-lectura-ms:3000}") int timeoutLecturaMs) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeoutConexionMs);
        fabrica.setReadTimeout(timeoutLecturaMs);
        // Buffering: manda Content-Length en lugar de un cuerpo "chunked", que algunos servidores
        // no leen.
        this.http = new RestTemplate(new BufferingClientHttpRequestFactory(fabrica));
        this.apiKey = apiKey;
    }

    @Override
    public EstadoSwitch comandar(String urlSwitch, String idSwitch, Accion accion) {
        String url =
                UriComponentsBuilder.fromUriString(urlSwitch)
                        .pathSegment("switches", idSwitch, "comandos")
                        .toUriString();
        JsonNode respuesta =
                llamar(idSwitch, url, HttpMethod.POST, Map.of("accion", accion.name()));
        EstadoSwitch estado = estado(respuesta);
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
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set(HEADER_API_KEY, apiKey);
        }
        String respuesta;
        try {
            respuesta =
                    http.exchange(url, metodo, new HttpEntity<>(cuerpo, headers), String.class)
                            .getBody();
        } catch (RestClientException e) {
            throw new SwitchNoDisponibleException(
                    idSwitch, metodo + " " + url + " -> " + e.getMessage(), e);
        }
        return leerJson(idSwitch, metodo + " " + url, respuesta);
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
        try {
            return EstadoSwitch.valueOf(respuesta.path("estado").asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
