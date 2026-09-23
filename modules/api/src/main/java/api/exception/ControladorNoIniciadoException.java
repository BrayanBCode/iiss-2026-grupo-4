package api.exception;

public class ControladorNoIniciadoException extends RuntimeException {

    public ControladorNoIniciadoException() {
        super("El controlador no está en ejecución");
    }
}
