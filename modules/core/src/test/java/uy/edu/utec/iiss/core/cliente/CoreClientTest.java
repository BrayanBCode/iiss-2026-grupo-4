package uy.edu.utec.iiss.core.cliente;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.MayorDeficitPrimero;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Sitio;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El CoreClient es la fachada del core: arma los estímulos y devuelve las
 * decisiones. Estos tests fijan que TODA operación que puede cambiar
 * decisiones (incluida actualizar el sitio) las devuelve, para que quien
 * integra el core pueda comandar los switches.
 */
@DisplayName("CoreClient (fachada del core)")
class CoreClientTest {

    private static final Sitio SITIO = sitio(3.7);
    private static final ConfiguracionHabitacion LIVING = new ConfiguracionHabitacion("living", "living", 21.5, 1.2);
    private static final ConfiguracionHabitacion DORMITORIO = new ConfiguracionHabitacion("dormitorio", "dormitorio", 20.0, 0.8);

    private final ICoreClient cliente = new CoreClient(new MayorDeficitPrimero());

    private static Sitio sitio(double tope) {
        return new Sitio("s", tope, new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES));
    }

    private static Instant lunes(int hora, int minuto) {
        return LocalDateTime.of(2026, 10, 5, hora, minuto).atZone(Sitio.ZONA_URUGUAY).toInstant();
    }

    private static Accion accionDe(List<Decision> decisiones, String id) {
        return decisiones.stream().filter(d -> d.idHabitacion().equals(id)).map(Decision::accion).findFirst()
                .orElseThrow(() -> new AssertionError("No hay decisión para " + id));
    }

    @Test
    @DisplayName("actualizarSitio devuelve las decisiones de la nueva configuración (una por habitación)")
    void actualizarSitio_devuelveDecisiones() {
        List<Decision> decisiones = cliente.actualizarSitio(SITIO, LIVING, DORMITORIO);

        assertNotNull(decisiones);
        assertEquals(2, decisiones.size());
        assertEquals(Accion.OFF, accionDe(decisiones, "living"));      // sin lecturas todavía: nada se calefacciona
        assertEquals(Accion.OFF, accionDe(decisiones, "dormitorio"));
    }

    @Test
    @DisplayName("actualizarSitio con un tope más bajo devuelve la decisión de apagar lo que ya no entra")
    void actualizarSitio_conTopeMasBajo_apagaLoQueNoEntra() {
        cliente.actualizarSitio(SITIO, LIVING, DORMITORIO);
        cliente.nuevaTemperatura("living", 15.0, lunes(10, 0));        // déficit 6.5
        cliente.nuevaTemperatura("dormitorio", 15.0, lunes(10, 1));    // déficit 5.0 -> ambas ON (2.0 kW)

        List<Decision> decisiones = cliente.actualizarSitio(sitio(1.5), LIVING, DORMITORIO);

        assertEquals(Accion.ON, accionDe(decisiones, "living"));       // mayor déficit y entra (1.2 <= 1.5)
        assertEquals(Accion.OFF, accionDe(decisiones, "dormitorio"));  // 1.2 + 0.8 = 2.0 > 1.5
    }

    @Test
    @DisplayName("nuevaTemperatura por debajo de la esperada enciende la habitación")
    void nuevaTemperatura_bajoLaEsperada_enciende() {
        cliente.actualizarSitio(SITIO, LIVING, DORMITORIO);

        List<Decision> decisiones = cliente.nuevaTemperatura("living", 19.0, lunes(10, 0));

        assertEquals(Accion.ON, accionDe(decisiones, "living"));
    }

    @Test
    @DisplayName("tick al entrar en la franja punta apaga lo encendido")
    void tick_alEntrarEnPunta_apaga() {
        cliente.actualizarSitio(SITIO, LIVING, DORMITORIO);
        cliente.nuevaTemperatura("living", 19.0, lunes(16, 30));

        List<Decision> decisiones = cliente.tick(lunes(17, 0));

        assertEquals(Accion.OFF, accionDe(decisiones, "living"));
    }

    @Test
    @DisplayName("estadoById devuelve la última temperatura y si está encendida")
    void estadoById_devuelveElEstado() {
        cliente.actualizarSitio(SITIO, LIVING, DORMITORIO);
        cliente.nuevaTemperatura("living", 19.0, lunes(10, 0));

        EstadoHabitacion estado = cliente.estadoById("living");

        assertEquals(19.0, estado.temperaturaActual(), 1e-9);
        assertTrue(estado.encendida());
    }
}