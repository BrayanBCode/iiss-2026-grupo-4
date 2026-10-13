package uy.edu.utec.iiss.core.cliente;

import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.Sitio;

import java.time.Instant;
import java.util.List;

/**
 * Fachada del core para quien lo integra (el engine). Toda operación que
 * puede cambiar decisiones las devuelve, para poder comandar los switches.
 */
public interface ICoreClient {
    List<Decision> nuevaTemperatura(String idHabitacion, double temperaturaC, Instant ts);

    List<Decision> tick(Instant ts);

    /** Cambiar el sitio también puede cambiar decisiones (p. ej. un tope más bajo): se devuelven. */
    List<Decision> actualizarSitio(Sitio sitio, ConfiguracionHabitacion... habitaciones);

    /** INICIAR del estándar §5.2: el reloj del control pasa a {@code ahora} con esa aceleración. */
    List<Decision> sincronizar(Instant ahora, double factorTiempo);

    EstadoHabitacion estadoById(String idHabitacion);
}
