package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Cruce de la franja punta con tiempo acelerado (T12). A ×600, 5 min de control son 0,5 s reales.
 * Se usa el tick de producción (1 s de control); sin escalarlo, el primer tick llegaría al segundo
 * real (10 min de control) y el corte saldría tarde. La vigencia se alarga a 10 h de control para
 * que las lecturas no venzan durante la prueba.
 */
@TestPropertySource(
        properties = {"control.tick-ms=1000", "control.vigencia-lectura-segundos=36000"})
@DisplayName("Integración: tiempo acelerado, cruce de la franja punta (T12)")
class TiempoAceleradoTest extends IntegracionBase {

    static final double FACTOR = 600;

    @Test
    @DisplayName(
            "A ×600, arrancando 5 min de control antes de la punta (16:55 en Montevideo), el corte"
                    + " llega en menos de 1 s real y con menos de 1 min de control de atraso")
    void cortaAlEntrarEnPunta() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        esperar(() -> BROKER.haySuscriptor(topic("living")), () -> "no se suscribió");

        long inicio = System.nanoTime();
        iniciar("2026-10-01T19:55:00Z", FACTOR); // jueves 16:55 en Montevideo
        temperatura("living", 15.0);
        esperarSwitch("living", "ON");

        esperarMotivo("living", "TARIFA_PUNTA");
        Duration real = Duration.ofNanos(System.nanoTime() - inicio);
        Instant horaDelCorte = control.estado().fechaHora();
        esperarSwitch("living", "OFF");

        assertTrue(real.toMillis() < 1000, "el corte tardó " + real.toMillis() + " ms reales");
        Duration atraso = Duration.between(Instant.parse("2026-10-01T20:00:00Z"), horaDelCorte);
        assertTrue(
                atraso.compareTo(Duration.ofMinutes(1)) < 0,
                "el corte llegó " + atraso.toSeconds() + " s de control después de las 17:00");
    }

    @Test
    @DisplayName(
            "A ×600, arrancando 5 min antes del fin de la punta (22:55 en Montevideo), la"
                    + " calefacción se restituye en menos de 1 s real")
    void restituyeAlSalirDePunta() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        esperar(() -> BROKER.haySuscriptor(topic("living")), () -> "no se suscribió");

        long inicio = System.nanoTime();
        iniciar("2026-10-02T01:55:00Z", FACTOR); // jueves 22:55 en Montevideo
        temperatura("living", 15.0);
        assertEquals("TARIFA_PUNTA", motivo("living"));

        esperarSwitch("living", "ON");
        Duration real = Duration.ofNanos(System.nanoTime() - inicio);

        assertEquals("CALEFACCIONANDO", motivo("living"));
        assertTrue(
                real.toMillis() < 1000, "la restitución tardó " + real.toMillis() + " ms reales");
    }

    @Test
    @DisplayName(
            "INICIAR en ejecución con un factor mayor conserva la lectura y aplica el factor nuevo:"
                    + " el corte de la punta llega en menos de 1 s real")
    void reiniciarConFactorMayor_aceleraElReloj() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar("2026-10-01T19:55:00Z", 1); // jueves 16:55 en Montevideo
        temperatura("living", 15.0);
        esperarSwitch("living", "ON");

        long inicio = System.nanoTime();
        iniciar("2026-10-01T19:55:00Z", FACTOR);
        assertEquals(FACTOR, control.estado().factorTiempo());
        assertEquals(15.0, estado("living").temperaturaActual());

        esperarMotivo("living", "TARIFA_PUNTA");
        Duration real = Duration.ofNanos(System.nanoTime() - inicio);
        esperarSwitch("living", "OFF");

        assertTrue(real.toMillis() < 1000, "el corte tardó " + real.toMillis() + " ms reales");
    }

    @Test
    @DisplayName(
            "INICIAR en ejecución con un factor menor frena el reloj: la hora del control y el ts"
                    + " de las lecturas nuevas ya no se aceleran")
    void reiniciarConFactorMenor_frenaElReloj() throws Exception {
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar(FECHA_INICIO, FACTOR);
        iniciar(FECHA_INICIO, 1);

        Thread.sleep(500); // a ×600 serían 5 min de control
        temperatura("living", 15.0);

        Instant inicio = Instant.parse(FECHA_INICIO);
        Duration reloj = Duration.between(inicio, control.estado().fechaHora());
        Duration lectura = Duration.between(inicio, estado("living").ultimaLectura());
        assertEquals(1.0, control.estado().factorTiempo());
        assertTrue(reloj.toSeconds() < 30, "el reloj avanzó " + reloj.toSeconds() + " s");
        assertTrue(lectura.toSeconds() < 30, "la lectura quedó a " + lectura.toSeconds() + " s");
    }
}
