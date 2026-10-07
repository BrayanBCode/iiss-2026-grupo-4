package uy.edu.utec.iiss.engine.core;

import uy.edu.utec.iiss.engine.core.model.Accion;
import uy.edu.utec.iiss.engine.core.model.Decision;
import uy.edu.utec.iiss.engine.core.model.DiasTarifa;
import uy.edu.utec.iiss.engine.core.model.Estimulo;
import uy.edu.utec.iiss.engine.core.model.FranjaHoraria;
import uy.edu.utec.iiss.engine.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.engine.core.model.Sitio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitarios (TDD) del core del controlador, Iteración 4.
 *
 * Cada grupo (@Nested) corresponde a una característica que pide la letra:
 *   1. Decidir qué habitación recibe energía y cuál no.
 *   2. Cortar el consumo al entrar en tarifa punta y restituirlo al salir.
 *   3. Nunca superar el consumo máximo contratado.
 *   4. Criterio no azaroso cuando la carga no alcanza para todas.
 *   5. Actualización de la configuración del sitio.
 *
 * Contrato asumido: cada llamada a {@link Core#procesar} devuelve una
 * {@link Decision} (ON u OFF) por cada habitación configurada.
 *
 * Los tests no usan reloj del sistema: el "ahora" es siempre el instante
 * del estímulo. Lunes 5/10/2026 y sábado 10/10/2026, hora local.
 */
class CoreTest {

    private static final String ROOM1 = "room1";
    private static final String ROOM2 = "room2";
    private static final String ROOM3 = "room3";

    /** Punta 17:00-23:00, solo días hábiles (estándar v2, §1). */
    private static final FranjaHoraria PUNTA =
            new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES);

    private Core core;
    private Sitio sitio;
    private List<ConfiguracionHabitacion> habitaciones;

    @BeforeEach
    void setUp() {
        // Datos del estándar v2 (§1): potencia contratada 3.7 kW
        sitio = new Sitio("casa-rodriguez", 3.7, PUNTA);
        habitaciones = List.of(
                new ConfiguracionHabitacion(ROOM1, "Living", 21.5, 1.2),
                new ConfiguracionHabitacion(ROOM2, "Dormitorio principal", 20.0, 0.8));
        core = nuevoCore(sitio, habitaciones);
    }

    // =================================================================
    // 1. Decidir qué habitación recibe energía y cuál no
    // =================================================================
    @Nested
    @DisplayName("1. Decidir qué habitación recibe energía")
    class DecisionBasica {

        @Test
        @DisplayName("Habitación por debajo de su temperatura esperada -> ON")
        void bajoLaEsperada_enciende() {
            List<Decision> d = core.procesar(lectura(ROOM1, 19.0, lunes(10, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Habitación justo en su temperatura esperada -> OFF")
        void enLaEsperada_noEnciende() {
            List<Decision> d = core.procesar(lectura(ROOM1, 21.5, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Habitación por encima de la esperada -> OFF")
        void porEncimaDeLaEsperada_noEnciende() {
            List<Decision> d = core.procesar(lectura(ROOM1, 24.0, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Estaba encendida y alcanza la esperada -> se apaga")
        void encendidaQueAlcanzaLaEsperada_seApaga() {
            core.procesar(lectura(ROOM1, 19.0, lunes(10, 0)));            // se enciende

            List<Decision> d = core.procesar(lectura(ROOM1, 21.5, lunes(10, 5)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Dos habitaciones con déficit y la potencia alcanza -> ambas ON")
        void dosConDeficitYAlcanza_ambasEncienden() {
            core.procesar(lectura(ROOM1, 19.0, lunes(10, 0)));

            // 1.2 kW + 0.8 kW = 2.0 kW <= 3.7 kW
            List<Decision> d = core.procesar(lectura(ROOM2, 18.0, lunes(10, 1)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Una con déficit y otra satisfecha -> solo la que tiene déficit")
        void unaConDeficitYOtraSatisfecha_soloEnciendeLaPrimera() {
            core.procesar(lectura(ROOM1, 21.5, lunes(10, 0)));            // satisfecha

            List<Decision> d = core.procesar(lectura(ROOM2, 17.0, lunes(10, 1)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Cada estímulo devuelve una decisión por habitación configurada")
        void unaDecisionPorHabitacionConfigurada() {
            List<Decision> d = core.procesar(lectura(ROOM1, 19.0, lunes(10, 0)));

            assertEquals(2, d.size());
            assertTrue(d.stream().anyMatch(x -> x.idHabitacion().equals(ROOM1)));
            assertTrue(d.stream().anyMatch(x -> x.idHabitacion().equals(ROOM2)));
        }
    }

    // =================================================================
    // 2. Franja de tarifa punta: cortar al entrar, restituir al salir
    // =================================================================
    @Nested
    @DisplayName("2. Tarifa punta: cortar y restituir")
    class TarifaPunta {

        @Test
        @DisplayName("Al entrar en punta, la habitación encendida se apaga")
        void alEntrarEnPunta_seApagaLaEncendida() {
            core.procesar(lectura(ROOM1, 15.0, lunes(16, 30)));           // fuera de punta: ON

            List<Decision> d = core.procesar(new Estimulo.Tick(lunes(17, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Al entrar en punta, se apagan todas las encendidas")
        void alEntrarEnPunta_seApaganTodas() {
            core.procesar(lectura(ROOM1, 15.0, lunes(16, 30)));
            core.procesar(lectura(ROOM2, 14.0, lunes(16, 31)));           // ambas ON

            List<Decision> d = core.procesar(new Estimulo.Tick(lunes(17, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Accion.OFF, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Una lectura con gran déficit dentro de la punta no enciende")
        void lecturaEnPunta_noEnciendeAunqueHayaDeficit() {
            List<Decision> d = core.procesar(lectura(ROOM1, 5.0, lunes(18, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Durante toda la punta se mantiene todo apagado")
        void duranteLaPunta_siguenApagadas() {
            core.procesar(lectura(ROOM1, 15.0, lunes(16, 30)));
            core.procesar(new Estimulo.Tick(lunes(17, 0)));

            List<Decision> d = core.procesar(lectura(ROOM1, 14.0, lunes(20, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Accion.OFF, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Al salir de la punta, se restituye la energía a la que tiene déficit")
        void alSalirDePunta_seRestituye() {
            core.procesar(lectura(ROOM1, 15.0, lunes(16, 30)));           // ON
            core.procesar(new Estimulo.Tick(lunes(17, 0)));               // corte por punta

            List<Decision> d = core.procesar(new Estimulo.Tick(lunes(23, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Al salir de la punta, solo se restituyen las que siguen con déficit")
        void alSalirDePunta_soloLasQueTienenDeficit() {
            core.procesar(lectura(ROOM1, 15.0, lunes(18, 0)));            // en punta
            core.procesar(lectura(ROOM2, 15.0, lunes(18, 1)));
            core.procesar(lectura(ROOM1, 22.0, lunes(22, 0)));            // room1 llegó a su objetivo

            List<Decision> d = core.procesar(new Estimulo.Tick(lunes(23, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Límite inferior: a las 17:00 exactas ya es punta")
        void aLas1700_yaEsPunta() {
            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, lunes(17, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Límite inferior: a las 16:59 todavía no es punta")
        void aLas1659_todaviaNoEsPunta() {
            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, lunes(16, 59)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Límite superior: a las 23:00 exactas ya no es punta")
        void aLas2300_yaNoEsPunta() {
            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, lunes(23, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Tarifa HABILES: el sábado a las 18:00 no hay punta")
        void sabadoConTarifaHabiles_noHayPunta() {
            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, sabado(18, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Tarifa TODOS: el sábado a las 18:00 sí hay punta")
        void sabadoConTarifaTodos_hayPunta() {
            FranjaHoraria todosLosDias =
                    new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.TODOS);
            Core otro = nuevoCore(new Sitio("casa", 3.7, todosLosDias), habitaciones);

            List<Decision> d = otro.procesar(lectura(ROOM1, 10.0, sabado(18, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }
    }

    // =================================================================
    // 3. Nunca superar el consumo máximo contratado
    // =================================================================
    @Nested
    @DisplayName("3. Nunca superar el consumo máximo")
    class ConsumoMaximo {

        @Test
        @DisplayName("Tres habitaciones con déficit: la suma encendida no supera la contratada")
        void sumaEncendidaNoSuperaLaContratada() {
            // 3 x 1.5 kW = 4.5 kW > 3.7 kW contratados: no pueden estar todas ON
            configurar(3.7,
                    new ConfiguracionHabitacion(ROOM1, "A", 21.0, 1.5),
                    new ConfiguracionHabitacion(ROOM2, "B", 21.0, 1.5),
                    new ConfiguracionHabitacion(ROOM3, "C", 21.0, 1.5));

            core.procesar(lectura(ROOM1, 10.0, lunes(10, 0)));
            core.procesar(lectura(ROOM2, 10.0, lunes(10, 1)));
            List<Decision> d = core.procesar(lectura(ROOM3, 10.0, lunes(10, 2)));

            assertTrue(potenciaEncendida(d) <= 3.7, "Potencia encendida: " + potenciaEncendida(d));
            assertTrue(potenciaEncendida(d) > 0, "Debería encender al menos una habitación");
        }

        @Test
        @DisplayName("Suma exactamente igual a la contratada está permitida")
        void sumaIgualALaContratada_esValida() {
            configurar(2.0,
                    new ConfiguracionHabitacion(ROOM1, "Living", 21.5, 1.2),
                    new ConfiguracionHabitacion(ROOM2, "Dormitorio", 20.0, 0.8));

            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            List<Decision> d = core.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Habitación cuya potencia supera la contratada nunca se enciende")
        void habitacionMasGrandeQueLaContratada_nuncaEnciende() {
            configurar(3.7, new ConfiguracionHabitacion(ROOM3, "Quincho", 18.0, 5.0));

            List<Decision> d = core.procesar(lectura(ROOM3, 5.0, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM3));
        }

        @Test
        @DisplayName("Si baja la potencia contratada, el nuevo tope se respeta")
        void bajaLaPotenciaContratada_seRespetaElNuevoTope() {
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            core.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));            // ambas ON (2.0 kW)

            configurar(1.0,
                    new ConfiguracionHabitacion(ROOM1, "Living", 21.5, 1.2),
                    new ConfiguracionHabitacion(ROOM2, "Dormitorio", 20.0, 0.8));
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 2)));
            List<Decision> d = core.procesar(lectura(ROOM2, 15.0, lunes(10, 3)));

            assertTrue(potenciaEncendida(d) <= 1.0, "Potencia encendida: " + potenciaEncendida(d));
        }

        @Test
        @DisplayName("En una secuencia larga de estímulos, el tope nunca se supera")
        void secuenciaDeEstimulos_topeNuncaSuperado() {
            configurar(3.7,
                    new ConfiguracionHabitacion(ROOM1, "A", 21.0, 1.5),
                    new ConfiguracionHabitacion(ROOM2, "B", 20.0, 1.5),
                    new ConfiguracionHabitacion(ROOM3, "C", 19.0, 1.5));

            Estimulo[] secuencia = {
                    lectura(ROOM1, 12.0, lunes(8, 0)),
                    lectura(ROOM2, 12.0, lunes(8, 1)),
                    lectura(ROOM3, 12.0, lunes(8, 2)),
                    lectura(ROOM1, 20.0, lunes(9, 0)),
                    lectura(ROOM2, 15.0, lunes(9, 1)),
                    lectura(ROOM3, 10.0, lunes(9, 2)),
                    new Estimulo.Tick(lunes(10, 0)),
                    lectura(ROOM1, 13.0, lunes(11, 0)),
                    lectura(ROOM2, 25.0, lunes(11, 1)),
                    new Estimulo.Tick(lunes(12, 0))
            };

            for (Estimulo e : secuencia) {
                List<Decision> d = core.procesar(e);
                assertTrue(potenciaEncendida(d) <= 3.7,
                        "Tope superado tras " + e + ": " + potenciaEncendida(d) + " kW");
            }
        }
    }

    // =================================================================
    // 4. Criterio no azaroso cuando la carga no alcanza para todas
    // =================================================================
    @Nested
    @DisplayName("4. Criterio justificable y no azaroso")
    class CriterioDeSeleccion {

        // Escenario de conflicto: dos habitaciones de 1.0 kW y solo 1.0 kW contratado,
        // así que solo una puede estar encendida (la potencia no sesga la elección).
        @BeforeEach
        void escenarioDeConflicto() {
            configurar(1.0,
                    new ConfiguracionHabitacion(ROOM1, "A", 21.0, 1.0),
                    new ConfiguracionHabitacion(ROOM2, "B", 21.0, 1.0));
        }

        @Test
        @DisplayName("Si no alcanza para todas, recibe energía la más lejos de su objetivo (room2)")
        void recibeLaDeMayorDeficit_room2() {
            core.procesar(lectura(ROOM1, 20.0, lunes(10, 0)));            // déficit 1.0
            List<Decision> d = core.procesar(lectura(ROOM2, 15.0, lunes(10, 1))); // déficit 6.0

            assertEquals(Accion.ON, accionDe(d, ROOM2));
            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Si no alcanza para todas, recibe energía la más lejos de su objetivo (room1)")
        void recibeLaDeMayorDeficit_room1() {
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));            // déficit 6.0
            List<Decision> d = core.procesar(lectura(ROOM2, 20.0, lunes(10, 1))); // déficit 1.0

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.OFF, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Cuando la favorecida llega a su objetivo, la energía pasa a la otra")
        void alLlegarAlObjetivo_laEnergiaPasaALaOtra() {
            core.procesar(lectura(ROOM1, 20.0, lunes(10, 0)));
            core.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));            // room2 recibe energía

            List<Decision> d = core.procesar(lectura(ROOM2, 21.0, lunes(10, 30))); // room2 llegó

            assertEquals(Accion.OFF, accionDe(d, ROOM2));
            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Empate de déficit: nunca se enciende más de una")
        void empate_soloUnaEnciende() {
            core.procesar(lectura(ROOM1, 18.0, lunes(10, 0)));
            List<Decision> d = core.procesar(lectura(ROOM2, 18.0, lunes(10, 1)));

            assertEquals(1.0, potenciaEncendida(d), 0.0001);
        }

        @Test
        @DisplayName("Empate de déficit: el resultado no depende del orden de llegada")
        void empate_noDependeDelOrdenDeLlegada() {
            List<Decision> room1Primero = correrEmpate(ROOM1, ROOM2);
            List<Decision> room2Primero = correrEmpate(ROOM2, ROOM1);

            assertEquals(accionDe(room1Primero, ROOM1), accionDe(room2Primero, ROOM1));
            assertEquals(accionDe(room1Primero, ROOM2), accionDe(room2Primero, ROOM2));
        }

        @Test
        @DisplayName("Mismo estado y mismo estímulo repetido -> siempre la misma decisión")
        void mismoEstimulo_mismaDecisionSiempre() {
            core.procesar(lectura(ROOM1, 18.0, lunes(10, 0)));
            List<Decision> referencia = core.procesar(lectura(ROOM2, 18.0, lunes(10, 1)));

            for (int i = 0; i < 20; i++) {
                List<Decision> d = core.procesar(lectura(ROOM2, 18.0, lunes(10, 1)));
                assertEquals(accionDe(referencia, ROOM1), accionDe(d, ROOM1), "Iteración " + i);
                assertEquals(accionDe(referencia, ROOM2), accionDe(d, ROOM2), "Iteración " + i);
            }
        }

        private List<Decision> correrEmpate(String primera, String segunda) {
            Core otro = nuevoCore(sitio, habitaciones);
            otro.procesar(lectura(primera, 18.0, lunes(10, 0)));
            return otro.procesar(lectura(segunda, 18.0, lunes(10, 0)));
        }
    }

    // =================================================================
    // 5. Actualización de la configuración del sitio
    // =================================================================
    @Nested
    @DisplayName("5. Configuración del sitio")
    class Configuracion {

        @Test
        @DisplayName("La nueva configuración reemplaza el inventario completo")
        void habitacionQuitada_noApareceEnLasDecisiones() {
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            configurar(3.7, new ConfiguracionHabitacion(ROOM2, "Dormitorio principal", 20.0, 0.8)); // sin room1
            List<Decision> d = core.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));

            assertTrue(d.stream().noneMatch(x -> x.idHabitacion().equals(ROOM1)),
                    "room1 ya no está configurada");
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Una habitación agregada por la configuración pasa a ser controlada")
        void habitacionNueva_pasaASerControlada() {
            configurar(3.7,
                    new ConfiguracionHabitacion(ROOM1, "Living", 21.5, 1.2),
                    new ConfiguracionHabitacion(ROOM2, "Dormitorio principal", 20.0, 0.8),
                    new ConfiguracionHabitacion(ROOM3, "Cocina", 19.0, 0.5));

            List<Decision> d = core.procesar(lectura(ROOM3, 10.0, lunes(10, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM3));
        }

        @Test
        @DisplayName("Cambiar la temperatura esperada cambia la decisión")
        void cambiaLaTemperaturaEsperada_cambiaLaDecision() {
            // 20.0 °C con esperada 21.5 -> ON
            assertEquals(Accion.ON, accionDe(core.procesar(lectura(ROOM1, 20.0, lunes(10, 0))), ROOM1));

            // Nueva esperada 18.0 °C -> con 20.0 °C ya no hace falta calefaccionar
            configurar(3.7,
                    new ConfiguracionHabitacion(ROOM1, "Living", 18.0, 1.2),
                    new ConfiguracionHabitacion(ROOM2, "Dormitorio principal", 20.0, 0.8));
            List<Decision> d = core.procesar(lectura(ROOM1, 20.0, lunes(10, 1)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Nueva configuración con la franja punta cambiada se aplica en caliente")
        void cambiaLaFranjaPunta_seAplica() {
            FranjaHoraria nueva = new FranjaHoraria(LocalTime.of(10, 0), LocalTime.of(12, 0), DiasTarifa.HABILES);
            core.procesar(new Estimulo.ConfiguracionActualizada(new Sitio("casa", 3.7, nueva), habitaciones));

            // 10:30 era horario normal con la franja anterior; ahora es punta
            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, lunes(10, 30)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }
    }

    // =================================================================
    // Helpers
    // =================================================================

    /** Crea un Core nuevo y le carga la configuración. */
    private Core nuevoCore(Sitio s, List<ConfiguracionHabitacion> hs) {
        Core c = new Core(new MayorDeficitPrimero());
        c.procesar(new Estimulo.ConfiguracionActualizada(s, hs));
        return c;
    }

    /** Reemplaza la configuración del core de este test (misma franja punta). */
    private void configurar(double potenciaContratadaKW, ConfiguracionHabitacion... nuevas) {
        sitio = new Sitio(sitio.id(), potenciaContratadaKW, PUNTA);
        habitaciones = List.of(nuevas);
        core.procesar(new Estimulo.ConfiguracionActualizada(sitio, habitaciones));
    }

    private Estimulo.NuevaLectura lectura(String idHabitacion, double tC, Instant ts) {
        return new Estimulo.NuevaLectura(idHabitacion, tC, ts);
    }

    /** Acción decidida para una habitación; falla si no hay decisión sobre ella. */
    private Accion accionDe(List<Decision> decisiones, String idHabitacion) {
        return decisiones.stream()
                .filter(d -> d.idHabitacion().equals(idHabitacion))
                .map(Decision::accion)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No hay decisión para " + idHabitacion));
    }

    /** Suma de potencia (kW) de las habitaciones que quedan en ON. */
    private double potenciaEncendida(List<Decision> decisiones) {
        return decisiones.stream()
                .filter(d -> d.accion() == Accion.ON)
                .mapToDouble(d -> habitaciones.stream()
                        .filter(h -> h.id().equals(d.idHabitacion()))
                        .mapToDouble(ConfiguracionHabitacion::potenciaKW)
                        .findFirst().orElse(0.0))
                .sum();
    }

    /** Lunes 5/10/2026, hora local. */
    private Instant lunes(int hora, int minuto) {
        return LocalDateTime.of(2026, 10, 5, hora, minuto).atZone(ZoneId.systemDefault()).toInstant();
    }

    /** Sábado 10/10/2026, hora local. */
    private Instant sabado(int hora, int minuto) {
        return LocalDateTime.of(2026, 10, 10, hora, minuto).atZone(ZoneId.systemDefault()).toInstant();
    }
}