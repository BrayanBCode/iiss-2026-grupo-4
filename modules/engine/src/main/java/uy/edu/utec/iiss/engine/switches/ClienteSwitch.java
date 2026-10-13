package uy.edu.utec.iiss.engine.switches;

import uy.edu.utec.iiss.core.model.Accion;

/**
 * Lo que el engine necesita de un switch (estándar v2, §3 y §4). Lo implementa
 * {@link ClienteSwitchRest}; en los tests se reemplaza por un switch falso, sin red.
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
}
