package api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import api.controlador.ControladorService;
import api.dto.ControladorEstadoResponse;

/**
 * Comandos (tasks) sobre el Controlador (Iteración 3): no son CRUD sobre
 * un recurso, son acciones que disparan comportamiento (arrancar/detener
 * la lógica de termostato simple), por eso se modelan con POST bajo
 * /controlador y no como un recurso con GET/PUT (Nivel 2 de Richardson).
 *
 *   POST /controlador/iniciar -> arranca la suscripción MQTT y la lógica
 *                                 de control (200, o 409 si ya corría)
 *   POST /controlador/parar   -> la detiene (200, o 409 si no corría)
 *   GET  /controlador/estado  -> consulta si está corriendo o no (200)
 */
@RestController
@RequestMapping("/controlador")
public class ControladorController {

    private final ControladorService controladorService;

    public ControladorController(ControladorService controladorService) {
        this.controladorService = controladorService;
    }

    @PostMapping("/iniciar")
    public ResponseEntity<ControladorEstadoResponse> iniciar() {
        controladorService.iniciar();
        return ResponseEntity.ok(new ControladorEstadoResponse(true));
    }

    @PostMapping("/parar")
    public ResponseEntity<ControladorEstadoResponse> parar() {
        controladorService.parar();
        return ResponseEntity.ok(new ControladorEstadoResponse(false));
    }

    @GetMapping("/estado")
    public ResponseEntity<ControladorEstadoResponse> estado() {
        return ResponseEntity.ok(new ControladorEstadoResponse(controladorService.estaCorriendo()));
    }
}
