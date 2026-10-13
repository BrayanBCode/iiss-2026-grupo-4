package uy.edu.utec.iiss.engine.rest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import uy.edu.utec.iiss.engine.inventario.Inventario;
import uy.edu.utec.iiss.engine.inventario.InventarioService;
import uy.edu.utec.iiss.engine.rest.error.SolicitudInvalidaException;

import java.net.URI;
import java.util.List;

/**
 * ABM de habitaciones (fuera del estándar, §5: "queda a criterio de cada
 * implementación"). Opera sobre el MISMO inventario de {@code /sitio}, con las
 * mismas reglas del §1 y la misma persistencia; cada cambio se aplica en caliente.
 * El formato de cada habitación es el del §1.
 *
 *   GET    /habitaciones        -> 200 (404 si no hay sitio)
 *   GET    /habitaciones/{id}   -> 200 / 404
 *   POST   /habitaciones        -> 201 + Location / 400 / 409 id duplicado
 *   PUT    /habitaciones/{id}   -> 200 / 400 / 404
 *   DELETE /habitaciones/{id}   -> 204 / 404
 */
@RestController
@RequestMapping("/habitaciones")
public class HabitacionController {

    private final InventarioService inventario;

    public HabitacionController(InventarioService inventario) {
        this.inventario = inventario;
    }

    @GetMapping
    public List<Inventario.Habitacion> listar() {
        return inventario.habitaciones();
    }

    @GetMapping("/{id}")
    public Inventario.Habitacion consultar(@PathVariable("id") String id) {
        return inventario.habitacion(id);
    }

    @PostMapping
    public ResponseEntity<Inventario.Habitacion> crear(@RequestBody Inventario.Habitacion nueva) {
        Inventario.Habitacion creada = inventario.agregar(nueva);
        return ResponseEntity.created(URI.create("/habitaciones/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    public Inventario.Habitacion modificar(@PathVariable("id") String id, @RequestBody Inventario.Habitacion datos) {
        if (datos.id() != null && !datos.id().equals(id)) {
            throw new SolicitudInvalidaException("el id del cuerpo (" + datos.id() + ") no coincide con el de la URL (" + id + ")");
        }
        return inventario.modificar(id, datos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable("id") String id) {
        inventario.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
