package uy.edu.utec.iiss.core.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uy.edu.utec.iiss.core.client.SwitchStubClient;
import uy.edu.utec.iiss.core.exception.HabitacionNoEncontradaException;
import uy.edu.utec.iiss.core.model.AccionSwitch;
import uy.edu.utec.iiss.core.model.Habitacion;
import uy.edu.utec.iiss.core.model.ReporteConsistencia;
import uy.edu.utec.iiss.core.repository.HabitacionRepository;

@Service
public class HabitacionService {

    private final HabitacionRepository repository;
    private final SwitchStubClient switchStubClient;

    public HabitacionService(HabitacionRepository repository, SwitchStubClient switchStubClient) {
        this.repository = repository;
        this.switchStubClient = switchStubClient;
    }

    @Transactional(readOnly = true)
    public List<Habitacion> listar() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Habitacion obtener(Long id) {
        return buscarOFallar(id);
    }

    @Transactional
    public Habitacion crear(DatosHabitacion datos) {
        Habitacion habitacion = new Habitacion(
                datos.nombre(),
                datos.temperaturaEsperada(),
                datos.idTermostato(),
                datos.idSwitch());
        return repository.save(habitacion);
    }

    @Transactional
    public Habitacion actualizar(Long id, DatosHabitacion datos) {
        Habitacion habitacion = buscarOFallar(id);
        habitacion.setNombre(datos.nombre());
        habitacion.setTemperaturaEsperada(datos.temperaturaEsperada());
        habitacion.setIdTermostato(datos.idTermostato());
        habitacion.setIdSwitch(datos.idSwitch());
        return repository.save(habitacion);
    }

    @Transactional
    public Habitacion actualizarParcial(Long id, DatosHabitacion datos) {
        Habitacion habitacion = buscarOFallar(id);
        if (datos.nombre() != null) {
            habitacion.setNombre(datos.nombre());
        }
        if (datos.temperaturaEsperada() != null) {
            habitacion.setTemperaturaEsperada(datos.temperaturaEsperada());
        }
        if (datos.idTermostato() != null) {
            habitacion.setIdTermostato(datos.idTermostato());
        }
        if (datos.idSwitch() != null) {
            habitacion.setIdSwitch(datos.idSwitch());
        }
        return repository.save(habitacion);
    }

    @Transactional
    public void eliminar(Long id) {
        Habitacion habitacion = buscarOFallar(id);
        repository.delete(habitacion);
    }

    /**
     * Comando manual "accionar switch" (letra de la Iteración 3): busca la
     * habitación, toma su idSwitch (identificador, no la URL concreta del
     * dispositivo -- eso lo resuelve SwitchStubClient contra la config del
     * sitio) y lo acciona contra el stub. Es de solo lectura para la base
     * (@Transactional readOnly): no modifica la habitación, solo consulta
     * cuál es su switch. Devuelve la habitación accionada; armar la
     * respuesta HTTP es tarea de la capa rest.
     */
    @Transactional(readOnly = true)
    public Habitacion accionarSwitch(Long id, AccionSwitch accion) {
        Habitacion habitacion = buscarOFallar(id);
        switchStubClient.accionar(habitacion.getIdSwitch(), accion);
        return habitacion;
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