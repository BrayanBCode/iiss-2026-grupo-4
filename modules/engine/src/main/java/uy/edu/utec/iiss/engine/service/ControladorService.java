package uy.edu.utec.iiss.engine.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uy.edu.utec.iiss.engine.exception.ControladorNoIniciadoException;
import uy.edu.utec.iiss.engine.exception.ControladorYaIniciadoException;
import uy.edu.utec.iiss.engine.model.Habitacion;
import uy.edu.utec.iiss.engine.model.Lectura;
import uy.edu.utec.iiss.engine.repository.HabitacionRepository;
import uy.edu.utec.iiss.engine.service.CoreAdapter.HabitacionControlada;

/**
 * El "Controlador": decide con el core qué habitaciones calefaccionar. Mientras
 * está iniciado, por cada lectura que le entrega
 * {@link uy.edu.utec.iiss.engine.mqtt.LecturaMqttListener} se la pasa al
 * {@link CoreAdapter}, que consulta al core y comanda los switches que cambian.
 *
 * Al iniciar carga en el core las habitaciones de la base y los datos del sitio
 * ({@link SitioConfig}). Si las habitaciones cambian con el controlador andando,
 * hay que llamar a {@link #sincronizarConfiguracion()} (todavía no lo hace
 * HabitacionService).
 *
 * iniciar()/parar() (POST /controlador/iniciar y /controlador/parar) activan o
 * desactivan la reacción a las lecturas; las lecturas se siguen guardando igual
 * con el Controlador parado.
 */
@Service
public class ControladorService {

    private static final Logger log = LoggerFactory.getLogger(ControladorService.class);

    private final CoreAdapter adaptador;
    private final HabitacionRepository habitacionRepository;
    private final SitioConfig sitioConfig;
    private final AtomicBoolean corriendo = new AtomicBoolean(false);

    public ControladorService(CoreAdapter adaptador,
                              HabitacionRepository habitacionRepository,
                              SitioConfig sitioConfig) {
        this.adaptador = adaptador;
        this.habitacionRepository = habitacionRepository;
        this.sitioConfig = sitioConfig;
    }

    /** Comando "iniciar controlador": carga la configuración en el core y empieza a reaccionar a las lecturas. */
    public void iniciar() {
        if (!corriendo.compareAndSet(false, true)) {
            throw new ControladorYaIniciadoException();
        }
        try {
            sincronizarConfiguracion();
        } catch (RuntimeException e) {
            corriendo.set(false);   // no quedó iniciado
            throw e;
        }
        log.info("Controlador iniciado.");
    }

    /** Comando "parar controlador": deja de reaccionar a las lecturas. */
    public void parar() {
        if (!corriendo.compareAndSet(true, false)) {
            throw new ControladorNoIniciadoException();
        }
        log.info("Controlador detenido.");
    }

    public boolean estaCorriendo() {
        return corriendo.get();
    }

    /** Vuelve a cargar en el core las habitaciones de la base y los datos del sitio. */
    public void sincronizarConfiguracion() {
        List<HabitacionControlada> controladas = new ArrayList<>();
        for (Habitacion h : habitacionRepository.findAll()) {
            if (h.getTemperaturaEsperada() == null) {
                log.warn("Controlador: la habitación '{}' no tiene temperatura esperada configurada, no se controla.",
                        h.getNombre());
                continue;
            }
            controladas.add(new HabitacionControlada(h.getNombre(), h.getTemperaturaEsperada(),
                    sitioConfig.potenciaHabitacionKW(), h.getIdSwitch()));
        }
        adaptador.configurar(sitioConfig.sitio(), controladas);
    }

    /**
     * Llamado por el listener MQTT con cada lectura ya guardada. Si el
     * Controlador no está iniciado la ignora. Nunca lanza: un fallo se registra
     * y no debe afectar al hilo que recibe los mensajes.
     */
    public void alRecibirLectura(Lectura lectura) {
        if (!corriendo.get()) {
            return;
        }
        try {
            Habitacion habitacion = lectura.getHabitacion();
            adaptador.alLlegarLectura(habitacion.getNombre(), lectura.getTemperaturaC(), lectura.getEpochMili());
        } catch (Exception e) {
            log.error("Controlador: error al procesar la lectura {}: {}", lectura.getId(), e.getMessage());
        }
    }
}