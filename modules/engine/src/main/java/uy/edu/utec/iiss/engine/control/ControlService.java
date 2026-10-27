package uy.edu.utec.iiss.engine.control;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Sitio;
import uy.edu.utec.iiss.engine.adaptador.CoreAdapter;
import uy.edu.utec.iiss.engine.adaptador.CoreAdapter.HabitacionControlada;
import uy.edu.utec.iiss.engine.inventario.Inventario;
import uy.edu.utec.iiss.engine.inventario.InventarioAplicado;
import uy.edu.utec.iiss.engine.inventario.InventarioService;

/**
 * El recurso "control" del estándar v2 (§5.2) y los eventos temporales del engine.
 *
 * <ul>
 *   <li>{@link #iniciar}: sincroniza el reloj del control, carga el sitio en el core, consulta los
 *       switches y empieza a comandarlos. Sobre un control ya en ejecución re-sincroniza (no es
 *       error).
 *   <li>{@link #detener}: deja de comandar switches. Idempotente.
 *   <li>Un temporizador dispara un Tick cada {@code control.tick-ms} reales (cortar/restituir en
 *       los cambios de franja, vencer lecturas) y reconcilia los switches cada {@code
 *       control.reconciliacion-segundos}.
 * </ul>
 *
 * <b>Zona horaria de la tarifa.</b> El estándar dice que la franja es "hora local, sin conversión
 * de zona". Se toma la zona en la que viene expresada la {@code fechaHora} de INICIAR ({@code ...Z}
 * ⇒ UTC, {@code ...-03:00} ⇒ UTC-3), así controlador y entorno leen la misma hora en el mismo
 * instante. Se puede fijar otra con {@code control.zona-horaria} (p. ej. America/Montevideo).
 */
@Service
public class ControlService {

    private static final Logger log = LoggerFactory.getLogger(ControlService.class);

    private final CoreAdapter adaptador;
    private final InventarioService inventarioService;
    private final Clock relojReal;
    private final Optional<ZoneId> zonaFija;
    private final long tickMs;
    private final long reconciliacionSegundos;

    private volatile RelojControl reloj; // null = DETENIDO
    private ScheduledExecutorService temporizador;

    public ControlService(
            CoreAdapter adaptador,
            InventarioService inventarioService,
            Clock relojReal,
            @Value("${control.zona-horaria:}") String zonaFija,
            @Value("${control.tick-ms:1000}") long tickMs,
            @Value("${control.reconciliacion-segundos:15}") long reconciliacionSegundos) {
        this.adaptador = adaptador;
        this.inventarioService = inventarioService;
        this.relojReal = relojReal;
        this.zonaFija =
                zonaFija == null || zonaFija.isBlank()
                        ? Optional.empty()
                        : Optional.of(ZoneId.of(zonaFija.trim()));
        this.tickMs = tickMs;
        this.reconciliacionSegundos = reconciliacionSegundos;
    }

    // ------------------------------------------------------------------
    // Comandos (§5.2)
    // ------------------------------------------------------------------

    /**
     * INICIAR. {@code zonaDeFechaHora} es la zona/offset en que vino escrita la fechaHora; se usa
     * para la tarifa salvo que haya una zona fija configurada.
     */
    public synchronized EstadoControl iniciar(
            Instant fechaHora, ZoneId zonaDeFechaHora, double factorTiempo) {
        boolean resincroniza = reloj != null;
        RelojControl nuevo =
                new RelojControl(
                        fechaHora,
                        factorTiempo,
                        relojReal.instant(),
                        zonaFija.orElse(zonaDeFechaHora));
        reloj = nuevo;
        inventarioService.actual().ifPresent(this::configurarCore); // la zona pudo cambiar
        adaptador.sincronizar(nuevo.fechaHora(), nuevo.factorTiempo());
        adaptador.habilitar();
        arrancarTemporizador();
        log.info(
                "Control {}: fechaHora={} factorTiempo={} zona={}",
                resincroniza ? "re-sincronizado" : "INICIADO",
                fechaHora,
                factorTiempo,
                nuevo.zona());
        return estado();
    }

    public synchronized EstadoControl detener() {
        if (reloj != null) {
            reloj = null;
            adaptador.deshabilitar();
            pararTemporizador();
            log.info("Control DETENIDO: no se comandan más switches.");
        }
        return estado();
    }

    public EstadoControl estado() {
        RelojControl r = reloj;
        if (r == null) {
            return EstadoControl.detenido();
        }
        return new EstadoControl(
                EstadoControl.EN_EJECUCION, r.ahora(relojReal), r.factorTiempo(), r.iniciadoEn());
    }

    public boolean enEjecucion() {
        return reloj != null;
    }

    // ------------------------------------------------------------------
    // Estímulos del entorno
    // ------------------------------------------------------------------

    /** Lectura ya resuelta a su habitación por el listener MQTT. Detenido ⇒ se ignora. */
    public void alRecibirLectura(String idHabitacion, double temperaturaC) {
        RelojControl r = reloj;
        if (r == null) {
            return;
        }
        try {
            adaptador.alLlegarLectura(idHabitacion, temperaturaC, r.ahora(relojReal));
        } catch (RuntimeException e) {
            log.error("Error al procesar la lectura de '{}': {}", idHabitacion, e.getMessage());
        }
    }

    /** PUT /sitio (o el ABM) aplicó un inventario nuevo: el core lo adopta en caliente. */
    @EventListener
    public synchronized void alAplicarseInventario(InventarioAplicado evento) {
        configurarCore(evento.inventario());
    }

    private void configurarCore(Inventario inv) {
        ZoneId zona = reloj != null ? reloj.zona() : zonaFija.orElse(ZoneId.systemDefault());
        adaptador.configurar(sitioDelCore(inv, zona), habitacionesDelCore(inv));
    }

    static Sitio sitioDelCore(Inventario inv, ZoneId zona) {
        Inventario.Franja punta = inv.sitio().tarifa().punta();
        return new Sitio(
                inv.sitio().id(),
                inv.sitio().potenciaContratadaKW(),
                new FranjaHoraria(
                        LocalTime.parse(punta.desde()),
                        LocalTime.parse(punta.hasta()),
                        DiasTarifa.valueOf(punta.dias())),
                zona);
    }

    static List<HabitacionControlada> habitacionesDelCore(Inventario inv) {
        return inv.habitaciones().stream()
                .map(
                        h ->
                                new HabitacionControlada(
                                        h.id(),
                                        h.temperaturaEsperada(),
                                        h.potenciaKW(),
                                        h.idSwitch(),
                                        h.urlSwitch()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Eventos temporales
    // ------------------------------------------------------------------

    void tick() {
        RelojControl r = reloj;
        if (r == null) {
            return;
        }
        try {
            adaptador.alTick(r.ahora(relojReal));
        } catch (RuntimeException e) {
            log.error("Error en el tick del control: {}", e.getMessage());
        }
    }

    void reconciliar() {
        if (reloj == null) {
            return;
        }
        try {
            adaptador.reconciliar();
        } catch (RuntimeException e) {
            log.error("Error al reconciliar los switches: {}", e.getMessage());
        }
    }

    private void arrancarTemporizador() {
        if (temporizador != null || tickMs <= 0) {
            return;
        }
        temporizador =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "control-temporizador");
                            t.setDaemon(true);
                            return t;
                        });
        temporizador.scheduleWithFixedDelay(this::tick, tickMs, tickMs, TimeUnit.MILLISECONDS);
        if (reconciliacionSegundos > 0) {
            temporizador.scheduleWithFixedDelay(
                    this::reconciliar,
                    reconciliacionSegundos,
                    reconciliacionSegundos,
                    TimeUnit.SECONDS);
        }
    }

    @PreDestroy
    synchronized void pararTemporizador() {
        if (temporizador != null) {
            temporizador.shutdownNow();
            temporizador = null;
        }
    }
}
