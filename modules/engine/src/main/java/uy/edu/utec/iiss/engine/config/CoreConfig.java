package uy.edu.utec.iiss.engine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import uy.edu.utec.iiss.core.MayorDeficitPrimero;
import uy.edu.utec.iiss.core.cliente.CoreClient;
import uy.edu.utec.iiss.core.cliente.ICoreClient;

/** Arma el core con su criterio de prioridad (mayor déficit primero). */
@Configuration
public class CoreConfig {

    @Bean
    public ICoreClient coreClient() {
        return new CoreClient(new MayorDeficitPrimero());
    }
}