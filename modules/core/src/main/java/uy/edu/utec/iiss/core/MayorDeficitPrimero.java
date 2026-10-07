package uy.edu.utec.iiss.core;

import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Criterio candidato #1: ordena las candidatas por mayor déficit térmico
 * ({@code temperaturaEsperada - temperaturaActual}) primero — "la que más
 * lejos está de su objetivo es la que más necesita energía ahora".
 *
 * El desempate es por {@code id} (orden alfabético): dos corridas con
 * exactamente el mismo estado dan siempre el mismo resultado, que es lo que
 * pide la letra con "no azaroso".
 *
 * Alternativa a discutir en clase: round-robin con ventana de tiempo (más
 * justo en el largo plazo, menos reactivo al frío puntual de una
 * habitación). No se implementa acá todavía — queda para decidir junto al
 * equipo cuál usar, o si conviene tener las dos.
 */
public class MayorDeficitPrimero implements CriterioPrioridad {

    @Override
    public List<String> ordenarCandidatas(List<ConfiguracionHabitacion> candidatas, Map<String, EstadoHabitacion> estado) {
        return candidatas.stream()
                .sorted(Comparator
                        .comparingDouble((ConfiguracionHabitacion h) -> deficit(h, estado))
                        .reversed()
                        .thenComparing(ConfiguracionHabitacion::id))
                .map(ConfiguracionHabitacion::id)
                .toList();
    }

    private double deficit(ConfiguracionHabitacion habitacion, Map<String, EstadoHabitacion> estado) {
        EstadoHabitacion estadoHabitacion = estado.get(habitacion.id());
        double temperaturaActual = (estadoHabitacion != null && estadoHabitacion.temperaturaActual() != null)
                ? estadoHabitacion.temperaturaActual()
                : habitacion.temperaturaEsperada(); // sin lectura todavía: no hay déficit conocido
        return habitacion.temperaturaEsperada() - temperaturaActual;
    }
}
