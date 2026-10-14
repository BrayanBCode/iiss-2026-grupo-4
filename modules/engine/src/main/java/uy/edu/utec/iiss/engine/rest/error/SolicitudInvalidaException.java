package uy.edu.utec.iiss.engine.rest.error;

/** Payload inválido → 400. */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
