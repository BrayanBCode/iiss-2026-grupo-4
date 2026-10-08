package uy.edu.utec.iiss.engine.client;

import uy.edu.utec.iiss.engine.model.AccionSwitch;

/**
 * Lo único que el engine necesita de un switch: poder prenderlo o apagarlo.
 * Lo implementa {@link SwitchStubClient} (REST contra el stub / simulador) y
 * en los tests se reemplaza por un switch falso, sin red.
 */
public interface AccionadorSwitch {

    /** @throws RuntimeException si no se pudo comandar el switch (p. ej. SwitchStubNoDisponibleException) */
    void accionar(String switchId, AccionSwitch accion);
}