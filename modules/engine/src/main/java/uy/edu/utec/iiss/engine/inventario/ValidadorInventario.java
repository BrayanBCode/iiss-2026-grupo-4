package uy.edu.utec.iiss.engine.inventario;

import java.net.URI;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reglas de consistencia del inventario (estándar v2, §1). Devuelve TODOS los problemas encontrados
 * (no solo el primero) para que quien carga el sitio los pueda corregir de una vez. Lista vacía =
 * válido.
 *
 * <p>La suma de potencias de las habitaciones PUEDE superar la contratada: no es un error, es lo
 * que el controlador administra encendiendo por turnos.
 */
public final class ValidadorInventario {

    private static final Pattern HH_MM = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");
    private static final Pattern SCHEMA_V2 = Pattern.compile("^2\\.\\d+$");

    private ValidadorInventario() {}

    public static List<String> validar(Inventario inv) {
        List<String> e = new ArrayList<>();
        if (inv == null) {
            e.add("falta el cuerpo con el inventario");
            return e;
        }
        if (vacio(inv.schemaVersion())) {
            e.add("falta schemaVersion");
        } else if (!SCHEMA_V2.matcher(inv.schemaVersion()).matches()) {
            e.add(
                    "schemaVersion '"
                            + inv.schemaVersion()
                            + "' no soportada: se acepta major 2 (\"2.x\")");
        }
        validarSitio(inv.sitio(), e);
        validarHabitaciones(inv.habitaciones(), e);
        return e;
    }

    private static void validarSitio(Inventario.Sitio s, List<String> e) {
        if (s == null) {
            e.add("falta sitio");
            return;
        }
        if (vacio(s.id())) {
            e.add("sitio: falta id");
        }
        if (vacio(s.nombre())) {
            e.add("sitio: falta nombre");
        }
        if (!positivo(s.potenciaContratadaKW())) {
            e.add("sitio: potenciaContratadaKW debe ser > 0 (es " + s.potenciaContratadaKW() + ")");
        }
        if (s.tarifa() == null || s.tarifa().punta() == null) {
            e.add("sitio: falta tarifa.punta");
            return;
        }
        Inventario.Franja f = s.tarifa().punta();
        boolean desdeOk = hora(f.desde(), "desde", e);
        boolean hastaOk = hora(f.hasta(), "hasta", e);
        if (desdeOk
                && hastaOk
                && !LocalTime.parse(f.desde()).isBefore(LocalTime.parse(f.hasta()))) {
            e.add(
                    "sitio: tarifa.punta debe cumplir desde < hasta (es "
                            + f.desde()
                            + " - "
                            + f.hasta()
                            + ")");
        }
        if (!"HABILES".equals(f.dias()) && !"TODOS".equals(f.dias())) {
            e.add("sitio: tarifa.punta.dias debe ser HABILES o TODOS (es " + f.dias() + ")");
        }
    }

    private static boolean hora(String valor, String campo, List<String> e) {
        if (valor == null || !HH_MM.matcher(valor).matches()) {
            e.add("sitio: tarifa.punta." + campo + " debe ser HH:MM (es " + valor + ")");
            return false;
        }
        return true;
    }

    private static void validarHabitaciones(List<Inventario.Habitacion> hs, List<String> e) {
        if (hs == null) {
            e.add("falta habitaciones");
            return;
        }
        Set<String> ids = new HashSet<>();
        Set<String> termostatos = new HashSet<>();
        Set<String> switches = new HashSet<>();
        Set<String> topics = new HashSet<>();
        for (int i = 0; i < hs.size(); i++) {
            Inventario.Habitacion h = hs.get(i);
            if (h == null) {
                e.add("habitaciones[" + i + "]: es null");
                continue;
            }
            String quien =
                    vacio(h.id()) ? "habitaciones[" + i + "]" : "habitación '" + h.id() + "'";
            if (vacio(h.id())) {
                e.add(quien + ": falta id");
            } else if (!ids.add(h.id())) {
                e.add("id duplicado: '" + h.id() + "'");
            }
            if (vacio(h.nombre())) {
                e.add(quien + ": falta nombre");
            } else if (h.nombre().length() > 50) {
                e.add(quien + ": nombre supera 50 caracteres");
            }
            if (h.temperaturaEsperada() == null || !Double.isFinite(h.temperaturaEsperada())) {
                e.add(quien + ": falta temperaturaEsperada");
            }
            if (!positivo(h.potenciaKW())) {
                e.add(quien + ": potenciaKW debe ser > 0 (es " + h.potenciaKW() + ")");
            }
            if (vacio(h.idTermostato())) {
                e.add(quien + ": falta idTermostato");
            } else if (!termostatos.add(h.idTermostato())) {
                e.add("idTermostato duplicado: '" + h.idTermostato() + "'");
            }
            if (vacio(h.topicTermostato())) {
                e.add(quien + ": falta topicTermostato");
            } else if (h.topicTermostato().contains("+") || h.topicTermostato().contains("#")) {
                e.add(quien + ": topicTermostato no puede tener comodines MQTT (+ o #)");
            } else if (!topics.add(h.topicTermostato())) {
                e.add("topicTermostato duplicado: '" + h.topicTermostato() + "'");
            }
            if (vacio(h.idSwitch())) {
                e.add(quien + ": falta idSwitch");
            } else if (!switches.add(h.idSwitch())) {
                e.add("idSwitch duplicado: '" + h.idSwitch() + "'");
            }
            if (vacio(h.urlSwitch())) {
                e.add(quien + ": falta urlSwitch");
            } else if (!urlHttp(h.urlSwitch())) {
                e.add(quien + ": urlSwitch no es una URL http(s) (es " + h.urlSwitch() + ")");
            }
        }
    }

    private static boolean urlHttp(String url) {
        try {
            URI u = URI.create(url);
            return ("http".equalsIgnoreCase(u.getScheme())
                            || "https".equalsIgnoreCase(u.getScheme()))
                    && u.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static boolean positivo(Double d) {
        return d != null && d > 0 && Double.isFinite(d);
    }
}
