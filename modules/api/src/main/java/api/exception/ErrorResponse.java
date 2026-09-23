package api.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Cuerpo JSON estándar para errores del API (404, 400, etc.).
 */
public class ErrorResponse {

    private final Instant timestamp = Instant.now();
    private final int status;
    private final String mensaje;
    private final Map<String, String> errores;

    public ErrorResponse(int status, String mensaje) {
        this(status, mensaje, null);
    }

    public ErrorResponse(int status, String mensaje, Map<String, String> errores) {
        this.status = status;
        this.mensaje = mensaje;
        this.errores = errores;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getMensaje() {
        return mensaje;
    }

    public Map<String, String> getErrores() {
        return errores;
    }
}