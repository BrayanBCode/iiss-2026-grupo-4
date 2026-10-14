package uy.edu.utec.iiss.engine.inventario;

/** Recurso inexistente (sitio sin cargar, habitación desconocida) → 404. */
public class NoEncontradoException extends RuntimeException {
    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
