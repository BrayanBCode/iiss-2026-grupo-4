package uy.edu.utec.iiss.core.model;

import java.time.ZoneId;

/**
 * Lo que el core necesita del sitio (estándar v2, §1): la potencia contratada, la franja punta y la
 * zona horaria en que se lee esa franja. La zona viaja con el sitio para no depender de la del
 * sistema; por defecto, Uruguay.
 */
public record Sitio(
        String id, double potenciaContratadaKW, FranjaHoraria puntaTarifa, ZoneId zona) {

    /** Zona horaria por defecto: Uruguay (UTC-3). */
    public static final ZoneId ZONA_URUGUAY = ZoneId.of("America/Montevideo");

    public Sitio {
        if (zona == null) {
            zona = ZONA_URUGUAY;
        }
    }

    /** Sitio en hora de Uruguay. */
    public Sitio(String id, double potenciaContratadaKW, FranjaHoraria puntaTarifa) {
        this(id, potenciaContratadaKW, puntaTarifa, ZONA_URUGUAY);
    }
}
