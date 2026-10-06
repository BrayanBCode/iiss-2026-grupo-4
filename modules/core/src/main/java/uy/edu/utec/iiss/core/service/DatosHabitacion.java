package uy.edu.utec.iiss.core.service;

/**
 * Datos de una habitación tal como los necesita HabitacionService para crear
 * o modificar. Reemplaza a los DTO HTTP (HabitacionRequest / PatchRequest),
 * que ahora viven en "rest": así el service no depende de la capa HTTP.
 *
 * En crear() y actualizar() todos los campos vienen completos; en
 * actualizarParcial() un campo null significa "no modificar".
 */
public record DatosHabitacion(String nombre, Double temperaturaEsperada,
                              String idTermostato, String idSwitch) {
}
