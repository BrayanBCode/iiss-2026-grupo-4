package uy.edu.utec.iiss.core.exception;

public class ControladorYaIniciadoException extends RuntimeException {

    public ControladorYaIniciadoException() {
        super("El controlador ya está en ejecución");
    }
}
