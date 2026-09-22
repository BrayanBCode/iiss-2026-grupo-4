package api.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.dto.HabitacionPatchRequest;
import api.dto.HabitacionRequest;
import api.dto.HabitacionResponse;
import api.dto.ReporteConsistencia;
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

    /**
     * Comando "validar consistencia" (letra de la Iteración 3): chequea
     * duplicados de idTermostato/idSwitch entre TODAS las habitaciones.
     * No chequea "faltantes" porque ambas columnas son NOT NULL en la
     * base — un valor null/vacío ya es imposible de insertar.
     */
    @Transactional(readOnly = true)
    public ReporteConsistencia validarConsistencia() {
        List<Habitacion> todas = repository.findAll();

        List<String> termostatosDuplicados = idsRepetidos(todas, Habitacion::getIdTermostato);
        List<String> switchesDuplicados = idsRepetidos(todas, Habitacion::getIdSwitch);

        boolean consistente = termostatosDuplicados.isEmpty() && switchesDuplicados.isEmpty();
        return new ReporteConsistencia(consistente, termostatosDuplicados, switchesDuplicados);
    }

    /** Devuelve los valores que aparecen más de una vez, según el extractor dado. */
    private List<String> idsRepetidos(List<Habitacion> habitaciones, Function<Habitacion, String> extractor) {
        Map<String, Integer> conteos = new HashMap<>();
        for (Habitacion h : habitaciones) {
            conteos.merge(extractor.apply(h), 1, Integer::sum);
        }
        List<String> repetidos = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : conteos.entrySet()) {
            if (entry.getValue() > 1) {
                repetidos.add(entry.getKey());
            }
        }
        return repetidos;
    }
}