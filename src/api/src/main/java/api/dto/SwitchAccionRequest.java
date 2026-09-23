package api.dto;

import api.model.AccionSwitch;
import jakarta.validation.constraints.NotNull;

/**
 * Body del comando manual "accionar switch": POST /habitaciones/{id}/switch.
 * Reutiliza el mismo enum ON/OFF que ya usa el Controlador automático
 * (api.model.AccionSwitch), para que ambos caminos hablen el mismo contrato.
 */
public class SwitchAccionRequest {

    @NotNull(message = "accion es obligatoria (ON u OFF)")
    private AccionSwitch accion;

    public SwitchAccionRequest() {
    }

    public SwitchAccionRequest(AccionSwitch accion) {
        this.accion = accion;
    }

    public AccionSwitch getAccion() {
        return accion;
    }

    public void setAccion(AccionSwitch accion) {
        this.accion = accion;
    }
}
