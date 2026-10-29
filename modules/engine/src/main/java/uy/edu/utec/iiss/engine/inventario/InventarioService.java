package uy.edu.utec.iiss.engine.inventario;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * El inventario del sitio en uso (estándar v2, §5.1).
 *
 * <ul>
 *   <li><b>Atómico</b>: se valida entero; si es inválido se rechaza y queda el anterior.
 *   <li><b>Persistente</b>: se graba ANTES de ponerlo en uso; si no se pudo grabar, tampoco se usa.
 *   <li><b>En caliente</b>: al aplicarse se publica {@link InventarioAplicado}; el control y el
 *       listener MQTT lo adoptan sin reiniciar, esté o no en ejecución.
 * </ul>
 *
 * Además ofrece un ABM de habitaciones (fuera del estándar) que opera sobre el mismo inventario y
 * pasa por las mismas reglas.
 */
@Service
public class InventarioService {

    private static final Logger log = LoggerFactory.getLogger(InventarioService.class);

    private final RepositorioInventario repositorio;
    private final ApplicationEventPublisher eventos;
    private volatile Inventario actual;

    public InventarioService(RepositorioInventario repositorio, ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.eventos = eventos;
    }

    /** Al arrancar: si hay un inventario guardado y sigue siendo válido, se pone en uso. */
    public synchronized void cargarGuardado() {
        repositorio
                .cargar()
                .ifPresent(
                        inv -> {
                            List<String> errores = ValidadorInventario.validar(inv);
                            if (!errores.isEmpty()) {
                                log.error(
                                        "El inventario guardado en {} no es válido ({}); se arranca"
                                                + " sin sitio.",
                                        repositorio.archivo(),
                                        errores);
                                return;
                            }
                            actual = inv;
                            log.info(
                                    "Inventario '{}' recuperado de {} ({} habitaciones).",
                                    inv.sitio().id(),
                                    repositorio.archivo(),
                                    inv.habitaciones().size());
                            eventos.publishEvent(new InventarioAplicado(inv));
                        });
    }

    public Optional<Inventario> actual() {
        return Optional.ofNullable(actual);
    }

    /**
     * PUT /sitio. @throws InventarioInvalidoException si no cumple el §1 (y entonces no cambia
     * nada).
     */
    public synchronized Inventario reemplazar(Inventario nuevo) {
        List<String> errores = ValidadorInventario.validar(nuevo);
        if (!errores.isEmpty()) {
            throw new InventarioInvalidoException(errores);
        }
        repositorio.guardar(nuevo);
        actual = nuevo;
        log.info(
                "Inventario '{}' aplicado ({} habitaciones).",
                nuevo.sitio().id(),
                nuevo.habitaciones().size());
        eventos.publishEvent(new InventarioAplicado(nuevo));
        return nuevo;
    }

    // ------------------------------------------------------------------
    // ABM de habitaciones (fuera del estándar)
    // ------------------------------------------------------------------

    public List<Inventario.Habitacion> habitaciones() {
        return requerido().habitaciones();
    }

    public Inventario.Habitacion habitacion(String id) {
        return requerido().habitaciones().stream()
                .filter(h -> h.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoEncontradoException("No existe la habitación " + id));
    }

    public synchronized Inventario.Habitacion agregar(Inventario.Habitacion nueva) {
        Inventario inv = requerido();
        if (nueva != null
                && nueva.id() != null
                && inv.habitaciones().stream().anyMatch(h -> h.id().equals(nueva.id()))) {
            throw new ConflictoException("Ya existe la habitación " + nueva.id());
        }
        List<Inventario.Habitacion> lista = new ArrayList<>(inv.habitaciones());
        lista.add(nueva);
        reemplazar(inv.conHabitaciones(lista));
        return nueva;
    }

    public synchronized Inventario.Habitacion modificar(String id, Inventario.Habitacion datos) {
        Inventario inv = requerido();
        habitacion(id);
        Inventario.Habitacion conId =
                new Inventario.Habitacion(
                        id,
                        datos.nombre(),
                        datos.temperaturaEsperada(),
                        datos.potenciaKW(),
                        datos.idTermostato(),
                        datos.topicTermostato(),
                        datos.idSwitch(),
                        datos.urlSwitch());
        List<Inventario.Habitacion> lista =
                inv.habitaciones().stream().map(h -> h.id().equals(id) ? conId : h).toList();
        reemplazar(inv.conHabitaciones(lista));
        return conId;
    }

    public synchronized void eliminar(String id) {
        Inventario inv = requerido();
        habitacion(id);
        reemplazar(
                inv.conHabitaciones(
                        inv.habitaciones().stream().filter(h -> !h.id().equals(id)).toList()));
    }

    private Inventario requerido() {
        Inventario inv = actual;
        if (inv == null) {
            throw new NoEncontradoException("Todavía no se cargó ningún sitio (PUT /sitio)");
        }
        return inv;
    }
}
