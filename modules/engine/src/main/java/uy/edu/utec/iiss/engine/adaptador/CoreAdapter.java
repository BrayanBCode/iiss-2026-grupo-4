package uy.edu.utec.iiss.engine.adaptador;

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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adaptador entre el engine y el core: la única clase del engine que habla con
 * el core. Hace cinco cosas:
 *
 *  1. Traduce lo que llega (lectura, tick, configuración, sincronización) a
 *     llamadas al core. Los instantes ya vienen en el reloj del control.
 *  2. Serializa las llamadas ({@code synchronized}): el core no es seguro para
 *     varios hilos y le llegan eventos de MQTT, del temporizador y de REST.
 *  3. Recuerda la última decisión de cada habitación ("deseado") y el último
 *     estado CONOCIDO de su switch; solo comanda cuando difieren.
 *  4. Reconcilia (§4): consulta el estado real de cada switch y, si no
 *     coincide con lo deseado (DESCONOCIDO porque el switch reinició, alguien
 *     lo movió a mano, un comando se perdió), lo vuelve a comandar.
 *  5. Tolera switches caídos: un switch que falla no impide comandar a los
 *     demás, y no se le reintenta antes de {@code esperaReintento} (para que un
 *     switch muerto no trabe el lazo con timeouts en cada tick).
 *
 * Mientras está deshabilitado (control DETENIDO, §5.2) el core sigue
 * recibiendo configuración, pero no se comanda ni se consulta ningún switch.
 *
 * Limitación conocida: se comanda al switch dentro del bloqueo, así que un
 * switch lento demora a los demás eventos hasta que venza el timeout REST.
 */
public class CoreAdapter {

    private static final Logger log = LoggerFactory.getLogger(CoreAdapter.class);

    /** Una habitación tal como la necesita el core, más el switch que la calefacciona. */
    public record HabitacionControlada(String id, double temperaturaEsperada, double potenciaKW,
                                       String idSwitch, String urlSwitch) {
    }

    /** Foto de una habitación para diagnóstico. */
    public record EstadoControlado(String idHabitacion, Double temperaturaActual, Instant ultimaLectura,
                                   Accion decision, String motivo, EstadoSwitch estadoSwitch,
                                   String idSwitch) {
    }

    private final ICoreClient core;
    private final ClienteSwitch switches;
    private final Clock relojReal;
    private final Duration esperaReintento;

    private final Map<String, HabitacionControlada> habitaciones = new LinkedHashMap<>();
    private final Map<String, Decision> deseado = new HashMap<>();
    private final Map<String, EstadoSwitch> estadoConocido = new HashMap<>();
    private final Map<String, Instant> noReintentarAntesDe = new HashMap<>();
    private boolean habilitado;

    public CoreAdapter(ICoreClient core, ClienteSwitch switches, Clock relojReal, Duration esperaReintento) {
        this.core = Objects.requireNonNull(core);
        this.switches = Objects.requireNonNull(switches);
        this.relojReal = Objects.requireNonNull(relojReal);
        this.esperaReintento = Objects.requireNonNull(esperaReintento);
    }

    // ------------------------------------------------------------------
    // Estímulos
    // ------------------------------------------------------------------

    /** Define (o redefine) el sitio y las habitaciones; puede cambiar decisiones, que se aplican al instante. */
    public synchronized void configurar(Sitio sitio, List<HabitacionControlada> nuevas) {
        ConfiguracionHabitacion[] configuracion = nuevas.stream()
                .map(h -> new ConfiguracionHabitacion(h.id(), h.id(), h.temperaturaEsperada(), h.potenciaKW()))
                .toArray(ConfiguracionHabitacion[]::new);
        List<Decision> decisiones = core.actualizarSitio(sitio, configuracion);   // si el core la rechaza, no se toca nada

        Map<String, HabitacionControlada> porId = new LinkedHashMap<>();
        nuevas.forEach(h -> porId.put(h.id(), h));
        // Lo que se sabía del switch de una habitación que ya no existe, o que cambió de switch, deja de valer.
        habitaciones.forEach((id, vieja) -> {
            HabitacionControlada nueva = porId.get(id);
            if (nueva == null || !nueva.idSwitch().equals(vieja.idSwitch()) || !nueva.urlSwitch().equals(vieja.urlSwitch())) {
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
    public synchronized void alLlegarLectura(String idHabitacion, double temperaturaC, Instant ahora) {
        aplicar(core.nuevaTemperatura(idHabitacion, temperaturaC, ahora));
    }

    /** Pasó el tiempo (lo dispara el temporizador del engine); permite cortar y restituir en los cambios de franja. */
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

    /** Empieza a comandar switches: primero consulta su estado real (§4) y después manda lo que falte. */
    public synchronized void habilitar() {
        habilitado = true;
        noReintentarAntesDe.clear();
        reconciliar();
    }

    /** Deja de comandar switches (§5.2: no se exige apagar lo que quedó encendido). */
    public synchronized void deshabilitar() {
        habilitado = false;
    }

    public synchronized boolean habilitado() {
        return habilitado;
    }

    /**
     * Consulta el estado real de cada switch y comanda los que no coinciden con
     * la última decisión. Se llama al habilitar y periódicamente.
     */
    public synchronized void reconciliar() {
        if (!habilitado) {
            return;
        }
        for (HabitacionControlada h : habitaciones.values()) {
            if (enEspera(h.id())) {
                continue;
            }
            try {
                EstadoSwitch real = switches.consultar(h.urlSwitch(), h.idSwitch());
                if (estadoConocido.get(h.id()) != real && estadoConocido.containsKey(h.id())) {
                    log.warn("Switch '{}' (habitación '{}') está {} y se creía {}: se corrige.",
                            h.idSwitch(), h.id(), real, estadoConocido.get(h.id()));
                }
                estadoConocido.put(h.id(), real);
            } catch (RuntimeException e) {
                registrarFalla(h, "consultar", e);
            }
        }
        enviarPendientes();
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private void aplicar(List<Decision> decisiones) {
        for (Decision d : decisiones) {
            if (habitaciones.containsKey(d.idHabitacion())) {
                Decision anterior = deseado.put(d.idHabitacion(), d);
                if (anterior == null || anterior.accion() != d.accion()) {
                    log.info("Decisión: habitación '{}' -> {} ({})", d.idHabitacion(), d.accion(), d.motivo());
                }
            }
        }
        enviarPendientes();
    }

    private void enviarPendientes() {
        if (!habilitado) {
            return;
        }
        for (HabitacionControlada h : habitaciones.values()) {
            Decision d = deseado.get(h.id());
            if (d == null || coincide(estadoConocido.get(h.id()), d.accion()) || enEspera(h.id())) {
                continue;
            }
            try {
                EstadoSwitch resultado = switches.comandar(h.urlSwitch(), h.idSwitch(), d.accion());
                estadoConocido.put(h.id(), resultado);
                if (noReintentarAntesDe.remove(h.id()) != null) {
                    log.info("Switch '{}' (habitación '{}') volvió a responder.", h.idSwitch(), h.id());
                }
                log.info("Switch '{}' (habitación '{}') -> {}", h.idSwitch(), h.id(), d.accion());
            } catch (RuntimeException e) {
                estadoConocido.remove(h.id());
                registrarFalla(h, "enviar " + d.accion() + " a", e);
            }
        }
    }

    private void registrarFalla(HabitacionControlada h, String que, RuntimeException e) {
        boolean primera = !noReintentarAntesDe.containsKey(h.id());
        noReintentarAntesDe.put(h.id(), relojReal.instant().plus(esperaReintento));
        if (primera) {
            log.error("No se pudo {} el switch '{}' (habitación '{}'); se reintenta: {}", que, h.idSwitch(), h.id(), e.getMessage());
        } else {
            log.debug("Sigue sin responder el switch '{}': {}", h.idSwitch(), e.getMessage());
        }
    }

    private boolean enEspera(String idHabitacion) {
        Instant hasta = noReintentarAntesDe.get(idHabitacion);
        return hasta != null && relojReal.instant().isBefore(hasta);
    }

    private static boolean coincide(EstadoSwitch estado, Accion accion) {
        return (estado == EstadoSwitch.ON && accion == Accion.ON) || (estado == EstadoSwitch.OFF && accion == Accion.OFF);
    }

    // ------------------------------------------------------------------
    // Diagnóstico
    // ------------------------------------------------------------------

    public synchronized List<EstadoControlado> estados() {
        List<EstadoControlado> salida = new ArrayList<>();
        for (HabitacionControlada h : habitaciones.values()) {
            EstadoHabitacion e = core.estadoById(h.id());
            Decision d = deseado.get(h.id());
            salida.add(new EstadoControlado(h.id(),
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
