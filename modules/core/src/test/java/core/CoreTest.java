package core;

import core.model.Accion;
import core.model.Decision;
import core.model.DiasTarifa;
import core.model.EstadoHabitacion;
import core.model.Estimulo;
import core.model.FranjaHoraria;
import core.model.Habitacion;
import core.model.Sitio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests propuestos del core del controlador (TDD, Iteración 4).
 *
 * IMPORTANTE: hoy {@link Core#procesar} y {@link Core#estadoDe} lanzan
 * {@code UnsupportedOperationException} — estos tests están en rojo a
 * propósito. Se escriben primero para discutir en clase (miércoles) el
 * criterio de prioridad y los casos límite, antes de implementar. Ver
 * docs/tests/criterio-tests.md para el criterio de adecuación de este
 * conjunto.
 *
 * Los datos de ejemplo son los del estándar de interoperabilidad v2 (§1):
 * Living (room1, esperada 21.5°C, 1.2kW) y Dormitorio principal (room2,
 * esperada 20.0°C, 0.8kW), sitio "casa-rodriguez" con potenciaContratadaKW
 * 3.7 y tarifa punta 17:00-23:00 en días hábiles.
 */
class CoreTest {

    private static final String ROOM1 = "room1"; // Living
    private static final String ROOM2 = "room2"; // Dormitorio principal

    private Core core;
    private Sitio sitio;
    private List<Habitacion> habitaciones;

    @BeforeEach
    void setUp() {
        FranjaHoraria puntaTarifa = new FranjaHoraria(
                LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES);
        sitio = new Sitio("casa-rodriguez", 3.7, puntaTarifa);

        habitaciones = List.of(
                new Habitacion(ROOM1, "Living", 21.5, 1.2),
                new Habitacion(ROOM2, "Dormitorio principal", 20.0, 0.8)
        );

        core = new Core(new MayorDeficitPrimero());
        core.procesar(new Estimulo.ConfiguracionActualizada(sitio, habitaciones));
    }

    // -----------------------------------------------------------------
    // Decidir qué habitación recibe energía, según déficit térmico
    // -----------------------------------------------------------------

    @Nested
    class DecisionBasica {

        @Test
        void habitacionPorDebajoDeLaEsperada_seEnciende() {
            List<Decision> decisiones = core.procesar(
                    new Estimulo.NuevaLectura(ROOM1, 19.0, unInstanteFueraDePunta()));

            assertEquals(Accion.ON, decisionDe(decisiones, ROOM1));
        }

        @Test
        void habitacionEnLaTemperaturaEsperada_noEnciende() {
            List<Decision> decisiones = core.procesar(
                    new Estimulo.NuevaLectura(ROOM1, 21.5, unInstanteFueraDePunta()));

            assertEquals(Accion.OFF, decisionDe(decisiones, ROOM1));
        }

        @Test
        void habitacionPorEncimaDeLaEsperadaYEncendida_seApaga() {
            core.procesar(new Estimulo.NuevaLectura(ROOM1, 19.0, unInstanteFueraDePunta())); // la prende

            List<Decision> decisiones = core.procesar(
                    new Estimulo.NuevaLectura(ROOM1, 22.0, unInstanteFueraDePunta()));

            assertEquals(Accion.OFF, decisionDe(decisiones, ROOM1));
        }

        @Test
        void dosHabitacionesConDeficit_potenciaAlcanzaParaAmbas_ambasEncienden() {
            core.procesar(new Estimulo.NuevaLectura(ROOM1, 19.0, unInstanteFueraDePunta()));

            // room1 (1.2kW) + room2 (0.8kW) = 2.0kW, por debajo de los 3.7kW contratados
            List<Decision> decisiones = core.procesar(
                    new Estimulo.NuevaLectura(ROOM2, 18.0, unInstanteFueraDePunta()));

            assertEquals(Accion.ON, decisionDe(decisiones, ROOM1));
            assertEquals(Accion.ON, decisionDe(decisiones, ROOM2));
        }

        @Test
        void dosHabitacionesConDeficit_potenciaAlcanzaParaUnaSola_prendeLaDeMayorDeficit() {
            // sitio más chico, a propósito, para forzar el conflicto de potencia
            Sitio sitioAjustado = new Sitio("casa-chica", 1.0, sitio.puntaTarifa());
            core.procesar(new Estimulo.ConfiguracionActualizada(sitioAjustado, habitaciones));

            core.procesar(new Estimulo.NuevaLectura(ROOM1, 20.0, unInstanteFueraDePunta())); // déficit 1.5
            List<Decision> decisiones = core.procesar(
                    new Estimulo.NuevaLectura(ROOM2, 15.0, unInstanteFueraDePunta())); // déficit 5.0

            // room2 tiene mayor déficit (5.0 > 1.5): se queda con la potencia disponible (1.0kW)
            assertEquals(Accion.ON, decisionDe(decisiones, ROOM2));
            assertEquals(Accion.OFF, decisionDe(decisiones, ROOM1));
        }

        @Test
        void empateExactoDeDeficit_desempateEsDeterministico() {
            // mismo déficit para ambas (1.5°C); potencia conjunta (1.2+0.8=2.0kW) no entra en 0.9kW
            Sitio sitioAjustado = new Sitio("casa-chica", 0.9, sitio.puntaTarifa());

            List<Decision> primeraCorrida = correrEscenarioDeEmpate(sitioAjustado);
            List<Decision> segundaCorrida = correrEscenarioDeEmpate(sitioAjustado);

            // Dos Core nuevos, mismo estado exacto: el desempate no puede depender de nada
            // mutable (ej. orden de llegada de mensajes) para que el resultado sea reproducible.
            assertEquals(decisionDe(primeraCorrida, ROOM1), decisionDe(segundaCorrida, ROOM1));
            assertEquals(decisionDe(primeraCorrida, ROOM2), decisionDe(segundaCorrida, ROOM2));
        }

        private List<Decision> correrEscenarioDeEmpate(Sitio sitioAjustado) {
            Core otroCore = new Core(new MayorDeficitPrimero());
            otroCore.procesar(new Estimulo.ConfiguracionActualizada(sitioAjustado, habitaciones));
            otroCore.procesar(new Estimulo.NuevaLectura(ROOM1, 20.0, unInstanteFueraDePunta()));
            return otroCore.procesar(new Estimulo.NuevaLectura(ROOM2, 18.5, unInstanteFueraDePunta()));
        }
    }

    // -----------------------------------------------------------------
    // Franja de tarifa punta
    // -----------------------------------------------------------------

    @Nested
    class TarifaPunta {

        @Test
        void alEntrarEnPunta_habitacionesEncendidasSeApagan() {
            core.procesar(new Estimulo.NuevaLectura(ROOM1, 15.0, unInstanteFueraDePunta())); // la prende

            List<Decision> decisiones = core.procesar(new Estimulo.Tick(unInstanteDentroDePunta()));

            assertEquals(Accion.OFF, decisionDe(decisiones, ROOM1));
        }

        @Test
        void unaLecturaDuranteLaFranjaPunta_noEnciendeAunqueHayaDeficitGrande() {
            List<Decision> decisiones = core.procesar(
                    new Estimulo.NuevaLectura(ROOM1, 10.0, unInstanteDentroDePunta()));

            assertEquals(Accion.OFF, decisionDe(decisiones, ROOM1));
        }

        @Test
        void alSalirDePunta_seRecalculaConElCriterioNormal() {
            core.procesar(new Estimulo.NuevaLectura(ROOM1, 10.0, unInstanteDentroDePunta())); // no prende: está en punta

            List<Decision> decisiones = core.procesar(new Estimulo.Tick(unInstanteFueraDePunta()));

            // al salir de punta, con el déficit pendiente, debería recalcular y encender
            assertEquals(Accion.ON, decisionDe(decisiones, ROOM1));
        }
    }

    // -----------------------------------------------------------------
    // Nunca superar el consumo máximo
    // -----------------------------------------------------------------

    @Nested
    class ConsumoMaximo {

        @Test
        void laSumaDePotenciaEncendidaNuncaSuperaLaContratada() {
            core.procesar(new Estimulo.NuevaLectura(ROOM1, 10.0, unInstanteFueraDePunta()));
            List<Decision> decisiones = core.procesar(
                    new Estimulo.NuevaLectura(ROOM2, 10.0, unInstanteFueraDePunta()));

            double potenciaEncendida = decisiones.stream()
                    .filter(d -> d.accion() == Accion.ON)
                    .mapToDouble(d -> potenciaDe(d.idHabitacion()))
                    .sum();

            assertTrue(potenciaEncendida <= sitio.potenciaContratadaKW(),
                    "Potencia encendida (" + potenciaEncendida + "kW) supera la contratada ("
                            + sitio.potenciaContratadaKW() + "kW)");
        }

        @Test
        void habitacionCuyaPotenciaYaSuperaLaContratada_nuncaSeEnciende() {
            Habitacion habitacionEnorme = new Habitacion("room3", "Quincho", 18.0, 5.0); // 5kW > 3.7kW contratados
            core.procesar(new Estimulo.ConfiguracionActualizada(
                    sitio, List.of(habitaciones.get(0), habitaciones.get(1), habitacionEnorme)));

            List<Decision> decisiones = core.procesar(
                    new Estimulo.NuevaLectura("room3", 5.0, unInstanteFueraDePunta()));

            assertEquals(Accion.OFF, decisionDe(decisiones, "room3"));
        }
    }

    // -----------------------------------------------------------------
    // Estímulos y estado interno
    // -----------------------------------------------------------------

    @Nested
    class EstimulosYEstado {

        @Test
        void lecturaDeUnaHabitacionDesconocida_seIgnoraSinRomper() {
            assertDoesNotThrow(() -> core.procesar(
                    new Estimulo.NuevaLectura("room-inexistente", 15.0, unInstanteFueraDePunta())));
        }

        @Test
        void configuracionActualizada_reemplazaElInventarioAnteriorEntero() {
            core.procesar(new Estimulo.NuevaLectura(ROOM1, 15.0, unInstanteFueraDePunta())); // la prende

            // nueva config que ya NO incluye room1 (PUT /sitio es reemplazo completo, §5.1)
            core.procesar(new Estimulo.ConfiguracionActualizada(sitio, List.of(habitaciones.get(1))));

            List<Decision> decisiones = core.procesar(
                    new Estimulo.NuevaLectura(ROOM2, 15.0, unInstanteFueraDePunta()));

            assertTrue(decisiones.stream().noneMatch(d -> d.idHabitacion().equals(ROOM1)),
                    "room1 ya no está configurada: no debería aparecer ninguna decisión sobre ella");
        }

        @Test
        void lecturaConTimestampMasViejoQueLaUltimaConocida_noPisaElEstado() {
            Instant tardia = unInstanteFueraDePunta();
            Instant temprana = tardia.minusSeconds(3600);

            core.procesar(new Estimulo.NuevaLectura(ROOM1, 19.0, tardia));   // última conocida: 19.0
            core.procesar(new Estimulo.NuevaLectura(ROOM1, 25.0, temprana)); // mensaje "viejo" fuera de orden

            EstadoHabitacion estado = core.estadoDe(ROOM1);

            assertEquals(19.0, estado.temperaturaActual(), 0.01,
                    "Una lectura más vieja que la última conocida no debería pisar el estado");
        }
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Accion decisionDe(List<Decision> decisiones, String idHabitacion) {
        return decisiones.stream()
                .filter(d -> d.idHabitacion().equals(idHabitacion))
                .map(Decision::accion)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay decisión para " + idHabitacion));
    }

    private double potenciaDe(String idHabitacion) {
        return habitaciones.stream()
                .filter(h -> h.id().equals(idHabitacion))
                .map(Habitacion::potenciaKW)
                .findFirst()
                .orElse(0.0);
    }

    /** Lunes 10:00 — fuera de la franja punta (17:00-23:00, días hábiles) del sitio de prueba. */
    private Instant unInstanteFueraDePunta() {
        return ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, ZoneId.systemDefault()).toInstant(); // lunes
    }

    /** Lunes 18:00 — dentro de la franja punta (17:00-23:00, días hábiles) del sitio de prueba. */
    private Instant unInstanteDentroDePunta() {
        return ZonedDateTime.of(2026, 10, 5, 18, 0, 0, 0, ZoneId.systemDefault()).toInstant(); // lunes
    }
}
