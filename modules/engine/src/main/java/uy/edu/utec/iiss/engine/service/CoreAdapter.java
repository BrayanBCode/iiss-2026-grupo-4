package uy.edu.utec.iiss.engine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uy.edu.utec.iiss.core.cliente.ICoreClient;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.Sitio;
import uy.edu.utec.iiss.engine.client.AccionadorSwitch;
import uy.edu.utec.iiss.engine.model.AccionSwitch;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adaptador entre el engine y el core: la única clase del engine que habla con
 * el core. Hace cuatro cosas:
 *
 *  1. Traduce lo que llega (lectura, tick, configuración) a llamadas al core;
 *     el ts llega como long (epoch en milisegundos) y se deriva a Instant acá.
 *  2. Serializa las llamadas ({@code synchronized}): el core no es seguro para
 *     varios hilos y le llegan eventos de MQTT, del temporizador y de REST.
 *  3. Filtra las decisiones: el core devuelve ON/OFF de TODAS las habitaciones
 *     en cada estímulo; acá solo se comanda al switch cuando algo cambió.
 *  4. Comanda el switch de cada habitación. Si un comando falla no se anota
 *     como enviado, así se reintenta en la próxima decisión, y un switch caído
 *     no impide comandar a los demás.
 *
 * Es la misma id de habitación en el core y acá (el nombre de la habitación).
 *
 * Limitación conocida: se comanda al switch dentro del bloqueo, así que un
 * switch lento demora a los demás eventos hasta que venza el timeout REST.
 */
@Component
public class CoreAdapter {

    private static final Logger log = LoggerFactory.getLogger(CoreAdapter.class);

    /** Una habitación tal como la necesita el core, más el switch que la calefacciona. */
    public record HabitacionControlada(String id, double temperaturaEsperada, double potenciaKW, String idSwitch) {
    }

    private final ICoreClient core;
    private final AccionadorSwitch switches;
    private final Map<String, String> switchPorHabitacion = new HashMap<>();
    private final Map<String, AccionSwitch> ultimoEnviado = new HashMap<>();

    public CoreAdapter(ICoreClient core, AccionadorSwitch switches) {
        this.core = core;
        this.switches = switches;
    }

    /** Define (o redefine) el sitio y las habitaciones; puede cambiar decisiones, que se aplican al instante. */
    public synchronized void configurar(Sitio sitio, List<HabitacionControlada> habitaciones) {
        Map<String, String> nuevoMapa = new HashMap<>();
        for (HabitacionControlada h : habitaciones) {
            nuevoMapa.put(h.id(), h.idSwitch());
        }
        // Lo enviado a una habitación que ya no existe, o que cambió de switch, deja de valer.
        ultimoEnviado.keySet().removeIf(id -> !Objects.equals(nuevoMapa.get(id), switchPorHabitacion.get(id)));
        switchPorHabitacion.clear();
        switchPorHabitacion.putAll(nuevoMapa);

        ConfiguracionHabitacion[] configuracion = habitaciones.stream()
                .map(h -> new ConfiguracionHabitacion(h.id(), h.id(), h.temperaturaEsperada(), h.potenciaKW()))
                .toArray(ConfiguracionHabitacion[]::new);
        aplicar(core.actualizarSitio(sitio, configuracion));
    }

    /** Llegó una medición de temperatura; epochMili = milisegundos desde 1970 (UTC), tal como viene del termostato. */
    public synchronized void alLlegarLectura(String idHabitacion, double temperaturaC, long epochMili) {
        aplicar(core.nuevaTemperatura(idHabitacion, temperaturaC, Instant.ofEpochMilli(epochMili)));
    }

    /** Pasó el tiempo (lo dispara el temporizador del engine); permite cortar y restituir en los cambios de franja. */
    public synchronized void alTick(long epochMili) {
        aplicar(core.tick(Instant.ofEpochMilli(epochMili)));
    }

    private void aplicar(List<Decision> decisiones) {
        for (Decision d : decisiones) {
            String idSwitch = switchPorHabitacion.get(d.idHabitacion());
            if (idSwitch == null) {
                log.warn("El core decidió sobre la habitación '{}' pero no tiene switch asociado; se ignora.", d.idHabitacion());
                continue;
            }
            AccionSwitch accion = traducir(d.accion());
            if (accion == ultimoEnviado.get(d.idHabitacion())) {
                continue;   // ya está así: no se repite el comando
            }
            try {
                switches.accionar(idSwitch, accion);
                ultimoEnviado.put(d.idHabitacion(), accion);
                log.info("Switch '{}' (habitación '{}') -> {}", idSwitch, d.idHabitacion(), accion);
            } catch (RuntimeException e) {
                log.error("No se pudo enviar {} al switch '{}' (habitación '{}'); se reintenta en la próxima decisión: {}",
                        accion, idSwitch, d.idHabitacion(), e.getMessage());
            }
        }
    }

    private static AccionSwitch traducir(Accion accion) {
        return switch (accion) {
            case ON -> AccionSwitch.ON;
            case OFF -> AccionSwitch.OFF;
        };
    }
}