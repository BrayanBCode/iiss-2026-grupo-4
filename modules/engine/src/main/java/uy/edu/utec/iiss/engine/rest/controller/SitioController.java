package uy.edu.utec.iiss.engine.rest.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.utec.iiss.engine.inventario.Inventario;
import uy.edu.utec.iiss.engine.inventario.InventarioService;
import uy.edu.utec.iiss.engine.inventario.NoEncontradoException;

/**
 * Recurso sitio, estándar v2 §5.1. GET /sitio -> 200 el inventario persistido y en uso (404 si
 * todavía no hay) PUT /sitio -> 200 reemplaza el inventario completo (400 con el detalle si es
 * inválido)
 */
@RestController
@RequestMapping("/sitio")
public class SitioController {

    private final InventarioService inventario;

    public SitioController(InventarioService inventario) {
        this.inventario = inventario;
    }

    @GetMapping
    public Inventario obtener() {
        return inventario
                .actual()
                .orElseThrow(
                        () ->
                                new NoEncontradoException(
                                        "Todavía no se cargó ningún sitio (PUT /sitio)"));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> reemplazar(@RequestBody Inventario nuevo) {
        Inventario aplicado = inventario.reemplazar(nuevo);
        return ResponseEntity.ok(
                Map.of(
                        "sitio",
                        aplicado.sitio().id(),
                        "habitaciones",
                        aplicado.habitaciones().size()));
    }
}
