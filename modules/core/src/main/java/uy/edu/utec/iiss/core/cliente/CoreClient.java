package uy.edu.utec.iiss.core.cliente;


import uy.edu.utec.iiss.core.Core;
import uy.edu.utec.iiss.core.CriterioPrioridad;
import uy.edu.utec.iiss.core.model.*;

import java.time.Instant;
import java.util.List;

//TODO: Consultar si esto debe ir en Core o en engine
public class CoreClient implements ICoreClient {

    private Core core;

    public CoreClient(CriterioPrioridad criterioPrioridad) {
        core = new Core(criterioPrioridad);
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
    public EstadoHabitacion estadoById(String idHabitacion) {
        return core.estadoDe(idHabitacion);
    }
}