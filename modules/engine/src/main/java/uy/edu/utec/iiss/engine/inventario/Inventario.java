package uy.edu.utec.iiss.engine.inventario;

import java.util.List;

/**
 * Inventario completo del sitio, estándar v2 §1: es el cuerpo de
 * {@code PUT /sitio}, lo que devuelve {@code GET /sitio} y lo que se persiste.
 *
 * Los números van "boxed" (Double) para distinguir un campo ausente de un 0:
 * la validación ({@link ValidadorInventario}) es la que decide si falta algo.
 */
public record Inventario(String schemaVersion, Sitio sitio, List<Habitacion> habitaciones) {

    public record Sitio(String id, String nombre, Double potenciaContratadaKW, Tarifa tarifa) {
    }

    public record Tarifa(Franja punta) {
    }

    public record Franja(String desde, String hasta, String dias) {
    }

    public record Habitacion(String id,
                             String nombre,
                             Double temperaturaEsperada,
                             Double potenciaKW,
                             String idTermostato,
                             String topicTermostato,
                             String idSwitch,
                             String urlSwitch) {
    }

    /** Copia con las habitaciones reemplazadas (para el ABM de habitaciones sobre el inventario). */
    public Inventario conHabitaciones(List<Habitacion> nuevas) {
        return new Inventario(schemaVersion, sitio, List.copyOf(nuevas));
    }
}
