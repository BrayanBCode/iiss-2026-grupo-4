package uy.edu.utec.iiss.engine.service;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uy.edu.utec.iiss.engine.client.SwitchStubClient;
import uy.edu.utec.iiss.engine.exception.ControladorNoIniciadoException;
import uy.edu.utec.iiss.engine.exception.ControladorYaIniciadoException;
import uy.edu.utec.iiss.engine.model.AccionSwitch;
import uy.edu.utec.iiss.engine.model.Habitacion;
import uy.edu.utec.iiss.engine.model.Lectura;

/**
 * El "Controlador" pedido en la Iteración 3: se comporta como un termostato
 * simple (el de un calefón). Mientras está iniciado, por cada lectura de
 * temperatura que le entrega {@link uy.edu.utec.iiss.engine.mqtt.LecturaMqttListener}:
 *
 *   - la compara con la temperaturaEsperada de la habitación de la lectura;
 *   - si la medida está por encima de la esperada, apaga su switch (OFF);
 *   - si está por debajo, lo prende (ON);
 *   - acciona ese switch contra el stub, vía {@link SwitchStubClient}.
 *
 * Ya no maneja MQTT: la conexión y la persistencia de las lecturas son del
 * listener. iniciar()/parar() (POST /controlador/iniciar y /controlador/parar)
 * solo activan o desactivan la reacción a las lecturas; las lecturas se siguen
 * guardando igual con el Controlador parado.
 */
@Service
public class ControladorService {

    private static final Logger log = LoggerFactory.getLogger(ControladorService.class);

    private final SwitchStubClient switchStubClient;
    private final AtomicBoolean corriendo = new AtomicBoolean(false);

    public ControladorService(SwitchStubClient switchStubClient) {
        this.switchStubClient = switchStubClient;
    }

    /** Comando "iniciar controlador": empieza a reaccionar a las lecturas. */
    public void iniciar() {
        if (!corriendo.compareAndSet(false, true)) {
            throw new ControladorYaIniciadoException();
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

    /**
     * Llamado por el listener MQTT con cada lectura ya guardada. Si el
     * Controlador no está iniciado la ignora. Nunca lanza: un fallo del switch
     * se registra y no debe afectar al hilo que recibe los mensajes.
     */
    public void alRecibirLectura(Lectura lectura) {
        if (!corriendo.get()) {
            return;
        }
        try {
            Habitacion habitacion = lectura.getHabitacion();
            Double esperada = habitacion.getTemperaturaEsperada();
            if (esperada == null) {
                log.warn("Controlador: la habitación '{}' no tiene temperatura esperada configurada, se ignora la lectura.",
                        habitacion.getNombre());
                return;
            }

            double medida = lectura.getTemperaturaC();
            // Termostato simple: por encima de lo esperado, apaga; por debajo, prende.
            AccionSwitch accion = medida > esperada ? AccionSwitch.OFF : AccionSwitch.ON;

            log.info("Controlador: habitación '{}' medida={}ºC esperada={}ºC -> switch '{}' {}",
                    habitacion.getNombre(), medida, esperada, habitacion.getIdSwitch(), accion);

            switchStubClient.accionar(habitacion.getIdSwitch(), accion);
        } catch (Exception e) {
            log.error("Controlador: error al procesar la lectura {}: {}", lectura.getId(), e.getMessage());
        }
    }
}
