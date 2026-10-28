package uy.edu.utec.iiss.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.Motivo;
import uy.edu.utec.iiss.core.model.ParametrosControl;
import uy.edu.utec.iiss.core.model.Sitio;

/**
 * Lógica de decisión del controlador, sin estado: dada una situación (sitio, habitaciones, lo que
 * se sabe de cada una y la hora del control) dice qué habitación recibe energía y por qué.
 *
 * <p>Java puro y determinista (sin reloj del sistema, red ni azar): la misma situación da siempre
 * las mismas decisiones. No guarda nada entre llamadas; el estado (lecturas, reloj, última
 * decisión) lo lleva el engine ({@code CoreAdapter}). Solo tiene campos finales, así que se puede
 * compartir entre hilos.
 *
 * <p>{@link #decidir} decide en tres pasos:
 *
 * <ol>
 *   <li><b>Necesidad</b> de cada habitación: sin lectura, lectura vencida, satisfecha (con
 *       histéresis), excede la contratada, o con déficit.
 *   <li><b>Tarifa</b>: en punta ninguna con déficit recibe energía.
 *   <li><b>Reparto</b>: las que tienen déficit se ordenan con el {@link CriterioPrioridad} y se les
 *       asigna potencia mientras entre en la contratada (una que no entra no bloquea a las
 *       siguientes).
 * </ol>
 */
public class Core {

    /** Tolerancia para comparar potencias: 0.1 + 0.2 tiene que "entrar" en 0.3. */
    private static final double EPS = 1e-9;

    private final CriterioPrioridad criterioPrioridad;
    private final ParametrosControl parametros;

    public Core(CriterioPrioridad criterioPrioridad) {
        this(criterioPrioridad, ParametrosControl.PREDETERMINADOS);
    }

    public Core(CriterioPrioridad criterioPrioridad, ParametrosControl parametros) {
        this.criterioPrioridad = Objects.requireNonNull(criterioPrioridad, "criterioPrioridad");
        this.parametros = Objects.requireNonNull(parametros, "parametros");
    }

    /**
     * Si una temperatura puede venir de un sensor sano: finita y dentro del rango físico del sensor
     * ({@link ParametrosControl#temperaturaMinimaValida()} a {@link
     * ParametrosControl#temperaturaMaximaValida()}). Una que no lo es se descarta y se conserva el
     * último dato bueno.
     */
    public boolean lecturaValida(double temperaturaC) {
        return Double.isFinite(temperaturaC)
                && temperaturaC >= parametros.temperaturaMinimaValida()
                && temperaturaC <= parametros.temperaturaMaximaValida();
    }

    /**
     * La función de decisión: dada una situación, qué habitaciones reciben energía y por qué. Todo
     * llega por parámetro, salvo el criterio y los parámetros de control.
     *
     * @param sitio potencia contratada, franja punta y zona horaria
     * @param habitaciones configuración de cada habitación; las decisiones salen en este orden
     * @param estados situación de cada habitación por id: última temperatura, cuándo se midió y si
     *     está encendida ahora. Una habitación sin entrada (o con temperatura null) se toma como
     *     sin lectura y apagada
     * @param ahora hora del control (null = todavía no se conoce: no hay punta ni vencimientos)
     * @return una decisión por habitación, en el orden de {@code habitaciones} según el {@link
     *     CriterioPrioridad}
     */
    public List<Decision> decidir(
            Sitio sitio,
            List<ConfiguracionHabitacion> habitaciones,
            Map<String, EstadoHabitacion> estados,
            Instant ahora) {
        Objects.requireNonNull(sitio, "sitio");
        Objects.requireNonNull(habitaciones, "habitaciones");
        Objects.requireNonNull(estados, "estados");

        // 1. Necesidad de cada habitación
        Map<String, Motivo> motivos = new HashMap<>();
        Map<String, Double> potencias = new HashMap<>();
        List<ConfiguracionHabitacion> conDeficit = new ArrayList<>();

        for (ConfiguracionHabitacion h : habitaciones) {
            potencias.put(h.id(), h.potenciaKW());
            Motivo sinDeficit = motivoSinDeficit(h, estados.get(h.id()), sitio, ahora);
            if (sinDeficit != null) {
                motivos.put(h.id(), sinDeficit);
            } else {
                conDeficit.add(h);
            }
        }

        // 2. Tarifa  /  3. Reparto de la potencia contratada
        if (enPunta(sitio, ahora)) {
            conDeficit.forEach(h -> motivos.put(h.id(), Motivo.TARIFA_PUNTA));

        } else {
            double usada = 0;
            for (String id : criterioPrioridad.ordenarCandidatas(conDeficit, estados)) {
                double potencia = potencias.get(id);
                if (usada + potencia <= sitio.potenciaContratadaKW() + EPS) {
                    usada += potencia;
                    motivos.put(id, Motivo.CALEFACCIONANDO);
                } else {
                    motivos.put(id, Motivo.SIN_POTENCIA_DISPONIBLE);
                }
            }
        }

        List<Decision> decisiones = new ArrayList<>(habitaciones.size());
        for (ConfiguracionHabitacion h : habitaciones) {
            Motivo motivo = motivos.get(h.id());
            decisiones.add(
                    new Decision(
                            h.id(),
                            motivo == Motivo.CALEFACCIONANDO ? Accion.ON : Accion.OFF,
                            motivo));
        }
        return decisiones;
    }

    /** Por qué la habitación NO necesita energía, o null si tiene déficit y podría recibirla. */
    private Motivo motivoSinDeficit(
            ConfiguracionHabitacion h, EstadoHabitacion e, Sitio sitio, Instant ahora) {
        if (e == null || e.temperaturaActual() == null) {
            return Motivo.SIN_LECTURA;
        }
        if (vencida(e.ultimaLectura(), ahora)) {
            return Motivo.LECTURA_VENCIDA;
        }
        // Histéresis: encendida sigue hasta la esperada; apagada arranca recién debajo de la banda.
        double umbral =
                e.encendida()
                        ? h.temperaturaEsperada()
                        : h.temperaturaEsperada() - parametros.histeresisC();
        if (e.temperaturaActual() >= umbral) {
            return Motivo.SATISFECHA;
        }
        if (h.potenciaKW() > sitio.potenciaContratadaKW() + EPS) {
            return Motivo.EXCEDE_CONTRATADA;
        }
        return null;
    }

    /** La vigencia se mide en tiempo del control (simulado), sin multiplicar por el factor. */
    private boolean vencida(Instant instante, Instant ahora) {
        if (ahora == null || instante == null) {
            return false;
        }
        return Duration.between(instante, ahora).compareTo(parametros.vigenciaLectura()) > 0;
    }

    private static boolean enPunta(Sitio sitio, Instant ahora) {
        if (ahora == null) {
            return false;
        }
        var local = ahora.atZone(sitio.zona());
        return sitio.puntaTarifa().incluye(local.getDayOfWeek(), local.toLocalTime());
    }
}
