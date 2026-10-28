package uy.edu.utec.iiss.engine.adaptador;

import java.time.Clock;
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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uy.edu.utec.iiss.core.Core;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.Estimulo;
import uy.edu.utec.iiss.core.model.Sitio;
import uy.edu.utec.iiss.engine.switches.ClienteSwitch;
import uy.edu.utec.iiss.engine.switches.EstadoSwitch;

/**
 * La única clase del engine que habla con el core, y la única dueña del estado del control.
 *
 * <ul>
 *   <li>Procesa los {@link Estimulo}s (lectura, tick, configuración, INICIAR): actualiza la
 *       situación (sitio, temperaturas, instantes, reloj) y le pide al core, que no tiene estado,
 *       que decida. Todo serializado con {@code synchronized}.
 *   <li>Recuerda la última decisión por habitación ("deseado"), que además dice cuáles están
 *       encendidas para la histéresis, y el último estado conocido de su switch; solo comanda
 *       cuando difieren.
 *   <li>Reconcilia (§4): consulta cada switch y corrige los que no coinciden (reinicio, cambio
 *       manual, comando perdido).
 *   <li>Un switch que falla no frena a los demás y no se reintenta antes de {@code
 *       esperaReintento}.
 *   <li>Cuida la potencia contratada también en la red: manda primero los OFF y después los ON, y
 *       retiene un ON si, contando como encendida toda habitación cuyo switch no confirmó OFF, la
 *       superaría (p. ej. falló el OFF que liberaba esa potencia).
 * </ul>
 *
 * La red con los switches corre en un hilo propio, en "pasadas": cada estímulo actualiza lo deseado
 * y pide una pasada sin esperarla, así ni INICIAR ni PUT /sitio esperan a los switches. Los pedidos
 * durante una pasada se juntan en la siguiente. Las pasadas son secuenciales: un switch lento
 * demora a los demás switches hasta su timeout.
 *
 * <p><b>En marcha.</b> El adaptador no guarda si el control está en marcha: lo pregunta ({@code
 * enMarcha}, que en el engine es {@code ControlService.enEjecucion()}). Así hay un solo lugar donde
 * vive ese dato y no se pueden desincronizar. Detenido, se sigue decidiendo (por ejemplo, ante un
 * PUT /sitio) pero no se toca ningún switch.
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

    private final Core core;
    private final ClienteSwitch switches;
    private final Clock relojReal;
    private final Duration esperaReintento;
    private final BooleanSupplier enMarcha;
    private final Executor hiloSwitches;

    // Situación del control: es lo que se le pasa a core.decidir.
    private Sitio sitio; // null hasta el primer inventario
    private List<ConfiguracionHabitacion> configuracion = List.of();
    private final Map<String, Double> temperaturas = new HashMap<>();
    private final Map<String, Instant> instanteLectura = new HashMap<>();
    private Instant ahora; // reloj del control según los estímulos (null = aún ninguno)
    private double factorTiempo = 1; // aceleración vigente (escala la vigencia de las lecturas)

    // Switches
    private final Map<String, HabitacionControlada> habitaciones = new LinkedHashMap<>();
    private final Map<String, Decision> deseado = new HashMap<>();
    private final Map<String, EstadoSwitch> estadoConocido = new HashMap<>();
    private final Map<String, Instant> noReintentarAntesDe = new HashMap<>();
    private final Set<String> retenidas = new HashSet<>(); // ON esperando potencia confirmada
    private boolean pasadaEncolada;
    private boolean consultaPendiente;

    /** Con su propio hilo de switches. */
    public CoreAdapter(
            Core core,
            ClienteSwitch switches,
            Clock relojReal,
            Duration esperaReintento,
            BooleanSupplier enMarcha) {
        this(
                core,
                switches,
                relojReal,
                esperaReintento,
                enMarcha,
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
            Core core,
            ClienteSwitch switches,
            Clock relojReal,
            Duration esperaReintento,
            BooleanSupplier enMarcha,
            Executor hiloSwitches) {
        this.core = Objects.requireNonNull(core);
        this.switches = Objects.requireNonNull(switches);
        this.relojReal = Objects.requireNonNull(relojReal);
        this.esperaReintento = Objects.requireNonNull(esperaReintento);
        this.enMarcha = Objects.requireNonNull(enMarcha);
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
     * Define (o redefine) el sitio y las habitaciones con sus switches; puede cambiar decisiones,
     * que se aplican al instante. El inventario ya viene validado ({@code ValidadorInventario}).
     */
    public synchronized void configurar(Sitio sitio, List<HabitacionControlada> nuevas) {
        Map<String, HabitacionControlada> porId = new LinkedHashMap<>();
        List<ConfiguracionHabitacion> configuracionNueva = new ArrayList<>();
        for (HabitacionControlada h : nuevas) {
            porId.put(h.id(), h);
            configuracionNueva.add(
                    new ConfiguracionHabitacion(
                            h.id(), h.id(), h.temperaturaEsperada(), h.potenciaKW()));
        }
        // Lo que se sabía del switch de una habitación que ya no existe, o que cambió de switch,
        // deja de valer.
        for (Map.Entry<String, HabitacionControlada> vieja : habitaciones.entrySet()) {
            HabitacionControlada nueva = porId.get(vieja.getKey());
            if (nueva == null
                    || !nueva.idSwitch().equals(vieja.getValue().idSwitch())
                    || !nueva.urlSwitch().equals(vieja.getValue().urlSwitch())) {
                estadoConocido.remove(vieja.getKey());
                noReintentarAntesDe.remove(vieja.getKey());
            }
        }
        habitaciones.clear();
        habitaciones.putAll(porId);
        procesar(new Estimulo.ConfiguracionActualizada(sitio, configuracionNueva));
    }

    /**
     * Procesa un estímulo (lectura, tick, configuración o INICIAR): actualiza la situación, le pide
     * al core que decida y aplica las decisiones (los switches se comandan en su hilo, si el
     * control está en marcha).
     *
     * <p>Una {@code ConfiguracionActualizada} recibida directamente cambia lo que se decide pero no
     * los switches; el engine usa {@link #configurar}, que actualiza las dos cosas.
     *
     * @return una decisión por habitación configurada, en el orden del inventario (vacía si todavía
     *     no hay sitio)
     */
    public synchronized List<Decision> procesar(Estimulo estimulo) {
        switch (estimulo) {
            case Estimulo.NuevaLectura l -> registrarLectura(l);
            case Estimulo.Tick t -> avanzarReloj(t.ts());
            case Estimulo.ConfiguracionActualizada c -> adoptarConfiguracion(c);
            case Estimulo.Sincronizacion s -> sincronizarReloj(s);
        }
        List<Decision> decisiones = decidir();
        aplicar(decisiones);
        return decisiones;
    }

    private void registrarLectura(Estimulo.NuevaLectura l) {
        String id = l.idHabitacion();
        if (!estaConfigurada(id)) {
            return; // habitación desconocida: no se guarda nada
        }
        if (!core.lecturaValida(l.temperaturaC())) {
            return; // sensor defectuoso: se conserva el último dato bueno
        }
        Instant anterior = instanteLectura.get(id);
        if (anterior != null && l.ts().isBefore(anterior)) {
            return; // llegó fuera de orden: hay un dato más nuevo
        }
        temperaturas.put(id, l.temperaturaC());
        instanteLectura.put(id, l.ts());
        avanzarReloj(l.ts());
    }

    /** El reloj solo avanza con lecturas y ticks: un estímulo atrasado no lo hace retroceder. */
    private void avanzarReloj(Instant ts) {
        if (ahora == null || ts.isAfter(ahora)) {
            ahora = ts;
        }
    }

    /** Lo que se sabía de una habitación que ya no está en el inventario se olvida. */
    private void adoptarConfiguracion(Estimulo.ConfiguracionActualizada c) {
        sitio = c.sitio();
        configuracion = c.habitaciones();
        Set<String> ids = new HashSet<>();
        for (ConfiguracionHabitacion h : configuracion) {
            ids.add(h.id());
        }
        temperaturas.keySet().retainAll(ids);
        instanteLectura.keySet().retainAll(ids);
        deseado.keySet().retainAll(ids);
    }

    /**
     * INICIAR (re)sincroniza el reloj: puede ir hacia atrás. Las temperaturas conocidas se
     * conservan y se toman como medidas en el nuevo instante, así no se apaga todo hasta que llegue
     * la próxima lectura, ni se descartan como "fuera de orden" las lecturas que vengan después de
     * un salto hacia atrás.
     */
    private void sincronizarReloj(Estimulo.Sincronizacion s) {
        ahora = s.ahora();
        factorTiempo = s.factorTiempo();
        instanteLectura.replaceAll((id, ts) -> s.ahora());
    }

    /** Arma la situación con el estado propio y le pide al core que decida. */
    private List<Decision> decidir() {
        if (sitio == null) {
            return List.of();
        }
        Map<String, EstadoHabitacion> estados = new HashMap<>();
        for (ConfiguracionHabitacion h : configuracion) {
            estados.put(h.id(), estadoDe(h.id()));
        }
        return core.decidir(sitio, configuracion, estados, ahora, factorTiempo);
    }

    private boolean estaConfigurada(String idHabitacion) {
        for (ConfiguracionHabitacion h : configuracion) {
            if (h.id().equals(idHabitacion)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lo que se sabe de una habitación: última temperatura, cuándo se midió y si está encendida (la
     * última decisión fue ON). Null si no está en el inventario.
     */
    public synchronized EstadoHabitacion estadoDe(String idHabitacion) {
        if (!estaConfigurada(idHabitacion)) {
            return null;
        }
        Decision ultima = deseado.get(idHabitacion);
        return new EstadoHabitacion(
                idHabitacion,
                temperaturas.get(idHabitacion),
                instanteLectura.get(idHabitacion),
                ultima != null && ultima.accion() == Accion.ON);
    }

    // ------------------------------------------------------------------
    // Arranque / reconciliar
    // ------------------------------------------------------------------

    /**
     * El control acaba de pasar a estar en marcha: se olvidan las esperas entre reintentos y se
     * pide una pasada que primero consulta el estado real de cada switch (§4) y después manda lo
     * que falte. No espera a los switches.
     */
    public synchronized void alIniciar() {
        noReintentarAntesDe.clear();
        programarPasada(true);
    }

    /**
     * Pide consultar el estado real de cada switch y comandar los que no coinciden con la última
     * decisión. Se llama al iniciar y periódicamente; no espera a los switches.
     */
    public synchronized void reconciliar() {
        programarPasada(true);
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private void aplicar(List<Decision> decisiones) {
        for (Decision d : decisiones) {
            Decision anterior = deseado.put(d.idHabitacion(), d);
            if (anterior == null || anterior.accion() != d.accion()) {
                log.info(
                        "Decisión: habitación '{}' -> {} ({})",
                        d.idHabitacion(),
                        d.accion(),
                        d.motivo());
            }
        }
        programarPasada(false);
    }

    /**
     * Encola una pasada en el hilo de switches, salvo que ya haya una esperando. Se llama con el
     * bloqueo tomado.
     */
    private void programarPasada(boolean consultarAntes) {
        if (!enMarcha.getAsBoolean()) {
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
                if (!enMarcha.getAsBoolean()) {
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
                if (!enMarcha.getAsBoolean()) {
                    return;
                }
                Decision d = deseado.get(h.id());
                if (!vigente(h)
                        || d == null
                        || d.accion() != soloEsta
                        || coincide(estadoConocido.get(h.id()), d.accion())
                        || enEspera(h.id())
                        || (soloEsta == Accion.ON && excederiaLaContratada(h))) {
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
     * Encender {@code h} superaría la contratada si las demás cuyo switch no confirmó OFF
     * estuvieran encendidas. Un switch que no respondió el OFF (o que nunca se pudo consultar)
     * puede seguir encendido: se retiene el ON hasta que lo confirme. Se llama con el bloqueo
     * tomado.
     */
    private boolean excederiaLaContratada(HabitacionControlada h) {
        double posible = h.potenciaKW();
        for (HabitacionControlada otra : habitaciones.values()) {
            if (!otra.id().equals(h.id()) && estadoConocido.get(otra.id()) != EstadoSwitch.OFF) {
                posible += otra.potenciaKW();
            }
        }
        double contratada = sitio == null ? Double.MAX_VALUE : sitio.potenciaContratadaKW();
        boolean excede = posible > contratada + 1e-9;
        if (excede && retenidas.add(h.id())) {
            log.warn(
                    "Se retiene el ON de '{}': con los switches sin confirmar OFF serían {} kW"
                            + " (contratada {} kW).",
                    h.id(),
                    posible,
                    contratada);
        } else if (!excede && retenidas.remove(h.id())) {
            log.info("Se libera el ON de '{}': la potencia ya está confirmada.", h.id());
        }
        return excede;
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
            EstadoHabitacion e = estadoDe(h.id());
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
