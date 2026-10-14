package uy.edu.utec.iiss.core.cliente;

import uy.edu.utec.iiss.core.Core;
import uy.edu.utec.iiss.core.CriterioPrioridad;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.Estimulo;
import uy.edu.utec.iiss.core.model.ParametrosControl;
import uy.edu.utec.iiss.core.model.Sitio;

import java.time.Instant;
import java.util.List;

/** Implementación de {@link ICoreClient}: arma los estímulos y se los pasa al {@link Core}. */
public class CoreClient implements ICoreClient {

    private final Core core;

    public CoreClient(CriterioPrioridad criterioPrioridad) {
        this(criterioPrioridad, ParametrosControl.PREDETERMINADOS);
    }

    public CoreClient(CriterioPrioridad criterioPrioridad, ParametrosControl parametros) {
        core = new Core(criterioPrioridad, parametros);
    }

    @Override
    public List<Decision> nuevaTemperatura(String idHabitacion, double temperaturaC, Instant ts) {
        return core.procesar(new Estimulo.NuevaLectura(idHabitacion, temperaturaC, ts));
    }

    @Override
    public List<Decision> tick(Instant ts) {
        return core.procesar(new Estimulo.Tick(ts));
    }

    @Override
    public List<Decision> actualizarSitio(Sitio sitio, ConfiguracionHabitacion... habitaciones) {
        return core.procesar(new Estimulo.ConfiguracionActualizada(sitio, List.of(habitaciones)));
    }

    @Override
    public List<Decision> sincronizar(Instant ahora, double factorTiempo) {
        return core.procesar(new Estimulo.Sincronizacion(ahora, factorTiempo));
    }

    @Override
    public EstadoHabitacion estadoById(String idHabitacion) {
        return core.estadoDe(idHabitacion);
    }
}
