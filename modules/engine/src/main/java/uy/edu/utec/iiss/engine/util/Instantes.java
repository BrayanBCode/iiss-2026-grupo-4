package uy.edu.utec.iiss.engine.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Formato de instantes del estándar: ISO-8601 en UTC, siempre con milisegundos ({@code
 * 2026-10-01T09:15:03.120Z}).
 */
public final class Instantes {

    private static final DateTimeFormatter ISO_MILIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Instantes() {}

    public static String iso(Instant instante) {
        return instante == null ? null : ISO_MILIS.format(instante);
    }
}
