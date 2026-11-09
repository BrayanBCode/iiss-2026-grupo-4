package uy.edu.utec.iiss.engine.switches;

import uy.edu.utec.iiss.core.model.Accion;

/**
 * Lo que el engine necesita de un switch (estándar v2, §3 y §4). Lo implementa {@link
 * ClienteSwitchRest}; en los tests se reemplaza por un switch falso, sin red.
 */
public interface ClienteSwitch {

    /**
     * §3: {@code POST {urlSwitch}/switches/{idSwitch}/comandos}.
     *
     * @return el estado en el que quedó el switch según su respuesta
     * @throws SwitchNoDisponibleException si no se pudo comandar
     */
    EstadoSwitch comandar(String urlSwitch, String idSwitch, Accion accion);

    /**
     * §4: {@code GET {urlSwitch}/switches/{idSwitch}}.
     *
     * @throws SwitchNoDisponibleException si no se pudo consultar
     */
    EstadoSwitch consultar(String urlSwitch, String idSwitch);

    /**
     * El control pasa a correr con este {@code factorTiempo} (INICIAR, §5.2): cuánto se espera a un
     * switch se mide en tiempo de control, como el tick y la reconciliación (T37). Por defecto no
     * hace nada (un switch falso no tiene timeouts).
     */
    default void ajustarAlFactor(double factorTiempo) {}
}
