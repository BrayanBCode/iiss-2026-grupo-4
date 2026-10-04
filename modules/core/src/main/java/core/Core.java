package core;

import core.model.Decision;
import core.model.EstadoHabitacion;
import core.model.Estimulo;

import java.util.List;

/**
 * Lógica de decisión del controlador: qué habitación recibe energía y cuál
 * no, sin comunicarse con switches ni termostatos (letra, "Objetivo") — eso
 * es responsabilidad del engine, que traduce MQTT/REST hacia y desde los
 * {@link Estimulo} y {@link Decision} de esta clase.
 *
 * TODO(iteración 4): implementar. Se deja sin implementar a propósito:
 * CoreTest.java se escribió primero (TDD) y hoy está en rojo. La idea es
 * discutir esos tests en clase (criterio de prioridad, casos límite) antes
 * de escribir la implementación — ver docs/tests/criterio-tests.md para el
 * criterio de adecuación del conjunto.
 */
public class Core {

    private final CriterioPrioridad criterioPrioridad;

    public Core(CriterioPrioridad criterioPrioridad) {
        this.criterioPrioridad = criterioPrioridad;
    }

    /**
     * Procesa un estímulo y devuelve las decisiones resultantes (puede ser
     * una lista vacía, por ejemplo ante un {@code ConfiguracionActualizada}
     * que no dispara ningún cambio de switch).
     */
    public List<Decision> procesar(Estimulo estimulo) {
        throw new UnsupportedOperationException(
                "Core.procesar: pendiente de implementar (TDD, iteración 4)");
    }

    /** Estado interno actual de una habitación — null si el core no la conoce. */
    public EstadoHabitacion estadoDe(String idHabitacion) {
        throw new UnsupportedOperationException(
                "Core.estadoDe: pendiente de implementar (TDD, iteración 4)");
    }
}
