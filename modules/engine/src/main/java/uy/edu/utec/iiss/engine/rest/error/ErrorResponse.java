package uy.edu.utec.iiss.engine.rest.error;

import java.time.Instant;
import java.util.List;
import uy.edu.utec.iiss.engine.util.Instantes;

/**
 * Cuerpo de error uniforme del estándar (convenciones transversales): {@code
 * {"timestamp":"2026-10-01T09:15:03.120Z","status":404,"mensaje":"...","errores":null}}.
 */
public record ErrorResponse(String timestamp, int status, String mensaje, List<String> errores) {

    public ErrorResponse(int status, String mensaje) {
        this(status, mensaje, null);
    }

    public ErrorResponse(int status, String mensaje, List<String> errores) {
        this(Instantes.iso(Instant.now()), status, mensaje, errores);
    }
}
