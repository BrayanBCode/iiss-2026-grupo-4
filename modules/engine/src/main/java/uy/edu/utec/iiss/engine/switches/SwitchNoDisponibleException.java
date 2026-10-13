package uy.edu.utec.iiss.engine.switches;

/** No se pudo comandar o consultar un switch (caído, timeout, 4xx/5xx, respuesta ilegible). */
public class SwitchNoDisponibleException extends RuntimeException {

    public SwitchNoDisponibleException(String idSwitch, String detalle, Throwable causa) {
        super("Switch '" + idSwitch + "' no disponible: " + detalle, causa);
    }
}
