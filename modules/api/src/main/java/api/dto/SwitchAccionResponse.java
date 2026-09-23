package api.dto;

import api.model.AccionSwitch;

/**
 * Confirmación del comando manual "accionar switch": qué switch se accionó
 * y con qué acción, una vez que el stub respondió OK (si el stub no
 * responde, SwitchStubClient tira SwitchStubNoDisponibleException -> 502,
 * y este DTO ni se construye).
 */
public class SwitchAccionResponse {

    private final String idSwitch;
    private final AccionSwitch accion;

    public SwitchAccionResponse(String idSwitch, AccionSwitch accion) {
        this.idSwitch = idSwitch;
        this.accion = accion;
    }

    public String getIdSwitch() {
        return idSwitch;
    }

    public AccionSwitch getAccion() {
        return accion;
    }
}
