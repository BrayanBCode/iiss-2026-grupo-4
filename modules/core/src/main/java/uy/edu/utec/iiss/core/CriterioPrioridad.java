package uy.edu.utec.iiss.core;

import java.util.List;
import java.util.Map;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;

/**
 * Orden en que las habitaciones con déficit reciben la potencia disponible hasta agotar la
 * contratada. Es una interfaz para poder cambiar de criterio sin tocar el {@link Core}: la letra
 * pide uno "no azaroso y justificable".
 */
public interface CriterioPrioridad {

    /**
     * @param candidatas habitaciones con déficit térmico en este momento
     * @param estado estado actual de cada habitación, por id
     * @return los ids de {@code candidatas}, en el orden en que deberían recibir potencia
     */
    List<String> ordenarCandidatas(
            List<ConfiguracionHabitacion> candidatas, Map<String, EstadoHabitacion> estado);
}
