package uy.edu.utec.iiss.engine.core;

import uy.edu.utec.iiss.engine.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.engine.core.model.ConfiguracionHabitacion;

import java.util.List;
import java.util.Map;

/**
 * Decide, entre las habitaciones candidatas a encenderse (temperatura actual
 * por debajo de la esperada, fuera de horario punta), en qué orden se les va
 * asignando la potencia disponible hasta agotar la contratada.
 *
 * Se deja como interfaz, en vez de método privado del Core, justamente para
 * poder discutir y comparar criterios (mayor déficit primero vs. round-robin,
 * etc.) sin tocar el resto de la lógica — la letra pide "un criterio no
 * azaroso y justificable", no dice cuál.
 */
public interface CriterioPrioridad {

    /**
     * @param candidatas habitaciones con déficit térmico en este momento
     * @param estado     estado interno actual de todas las habitaciones (por si el criterio lo necesita)
     * @return los ids de {@code candidatas}, en el orden en que deberían recibir potencia
     */
    List<String> ordenarCandidatas(List<ConfiguracionHabitacion> candidatas, Map<String, EstadoHabitacion> estado);
}
