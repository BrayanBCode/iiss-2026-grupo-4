package uy.edu.utec.iiss.engine.adaptador;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import uy.edu.utec.iiss.core.Core;
import uy.edu.utec.iiss.core.MayorDeficitPrimero;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.Estimulo;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Motivo;
import uy.edu.utec.iiss.core.model.Sitio;
import uy.edu.utec.iiss.engine.switches.ClienteSwitch;
import uy.edu.utec.iiss.engine.switches.EstadoSwitch;

/**
 * Utilitarios de {@link CoreAdapterEstimulosTest} (no es un test en sí): arman un {@link
 * CoreAdapter} sin switches y los estímulos que se le mandan.
 *
 * <p>Calendario usado: octubre de 2026, hora de Uruguay (UTC-3). lunes 5, martes 6, miércoles 7,
 * jueves 8, viernes 9, sábado 10, domingo 11.
 */
final class EstimulosFixtures {

    static final String ROOM1 = "room1";
    static final String ROOM2 = "room2";
    static final String ROOM3 = "room3";

    /** Punta 17:00-23:00, solo días hábiles. */
    static final FranjaHoraria PUNTA =
            new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES);

    /** Nunca se llama: el adaptador de estos tests no se habilita, así que no comanda switches. */
    private static final ClienteSwitch SIN_SWITCHES =
            new ClienteSwitch() {
                @Override
                public EstadoSwitch comandar(String url, String idSwitch, Accion accion) {
                    throw new AssertionError("no se esperaba comandar switches");
                }

                @Override
                public EstadoSwitch consultar(String url, String idSwitch) {
                    throw new AssertionError("no se esperaba consultar switches");
                }
            };

    private EstimulosFixtures() {}

    // ---------- construcción del adaptador / configuración ----------

    static ConfiguracionHabitacion hab(String id, double temperaturaEsperada, double potenciaKW) {
        return new ConfiguracionHabitacion(id, id, temperaturaEsperada, potenciaKW);
    }

    /** Adaptador sin configurar, con el core dado. Detenido: decide, pero no toca switches. */
    static CoreAdapter adaptadorCon(Core core) {
        return new CoreAdapter(
                core, SIN_SWITCHES, Clock.systemUTC(), Duration.ZERO, () -> false, Runnable::run);
    }

    /** Adaptador con el core por defecto y la configuración dada. */
    static CoreAdapter nuevoAdaptador(
            double potenciaContratadaKW, ConfiguracionHabitacion... habitaciones) {
        CoreAdapter adaptador = adaptadorCon(new Core(new MayorDeficitPrimero()));
        adaptador.procesar(config(potenciaContratadaKW, habitaciones));
        return adaptador;
    }

    static Estimulo.ConfiguracionActualizada config(
            double potenciaContratadaKW, ConfiguracionHabitacion... habitaciones) {
        return config(new Sitio("sitio-test", potenciaContratadaKW, PUNTA), habitaciones);
    }

    static Estimulo.ConfiguracionActualizada config(
            Sitio sitio, ConfiguracionHabitacion... habitaciones) {
        return new Estimulo.ConfiguracionActualizada(sitio, List.of(habitaciones));
    }

    // ---------- estímulos ----------

    static Estimulo.NuevaLectura lectura(String idHabitacion, double temperaturaC, Instant ts) {
        return new Estimulo.NuevaLectura(idHabitacion, temperaturaC, ts);
    }

    static Estimulo.Tick tick(Instant ts) {
        return new Estimulo.Tick(ts);
    }

    static Estimulo.Sincronizacion sincronizar(Instant ahora, double factorTiempo) {
        return new Estimulo.Sincronizacion(ahora, factorTiempo);
    }

    // ---------- tiempo ----------

    static Instant dia(int diaDelMes, int hora, int minuto) {
        return LocalDateTime.of(2026, 10, diaDelMes, hora, minuto)
                .atZone(Sitio.ZONA_URUGUAY)
                .toInstant();
    }

    static Instant lunes(int hora, int minuto) {
        return dia(5, hora, minuto);
    }

    static Instant viernes(int hora, int minuto) {
        return dia(9, hora, minuto);
    }

    static Instant sabado(int hora, int minuto) {
        return dia(10, hora, minuto);
    }

    static Instant domingo(int hora, int minuto) {
        return dia(11, hora, minuto);
    }

    // ---------- lectura de resultados ----------

    static Decision decisionDe(List<Decision> decisiones, String idHabitacion) {
        for (Decision d : decisiones) {
            if (d.idHabitacion().equals(idHabitacion)) {
                return d;
            }
        }
        throw new AssertionError("No hay decisión para " + idHabitacion);
    }

    /** Acción decidida para una habitación; falla si no hay decisión sobre ella. */
    static Accion accionDe(List<Decision> decisiones, String idHabitacion) {
        return decisionDe(decisiones, idHabitacion).accion();
    }

    static Motivo motivoDe(List<Decision> decisiones, String idHabitacion) {
        return decisionDe(decisiones, idHabitacion).motivo();
    }

    static long cantidadEncendidas(List<Decision> decisiones) {
        long encendidas = 0;
        for (Decision d : decisiones) {
            if (d.accion() == Accion.ON) {
                encendidas++;
            }
        }
        return encendidas;
    }

    /** Suma de potencia (kW) de las habitaciones que quedaron en ON. */
    static double potenciaEncendida(
            List<Decision> decisiones, List<ConfiguracionHabitacion> habitaciones) {
        double total = 0;
        for (Decision d : decisiones) {
            if (d.accion() != Accion.ON) {
                continue;
            }
            for (ConfiguracionHabitacion h : habitaciones) {
                if (h.id().equals(d.idHabitacion())) {
                    total += h.potenciaKW();
                }
            }
        }
        return total;
    }
}
