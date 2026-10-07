package uy.edu.utec.iiss.engine.exception;

public class ControladorYaIniciadoException extends RuntimeException {

    public ControladorYaIniciadoException() {
        super("El controlador ya está en ejecución");
    }
}
