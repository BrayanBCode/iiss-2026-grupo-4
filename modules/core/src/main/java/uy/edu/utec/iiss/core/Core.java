package uy.edu.utec.iiss.core;

import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.Estimulo;
import uy.edu.utec.iiss.core.model.Motivo;
import uy.edu.utec.iiss.core.model.ParametrosControl;
import uy.edu.utec.iiss.core.model.Sitio;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lógica de decisión del controlador: recibe {@link Estimulo}s y devuelve,
 * para cada habitación del inventario, si debe recibir energía y por qué.
 *
 * Es Java puro y determinista: no lee el reloj del sistema, ni la red, ni
 * números al azar. Todo lo que necesita llega en los estímulos, así que la
 * misma secuencia de estímulos da siempre las mismas decisiones.
 *
 * NO es seguro para varios hilos: quien lo integra (el engine) serializa las
 * llamadas.
 *
 * La función de decisión ({@link #decidir()}) se arma en tres pasos:
 * <ol>
 *   <li><b>Necesidad</b> de cada habitación: sin lectura, lectura vencida,
 *       satisfecha (con histéresis), excede la contratada, o con déficit.</li>
 *   <li><b>Tarifa</b>: en punta ninguna con déficit recibe energía.</li>
 *   <li><b>Reparto</b>: las que tienen déficit se ordenan con el
 *       {@link CriterioPrioridad} y se les asigna potencia mientras entre en
 *       la contratada (una que no entra no bloquea a las siguientes).</li>
 * </ol>
 */
public class Core {

    /** Tolerancia para comparar potencias: 0.1 + 0.2 tiene que "entrar" en 0.3. */
    private static final double EPS = 1e-9;

    private final CriterioPrioridad criterioPrioridad;
    private final ParametrosControl parametros;

    private Sitio sitio;
    private final Map<String, ConfiguracionHabitacion> habitaciones = new LinkedHashMap<>();
    private final Map<String, Double> temperaturas = new HashMap<>();
    private final Map<String, Instant> instanteLectura = new HashMap<>();
    private final Set<String> encendidas = new HashSet<>();
    private Instant ahora;            // reloj del control según los estímulos (null = aún ninguno)
    private double factorTiempo = 1;  // aceleración vigente (escala la vigencia de las lecturas)

    public Core(CriterioPrioridad criterioPrioridad) {
        this(criterioPrioridad, ParametrosControl.PREDETERMINADOS);
    }

    public Core(CriterioPrioridad criterioPrioridad, ParametrosControl parametros) {
        this.criterioPrioridad = Objects.requireNonNull(criterioPrioridad, "criterioPrioridad");
        this.parametros = Objects.requireNonNull(parametros, "parametros");
    }

    /**
     * Procesa un estímulo y devuelve una decisión por habitación configurada,
     * en el orden del inventario (lista vacía si todavía no hay sitio).
     *
     * @throws IllegalArgumentException si es una configuración inválida; en
     *         ese caso el estado queda exactamente como estaba.
     */
    public List<Decision> procesar(Estimulo estimulo) {
        switch (estimulo) {
            case Estimulo.NuevaLectura l -> registrarLectura(l);
            case Estimulo.Tick t -> avanzarReloj(t.ts());
            case Estimulo.ConfiguracionActualizada c -> configurar(c);
            case Estimulo.Sincronizacion s -> sincronizar(s);
        }
        return decidir();
    }

    // ------------------------------------------------------------------
    // Estímulos
    // ------------------------------------------------------------------

    private void registrarLectura(Estimulo.NuevaLectura l) {
        String id = l.idHabitacion();
        if (!habitaciones.containsKey(id)) {
            return;                                   // habitación desconocida: no se guarda nada
        }
        double t = l.temperaturaC();
        if (!Double.isFinite(t) || t < parametros.temperaturaMinimaValida() || t > parametros.temperaturaMaximaValida()) {
            return;                                   // sensor defectuoso: se conserva el último dato bueno
        }
        Instant anterior = instanteLectura.get(id);
        if (anterior != null && l.ts().isBefore(anterior)) {
            return;                                   // llegó fuera de orden: hay un dato más nuevo
        }
        temperaturas.put(id, t);
        instanteLectura.put(id, l.ts());
        avanzarReloj(l.ts());
    }

    /** El reloj solo avanza con lecturas y ticks: un estímulo atrasado no lo hace retroceder. */
    private void avanzarReloj(Instant ts) {
        if (ahora == null || ts.isAfter(ahora)) {
            ahora = ts;
        }
    }

    private void configurar(Estimulo.ConfiguracionActualizada c) {
        validar(c);                                   // si falla, no se tocó nada
        sitio = c.sitio();
        habitaciones.clear();
        for (ConfiguracionHabitacion h : c.habitaciones()) {
            habitaciones.put(h.id(), h);
        }
        temperaturas.keySet().retainAll(habitaciones.keySet());
        instanteLectura.keySet().retainAll(habitaciones.keySet());
        encendidas.retainAll(habitaciones.keySet());
    }

    private static void validar(Estimulo.ConfiguracionActualizada c) {
        double contratada = c.sitio().potenciaContratadaKW();
        if (!(contratada > 0) || Double.isInfinite(contratada)) {
            throw new IllegalArgumentException("potenciaContratadaKW debe ser > 0 (es " + contratada + ")");
        }
        Set<String> ids = new HashSet<>();
        for (ConfiguracionHabitacion h : c.habitaciones()) {
            if (h == null || h.id() == null || h.id().isBlank()) {
                throw new IllegalArgumentException("Hay una habitación sin id");
            }
            if (!ids.add(h.id())) {
                throw new IllegalArgumentException("id de habitación duplicado: '" + h.id() + "'");
            }
            if (!(h.potenciaKW() > 0) || Double.isInfinite(h.potenciaKW())) {
                throw new IllegalArgumentException("habitación '" + h.id() + "': potenciaKW debe ser > 0");
            }
            if (!Double.isFinite(h.temperaturaEsperada())) {
                throw new IllegalArgumentException("habitación '" + h.id() + "': temperaturaEsperada inválida");
            }
        }
    }

    /**
     * INICIAR (re)sincroniza el reloj: puede ir hacia atrás. Las temperaturas
     * conocidas se conservan y se toman como medidas en el nuevo instante, así
     * no se apaga todo hasta que llegue la próxima lectura, ni se descartan como
     * "fuera de orden" las lecturas que vengan después de un salto hacia atrás.
     */
    private void sincronizar(Estimulo.Sincronizacion s) {
        ahora = s.ahora();
        factorTiempo = s.factorTiempo();
        instanteLectura.replaceAll((id, ts) -> s.ahora());
    }

    // ------------------------------------------------------------------
    // Función de decisión
    // ------------------------------------------------------------------

    private List<Decision> decidir() {
        if (sitio == null) {
            return List.of();
        }
        Map<String, EstadoHabitacion> estadoPrevio = new HashMap<>();
        for (String id : habitaciones.keySet()) {
            estadoPrevio.put(id, estadoDe(id));
        }

        // 1. Necesidad de cada habitación
        Map<String, Motivo> motivos = new LinkedHashMap<>();
        List<ConfiguracionHabitacion> conDeficit = new ArrayList<>();
        for (ConfiguracionHabitacion h : habitaciones.values()) {
            Motivo sinDeficit = motivoSinDeficit(h);
            if (sinDeficit != null) {
                motivos.put(h.id(), sinDeficit);
            } else {
                conDeficit.add(h);
            }
        }

        // 2. Tarifa  /  3. Reparto de la potencia contratada
        if (enPunta()) {
            conDeficit.forEach(h -> motivos.put(h.id(), Motivo.TARIFA_PUNTA));
        } else {
            double usada = 0;
            for (String id : criterioPrioridad.ordenarCandidatas(conDeficit, estadoPrevio)) {
                double potencia = habitaciones.get(id).potenciaKW();
                if (usada + potencia <= sitio.potenciaContratadaKW() + EPS) {
                    usada += potencia;
                    motivos.put(id, Motivo.CALEFACCIONANDO);
                } else {
                    motivos.put(id, Motivo.SIN_POTENCIA_DISPONIBLE);
                }
            }
        }

        encendidas.clear();
        List<Decision> decisiones = new ArrayList<>(habitaciones.size());
        for (String id : habitaciones.keySet()) {
            Motivo motivo = motivos.get(id);
            boolean on = motivo == Motivo.CALEFACCIONANDO;
            if (on) {
                encendidas.add(id);
            }
            decisiones.add(new Decision(id, on ? Accion.ON : Accion.OFF, motivo));
        }
        return decisiones;
    }

    /** Por qué la habitación NO necesita energía, o null si tiene déficit y podría recibirla. */
    private Motivo motivoSinDeficit(ConfiguracionHabitacion h) {
        Double t = temperaturas.get(h.id());
        if (t == null) {
            return Motivo.SIN_LECTURA;
        }
        if (vencida(instanteLectura.get(h.id()))) {
            return Motivo.LECTURA_VENCIDA;
        }
        // Histéresis: encendida sigue hasta la esperada; apagada arranca recién debajo de la banda.
        double umbral = encendidas.contains(h.id())
                ? h.temperaturaEsperada()
                : h.temperaturaEsperada() - parametros.histeresisC();
        if (t >= umbral) {
            return Motivo.SATISFECHA;
        }
        if (h.potenciaKW() > sitio.potenciaContratadaKW() + EPS) {
            return Motivo.EXCEDE_CONTRATADA;
        }
        return null;
    }

    private boolean vencida(Instant instante) {
        if (ahora == null || instante == null) {
            return false;
        }
        long vigenciaMs = Math.round(parametros.vigenciaLectura().toMillis() * factorTiempo);
        return Duration.between(instante, ahora).toMillis() > vigenciaMs;
    }

    private boolean enPunta() {
        if (ahora == null) {
            return false;
        }
        var local = ahora.atZone(sitio.zona());
        return sitio.puntaTarifa().incluye(local.getDayOfWeek(), local.toLocalTime());
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /** Estado interno de una habitación, o null si no está en el inventario. */
    public EstadoHabitacion estadoDe(String idHabitacion) {
        if (!habitaciones.containsKey(idHabitacion)) {
            return null;
        }
        return new EstadoHabitacion(idHabitacion, temperaturas.get(idHabitacion),
                instanteLectura.get(idHabitacion), encendidas.contains(idHabitacion));
    }

    /** Reloj del control según el último estímulo (null si todavía no llegó ninguno con instante). */
    public Instant ahora() {
        return ahora;
    }
}
