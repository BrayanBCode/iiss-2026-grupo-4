package uy.edu.utec.iiss.engine.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uy.edu.utec.iiss.engine.model.Lectura;
import uy.edu.utec.iiss.engine.repository.HabitacionRepository;
import uy.edu.utec.iiss.engine.repository.LecturaRepository;

/**
 * Persistencia de las lecturas de temperatura que llegan por MQTT (lo que
 * hacía el módulo subscriber con LecturaDAO + HabitacionDAO).
 */
@Service
public class LecturaService {

    private final HabitacionRepository habitacionRepository;
    private final LecturaRepository lecturaRepository;

    public LecturaService(HabitacionRepository habitacionRepository,
                          LecturaRepository lecturaRepository) {
        this.habitacionRepository = habitacionRepository;
        this.lecturaRepository = lecturaRepository;
    }

    /**
     * Resuelve la habitación dueña del termostato y guarda la lectura.
     * Devuelve Optional.empty() (sin guardar nada) si el termostato no está
     * asignado a ninguna habitación: un dispositivo desconocido no se inventa
     * a qué habitación pertenece, se descarta.
     *
     * La Lectura devuelta trae su Habitacion ya cargada (es la misma
     * instancia que se resolvió acá), así que se puede usar fuera de la
     * transacción sin disparar carga perezosa.
     */
    @Transactional
    public Optional<Lectura> registrar(String idTermostato, double temperaturaC,
                                       double temperaturaF, long epochMili) {
        return habitacionRepository.findByIdTermostato(idTermostato)
                .map(habitacion -> lecturaRepository.save(
                        new Lectura(habitacion, temperaturaC, temperaturaF, epochMili)));
    }
}
