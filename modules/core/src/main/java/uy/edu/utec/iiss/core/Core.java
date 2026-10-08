package uy.edu.utec.iiss.core;

import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.Estimulo;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Sitio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Core {

    private static final double EPS = 1e-9;

    private final CriterioPrioridad criterioPrioridad;

    private Sitio sitio;
    private final Map<String, ConfiguracionHabitacion> habitaciones = new LinkedHashMap<>();
    private final Map<String, Double> temperaturas = new HashMap<>();
    private final Set<String> encendidas = new HashSet<>();
    private Instant ahora; // último instante visto (null = aún ninguno)

    public Core(CriterioPrioridad criterioPrioridad) {
        this.criterioPrioridad = criterioPrioridad;
    }

    public List<Decision> procesar(Estimulo estimulo) {
        switch (estimulo) {
            case Estimulo.NuevaLectura l -> {
                temperaturas.put(l.idHabitacion(), l.temperaturaC());
                ahora = l.ts();
            }
            case Estimulo.Tick t -> ahora = t.ts();
            case Estimulo.ConfiguracionActualizada c -> {
                sitio = c.sitio();
                habitaciones.clear();
                for (ConfiguracionHabitacion h : c.habitaciones()) habitaciones.put(h.id(), h);
                temperaturas.keySet().retainAll(habitaciones.keySet());
            }
            default -> { }
        }
        return decidir();
    }

    private List<Decision> decidir() {
        encendidas.clear();
        if (sitio != null && !enPunta()) {
            double contratada = sitio.potenciaContratadaKW();

            List<ConfiguracionHabitacion> candidatas = new ArrayList<>();
            for (ConfiguracionHabitacion h : habitaciones.values()) {
                Double t = temperaturas.get(h.id());
                if (t != null && t < h.temperaturaEsperada()
                        && h.potenciaKW() <= contratada + EPS) {
                    candidatas.add(h);
                }
            }

            Map<String, EstadoHabitacion> estado = new HashMap<>();
            for (String id : habitaciones.keySet()) estado.put(id, estadoDe(id));

            double usada = 0;
            for (String id : criterioPrioridad.ordenarCandidatas(candidatas, estado)) {
                ConfiguracionHabitacion h = habitaciones.get(id);
                if (usada + h.potenciaKW() <= contratada + EPS) {
                    encendidas.add(id);
                    usada += h.potenciaKW();
                }
            }
        }

        List<Decision> out = new ArrayList<>();
        for (ConfiguracionHabitacion h : habitaciones.values()) {
            out.add(new Decision(h.id(), encendidas.contains(h.id()) ? Accion.ON : Accion.OFF));
        }
        return out;
    }

    private boolean enPunta() {
        if (ahora == null) return false;
        var local = ahora.atZone(sitio.zona());
        return sitio.puntaTarifa().incluye(local.getDayOfWeek(), local.toLocalTime());
    }

    public EstadoHabitacion estadoDe(String idHabitacion) {
        ConfiguracionHabitacion h = habitaciones.get(idHabitacion);
        if (h == null) return null;
        return new EstadoHabitacion(idHabitacion, temperaturas.get(idHabitacion),
                encendidas.contains(idHabitacion));
    }
}