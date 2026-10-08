package uy.edu.utec.iiss.core.model;

import java.time.ZoneId;

/**
 * Datos de sitio que le importan al core para decidir (estándar v2, §1
 * "Sitio"): el tope de potencia que nunca hay que superar, la franja de
 * tarifa punta y la zona horaria en la que esa franja está expresada.
 *
 * La franja punta ("17:00 a 23:00") es hora local del sitio, no hora de la
 * máquina donde corre el core: por eso la zona viaja con el sitio y el core
 * nunca consulta la zona del sistema. Por defecto es Uruguay (UTC-3 todo el
 * año, sin horario de verano). Los instantes que recibe el core son
 * absolutos; solo al mostrarlos se convierten a hora local.
 * <ul>
 *      <li>String id</li>
 *      <li>double potenciaContratadaKW</li>
 *      <li>FranjaHoraria puntaTarifa</li>
 *      <li>ZoneId zona</li>
 * </ul>
 */
public record Sitio(
        String id,
        double potenciaContratadaKW,
        FranjaHoraria puntaTarifa,
        ZoneId zona
) {

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
