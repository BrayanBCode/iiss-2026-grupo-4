package uy.edu.utec.iiss.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;

import java.time.Instant;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uy.edu.utec.iiss.core.CoreTestFixtures.*;

/**
 * Tests unitarios (TDD) del core, Iteración 4.
 *
 * Cada grupo corresponde a una característica que pide la letra:
 *   1. Decidir qué habitación recibe energía.
 *   2. Cortar en tarifa punta y restituir al salir.
 *   3. Nunca superar el consumo máximo contratado.
 *   4. Criterio no azaroso cuando la carga no alcanza para todas.
 *   5. Actualización de la configuración del sitio.
 *
 * Contrato: cada llamada a {@link Core#procesar} devuelve una {@link Decision}
 * (ON u OFF) por cada habitación configurada. Calendario: ver CoreTestFixtures.
 */
class CoreTest {

    /** Por defecto: contratada 3.7 kW, room1 (21.5 °C, 1.2 kW) y room2 (20.0 °C, 0.8 kW). */
    private Core core;

    @BeforeEach
    void setUp() {
        core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8));
    }

    private void configurar(double contratadaKW, ConfiguracionHabitacion... habitaciones) {
        core.procesar(config(contratadaKW, habitaciones));
    }

    private double potencia(List<Decision> decisiones, ConfiguracionHabitacion... habitaciones) {
        return potenciaEncendida(decisiones, List.of(habitaciones));
    }

    // =================================================================
    // 1. Decidir qué habitación recibe energía
    // =================================================================
    @Nested
    @DisplayName("1. Decidir qué habitación recibe energía")
    class DecisionBasica {

        @Test
        @DisplayName("Por debajo de su temperatura esperada -> ON")
        void bajoLaEsperada_enciende() {
            List<Decision> d = core.procesar(lectura(ROOM1, 19.0, lunes(10, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("En su temperatura esperada (o más) -> OFF")
        void enLaEsperada_noEnciende() {
            List<Decision> d = core.procesar(lectura(ROOM1, 21.5, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Estaba encendida y alcanza la esperada -> se apaga")
        void encendidaQueAlcanzaLaEsperada_seApaga() {
            core.procesar(lectura(ROOM1, 19.0, lunes(10, 0)));

            List<Decision> d = core.procesar(lectura(ROOM1, 21.5, lunes(10, 5)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Una con déficit y otra satisfecha -> solo enciende la que tiene déficit")
        void unaConDeficitYOtraSatisfecha() {
            core.procesar(lectura(ROOM1, 21.5, lunes(10, 0)));

            List<Decision> d = core.procesar(lectura(ROOM2, 17.0, lunes(10, 1)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Habitación sin ninguna lectura todavía -> OFF (no hay dato para calefaccionar)")
        void sinLecturaTodavia_noEnciende() {
            List<Decision> d = core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Sin configuración, una lectura no produce decisiones ni falla")
        void sinConfiguracion_noDecideNada() {
            Core sinConfig = new Core(new MayorDeficitPrimero());

            assertTrue(sinConfig.procesar(lectura(ROOM1, 10.0, lunes(10, 0))).isEmpty());
        }

        @Test
        @DisplayName("Prevalece la última lectura recibida")
        void prevaleceLaUltimaLectura() {
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            core.procesar(lectura(ROOM1, 22.0, lunes(10, 5)));

            EstadoHabitacion e = core.estadoDe(ROOM1);

            assertEquals(22.0, e.temperaturaActual(), 0.0001);
            assertFalse(e.encendida());
        }
    }

    // =================================================================
    // 2. Tarifa punta: cortar al entrar, restituir al salir
    // =================================================================
    // Los Tick de 17:00 y 23:00 ya cubren los dos límites de la franja
    // (17:00 inclusive, 23:00 exclusive).
    @Nested
    @DisplayName("2. Tarifa punta: cortar y restituir")
    class TarifaPunta {

        @Test
        @DisplayName("Al entrar en punta (17:00), la habitación encendida se apaga")
        void alEntrarEnPunta_seApagaLaEncendida() {
            core.procesar(lectura(ROOM1, 15.0, lunes(16, 30)));   // ON

            List<Decision> d = core.procesar(tick(lunes(17, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Una lectura con gran déficit dentro de la punta no enciende")
        void lecturaEnPunta_noEnciendeAunqueHayaDeficit() {
            List<Decision> d = core.procesar(lectura(ROOM1, 5.0, lunes(18, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Al salir de la punta (23:00), se restituye la energía a la que tiene déficit")
        void alSalirDePunta_seRestituye() {
            core.procesar(lectura(ROOM1, 15.0, lunes(16, 30)));   // ON
            core.procesar(tick(lunes(17, 0)));                    // corte por punta

            List<Decision> d = core.procesar(tick(lunes(23, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Tarifa HABILES: el sábado a las 18:00 no hay punta")
        void sabadoConTarifaHabiles_noHayPunta() {
            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, sabado(18, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("La punta se mide en hora de Uruguay aunque la JVM esté en UTC (Docker)")
        void laPuntaSeMideEnHoraDeUruguay() {
            TimeZone original = TimeZone.getDefault();
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            try {
                // 22:59 en Uruguay = 01:59 UTC del día siguiente: todavía es punta
                List<Decision> d = core.procesar(lectura(ROOM1, 10.0, Instant.parse("2026-10-06T01:59:00Z")));

                assertEquals(Accion.OFF, accionDe(d, ROOM1));
            } finally {
                TimeZone.setDefault(original);
            }
        }
    }

    // =================================================================
    // 3. Nunca superar el consumo máximo contratado
    // =================================================================
    @Nested
    @DisplayName("3. Nunca superar el consumo máximo")
    class ConsumoMaximo {

        @Test
        @DisplayName("Tres habitaciones de 1.5 kW con 3.7 kW contratados: no pueden estar las tres ON")
        void sumaEncendidaNoSuperaLaContratada() {
            ConfiguracionHabitacion[] hs = {
                    hab(ROOM1, 21.0, 1.5), hab(ROOM2, 21.0, 1.5), hab(ROOM3, 21.0, 1.5)};
            configurar(3.7, hs);

            core.procesar(lectura(ROOM1, 10.0, lunes(10, 0)));
            core.procesar(lectura(ROOM2, 10.0, lunes(10, 1)));
            List<Decision> d = core.procesar(lectura(ROOM3, 10.0, lunes(10, 2)));

            assertEquals(2, cantidadEncendidas(d));
            assertTrue(potencia(d, hs) <= 3.7);
        }

        @Test
        @DisplayName("Suma exactamente igual a la contratada está permitida")
        void sumaIgualALaContratada_esValida() {
            ConfiguracionHabitacion[] hs = {hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8)};
            configurar(2.0, hs);

            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            List<Decision> d = core.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Habitación cuya potencia supera la contratada nunca se enciende")
        void habitacionMasGrandeQueLaContratada_nuncaEnciende() {
            configurar(3.7, hab(ROOM3, 18.0, 5.0));

            List<Decision> d = core.procesar(lectura(ROOM3, 5.0, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM3));
        }

        @Test
        @DisplayName("Una habitación que no entra por potencia no bloquea a las que sí entran")
        void laQueNoEntra_noBloqueaALasQueSiEntran() {
            // tope 2.0: room1 (1.5) entra; room2 (0.8) ya no (2.3); room3 (0.5) sí (2.0)
            configurar(2.0, hab(ROOM1, 21.0, 1.5), hab(ROOM2, 21.0, 0.8), hab(ROOM3, 21.0, 0.5));
            core.procesar(lectura(ROOM1, 12.0, lunes(10, 0)));            // déficit 9
            core.procesar(lectura(ROOM2, 13.0, lunes(10, 1)));            // déficit 8
            List<Decision> d = core.procesar(lectura(ROOM3, 14.0, lunes(10, 2))); // déficit 7

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.OFF, accionDe(d, ROOM2));
            assertEquals(Accion.ON, accionDe(d, ROOM3));
        }
    }

    // =================================================================
    // 4. Criterio no azaroso cuando la carga no alcanza para todas
    // =================================================================
    @Nested
    @DisplayName("4. Criterio justificable y no azaroso")
    class CriterioDeSeleccion {

        // Dos habitaciones de 1.0 kW y solo 1.0 kW contratado: solo una puede estar ON.
        private final ConfiguracionHabitacion a = hab(ROOM1, 21.0, 1.0);
        private final ConfiguracionHabitacion b = hab(ROOM2, 21.0, 1.0);

        @BeforeEach
        void escenarioDeConflicto() {
            configurar(1.0, a, b);
        }

        @Test
        @DisplayName("Si no alcanza para todas, recibe energía la más lejos de su objetivo")
        void recibeLaDeMayorDeficit() {
            core.procesar(lectura(ROOM1, 20.0, lunes(10, 0)));                    // déficit 1.0
            List<Decision> d = core.procesar(lectura(ROOM2, 15.0, lunes(10, 1))); // déficit 6.0

            assertEquals(Accion.ON, accionDe(d, ROOM2));
            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Cuando la favorecida llega a su objetivo, la energía pasa a la otra")
        void alLlegarAlObjetivo_laEnergiaPasaALaOtra() {
            core.procesar(lectura(ROOM1, 20.0, lunes(10, 0)));
            core.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));                    // room2 recibe

            List<Decision> d = core.procesar(lectura(ROOM2, 21.0, lunes(10, 30))); // room2 llegó

            assertEquals(Accion.OFF, accionDe(d, ROOM2));
            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Empate de déficit: se enciende una sola y no depende del orden de llegada")
        void empate_unaSolaYSinDependerDelOrden() {
            List<Decision> room1Primero = correrEmpate(ROOM1, ROOM2);
            List<Decision> room2Primero = correrEmpate(ROOM2, ROOM1);

            assertEquals(1, cantidadEncendidas(room1Primero));
            assertEquals(accionDe(room1Primero, ROOM1), accionDe(room2Primero, ROOM1));
            assertEquals(accionDe(room1Primero, ROOM2), accionDe(room2Primero, ROOM2));
        }

        private List<Decision> correrEmpate(String primera, String segunda) {
            Core otro = nuevoCore(1.0, a, b);
            otro.procesar(lectura(primera, 18.0, lunes(10, 0)));
            return otro.procesar(lectura(segunda, 18.0, lunes(10, 1)));
        }
    }

    // =================================================================
    // 5. Actualización de la configuración del sitio
    // =================================================================
    @Nested
    @DisplayName("5. Configuración del sitio")
    class Configuracion {

        @Test
        @DisplayName("La nueva configuración reemplaza el inventario: la habitación quitada ya no decide")
        void habitacionQuitada_noApareceEnLasDecisiones() {
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            configurar(3.7, hab(ROOM2, 20.0, 0.8));                               // sin room1
            List<Decision> d = core.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));

            assertTrue(d.stream().noneMatch(x -> x.idHabitacion().equals(ROOM1)));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Cambiar la temperatura esperada cambia la decisión")
        void cambiaLaTemperaturaEsperada_cambiaLaDecision() {
            assertEquals(Accion.ON, accionDe(core.procesar(lectura(ROOM1, 20.0, lunes(10, 0))), ROOM1));

            configurar(3.7, hab(ROOM1, 18.0, 1.2), hab(ROOM2, 20.0, 0.8));
            List<Decision> d = core.procesar(lectura(ROOM1, 20.0, lunes(10, 1)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }
    }
}