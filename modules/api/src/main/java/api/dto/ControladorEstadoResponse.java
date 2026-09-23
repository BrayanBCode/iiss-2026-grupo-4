package api.dto;

/** Cuerpo de respuesta de los comandos /controlador/*. */
public class ControladorEstadoResponse {

    private boolean corriendo;

    public ControladorEstadoResponse() {
    }

    public ControladorEstadoResponse(boolean corriendo) {
        this.corriendo = corriendo;
    }

    public boolean isCorriendo() {
        return corriendo;
    }

    public void setCorriendo(boolean corriendo) {
        this.corriendo = corriendo;
    }
}
