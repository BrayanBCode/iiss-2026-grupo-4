package api.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import api.exception.SwitchStubNoDisponibleException;
import api.model.AccionSwitch;

/**
 * Cliente REST hacia el stub del switch (módulo switch-stub, POST /switch).
 *
 * La URL concreta del stub NO viene del recurso habitación (idSwitch es
 * solo un identificador): viene de la configuración del sitio, acá
 * representada por la propiedad "switch.stub.url" (variable de entorno
 * SWITCH_STUB_URL en docker-compose), tal como pide la letra de la
 * Iteración 3.
 *
 * Lo usan dos comandos distintos:
 *   - el comando manual "accionar switch" (POST /habitaciones/{id}/switch), y
 *   - la lógica automática del Controlador (termostato simple).
 */
@Component
public class SwitchStubClient {

    private static final Logger log = LoggerFactory.getLogger(SwitchStubClient.class);

    private final RestTemplate restTemplate;
    private final String switchStubUrl;

    public SwitchStubClient(RestTemplate restTemplate,
                             @Value("${switch.stub.url}") String switchStubUrl) {
        this.restTemplate = restTemplate;
        this.switchStubUrl = switchStubUrl;
    }

    public void accionar(String switchId, AccionSwitch accion) {
        SwitchAccionRequest body = new SwitchAccionRequest(switchId, accion);
        try {
            restTemplate.postForEntity(switchStubUrl + "/switch", body, Void.class);
            log.debug("Switch '{}' -> {} (OK, stub en {})", switchId, accion, switchStubUrl);
        } catch (RestClientException e) {
            log.error("No se pudo accionar el switch '{}' ({}) contra {}: {}",
                    switchId, accion, switchStubUrl, e.getMessage());
            throw new SwitchStubNoDisponibleException(switchId, e);
        }
    }

    /** Mismo contrato que switchstub.SwitchAccionRequest del lado del stub. */
    private record SwitchAccionRequest(String switchId, AccionSwitch accion) {
    }
}
