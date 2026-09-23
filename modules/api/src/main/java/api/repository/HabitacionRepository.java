package api.repository;

import java.util.Optional;

import api.model.Habitacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HabitacionRepository extends JpaRepository<Habitacion, Long> {

    /**
     * Usado por el Controlador: cada lectura MQTT llega con el id del
     * termostato (prefijo del tópico), no con el id de la habitación, así
     * que hay que resolverla antes de decidir la acción del switch.
     */
    Optional<Habitacion> findByIdTermostato(String idTermostato);
}