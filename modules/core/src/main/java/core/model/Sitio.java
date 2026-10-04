package core.model;

/**
 * Datos de sitio que le importan al core para decidir (estándar v2, §1
 * "Sitio"): el tope de potencia que nunca hay que superar, y la franja de
 * tarifa punta.
 */
public record Sitio(
        String id,
        double potenciaContratadaKW,
        FranjaHoraria puntaTarifa
) {
}
