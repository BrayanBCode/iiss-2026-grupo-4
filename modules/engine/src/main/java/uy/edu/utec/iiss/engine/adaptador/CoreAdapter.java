package uy.edu.utec.iiss.engine.adaptador;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uy.edu.utec.iiss.core.Core;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.Estimulo;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Motivo;
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
 *   <li>Recuerda la última decisión por habitación ({@code ultimaDecision}), que además dice cuáles
 *       están encendidas para la histéresis, y el último estado conocido de su switch; solo comanda
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
 * La red con los switches corre en un hilo propio, en "pasadas": cada estímulo actualiza la última
 * decisión y pide una pasada sin esperarla, así ni INICIAR ni PUT /sitio esperan a los switches.
 * Los pedidos durante una pasada se juntan en la siguiente. Las pasadas son secuenciales: un switch
 * lento demora a los demás switches hasta su timeout.
 *
 * <p><b>Estado de ejecución.</b> El adaptador es el único dueño del estado de ejecución ({@link
 * #iniciar}, {@link #detener}, {@link #enEjecucion}); {@code ControlService} le delega y nunca
 * guarda una copia. La dependencia va en un solo sentido (ControlService usa al adaptador, nunca al
 * revés), así los candados se toman siempre en el mismo orden y no hay deadlock.
 *
 * <p><b>Detenido no decide.</b> Los estímulos actualizan la situación (sitio, lecturas, reloj) pero
 * no se le pide nada al core ni se toca ningún switch: el reloj de un control detenido es el del
 * último INICIAR y una decisión con esa hora sería engañosa. {@link #iniciar} decide con la
 * situación ya re-sincronizada. Mientras tanto queda la última decisión tomada en ejecución (la que
 * muestra {@code /diagnostico}), o ninguna si nunca se inició.
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
            String idSwitch,
            Map<MotivoDescarte, Integer> lecturasDescartadas) {}

    /**
     * Foto del control para el resumen periódico del log: cuántas habitaciones están decididas ON,
     * la potencia que comprometen frente a la contratada y qué habitaciones hay por motivo.
     */
    public record Resumen(
            int encendidas,
            int habitaciones,
            double comprometidaKW,
            double contratadaKW,
            Map<Motivo, List<String>> porMotivo) {}

    private final Core core;
    private final ClienteSwitch switches;
    private final Clock relojReal;
    private final Duration esperaReintento;
    private final Executor hiloSwitches;

    private boolean enEjecucion; // solo se toca con el candado de esta clase

    // Situación del control: es lo que se le pasa a core.decidir.
    private Sitio sitio; // null hasta el primer inventario
    private List<ConfiguracionHabitacion> configuracion = List.of();
    private final Map<String, Double> temperaturas = new HashMap<>();
    private final Map<String, Instant> instanteLectura = new HashMap<>();
    // Lecturas descartadas por habitación y motivo, para /diagnostico (D8).
    private final Map<String, Map<MotivoDescarte, Integer>> descartes = new HashMap<>();
    private Instant ahora; // reloj del control según los estímulos (null = aún ninguno)

    // Switches
    private final Map<String, HabitacionControlada> habitaciones = new LinkedHashMap<>();
    private final Map<String, Decision> ultimaDecision = new HashMap<>();
    private final Map<String, EstadoSwitch> estadoConocido = new HashMap<>();
    private final Map<String, Instant> noReintentarAntesDe = new HashMap<>();
    private final Set<String> retenidas = new HashSet<>(); // ON esperando potencia confirmada
    private boolean pasadaEncolada;
    private boolean consultaPendiente;

    /** Con su propio hilo de switches. */
    public CoreAdapter(
            Core core, ClienteSwitch switches, Clock relojReal, Duration esperaReintento) {
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
            Core core,
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
     * Define (o redefine) el sitio y las habitaciones con sus switches. En ejecución puede cambiar
     * decisiones, que se aplican al instante; detenido solo se guarda (se decide al iniciar). El
     * inventario ya viene validado ({@code ValidadorInventario}).
     */
    public synchronized void configurar(Sitio sitio, List<HabitacionControlada> nuevas) {
        registrarSitio(sitio, nuevas);
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
     * Deja en el log con qué sitio se controla: sin esto no se puede verificar después una corrida
     * contra el simulador (potencia, franja, zona y kW de cada habitación).
     */
    private static void registrarSitio(Sitio sitio, List<HabitacionControlada> nuevas) {
        FranjaHoraria punta = sitio.puntaTarifa();
        log.info(
                "Sitio aplicado '{}': contratada {} kW, punta {}-{} {}, zona {}, habitaciones: {}",
                sitio.id(),
                sitio.potenciaContratadaKW(),
                punta.desde(),
                punta.hasta(),
                punta.dias(),
                sitio.zona(),
                nuevas.size());
        for (HabitacionControlada h : nuevas) {
            log.info(
                    "  habitación '{}': esperada {} °C, {} kW, switch '{}'",
                    h.id(),
                    h.temperaturaEsperada(),
                    h.potenciaKW(),
                    h.idSwitch());
        }
    }

    /**
     * Procesa un estímulo (lectura, tick, configuración o INICIAR): actualiza la situación y, si el
     * control está en ejecución, le pide al core que decida y aplica las decisiones (los switches
     * se comandan en su hilo). Detenido no decide (ver la clase).
     *
     * <p>Una {@code ConfiguracionActualizada} recibida directamente cambia lo que se decide pero no
     * los switches; el engine usa {@link #configurar}, que actualiza las dos cosas.
     *
     * @return una decisión por habitación configurada, en el orden del inventario (vacía si todavía
     *     no hay sitio o si el control está detenido)
     */
    public synchronized List<Decision> procesar(Estimulo estimulo) {
        switch (estimulo) {
            case Estimulo.NuevaLectura l -> registrarLectura(l);
            case Estimulo.Tick t -> avanzarReloj(t.ts());
            case Estimulo.ConfiguracionActualizada c -> adoptarConfiguracion(c);
            case Estimulo.Sincronizacion s -> sincronizarReloj(s);
        }
        if (!enEjecucion) {
            return List.of();
        }
        List<Decision> decisiones = decidir();
        aplicar(decisiones);
        return decisiones;
    }

    /**
     * Una lectura que no llegó a ser estímulo porque se descartó antes (por ejemplo, su {@code ts}
     * es más viejo que la vigencia): solo se cuenta para {@code /diagnostico}. No cambia ninguna
     * decisión. El log lo escribe quien descarta, que es el que conoce el {@code ts} original.
     */
    public synchronized void descartarLectura(String idHabitacion, MotivoDescarte motivo) {
        if (estaConfigurada(idHabitacion)) {
            contarDescarte(idHabitacion, motivo);
        }
    }

    private void contarDescarte(String idHabitacion, MotivoDescarte motivo) {
        descartes
                .computeIfAbsent(idHabitacion, id -> new EnumMap<>(MotivoDescarte.class))
                .merge(motivo, 1, Integer::sum);
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
            contarDescarte(id, MotivoDescarte.FUERA_DE_ORDEN); // hay un dato más nuevo
            // INFO porque es excepcional y explica por qué una lectura no cambió nada. Acá el
            // instante ya está en hora del control: el ts original solo lo conoce ControlService.
            log.info(
                    "Lectura de '{}' descartada ({}): medida a las {} (hora del control), anterior"
                            + " a la última guardada {}; hora del control {}",
                    id,
                    MotivoDescarte.FUERA_DE_ORDEN,
                    l.ts(),
                    anterior,
                    ahora);
            return;
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
        ultimaDecision.keySet().retainAll(ids);
        descartes.keySet().retainAll(ids);
    }

    /**
     * INICIAR (re)sincroniza el reloj: puede ir hacia atrás.
     *
     * <ul>
     *   <li>Una lectura que, con el reloj <b>anterior</b>, ya estaba vencida se olvida: la
     *       habitación queda sin lectura hasta que llegue una nueva (D7). Si no, al sellarla con la
     *       hora nueva un dato viejo volvería a contar como reciente.
     *   <li>Las vigentes se conservan y se toman como medidas en el nuevo instante, así no se apaga
     *       todo hasta la próxima lectura, ni se descartan como "fuera de orden" las que lleguen
     *       después de un salto hacia atrás.
     * </ul>
     *
     * El reloj anterior es {@code ahora}: {@code ControlService} manda un Tick con la hora actual
     * del reloj viejo justo antes, para que cuente también el tiempo que estuvo detenido.
     */
    private void sincronizarReloj(Estimulo.Sincronizacion s) {
        Iterator<Map.Entry<String, Instant>> lecturas = instanteLectura.entrySet().iterator();
        while (lecturas.hasNext()) {
            Map.Entry<String, Instant> lectura = lecturas.next();
            if (core.lecturaVencida(lectura.getValue(), ahora)) {
                temperaturas.remove(lectura.getKey());
                lecturas.remove();
            } else {
                lectura.setValue(s.ahora());
            }
        }
        ahora = s.ahora();
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
        return core.decidir(sitio, configuracion, estados, ahora);
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
        Decision ultima = ultimaDecision.get(idHabitacion);
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
     * Pone el control en ejecución (o lo re-sincroniza si ya lo estaba): se olvidan las esperas
     * entre reintentos, se decide con la situación actual (detenido no se decidía) y se pide una
     * pasada que primero consulta el estado real de cada switch (§4) y después manda lo que falte.
     * No espera a los switches.
     */
    public synchronized void iniciar() {
        enEjecucion = true;
        noReintentarAntesDe.clear();
        // Antes de aplicar: así la pasada que pide aplicar ya consulta antes de comandar, aun con
        // un executor sincrónico.
        consultaPendiente = true;
        aplicar(decidir());
    }

    /**
     * Deja de comandar switches. Una pasada que ya está en curso lo ve antes de mandar el próximo
     * comando, porque lee el estado de ejecución con el mismo candado. Idempotente.
     */
    public synchronized void detener() {
        enEjecucion = false;
    }

    public synchronized boolean enEjecucion() {
        return enEjecucion;
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

    /**
     * Guarda la última decisión y pide una pasada. Se loguea cuando cambia la acción o el motivo:
     * un OFF que sigue OFF pero pasa de SATISFECHA a TARIFA_PUNTA (o a SIN_POTENCIA_DISPONIBLE) es
     * la única evidencia de la entrada a la punta o de la carga limitada.
     */
    private void aplicar(List<Decision> decisiones) {
        for (Decision d : decisiones) {
            Decision anterior = ultimaDecision.put(d.idHabitacion(), d);
            if (!d.equals(anterior)) {
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
        if (!enEjecucion) {
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
                if (!enEjecucion) {
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
                if (!enEjecucion) {
                    return;
                }
                Decision d = ultimaDecision.get(h.id());
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

    /**
     * Resumen de lo decidido para el log periódico. Barato y sin red: solo recorre las últimas
     * decisiones con el candado tomado. Una habitación todavía sin decisión no cuenta en ningún
     * motivo.
     */
    public synchronized Resumen resumen() {
        int encendidas = 0;
        double comprometida = 0;
        Map<Motivo, List<String>> porMotivo = new EnumMap<>(Motivo.class);
        for (HabitacionControlada h : habitaciones.values()) {
            Decision d = ultimaDecision.get(h.id());
            if (d == null) {
                continue;
            }
            if (d.accion() == Accion.ON) {
                encendidas++;
                comprometida += h.potenciaKW();
            }
            porMotivo.computeIfAbsent(d.motivo(), m -> new ArrayList<>()).add(h.id());
        }
        double contratada = sitio == null ? 0 : sitio.potenciaContratadaKW();
        return new Resumen(encendidas, habitaciones.size(), comprometida, contratada, porMotivo);
    }

    public synchronized List<EstadoControlado> estados() {
        List<EstadoControlado> salida = new ArrayList<>();
        for (HabitacionControlada h : habitaciones.values()) {
            EstadoHabitacion e = estadoDe(h.id());
            Decision d = ultimaDecision.get(h.id());
            salida.add(
                    new EstadoControlado(
                            h.id(),
                            e == null ? null : e.temperaturaActual(),
                            e == null ? null : e.ultimaLectura(),
                            d == null ? null : d.accion(),
                            d == null ? null : d.motivo().name(),
                            estadoConocido.getOrDefault(h.id(), EstadoSwitch.DESCONOCIDO),
                            h.idSwitch(),
                            Map.copyOf(descartes.getOrDefault(h.id(), Map.of()))));
        }
        return salida;
    }
}
