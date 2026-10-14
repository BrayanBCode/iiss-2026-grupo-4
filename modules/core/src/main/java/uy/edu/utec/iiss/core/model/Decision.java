package uy.edu.utec.iiss.core.model;

import java.util.Objects;

/**
 * Lo que el core decide para una habitación, y por qué. El engine traduce
 * esto al comando REST contra el switch real (estándar v2, §3) — el core
 * nunca ejecuta nada, solo decide.
 */
public record Decision(String idHabitacion, Accion accion, Motivo motivo) {

    public Decision {
        Objects.requireNonNull(idHabitacion, "idHabitacion");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(motivo, "motivo");
    }
}
