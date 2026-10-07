package uy.edu.utec.iiss.core.model;

/**
 * Lo que el core decide para una habitación. El engine traduce esto al
 * comando REST contra el switch real (estándar v2, §3) — el core nunca
 * ejecuta nada, solo decide.
 */
public record Decision(String idHabitacion, Accion accion) {
}
