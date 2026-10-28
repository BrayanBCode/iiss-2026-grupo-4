package uy.edu.utec.iiss.engine.control;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
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
import uy.edu.utec.iiss.core.model.Estimulo;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Sitio;
import uy.edu.utec.iiss.engine.adaptador.CoreAdapter;
import uy.edu.utec.iiss.engine.adaptador.CoreAdapter.HabitacionControlada;
import uy.edu.utec.iiss.engine.adaptador.MotivoDescarte;
import uy.edu.utec.iiss.engine.inventario.Inventario;
import uy.edu.utec.iiss.engine.inventario.InventarioAplicado;
import uy.edu.utec.iiss.engine.inventario.InventarioService;

/**
 * El recurso "control" del estándar v2 (§5.2) y los eventos temporales del engine.
 *
 * <ul>
 *   <li>{@link #iniciar}: sincroniza el reloj del control, configura el sitio en el adaptador,
 *       consulta los switches y empieza a comandarlos. Sobre un control ya en ejecución
 *       re-sincroniza (no es error).
 *   <li>{@link #detener}: deja de comandar switches. Idempotente.
 *   <li>Un temporizador dispara un Tick cada {@code control.tick-ms} reales (cortar/restituir en
 *       los cambios de franja, vencer lecturas) y reconcilia los switches cada {@code
 *       control.reconciliacion-segundos}.
 * </ul>
 *
 * <b>Zona horaria de la tarifa.</b> El estándar dice que la franja es "hora local, sin conversión
 * de zona". El sitio es de Uruguay: la zona es siempre la de {@code control.zona-horaria}
 * (America/Montevideo por defecto, y también si la propiedad viene vacía). No depende del offset
 * con que llegue la {@code fechaHora} de INICIAR ni de la zona del contenedor (que en Docker suele
 * ser UTC): con {@code 21:00Z} son las 18:00 en Montevideo y es punta (T9).
 *
 * <p><b>Tiempo.</b> La hora del control es {@code fechaHora + (real − iniciadoEn) × factor} y es la
 * única fuente de "qué hora es"; el reloj del sistema solo mide duraciones. Por eso el {@code ts}
 * de las lecturas (en hora real) se convierte con {@link RelojControl#aTiempoDeControl}, y la
 * vigencia de una lectura se mide en tiempo de control, sin escalar por el factor.
 */
@Service
public class ControlService {

    private static final Logger log = LoggerFactory.getLogger(ControlService.class);

    private final CoreAdapter adaptador;
    private final InventarioService inventarioService;
    private final Clock relojReal;
    private final ZoneId zona; // zona del sitio, fija (T9)
    private final long tickMs;
    private final long reconciliacionSegundos;
    private final Duration vigenciaLectura;

    // `reloj` solo da la hora (fechaHora, factor, zona) y NO dice si el control está detenido: eso
    // lo dice adaptador.enEjecucion(), así hay una sola bandera de estado de ejecución. Queda con
    // el último INICIAR después de DETENER y es null antes del primero.
    private volatile RelojControl reloj;
    private ScheduledExecutorService temporizador;

    public ControlService(
            CoreAdapter adaptador,
            InventarioService inventarioService,
            Clock relojReal,
            @Value("${control.zona-horaria:America/Montevideo}") String zona,
            @Value("${control.tick-ms:1000}") long tickMs,
            @Value("${control.reconciliacion-segundos:15}") long reconciliacionSegundos,
            @Value("${control.vigencia-lectura-segundos:600}") long vigenciaSegundos) {
        this.adaptador = adaptador;
        this.inventarioService = inventarioService;
        this.relojReal = relojReal;
        this.zona = zona == null || zona.isBlank() ? Sitio.ZONA_URUGUAY : ZoneId.of(zona.trim());
        this.tickMs = tickMs;
        this.reconciliacionSegundos = reconciliacionSegundos;
        this.vigenciaLectura = Duration.ofSeconds(vigenciaSegundos);
    }

    // ------------------------------------------------------------------
    // Comandos (§5.2)
    // ------------------------------------------------------------------

    /**
     * INICIAR. {@code fechaHora} ya es un instante: el offset con que vino escrita no cambia la
     * zona de la tarifa, que es siempre la del sitio ({@link #zona()}).
     */
    public synchronized EstadoControl iniciar(Instant fechaHora, double factorTiempo) {
        boolean resincroniza = enEjecucion();
        RelojControl nuevo = new RelojControl(fechaHora, factorTiempo, relojReal.instant(), zona);
        // Primero se configura y se sincroniza, y recién después se marca "en ejecución": así la
        // primera pasada de switches es la que consulta su estado real (§4) antes de comandar.
        Optional<Inventario> inventario = inventarioService.actual();
        if (inventario.isPresent()) {
            configurarCore(inventario.get());
        }
        adaptador.procesar(new Estimulo.Sincronizacion(nuevo.fechaHora(), nuevo.factorTiempo()));
        reloj = nuevo;
        adaptador.iniciar();
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
        if (enEjecucion()) {
            adaptador.detener(); // desde acá el adaptador no comanda más switches
            pararTemporizador();
            log.info("Control DETENIDO: no se comandan más switches.");
        }
        return estado();
    }

    public EstadoControl estado() {
        // iniciar() fija el reloj antes de poner en ejecución al adaptador, así que en ejecución
        // implica reloj != null; el chequeo de r solo evita un NPE, no decide si está detenido.
        RelojControl r = reloj;
        if (!enEjecucion() || r == null) {
            return EstadoControl.detenido();
        }
        return new EstadoControl(
                EstadoControl.EN_EJECUCION, r.ahora(relojReal), r.factorTiempo(), r.iniciadoEn());
    }

    public boolean enEjecucion() {
        return adaptador.enEjecucion();
    }

    // ------------------------------------------------------------------
    // Estímulos del entorno
    // ------------------------------------------------------------------

    /**
     * Lectura ya resuelta a su habitación por el listener MQTT. Detenido ⇒ se ignora. Se guarda con
     * el instante que dice el termostato ({@code ts}, en hora real, puede ser null) convertido a la
     * hora del control, con las salvedades de {@link RelojControl#instanteDeLectura}. Se descarta
     * (y se cuenta en {@code /diagnostico}) si su {@code ts} es más viejo que la vigencia (D8); el
     * adaptador descarta además la que es más vieja que la última. Un error se propaga: lo atrapa
     * {@code LecturaMqttListener.messageArrived}, el único try/catch del camino (Paho cierra la
     * conexión si el callback lanza).
     *
     * <p>{@code synchronized} como {@link #iniciar}: así una lectura sellada con el reloj viejo no
     * se procesa después de que INICIAR re-sincronizó el reloj (lo dejaría en la línea de tiempo
     * vieja). No hay riesgo de deadlock: el adaptador nunca toma el candado de esta clase.
     */
    public synchronized void alRecibirLectura(
            String idHabitacion, double temperaturaC, Instant ts) {
        if (!enEjecucion()) {
            return;
        }
        Optional<Instant> instante = reloj.instanteDeLectura(ts, vigenciaLectura, relojReal);
        if (instante.isEmpty()) {
            adaptador.descartarLectura(idHabitacion, MotivoDescarte.TS_VENCIDO); // D8
            return;
        }
        adaptador.procesar(new Estimulo.NuevaLectura(idHabitacion, temperaturaC, instante.get()));
    }

    /** PUT /sitio (o el ABM) aplicó un inventario nuevo: el core lo adopta en caliente. */
    @EventListener
    public synchronized void aplicarInventario(InventarioAplicado evento) {
        configurarCore(evento.inventario());
    }

    /** La zona del sitio para leer la tarifa: siempre la misma, esté o no en ejecución. */
    public ZoneId zona() {
        return zona;
    }

    private void configurarCore(Inventario inv) {
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

    /**
     * Corre en el temporizador. El try/catch es necesario: una excepción que escapa de una tarea de
     * {@code scheduleWithFixedDelay} cancela todas sus ejecuciones siguientes sin avisar. Es {@code
     * synchronized} por lo mismo que {@link #alRecibirLectura}.
     */
    synchronized void tick() {
        if (!enEjecucion()) {
            return;
        }
        try {
            adaptador.procesar(new Estimulo.Tick(reloj.ahora(relojReal)));
        } catch (RuntimeException e) {
            log.error("Error en el tick del control: {}", e.getMessage());
        }
    }

    void reconciliar() {
        if (!enEjecucion()) {
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
