package uy.edu.utec.iiss.engine.rest.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import uy.edu.utec.iiss.engine.control.ControlService;
import uy.edu.utec.iiss.engine.control.EstadoControl;
import uy.edu.utec.iiss.engine.rest.error.SolicitudInvalidaException;
import uy.edu.utec.iiss.engine.util.Instantes;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recurso control, estándar v2 §5.2.
 *   GET  /control           -> 200 estado del control
 *   POST /control/comandos  -> 200 INICIAR (fechaHora obligatoria, factorTiempo opcional) / DETENER
 *
 * El cuerpo del comando se valida a mano (JsonNode) para responder 400 con un
 * mensaje claro ante cada caso del estándar: acción desconocida, INICIAR sin
 * fechaHora, fechaHora no ISO-8601, factorTiempo que no es un número &gt; 0.
 * Se soporta aceleración, así que cualquier factor &gt; 0 es válido.
 */
@RestController
@RequestMapping("/control")
public class ControlController {

    private final ControlService control;

    public ControlController(ControlService control) {
        this.control = control;
    }

    @GetMapping
    public Map<String, Object> estado() {
        return cuerpo(control.estado());
    }

    @PostMapping("/comandos")
    public Map<String, Object> comando(@RequestBody JsonNode cuerpo) {
        if (cuerpo == null || !cuerpo.isObject()) {
            throw new SolicitudInvalidaException("el cuerpo debe ser un objeto JSON con 'accion'");
        }
        String accion = cuerpo.path("accion").isTextual() ? cuerpo.path("accion").asText() : null;
        if ("DETENER".equals(accion)) {
            return cuerpo(control.detener());
        }
        if (!"INICIAR".equals(accion)) {
            throw new SolicitudInvalidaException("accion debe ser INICIAR o DETENER (es " + cuerpo.path("accion") + ")");
        }
        JsonNode fecha = cuerpo.path("fechaHora");
        if (!fecha.isTextual()) {
            throw new SolicitudInvalidaException("INICIAR requiere fechaHora (ISO-8601)");
        }
        double factor = factorTiempo(cuerpo.get("factorTiempo"));
        try {
            OffsetDateTime f = OffsetDateTime.parse(fecha.asText());
            return cuerpo(control.iniciar(f.toInstant(), f.getOffset(), factor));
        } catch (DateTimeParseException conZona) {
            try {
                ZoneId zona = ZoneId.systemDefault();
                return cuerpo(control.iniciar(LocalDateTime.parse(fecha.asText()).atZone(zona).toInstant(), zona, factor));
            } catch (DateTimeParseException sinZona) {
                throw new SolicitudInvalidaException("fechaHora no es ISO-8601: " + fecha.asText());
            }
        }
    }

    private static double factorTiempo(JsonNode nodo) {
        if (nodo == null || nodo.isNull()) {
            return 1.0;
        }
        if (!nodo.isNumber() || !(nodo.asDouble() > 0) || Double.isInfinite(nodo.asDouble())) {
            throw new SolicitudInvalidaException("factorTiempo debe ser un número > 0 (es " + nodo + ")");
        }
        return nodo.asDouble();
    }

    /** Los instantes van con milisegundos y los campos en null se incluyen (detenido). */
    private static Map<String, Object> cuerpo(EstadoControl e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estado", e.estado());
        m.put("fechaHora", Instantes.iso(e.fechaHora()));
        m.put("factorTiempo", e.factorTiempo());
        m.put("iniciadoEn", Instantes.iso(e.iniciadoEn()));
        return m;
    }
}
