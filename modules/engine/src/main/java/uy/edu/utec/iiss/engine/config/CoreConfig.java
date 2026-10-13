package uy.edu.utec.iiss.engine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import uy.edu.utec.iiss.core.MayorDeficitPrimero;
import uy.edu.utec.iiss.core.cliente.CoreClient;
import uy.edu.utec.iiss.core.cliente.ICoreClient;
import uy.edu.utec.iiss.core.model.ParametrosControl;
import uy.edu.utec.iiss.engine.service.CoreAdapter;
import uy.edu.utec.iiss.engine.switches.ClienteSwitch;

import java.time.Clock;
import java.time.Duration;

/**
 * Arma el core con su criterio de prioridad y sus parámetros de control, y el
 * adaptador que lo conecta con los switches. Los parámetros son decisión del
 * controlador (no vienen del inventario) y se pueden ajustar por entorno.
 */
@Configuration
public class CoreConfig {

    @Bean
    public Clock relojReal() {
        return Clock.systemUTC();
    }

    @Bean
    public ICoreClient coreClient(@Value("${control.histeresis-c:0.3}") double histeresisC,
                                  @Value("${control.margen-intercambio-c:0.5}") double margenIntercambioC,
                                  @Value("${control.vigencia-lectura-segundos:600}") long vigenciaSegundos) {
        ParametrosControl parametros = new ParametrosControl(histeresisC, Duration.ofSeconds(vigenciaSegundos),
                ParametrosControl.PREDETERMINADOS.temperaturaMinimaValida(),
                ParametrosControl.PREDETERMINADOS.temperaturaMaximaValida());
        return new CoreClient(new MayorDeficitPrimero(margenIntercambioC), parametros);
    }

    @Bean
    public CoreAdapter coreAdapter(ICoreClient core, ClienteSwitch switches, Clock relojReal,
                                   @Value("${switch.espera-reintento-segundos:5}") long esperaReintento) {
        return new CoreAdapter(core, switches, relojReal, Duration.ofSeconds(esperaReintento));
    }
}
