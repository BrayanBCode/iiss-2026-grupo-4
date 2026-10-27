package uy.edu.utec.iiss.engine.adaptador;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uy.edu.utec.iiss.core.cliente.ICoreClient;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.Sitio;
import uy.edu.utec.iiss.engine.switches.ClienteSwitch;
import uy.edu.utec.iiss.engine.switches.EstadoSwitch;

/**
 * La única clase del engine que habla con el core.
 *
 * <ul>
 *   <li>Traduce lecturas, ticks, configuración e INICIAR a llamadas al core, serializadas con
 *       {@code synchronized} (el core no es thread-safe).
 *   <li>Recuerda la última decisión por habitación ("deseado") y el último estado conocido de su
 *       switch; solo comanda cuando difieren.
 *   <li>Reconcilia (§4): consulta cada switch y corrige los que no coinciden (reinicio, cambio
 *       manual, comando perdido).
 *   <li>Un switch que falla no frena a los demás y no se reintenta antes de {@code
 *       esperaReintento}.
 * </ul>
 *
 * La red con los switches corre en un hilo propio, en "pasadas": cada estímulo actualiza lo deseado
 * y pide una pasada sin esperarla, así ni INICIAR ni PUT /sitio esperan a los switches. Los pedidos
 * durante una pasada se juntan en la siguiente. Las pasadas son secuenciales: un switch lento
 * demora a los demás switches hasta su timeout. Deshabilitado (DETENIDO) no se toca ningún switch.
 */
public class CoreAdapter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CoreAdapter.class);

    /** Una habitación tal como la necesita el core, más el switch que la calefacciona. */
    public record HabitacionControlada(
            String id,
            double temperaturaEsperada,
            double potenciaKW,
            String idSwitch,
            String urlSwitch) {}

    /** Foto de una habitación para diagnóstico. */
    public record EstadoControlado(
            String idHabitacion,
            Double temperaturaActual,
            Instant ultimaLectura,
            Accion decision,
            String motivo,
            EstadoSwitch estadoSwitch,
            String idSwitch) {}

    private final ICoreClient core;
    private final ClienteSwitch switches;
    private final Clock relojReal;
    private final Duration esperaReintento;
    private final Executor hiloSwitches;

    private final Map<String, HabitacionControlada> habitaciones = new LinkedHashMap<>();
    private final Map<String, Decision> deseado = new HashMap<>();
    private final Map<String, EstadoSwitch> estadoConocido = new HashMap<>();
    private final Map<String, Instant> noReintentarAntesDe = new HashMap<>();
    private boolean habilitado;
    private boolean pasadaEncolada;
    private boolean consultaPendiente;

    /** Con su propio hilo de switches. */
    public CoreAdapter(
            ICoreClient core, ClienteSwitch switches, Clock relojReal, Duration esperaReintento) {
        this(
                core,
                switches,
                relojReal,
                esperaReintento,
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "switches");
                            t.setDaemon(true);
                            return t;
                        }));
    }

    /**
     * Con el executor que corre las pasadas; los tests pasan uno sincrónico ({@code
     * Runnable::run}).
     */
    public CoreAdapter(
            ICoreClient core,
            ClienteSwitch switches,
            Clock relojReal,
            Duration esperaReintento,
            Executor hiloSwitches) {
        this.core = Objects.requireNonNull(core);
        this.switches = Objects.requireNonNull(switches);
        this.relojReal = Objects.requireNonNull(relojReal);
        this.esperaReintento = Objects.requireNonNull(esperaReintento);
        this.hiloSwitches = Objects.requireNonNull(hiloSwitches);
    }

    /** Detiene el hilo de switches (Spring lo llama al cerrar el contexto). */
    @Override
    public void close() {
        if (hiloSwitches instanceof ExecutorService servicio) {
            servicio.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Estímulos
    // ------------------------------------------------------------------

    /**
     * Define (o redefine) el sitio y las habitaciones; puede cambiar decisiones, que se aplican al
     * instante.
     */
    public synchronized void configurar(Sitio sitio, List<HabitacionControlada> nuevas) {
        ConfiguracionHabitacion[] configuracion =
                nuevas.stream()
                        .map(
                                h ->
                                        new ConfiguracionHabitacion(
                                                h.id(),
                                                h.id(),
                                                h.temperaturaEsperada(),
                                                h.potenciaKW()))
                        .toArray(ConfiguracionHabitacion[]::new);
        List<Decision> decisiones =
                core.actualizarSitio(
                        sitio, configuracion); // si el core la rechaza, no se toca nada

        Map<String, HabitacionControlada> porId = new LinkedHashMap<>();
        nuevas.forEach(h -> porId.put(h.id(), h));
        // Lo que se sabía del switch de una habitación que ya no existe, o que cambió de switch,
        // deja de valer.
        habitaciones.forEach(
                (id, vieja) -> {
                    HabitacionControlada nueva = porId.get(id);
                    if (nueva == null
                            || !nueva.idSwitch().equals(vieja.idSwitch())
                            || !nueva.urlSwitch().equals(vieja.urlSwitch())) {
                        estadoConocido.remove(id);
                        noReintentarAntesDe.remove(id);
                    }
                });
        habitaciones.clear();
        habitaciones.putAll(porId);
        deseado.keySet().retainAll(habitaciones.keySet());
        aplicar(decisiones);
    }

    /** Llegó una medición de temperatura; {@code ahora} es el reloj del control. */
    public synchronized void alLlegarLectura(
            String idHabitacion, double temperaturaC, Instant ahora) {
        aplicar(core.nuevaTemperatura(idHabitacion, temperaturaC, ahora));
    }

    /**
     * Pasó el tiempo (lo dispara el temporizador del engine); permite cortar y restituir en los
     * cambios de franja.
     */
    public synchronized void alTick(Instant ahora) {
        aplicar(core.tick(ahora));
    }

    /** INICIAR: el reloj del control se re-sincroniza. */
    public synchronized void sincronizar(Instant ahora, double factorTiempo) {
        aplicar(core.sincronizar(ahora, factorTiempo));
    }

    // ------------------------------------------------------------------
    // Habilitar / reconciliar
    // ------------------------------------------------------------------

    /**
     * Empieza a comandar switches: pide una pasada que primero consulta su estado real (§4) y
     * después manda lo que falte. No espera a los switches.
     */
    public synchronized void habilitar() {
        habilitado = true;
        noReintentarAntesDe.clear();
        programarPasada(true);
    }

    /** Deja de comandar switches (§5.2: no se exige apagar lo que quedó encendido). */
    public synchronized void deshabilitar() {
        habilitado = false;
    }

    public synchronized boolean habilitado() {
        return habilitado;
    }

    /**
     * Pide consultar el estado real de cada switch y comandar los que no coinciden con la última
     * decisión. Se llama al habilitar y periódicamente; no espera a los switches.
     */
    public synchronized void reconciliar() {
        programarPasada(true);
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private void aplicar(List<Decision> decisiones) {
        for (Decision d : decisiones) {
            if (habitaciones.containsKey(d.idHabitacion())) {
                Decision anterior = deseado.put(d.idHabitacion(), d);
                if (anterior == null || anterior.accion() != d.accion()) {
                    log.info(
                            "Decisión: habitación '{}' -> {} ({})",
                            d.idHabitacion(),
                            d.accion(),
                            d.motivo());
                }
            }
        }
        programarPasada(false);
    }

    /**
     * Encola una pasada en el hilo de switches, salvo que ya haya una esperando. Se llama con el
     * bloqueo tomado.
     */
    private void programarPasada(boolean consultarAntes) {
        if (!habilitado) {
            return;
        }
        consultaPendiente |= consultarAntes;
        if (pasadaEncolada) {
            return;
        }
        pasadaEncolada = true;
        try {
            hiloSwitches.execute(this::pasada);
        } catch (RejectedExecutionException e) {
            pasadaEncolada = false; // el adaptador se está cerrando
        }
    }

    /**
     * Corre en el hilo de switches: consulta (si se pidió) y manda lo pendiente. El bloqueo se toma
     * solo para leer o actualizar el estado, nunca durante la red.
     */
    private void pasada() {
        try {
            List<HabitacionControlada> aConsultar;
            synchronized (this) {
                pasadaEncolada = false; // lo que llegue desde ahora pide otra pasada
                boolean consultar = consultaPendiente;
                consultaPendiente = false;
                if (!habilitado) {
                    return;
                }
                aConsultar =
                        consultar
                                ? habitaciones.values().stream()
                                        .filter(h -> !enEspera(h.id()))
                                        .toList()
                                : List.of();
            }
            aConsultar.forEach(this::consultar);
            enviarPendientes();
        } catch (RuntimeException e) {
            log.error("Error inesperado al comunicarse con los switches: {}", e.getMessage(), e);
        }
    }

    private void consultar(HabitacionControlada h) {
        EstadoSwitch real;
        try {
            real = switches.consultar(h.urlSwitch(), h.idSwitch());
        } catch (RuntimeException e) {
            synchronized (this) {
                if (vigente(h)) {
                    registrarFalla(h, "consultar", e);
                }
            }
            return;
        }
        synchronized (this) {
            if (!vigente(h)) {
                return;
            }
            if (estadoConocido.get(h.id()) != real && estadoConocido.containsKey(h.id())) {
                log.warn(
                        "Switch '{}' (habitación '{}') está {} y se creía {}: se corrige.",
                        h.idSwitch(),
                        h.id(),
                        real,
                        estadoConocido.get(h.id()));
            }
            estadoConocido.put(h.id(), real);
        }
    }

    /**
     * Primero los OFF y después los ON: si una habitación cede su potencia a otra, apagar antes de
     * encender evita que por un instante se supere la potencia contratada.
     */
    private void enviarPendientes() {
        List<HabitacionControlada> candidatas;
        synchronized (this) {
            candidatas = List.copyOf(habitaciones.values());
        }
        enviarPendientes(candidatas, Accion.OFF);
        enviarPendientes(candidatas, Accion.ON);
    }

    private void enviarPendientes(List<HabitacionControlada> candidatas, Accion soloEsta) {
        for (HabitacionControlada h : candidatas) {
            Accion accion;
            synchronized (this) { // la decisión vigente se lee justo antes de mandarla
                if (!habilitado) {
                    return;
                }
                Decision d = deseado.get(h.id());
                if (!vigente(h)
                        || d == null
                        || d.accion() != soloEsta
                        || coincide(estadoConocido.get(h.id()), d.accion())
                        || enEspera(h.id())) {
                    continue;
                }
                accion = d.accion();
            }
            try {
                EstadoSwitch resultado = switches.comandar(h.urlSwitch(), h.idSwitch(), accion);
                synchronized (this) {
                    if (!vigente(h)) {
                        continue;
                    }
                    estadoConocido.put(h.id(), resultado);
                    if (noReintentarAntesDe.remove(h.id()) != null) {
                        log.info(
                                "Switch '{}' (habitación '{}') volvió a responder.",
                                h.idSwitch(),
                                h.id());
                    }
                }
                log.info("Switch '{}' (habitación '{}') -> {}", h.idSwitch(), h.id(), accion);
            } catch (RuntimeException e) {
                synchronized (this) {
                    if (vigente(h)) {
                        estadoConocido.remove(h.id());
                        registrarFalla(h, "enviar " + accion + " a", e);
                    }
                }
            }
        }
    }

    /**
     * La habitación sigue configurada con el mismo switch (si no, lo que respondió el viejo ya no
     * vale).
     */
    private boolean vigente(HabitacionControlada h) {
        HabitacionControlada actual = habitaciones.get(h.id());
        return actual != null
                && actual.idSwitch().equals(h.idSwitch())
                && actual.urlSwitch().equals(h.urlSwitch());
    }

    private void registrarFalla(HabitacionControlada h, String que, RuntimeException e) {
        boolean primera = !noReintentarAntesDe.containsKey(h.id());
        noReintentarAntesDe.put(h.id(), relojReal.instant().plus(esperaReintento));
        if (primera) {
            log.error(
                    "No se pudo {} el switch '{}' (habitación '{}'); se reintenta: {}",
                    que,
                    h.idSwitch(),
                    h.id(),
                    e.getMessage());
        } else {
            log.debug("Sigue sin responder el switch '{}': {}", h.idSwitch(), e.getMessage());
        }
    }

    private boolean enEspera(String idHabitacion) {
        Instant hasta = noReintentarAntesDe.get(idHabitacion);
        return hasta != null && relojReal.instant().isBefore(hasta);
    }

    private static boolean coincide(EstadoSwitch estado, Accion accion) {
        return (estado == EstadoSwitch.ON && accion == Accion.ON)
                || (estado == EstadoSwitch.OFF && accion == Accion.OFF);
    }

    // ------------------------------------------------------------------
    // Diagnóstico
    // ------------------------------------------------------------------

    public synchronized List<EstadoControlado> estados() {
        List<EstadoControlado> salida = new ArrayList<>();
        for (HabitacionControlada h : habitaciones.values()) {
            EstadoHabitacion e = core.estadoById(h.id());
            Decision d = deseado.get(h.id());
            salida.add(
                    new EstadoControlado(
                            h.id(),
                            e == null ? null : e.temperaturaActual(),
                            e == null ? null : e.ultimaLectura(),
                            d == null ? null : d.accion(),
                            d == null ? null : d.motivo().name(),
                            estadoConocido.getOrDefault(h.id(), EstadoSwitch.DESCONOCIDO),
                            h.idSwitch()));
        }
        return salida;
    }
}
