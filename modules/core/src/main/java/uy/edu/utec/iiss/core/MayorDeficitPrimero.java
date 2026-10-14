package uy.edu.utec.iiss.core;

import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Criterio de prioridad: ordena las candidatas por mayor déficit térmico
 * ({@code temperaturaEsperada - temperaturaActual}) primero — "la que más
 * lejos está de su objetivo es la que más necesita energía ahora".
 *
 * <b>Anti-oscilación.</b> A una habitación que ya está encendida se le suma
 * un margen ({@code margenIntercambioC}, 0.5 °C por defecto) a su déficit.
 * Sin eso, con dos habitaciones parecidas y potencia para una sola, la energía
 * saltaría de una a otra con cada décima de diferencia y los relés conmutarían
 * todo el tiempo. Con el margen se turnan: la que espera recibe la energía
 * cuando su déficit supera al de la encendida por más del margen.
 *
 * El desempate es por {@code id} (orden alfabético): dos corridas con
 * exactamente el mismo estado dan siempre el mismo resultado, que es lo que
 * pide la letra con "no azaroso".
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
    public List<String> ordenarCandidatas(List<ConfiguracionHabitacion> candidatas, Map<String, EstadoHabitacion> estado) {
        return candidatas.stream()
                .sorted(Comparator
                        .comparingDouble((ConfiguracionHabitacion h) -> prioridad(h, estado))
                        .reversed()
                        .thenComparing(ConfiguracionHabitacion::id))
                .map(ConfiguracionHabitacion::id)
                .toList();
    }

    private double prioridad(ConfiguracionHabitacion habitacion, Map<String, EstadoHabitacion> estado) {
        EstadoHabitacion e = estado.get(habitacion.id());
        double temperaturaActual = (e != null && e.temperaturaActual() != null)
                ? e.temperaturaActual()
                : habitacion.temperaturaEsperada(); // sin lectura todavía: no hay déficit conocido
        double deficit = habitacion.temperaturaEsperada() - temperaturaActual;
        boolean encendida = e != null && e.encendida();
        return encendida ? deficit + margenIntercambioC : deficit;
    }
}
