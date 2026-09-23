package api.exception;

/** Errores al conectar/desconectar el Controlador contra el broker MQTT. */
public class ControladorException extends RuntimeException {

    public ControladorException(String mensaje, Throwable cause) {
        super(mensaje, cause);
    }
}
