package api.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import api.dto.HabitacionPatchRequest;
import api.dto.HabitacionRequest;
import api.dto.HabitacionResponse;
import api.service.HabitacionService;

/**
 * CRUD de habitaciones (Nivel 2 de Richardson): el recurso es /habitaciones,
 * identificado por id, y cada verbo HTTP tiene la semántica estándar:
 *   GET     /habitaciones      -> listar               (200)
 *   GET     /habitaciones/{id} -> consultar por id      (200 / 404)
 *   POST    /habitaciones      -> crear                 (201 + Location)
 *   PUT     /habitaciones/{id} -> modificar completo     (200 / 404)
 *   PATCH   /habitaciones/{id} -> modificar parcial       (200 / 404)
 *   DELETE  /habitaciones/{id} -> eliminar                (204 / 404)
 */
@RestController
@RequestMapping("/habitaciones")
public class HabitacionController {

    private final HabitacionService service;

    public HabitacionController(HabitacionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<HabitacionResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HabitacionResponse> consultar(@PathVariable Long id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PostMapping
    public ResponseEntity<HabitacionResponse> crear(@Valid @RequestBody HabitacionRequest request) {
        HabitacionResponse creada = service.crear(request);
        URI ubicacion = URI.create("/habitaciones/" + creada.getId());
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<HabitacionResponse> actualizar(
            @PathVariable Long id, @Valid @RequestBody HabitacionRequest request) {
        return ResponseEntity.ok(service.actualizar(id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<HabitacionResponse> actualizarParcial(
            @PathVariable Long id, @Valid @RequestBody HabitacionPatchRequest request) {
        return ResponseEntity.ok(service.actualizarParcial(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}