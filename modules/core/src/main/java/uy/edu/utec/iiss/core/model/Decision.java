package uy.edu.utec.iiss.core.model;

import java.util.Objects;

/** Lo que el core decide para una habitación, y por qué. El engine lo traduce a un comando al switch (§3). */
public record Decision(String idHabitacion, Accion accion, Motivo motivo) {

    public Decision {
        Objects.requireNonNull(idHabitacion, "idHabitacion");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(motivo, "motivo");
    }
}
