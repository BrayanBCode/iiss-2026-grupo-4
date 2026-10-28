package uy.edu.utec.iiss.engine.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uy.edu.utec.iiss.core.Core;
import uy.edu.utec.iiss.core.MayorDeficitPrimero;
import uy.edu.utec.iiss.core.model.ParametrosControl;
import uy.edu.utec.iiss.engine.adaptador.CoreAdapter;
import uy.edu.utec.iiss.engine.control.ControlService;
import uy.edu.utec.iiss.engine.switches.ClienteSwitch;

/**
 * Arma el core con su criterio de prioridad y sus parámetros de control, y el adaptador que lo
 * conecta con los switches. Los parámetros son decisión del controlador (no vienen del inventario)
 * y se pueden ajustar por entorno.
 */
@Configuration
public class CoreConfig {

    @Bean
    public Clock relojReal() {
        return Clock.systemUTC();
    }

    @Bean
    public Core core(
            @Value("${control.histeresis-c:0.3}") double histeresisC,
            @Value("${control.margen-intercambio-c:0.5}") double margenIntercambioC,
            @Value("${control.vigencia-lectura-segundos:600}") long vigenciaSegundos) {
        ParametrosControl parametros =
                new ParametrosControl(
                        histeresisC,
                        Duration.ofSeconds(vigenciaSegundos),
                        ParametrosControl.PREDETERMINADOS.temperaturaMinimaValida(),
                        ParametrosControl.PREDETERMINADOS.temperaturaMaximaValida());
        return new Core(new MayorDeficitPrimero(margenIntercambioC), parametros);
    }

    /**
     * El adaptador pregunta si el control está en marcha a {@link ControlService}, el único que lo
     * sabe. Se busca recién cuando se pregunta ({@code ObjectProvider}) porque {@code
     * ControlService} a su vez usa el adaptador: inyectarlo directo sería una dependencia circular.
     */
    @Bean
    public CoreAdapter coreAdapter(
            Core core,
            ClienteSwitch switches,
            Clock relojReal,
            @Value("${switch.espera-reintento-segundos:5}") long esperaReintento,
            ObjectProvider<ControlService> control) {
        return new CoreAdapter(
                core,
                switches,
                relojReal,
                Duration.ofSeconds(esperaReintento),
                () -> control.getObject().enEjecucion());
    }
}
