package uy.edu.utec.iiss.engine.rest.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uy.edu.utec.iiss.engine.adaptador.CoreAdapter;
import uy.edu.utec.iiss.engine.control.ControlService;
import uy.edu.utec.iiss.engine.mqtt.LecturaMqttListener;
import uy.edu.utec.iiss.engine.registro.RegistroEntorno;
import uy.edu.utec.iiss.engine.util.Instantes;

/**
 * Diagnóstico (fuera del estándar): qué decidió el core para cada habitación y por qué, qué se sabe
 * de cada switch, y el estado de las conexiones. GET /diagnostico -> 200
 */
@RestController
public class DiagnosticoController {

    private final CoreAdapter adaptador;
    private final ControlService control;
    private final LecturaMqttListener mqtt;
    private final RegistroEntorno registro;

    public DiagnosticoController(
            CoreAdapter adaptador,
            ControlService control,
            LecturaMqttListener mqtt,
            RegistroEntorno registro) {
        this.adaptador = adaptador;
        this.control = control;
        this.mqtt = mqtt;
        this.registro = registro;
    }

    @GetMapping("/diagnostico")
    public Map<String, Object> diagnostico() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("control", control.estado().estado());
        m.put("mqttConectado", mqtt.conectado());
        m.put("registradoAnteEntorno", registro.registrado());
        List<Map<String, Object>> habitaciones =
                adaptador.estados().stream()
                        .map(
                                e -> {
                                    Map<String, Object> h = new LinkedHashMap<>();
                                    h.put("id", e.idHabitacion());
                                    h.put("temperaturaActual", e.temperaturaActual());
                                    h.put("ultimaLectura", Instantes.iso(e.ultimaLectura()));
                                    h.put("decision", e.decision());
                                    h.put("motivo", e.motivo());
                                    h.put("idSwitch", e.idSwitch());
                                    h.put("estadoSwitch", e.estadoSwitch());
                                    h.put("lecturasDescartadas", e.lecturasDescartadas());
                                    return h;
                                })
                        .toList();
        m.put("habitaciones", habitaciones);
        return m;
    }
}
