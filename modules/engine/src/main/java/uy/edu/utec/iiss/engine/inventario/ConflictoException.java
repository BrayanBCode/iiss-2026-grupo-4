package uy.edu.utec.iiss.engine.inventario;

/** Identificador duplicado → 409. */
public class ConflictoException extends RuntimeException {
    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
