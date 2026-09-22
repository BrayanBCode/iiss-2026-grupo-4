package api.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.dto.HabitacionPatchRequest;
import api.dto.HabitacionRequest;
import api.dto.HabitacionResponse;
import api.exception.HabitacionNoEncontradaException;
import api.model.Habitacion;
import api.repository.HabitacionRepository;

@Service
public class HabitacionService {

    private final HabitacionRepository repository;

    public HabitacionService(HabitacionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<HabitacionResponse> listar() {
        return repository.findAll().stream()
                .map(HabitacionResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public HabitacionResponse obtener(Long id) {
        return HabitacionResponse.desde(buscarOFallar(id));
    }

    @Transactional
    public HabitacionResponse crear(HabitacionRequest request) {
        Habitacion habitacion = new Habitacion(
                request.getNombre(),
                request.getTemperaturaEsperada(),
                request.getIdTermostato(),
                request.getIdSwitch());
        return HabitacionResponse.desde(repository.save(habitacion));
    }

    @Transactional
    public HabitacionResponse actualizar(Long id, HabitacionRequest request) {
        Habitacion habitacion = buscarOFallar(id);
        habitacion.setNombre(request.getNombre());
        habitacion.setTemperaturaEsperada(request.getTemperaturaEsperada());
        habitacion.setIdTermostato(request.getIdTermostato());
        habitacion.setIdSwitch(request.getIdSwitch());
        return HabitacionResponse.desde(repository.save(habitacion));
    }

    @Transactional
    public HabitacionResponse actualizarParcial(Long id, HabitacionPatchRequest request) {
        Habitacion habitacion = buscarOFallar(id);
        if (request.getNombre() != null) {
            habitacion.setNombre(request.getNombre());
        }
        if (request.getTemperaturaEsperada() != null) {
            habitacion.setTemperaturaEsperada(request.getTemperaturaEsperada());
        }
        if (request.getIdTermostato() != null) {
            habitacion.setIdTermostato(request.getIdTermostato());
        }
        if (request.getIdSwitch() != null) {
            habitacion.setIdSwitch(request.getIdSwitch());
        }
        return HabitacionResponse.desde(repository.save(habitacion));
    }

    @Transactional
    public void eliminar(Long id) {
        Habitacion habitacion = buscarOFallar(id);
        repository.delete(habitacion);
    }

    private Habitacion buscarOFallar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new HabitacionNoEncontradaException(id));
    }
}