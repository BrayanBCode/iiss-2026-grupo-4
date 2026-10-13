package uy.edu.utec.iiss.engine.switches;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.Map;

/**
 * Cliente REST de los switches según el estándar v2 (§3 comandar, §4 consultar),
 * con el header {@code X-API-Key} que exige el switch.
 *
 * Timeouts cortos a propósito: un switch caído no puede trabar el lazo de
 * control (el {@link uy.edu.utec.iiss.engine.service.CoreAdapter} además espacia
 * los reintentos a un switch que viene fallando).
 */
@Component
public class ClienteSwitchRest implements ClienteSwitch {

    static final String HEADER_API_KEY = "X-API-Key";

    private final RestTemplate http;
    private final String apiKey;

    public ClienteSwitchRest(@Value("${switch.api-key:}") String apiKey,
                             @Value("${switch.timeout-conexion-ms:2000}") int timeoutConexionMs,
                             @Value("${switch.timeout-lectura-ms:3000}") int timeoutLecturaMs) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeoutConexionMs);
        fabrica.setReadTimeout(timeoutLecturaMs);
        // Buffering: manda Content-Length en lugar de un cuerpo "chunked", que algunos servidores no leen.
        this.http = new RestTemplate(new BufferingClientHttpRequestFactory(fabrica));
        this.apiKey = apiKey;
    }

    @Override
    public EstadoSwitch comandar(String urlSwitch, String idSwitch, Accion accion) {
        String url = UriComponentsBuilder.fromUriString(urlSwitch)
                .pathSegment("switches", idSwitch, "comandos").toUriString();
        JsonNode respuesta = llamar(idSwitch, url, HttpMethod.POST, Map.of("accion", accion.name()));
        EstadoSwitch estado = estado(respuesta);
        return estado != null ? estado : (accion == Accion.ON ? EstadoSwitch.ON : EstadoSwitch.OFF);
    }

    @Override
    public EstadoSwitch consultar(String urlSwitch, String idSwitch) {
        String url = UriComponentsBuilder.fromUriString(urlSwitch)
                .pathSegment("switches", idSwitch).toUriString();
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
        try {
            return http.exchange(url, metodo, new HttpEntity<>(cuerpo, headers), JsonNode.class).getBody();
        } catch (RestClientException e) {
            throw new SwitchNoDisponibleException(idSwitch, metodo + " " + url + " -> " + e.getMessage(), e);
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
