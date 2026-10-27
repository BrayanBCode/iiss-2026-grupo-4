package uy.edu.utec.iiss.core;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;

/**
 * Primero la de mayor déficit ({@code temperaturaEsperada - temperaturaActual}): la más lejos de su
 * objetivo es la que más necesita energía.
 *
 * <p>A una habitación encendida se le suma {@code margenIntercambioC} a su déficit, para que con
 * potencia para una sola la energía no salte entre dos parecidas con cada décima: la que espera la
 * recibe cuando la supera por más del margen. Los empates se resuelven por {@code id}, así el
 * resultado es reproducible.
 */
public class MayorDeficitPrimero implements CriterioPrioridad {

    public static final double MARGEN_INTERCAMBIO_PREDETERMINADO_C = 0.5;

    private final double margenIntercambioC;

    public MayorDeficitPrimero() {
        this(MARGEN_INTERCAMBIO_PREDETERMINADO_C);
    }

    public MayorDeficitPrimero(double margenIntercambioC) {
        if (!(margenIntercambioC >= 0) || Double.isInfinite(margenIntercambioC)) {
            throw new IllegalArgumentException("margenIntercambioC debe ser >= 0");
        }
        this.margenIntercambioC = margenIntercambioC;
    }

    @Override
    public List<String> ordenarCandidatas(
            List<ConfiguracionHabitacion> candidatas, Map<String, EstadoHabitacion> estado) {
        return candidatas.stream()
                .sorted(
                        Comparator.comparingDouble(
                                        (ConfiguracionHabitacion h) -> prioridad(h, estado))
                                .reversed()
                                .thenComparing(ConfiguracionHabitacion::id))
                .map(ConfiguracionHabitacion::id)
                .toList();
    }

    private double prioridad(
            ConfiguracionHabitacion habitacion, Map<String, EstadoHabitacion> estado) {
        EstadoHabitacion e = estado.get(habitacion.id());
        double temperaturaActual =
                (e != null && e.temperaturaActual() != null)
                        ? e.temperaturaActual()
                        : habitacion.temperaturaEsperada(); // sin lectura todavía: no hay déficit
        // conocido
        double deficit = habitacion.temperaturaEsperada() - temperaturaActual;
        boolean encendida = e != null && e.encendida();
        return encendida ? deficit + margenIntercambioC : deficit;
    }
}
