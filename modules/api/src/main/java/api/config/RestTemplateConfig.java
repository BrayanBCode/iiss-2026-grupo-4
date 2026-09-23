package api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate simple para las llamadas salientes del api hacia el stub
 * del switch (G1234-67 / G1234-94). No hace falta nada más elaborado:
 * es un solo POST sincrónico por acción.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
