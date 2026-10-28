package uy.edu.utec.iiss.engine.adaptador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uy.edu.utec.iiss.engine.adaptador.EstimulosFixtures.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.Core;
import uy.edu.utec.iiss.core.MayorDeficitPrimero;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Motivo;
import uy.edu.utec.iiss.core.model.ParametrosControl;
import uy.edu.utec.iiss.core.model.Sitio;

/**
 * Tests del procesamiento de estímulos: la secuencia lectura / tick / configuración / INICIAR, el
 * estado que se va acumulando y las decisiones que salen. Hasta la iteración 4 era {@code CoreTest}
 * y probaba {@code Core.procesar}; con el core sin estado ese manejo pasó a {@link
 * CoreAdapter#procesar}, y los tests se mudaron con él sin cambiar lo que verifican (las decisiones
 * las sigue tomando {@link Core#decidir}). Las validaciones de configuración inválida pasaron a
 * {@code ValidadorInventarioTest}.
 *
 * <p>Cada grupo corresponde a una característica que pide la letra o a un escenario del entorno al
 * que el core tiene que adaptarse: 1. Decidir qué habitación recibe energía. 2. Cortar en tarifa
 * punta y restituir al salir. 3. Nunca superar el consumo máximo contratado. 4. Criterio no azaroso
 * cuando la carga no alcanza para todas. 5. Actualización de la configuración del sitio. 6.
 * Histéresis: no conmutar el relé con cada décima. 7. Lecturas defectuosas: fuera de orden,
 * imposibles, de habitaciones desconocidas. 8. Termostato caído: lecturas vencidas. 9. Reloj del
 * control: ticks desordenados, re-sincronización y tiempo acelerado. 10. Invariantes que valen para
 * cualquier secuencia de estímulos.
 *
 * <p>Contrato: cada llamada a {@link CoreAdapter#procesar} devuelve una {@link Decision} (ON u OFF,
 * con su {@link Motivo}) por cada habitación configurada, en el orden del inventario. Calendario:
 * ver {@link EstimulosFixtures}.
 *
 * <p>El criterio de adecuación de este conjunto está en docs/tests/criterio-tests.md.
 */
class CoreAdapterEstimulosTest {

    /** Por defecto: contratada 3.7 kW, room1 (21.5 °C, 1.2 kW) y room2 (20.0 °C, 0.8 kW). */
    private CoreAdapter adaptador;

    @BeforeEach
    void setUp() {
        adaptador = nuevoAdaptador(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8));
    }

    private void configurar(double contratadaKW, ConfiguracionHabitacion... habitaciones) {
        adaptador.procesar(config(contratadaKW, habitaciones));
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
        @DisplayName("Por debajo de su temperatura esperada -> ON (calefaccionando)")
        void bajoLaEsperada_enciende() {
            List<Decision> d = adaptador.procesar(lectura(ROOM1, 19.0, lunes(10, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Motivo.CALEFACCIONANDO, motivoDe(d, ROOM1));
        }

        @Test
        @DisplayName("En su temperatura esperada (o más) -> OFF (satisfecha)")
        void enLaEsperada_noEnciende() {
            List<Decision> d = adaptador.procesar(lectura(ROOM1, 21.5, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Motivo.SATISFECHA, motivoDe(d, ROOM1));
        }

        @Test
        @DisplayName("Estaba encendida y alcanza la esperada -> se apaga")
        void encendidaQueAlcanzaLaEsperada_seApaga() {
            adaptador.procesar(lectura(ROOM1, 19.0, lunes(10, 0)));

            List<Decision> d = adaptador.procesar(lectura(ROOM1, 21.5, lunes(10, 5)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Una con déficit y otra satisfecha -> solo enciende la que tiene déficit")
        void unaConDeficitYOtraSatisfecha() {
            adaptador.procesar(lectura(ROOM1, 21.5, lunes(10, 0)));

            List<Decision> d = adaptador.procesar(lectura(ROOM2, 17.0, lunes(10, 1)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName(
                "Habitación sin ninguna lectura todavía -> OFF (no hay dato para calefaccionar)")
        void sinLecturaTodavia_noEnciende() {
            List<Decision> d = adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM2));
            assertEquals(Motivo.SIN_LECTURA, motivoDe(d, ROOM2));
        }

        @Test
        @DisplayName("Sin configuración, cualquier estímulo no produce decisiones ni falla")
        void sinConfiguracion_noDecideNada() {
            CoreAdapter sinConfig = adaptadorCon(new Core(new MayorDeficitPrimero()));

            assertTrue(sinConfig.procesar(lectura(ROOM1, 10.0, lunes(10, 0))).isEmpty());
            assertTrue(sinConfig.procesar(tick(lunes(10, 1))).isEmpty());
            assertTrue(sinConfig.procesar(sincronizar(lunes(10, 2), 1)).isEmpty());
        }

        @Test
        @DisplayName("Prevalece la última lectura recibida")
        void prevaleceLaUltimaLectura() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            adaptador.procesar(lectura(ROOM1, 22.0, lunes(10, 5)));

            EstadoHabitacion e = adaptador.estadoDe(ROOM1);

            assertEquals(22.0, e.temperaturaActual(), 0.0001);
            assertEquals(lunes(10, 5), e.ultimaLectura());
            assertFalse(e.encendida());
        }

        @Test
        @DisplayName("El estado de una habitación inexistente es null")
        void estadoDeHabitacionInexistente_esNull() {
            assertNull(adaptador.estadoDe("no-existe"));
        }
    }

    // =================================================================
    // 2. Tarifa punta: cortar al entrar, restituir al salir
    // =================================================================
    @Nested
    @DisplayName("2. Tarifa punta: cortar y restituir")
    class TarifaPunta {

        @Test
        @DisplayName("Al entrar en punta (17:00), la habitación encendida se apaga por tarifa")
        void alEntrarEnPunta_seApagaLaEncendida() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(16, 55))); // ON

            List<Decision> d = adaptador.procesar(tick(lunes(17, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Motivo.TARIFA_PUNTA, motivoDe(d, ROOM1));
        }

        @Test
        @DisplayName("Un minuto antes de la punta (16:59) todavía se calefacciona")
        void unMinutoAntesDeLaPunta_calefacciona() {
            List<Decision> d = adaptador.procesar(lectura(ROOM1, 15.0, lunes(16, 59)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Una lectura con gran déficit dentro de la punta no enciende")
        void lecturaEnPunta_noEnciendeAunqueHayaDeficit() {
            List<Decision> d = adaptador.procesar(lectura(ROOM1, 5.0, lunes(18, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Motivo.TARIFA_PUNTA, motivoDe(d, ROOM1));
        }

        @Test
        @DisplayName("Al salir de la punta (23:00), se restituye la energía a la que tiene déficit")
        void alSalirDePunta_seRestituye() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(16, 55))); // ON
            adaptador.procesar(tick(lunes(17, 0))); // corte por punta
            adaptador.procesar(
                    lectura(ROOM1, 14.0, lunes(22, 58))); // sigue llegando dato, sigue cortada

            List<Decision> d = adaptador.procesar(tick(lunes(23, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName(
                "En punta, una habitación satisfecha figura como satisfecha (no como cortada por"
                        + " tarifa)")
        void enPunta_laSatisfechaNoEsCortadaPorTarifa() {
            List<Decision> d = adaptador.procesar(lectura(ROOM1, 25.0, lunes(18, 0)));

            assertEquals(Motivo.SATISFECHA, motivoDe(d, ROOM1));
        }

        @Test
        @DisplayName("Tarifa HABILES: el sábado y el domingo a las 18:00 no hay punta")
        void finDeSemanaConTarifaHabiles_noHayPunta() {
            assertEquals(
                    Accion.ON,
                    accionDe(adaptador.procesar(lectura(ROOM1, 10.0, sabado(18, 0))), ROOM1));
            assertEquals(
                    Accion.ON,
                    accionDe(adaptador.procesar(lectura(ROOM1, 10.0, domingo(18, 0))), ROOM1));
        }

        @Test
        @DisplayName("Tarifa HABILES: el viernes a las 18:00 sí hay punta")
        void viernesConTarifaHabiles_hayPunta() {
            assertEquals(
                    Accion.OFF,
                    accionDe(adaptador.procesar(lectura(ROOM1, 10.0, viernes(18, 0))), ROOM1));
        }

        @Test
        @DisplayName("Tarifa TODOS: el sábado a las 18:00 también hay punta")
        void sabadoConTarifaTodos_hayPunta() {
            Sitio todos =
                    new Sitio(
                            "s",
                            3.7,
                            new FranjaHoraria(
                                    LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.TODOS));
            adaptador.procesar(config(todos, hab(ROOM1, 21.5, 1.2)));

            List<Decision> d = adaptador.procesar(lectura(ROOM1, 10.0, sabado(18, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName(
                "La punta se mide en la zona del sitio aunque la JVM esté en otra (Docker en UTC)")
        void laPuntaSeMideEnLaZonaDelSitio() {
            TimeZone original = TimeZone.getDefault();
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            try {
                // 22:59 en Uruguay = 01:59 UTC del día siguiente: todavía es punta
                List<Decision> d =
                        adaptador.procesar(
                                lectura(ROOM1, 10.0, Instant.parse("2026-10-06T01:59:00Z")));

                assertEquals(Accion.OFF, accionDe(d, ROOM1));
            } finally {
                TimeZone.setDefault(original);
            }
        }

        @Test
        @DisplayName("Con un sitio en UTC, 18:00Z es punta (la zona viaja con el sitio)")
        void sitioEnUtc_laPuntaSeMideEnUtc() {
            adaptador.procesar(
                    config(new Sitio("s", 3.7, PUNTA, ZoneOffset.UTC), hab(ROOM1, 21.5, 1.2)));

            assertEquals(
                    Accion.OFF,
                    accionDe(
                            adaptador.procesar(
                                    lectura(ROOM1, 10.0, Instant.parse("2026-10-05T18:00:00Z"))),
                            ROOM1));
            assertEquals(
                    Accion.ON,
                    accionDe(
                            adaptador.procesar(
                                    lectura(ROOM1, 10.0, Instant.parse("2026-10-05T23:00:00Z"))),
                            ROOM1));
        }
    }

    // =================================================================
    // 3. Nunca superar el consumo máximo contratado
    // =================================================================
    @Nested
    @DisplayName("3. Nunca superar el consumo máximo")
    class ConsumoMaximo {

        @Test
        @DisplayName(
                "Tres habitaciones de 1.5 kW con 3.7 kW contratados: no pueden estar las tres ON")
        void sumaEncendidaNoSuperaLaContratada() {
            ConfiguracionHabitacion[] hs = {
                hab(ROOM1, 21.0, 1.5), hab(ROOM2, 21.0, 1.5), hab(ROOM3, 21.0, 1.5)
            };
            configurar(3.7, hs);

            adaptador.procesar(lectura(ROOM1, 10.0, lunes(10, 0)));
            adaptador.procesar(lectura(ROOM2, 10.0, lunes(10, 1)));
            List<Decision> d = adaptador.procesar(lectura(ROOM3, 10.0, lunes(10, 2)));

            assertEquals(2, cantidadEncendidas(d));
            assertTrue(potencia(d, hs) <= 3.7);
        }

        @Test
        @DisplayName("La que queda afuera figura como 'sin potencia disponible'")
        void laQueQuedaAfuera_motivoSinPotencia() {
            configurar(1.0, hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0));
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            List<Decision> d = adaptador.procesar(lectura(ROOM2, 18.0, lunes(10, 1)));

            assertEquals(Motivo.SIN_POTENCIA_DISPONIBLE, motivoDe(d, ROOM2));
        }

        @Test
        @DisplayName("Suma exactamente igual a la contratada está permitida")
        void sumaIgualALaContratada_esValida() {
            ConfiguracionHabitacion[] hs = {hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8)};
            configurar(2.0, hs);

            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            List<Decision> d = adaptador.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName(
                "Suma igual a la contratada con errores de redondeo binario (0.1 + 0.2 = 0.3) está"
                        + " permitida")
        void sumaIgualConRedondeoBinario_esValida() {
            configurar(0.3, hab(ROOM1, 21.0, 0.1), hab(ROOM2, 21.0, 0.2));

            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            List<Decision> d = adaptador.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));

            assertEquals(2, cantidadEncendidas(d));
        }

        @Test
        @DisplayName("Habitación cuya potencia supera la contratada nunca se enciende")
        void habitacionMasGrandeQueLaContratada_nuncaEnciende() {
            configurar(3.7, hab(ROOM3, 18.0, 5.0));

            List<Decision> d = adaptador.procesar(lectura(ROOM3, 5.0, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM3));
            assertEquals(Motivo.EXCEDE_CONTRATADA, motivoDe(d, ROOM3));
        }

        @Test
        @DisplayName("Una habitación que no entra por potencia no bloquea a las que sí entran")
        void laQueNoEntra_noBloqueaALasQueSiEntran() {
            // tope 2.0: room1 (1.5) entra; room2 (0.8) ya no (2.3); room3 (0.5) sí (2.0)
            configurar(2.0, hab(ROOM1, 21.0, 1.5), hab(ROOM2, 21.0, 0.8), hab(ROOM3, 21.0, 0.5));
            adaptador.procesar(lectura(ROOM1, 12.0, lunes(10, 0))); // déficit 9
            adaptador.procesar(lectura(ROOM2, 13.0, lunes(10, 1))); // déficit 8
            List<Decision> d = adaptador.procesar(lectura(ROOM3, 14.0, lunes(10, 2))); // déficit 7

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.OFF, accionDe(d, ROOM2));
            assertEquals(Accion.ON, accionDe(d, ROOM3));
        }

        @Test
        @DisplayName(
                "Bajar la potencia contratada en caliente apaga de inmediato lo que ya no entra")
        void bajarLaContratadaEnCaliente_apagaLoQueNoEntra() {
            ConfiguracionHabitacion[] hs = {hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8)};
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            adaptador.procesar(lectura(ROOM2, 15.0, lunes(10, 1))); // ambas ON: 2.0 kW

            List<Decision> d = adaptador.procesar(config(1.5, hs));

            assertTrue(potencia(d, hs) <= 1.5);
            assertEquals(1, cantidadEncendidas(d));
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
            adaptador.procesar(lectura(ROOM1, 20.0, lunes(10, 0))); // déficit 1.0
            List<Decision> d =
                    adaptador.procesar(lectura(ROOM2, 15.0, lunes(10, 1))); // déficit 6.0

            assertEquals(Accion.ON, accionDe(d, ROOM2));
            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Cuando la favorecida llega a su objetivo, la energía pasa a la otra")
        void alLlegarAlObjetivo_laEnergiaPasaALaOtra() {
            adaptador.procesar(lectura(ROOM1, 20.0, lunes(10, 0)));
            adaptador.procesar(lectura(ROOM2, 15.0, lunes(10, 1))); // room2 recibe

            List<Decision> d =
                    adaptador.procesar(lectura(ROOM2, 21.0, lunes(10, 5))); // room2 llegó

            assertEquals(Accion.OFF, accionDe(d, ROOM2));
            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName(
                "Empate de déficit sin ninguna encendida: se enciende una sola y no depende del"
                        + " orden de llegada")
        void empate_unaSolaYSinDependerDelOrden() {
            List<Decision> room1Primero = correrEmpate(ROOM1, ROOM2);
            List<Decision> room2Primero = correrEmpate(ROOM2, ROOM1);

            assertEquals(1, cantidadEncendidas(room1Primero));
            assertEquals(room1Primero, room2Primero);
        }

        /**
         * Las dos lecturas llegan en punta (nada se enciende); la decisión se toma al salir de la
         * punta.
         */
        private List<Decision> correrEmpate(String primera, String segunda) {
            CoreAdapter otro = nuevoAdaptador(1.0, a, b);
            otro.procesar(lectura(primera, 18.0, lunes(22, 55)));
            otro.procesar(lectura(segunda, 18.0, lunes(22, 56)));
            return otro.procesar(tick(lunes(23, 0)));
        }

        @Test
        @DisplayName(
                "Anti-oscilación: una diferencia chica de déficit no le quita la energía a la que"
                        + " ya está calefaccionando")
        void diferenciaChica_noProvocaIntercambio() {
            adaptador.procesar(lectura(ROOM1, 18.0, lunes(10, 0))); // room1 ON (déficit 3.0)

            List<Decision> d =
                    adaptador.procesar(
                            lectura(ROOM2, 17.8, lunes(10, 1))); // déficit 3.2: apenas mayor

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.OFF, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName(
                "Anti-oscilación: si la diferencia de déficit supera el margen, la energía cambia"
                        + " de habitación")
        void diferenciaGrande_siProvocaIntercambio() {
            adaptador.procesar(lectura(ROOM1, 18.0, lunes(10, 0))); // room1 ON (déficit 3.0)

            List<Decision> d =
                    adaptador.procesar(lectura(ROOM2, 17.0, lunes(10, 1))); // déficit 4.0

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName(
                "Turnos: con el tiempo las dos reciben energía (ninguna queda sin calefaccionar"
                        + " para siempre)")
        void conElTiempo_ambasRecibenEnergia() {
            // room1 se calienta 0.2 °C por paso mientras está ON y se enfría 0.1 si está OFF; idem
            // room2.
            double t1 = 18.0, t2 = 18.0;
            boolean room1Recibio = false, room2Recibio = false;
            Instant ts = lunes(10, 0);
            adaptador.procesar(lectura(ROOM1, t1, ts));
            List<Decision> d = adaptador.procesar(lectura(ROOM2, t2, ts));
            for (int paso = 0; paso < 60; paso++) {
                boolean on1 = accionDe(d, ROOM1) == Accion.ON;
                boolean on2 = accionDe(d, ROOM2) == Accion.ON;
                room1Recibio |= on1;
                room2Recibio |= on2;
                t1 += on1 ? 0.2 : -0.1;
                t2 += on2 ? 0.2 : -0.1;
                ts = ts.plusSeconds(60);
                adaptador.procesar(lectura(ROOM1, t1, ts));
                d = adaptador.procesar(lectura(ROOM2, t2, ts));
                assertTrue(cantidadEncendidas(d) <= 1);
            }

            assertTrue(room1Recibio && room2Recibio);
        }
    }

    // =================================================================
    // 5. Actualización de la configuración del sitio
    // =================================================================
    @Nested
    @DisplayName("5. Configuración del sitio")
    class Configuracion {

        @Test
        @DisplayName(
                "La nueva configuración reemplaza el inventario: la habitación quitada ya no"
                        + " decide")
        void habitacionQuitada_noApareceEnLasDecisiones() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            configurar(3.7, hab(ROOM2, 20.0, 0.8)); // sin room1
            List<Decision> d = adaptador.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));

            assertTrue(d.stream().noneMatch(x -> x.idHabitacion().equals(ROOM1)));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Cambiar la temperatura esperada cambia la decisión")
        void cambiaLaTemperaturaEsperada_cambiaLaDecision() {
            assertEquals(
                    Accion.ON,
                    accionDe(adaptador.procesar(lectura(ROOM1, 20.0, lunes(10, 0))), ROOM1));

            configurar(3.7, hab(ROOM1, 18.0, 1.2), hab(ROOM2, 20.0, 0.8));
            List<Decision> d = adaptador.procesar(lectura(ROOM1, 20.0, lunes(10, 1)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Reconfigurar conserva la temperatura conocida de las habitaciones que siguen")
        void reconfigurar_conservaLaTemperaturaConocida() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            List<Decision> d =
                    adaptador.procesar(config(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM3, 20.0, 1.0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Motivo.SIN_LECTURA, motivoDe(d, ROOM3));
        }

        @Test
        @DisplayName(
                "Una habitación quitada y vuelta a agregar arranca sin lectura (no revive datos"
                        + " viejos)")
        void habitacionReagregada_arrancaSinLectura() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            configurar(3.7, hab(ROOM2, 20.0, 0.8));

            List<Decision> d =
                    adaptador.procesar(config(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8)));

            assertEquals(Motivo.SIN_LECTURA, motivoDe(d, ROOM1));
        }

        // Las configuraciones inválidas (potencias <= 0 o NaN, ids duplicados o vacíos,
        // temperatura no numérica) las rechaza ValidadorInventario antes de llegar acá: ver
        // ValidadorInventarioTest (potencias, duplicados, camposObligatorios,
        // loQueAntesValidabaElCore).

        @Test
        @DisplayName("Un sitio sin habitaciones es válido y no decide nada")
        void sitioSinHabitaciones_noDecideNada() {
            assertTrue(adaptador.procesar(config(3.7)).isEmpty());
        }
    }

    // =================================================================
    // 6. Histéresis
    // =================================================================
    @Nested
    @DisplayName("6. Histéresis")
    class Histeresis {

        @Test
        @DisplayName(
                "Apagada y apenas por debajo de la esperada (dentro de la banda) -> sigue apagada")
        void apagadaDentroDeLaBanda_noEnciende() {
            List<Decision> d =
                    adaptador.procesar(
                            lectura(ROOM1, 21.3, lunes(10, 0))); // esperada 21.5, banda 0.3

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Motivo.SATISFECHA, motivoDe(d, ROOM1));
        }

        @Test
        @DisplayName("Apagada y por debajo de la banda -> enciende")
        void apagadaDebajoDeLaBanda_enciende() {
            List<Decision> d = adaptador.procesar(lectura(ROOM1, 21.1, lunes(10, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Encendida dentro de la banda -> sigue encendida hasta llegar a la esperada")
        void encendidaDentroDeLaBanda_sigueEncendida() {
            adaptador.procesar(lectura(ROOM1, 20.0, lunes(10, 0))); // ON

            List<Decision> d = adaptador.procesar(lectura(ROOM1, 21.4, lunes(10, 5)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName(
                "Lecturas que oscilan alrededor de la esperada no hacen conmutar el relé en cada"
                        + " lectura")
        void oscilacionAlrededorDeLaEsperada_noConmutaCadaVez() {
            double[] lecturas = {21.4, 21.5, 21.4, 21.5, 21.4, 21.5};
            int cambios = 0;
            Accion anterior = Accion.OFF;
            Instant ts = lunes(10, 0);
            for (double t : lecturas) {
                ts = ts.plusSeconds(30);
                Accion ahora = accionDe(adaptador.procesar(lectura(ROOM1, t, ts)), ROOM1);
                if (ahora != anterior) {
                    cambios++;
                }
                anterior = ahora;
            }

            assertEquals(0, cambios);
        }

        @Test
        @DisplayName("Con histéresis 0 (configurable) cualquier déficit enciende")
        void histeresisCero_cualquierDeficitEnciende() {
            CoreAdapter sinBanda =
                    adaptadorCon(
                            new Core(
                                    new MayorDeficitPrimero(),
                                    new ParametrosControl(0.0, Duration.ofMinutes(10), -40, 85)));
            sinBanda.procesar(config(3.7, hab(ROOM1, 21.5, 1.2)));

            assertEquals(
                    Accion.ON,
                    accionDe(sinBanda.procesar(lectura(ROOM1, 21.4, lunes(10, 0))), ROOM1));
        }
    }

    // =================================================================
    // 7. Lecturas defectuosas
    // =================================================================
    @Nested
    @DisplayName("7. Lecturas defectuosas")
    class LecturasDefectuosas {

        @Test
        @DisplayName(
                "Una lectura más vieja que la última conocida (llegó fuera de orden) se descarta")
        void lecturaFueraDeOrden_seDescarta() {
            adaptador.procesar(lectura(ROOM1, 22.0, lunes(10, 5)));

            List<Decision> d = adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(22.0, adaptador.estadoDe(ROOM1).temperaturaActual(), 1e-9);
        }

        @Test
        @DisplayName("Una lectura NaN o infinita se descarta y se mantiene la anterior")
        void lecturaNoNumerica_seDescarta() {
            adaptador.procesar(lectura(ROOM1, 19.0, lunes(10, 0)));

            adaptador.procesar(lectura(ROOM1, Double.NaN, lunes(10, 1)));
            List<Decision> d =
                    adaptador.procesar(lectura(ROOM1, Double.POSITIVE_INFINITY, lunes(10, 2)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(19.0, adaptador.estadoDe(ROOM1).temperaturaActual(), 1e-9);
        }

        @Test
        @DisplayName(
                "Una lectura físicamente imposible (sensor roto: -100 °C o 150 °C) se descarta")
        void lecturaFueraDeRango_seDescarta() {
            List<Decision> frio = adaptador.procesar(lectura(ROOM1, -100.0, lunes(10, 0)));
            List<Decision> calor = adaptador.procesar(lectura(ROOM2, 150.0, lunes(10, 0)));

            assertEquals(Motivo.SIN_LECTURA, motivoDe(frio, ROOM1));
            assertEquals(Motivo.SIN_LECTURA, motivoDe(calor, ROOM2));
        }

        @Test
        @DisplayName(
                "Una lectura de una habitación que no está en el inventario no crea estado ni"
                        + " falla")
        void lecturaDeHabitacionDesconocida_seIgnora() {
            List<Decision> d = adaptador.procesar(lectura("roomX", 5.0, lunes(10, 0)));

            assertEquals(2, d.size());
            assertNull(adaptador.estadoDe("roomX"));
            // si después se agrega roomX, arranca sin lectura: la lectura vieja no quedó guardada
            List<Decision> conRoomX = adaptador.procesar(config(3.7, hab("roomX", 21.0, 1.0)));
            assertEquals(Motivo.SIN_LECTURA, motivoDe(conRoomX, "roomX"));
        }
    }

    // =================================================================
    // 8. Termostato caído: lecturas vencidas
    // =================================================================
    @Nested
    @DisplayName("8. Lecturas vencidas (termostato caído)")
    class LecturasVencidas {

        @Test
        @DisplayName(
                "Sin lecturas nuevas por más de la vigencia (10 min) -> se apaga por lectura"
                        + " vencida")
        void sinLecturasPorMasDeLaVigencia_seApaga() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0))); // ON

            List<Decision> d = adaptador.procesar(tick(lunes(10, 11)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Motivo.LECTURA_VENCIDA, motivoDe(d, ROOM1));
        }

        @Test
        @DisplayName("Justo en el límite de la vigencia la lectura todavía vale")
        void enElLimiteDeLaVigencia_todaviaVale() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            assertEquals(Accion.ON, accionDe(adaptador.procesar(tick(lunes(10, 10))), ROOM1));
        }

        @Test
        @DisplayName("Cuando el termostato vuelve a publicar, la habitación se recupera")
        void alVolverElTermostato_seRecupera() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            adaptador.procesar(tick(lunes(10, 30))); // vencida

            List<Decision> d = adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 31)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName(
                "La potencia que libera una habitación con lectura vencida pasa a otra que la"
                        + " necesita")
        void laPotenciaLiberadaPasaAOtra() {
            configurar(1.0, hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0));
            adaptador.procesar(lectura(ROOM1, 10.0, lunes(10, 0))); // room1 ON (gran déficit)
            adaptador.procesar(lectura(ROOM2, 19.0, lunes(10, 5))); // room2 espera

            List<Decision> d =
                    adaptador.procesar(lectura(ROOM2, 19.0, lunes(10, 12))); // room1 vencida

            assertEquals(Motivo.LECTURA_VENCIDA, motivoDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName(
                "Con tiempo acelerado x60, la vigencia no se escala: 5 min de control no vencen"
                        + " (vigencia 10 min de control)")
        void conTiempoAcelerado_laVigenciaNoSeEscala() {
            adaptador.procesar(sincronizar(lunes(10, 0), 60));
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            List<Decision> d = adaptador.procesar(tick(lunes(10, 5)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName(
                "Con tiempo acelerado x60, pasados 10 min de control sin lecturas (10 s reales)"
                        + " vencen")
        void conTiempoAcelerado_vencePorTiempoDeControl() {
            adaptador.procesar(sincronizar(lunes(1, 0), 60));
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(1, 0)));

            List<Decision> d = adaptador.procesar(tick(lunes(1, 11)));

            assertEquals(Motivo.LECTURA_VENCIDA, motivoDe(d, ROOM1));
        }
    }

    // =================================================================
    // 9. Reloj del control
    // =================================================================
    @Nested
    @DisplayName("9. Reloj del control")
    class RelojDelControl {

        @Test
        @DisplayName("Un tick atrasado (llegó tarde) no hace retroceder el reloj")
        void tickAtrasado_noRetrocedeElReloj() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(16, 55)));
            adaptador.procesar(tick(lunes(17, 1))); // punta

            List<Decision> d = adaptador.procesar(tick(lunes(16, 58))); // tick viejo

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Motivo.TARIFA_PUNTA, motivoDe(d, ROOM1));
        }

        @Test
        @DisplayName(
                "Re-sincronizar hacia atrás (INICIAR con otra fecha) sí mueve el reloj y saca de la"
                        + " punta")
        void resincronizarHaciaAtras_mueveElReloj() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(18, 0))); // en punta: OFF

            List<Decision> d = adaptador.procesar(sincronizar(lunes(9, 0), 1));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName(
                "Re-sincronizar conserva las temperaturas conocidas (no apaga todo hasta la próxima"
                        + " lectura)")
        void resincronizar_conservaLasTemperaturas() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0))); // ON

            List<Decision> d =
                    adaptador.procesar(sincronizar(lunes(10, 0).plus(Duration.ofDays(30)), 1));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName(
                "Re-sincronizar no revive una lectura que ya estaba vencida con el reloj anterior"
                        + " (D7); la vigente se conserva")
        void resincronizar_noReviveLecturasVencidas() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(10, 0))); // vencerá
            adaptador.procesar(lectura(ROOM2, 15.0, lunes(10, 8))); // sigue vigente
            adaptador.procesar(tick(lunes(10, 11))); // room1 tiene 11 min: vencida

            List<Decision> d = adaptador.procesar(sincronizar(lunes(14, 0), 1));

            assertEquals(Motivo.SIN_LECTURA, motivoDe(d, ROOM1));
            assertNull(adaptador.estadoDe(ROOM1).temperaturaActual());
            assertEquals(Accion.ON, accionDe(d, ROOM2));
            assertEquals(lunes(14, 0), adaptador.estadoDe(ROOM2).ultimaLectura());
        }

        @Test
        @DisplayName(
                "Después de re-sincronizar hacia atrás, las lecturas nuevas se aceptan (no se las"
                        + " toma como fuera de orden)")
        void despuesDeResincronizarHaciaAtras_lasLecturasNuevasSeAceptan() {
            adaptador.procesar(lectura(ROOM1, 15.0, lunes(12, 0)));
            adaptador.procesar(sincronizar(lunes(8, 0), 1));

            List<Decision> d = adaptador.procesar(lectura(ROOM1, 25.0, lunes(8, 1)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(25.0, adaptador.estadoDe(ROOM1).temperaturaActual(), 1e-9);
        }

        @Test
        @DisplayName("Un factor de tiempo no positivo se rechaza")
        void factorNoPositivo_seRechaza() {
            assertThrows(IllegalArgumentException.class, () -> sincronizar(lunes(10, 0), 0));
            assertThrows(IllegalArgumentException.class, () -> sincronizar(lunes(10, 0), -2));
            assertThrows(
                    IllegalArgumentException.class, () -> sincronizar(lunes(10, 0), Double.NaN));
        }
    }

    // =================================================================
    // 10. Invariantes
    // =================================================================
    @Nested
    @DisplayName("10. Invariantes ante cualquier secuencia de estímulos")
    class Invariantes {

        @Test
        @DisplayName("Siempre una decisión por habitación configurada, en el orden del inventario")
        void unaDecisionPorHabitacion_enOrden() {
            configurar(3.7, hab(ROOM3, 20.0, 1.0), hab(ROOM1, 20.0, 1.0), hab(ROOM2, 20.0, 1.0));

            List<Decision> d = adaptador.procesar(lectura(ROOM2, 10.0, lunes(10, 0)));

            assertEquals(
                    List.of(ROOM3, ROOM1, ROOM2), d.stream().map(Decision::idHabitacion).toList());
        }

        @Test
        @DisplayName(
                "Secuencia larga y variada (seudoaleatoria con semilla fija): nunca se supera la"
                        + " contratada y nunca se enciende en punta")
        void secuenciaLarga_respetaLosLimites() {
            ConfiguracionHabitacion[] hs = {
                hab(ROOM1, 21.5, 1.2),
                hab(ROOM2, 20.0, 0.8),
                hab(ROOM3, 22.0, 1.5),
                hab("room4", 19.0, 2.0)
            };
            configurar(3.0, hs);
            java.util.Random azar =
                    new java.util.Random(42); // semilla fija: el test es reproducible
            Instant ts = lunes(0, 0);
            List<String> ids = List.of(ROOM1, ROOM2, ROOM3, "room4");
            List<Decision> d = new ArrayList<>();
            for (int i = 0; i < 3000; i++) {
                ts = ts.plusSeconds(60 + azar.nextInt(120));
                d =
                        azar.nextInt(5) == 0
                                ? adaptador.procesar(tick(ts))
                                : adaptador.procesar(
                                        lectura(
                                                ids.get(azar.nextInt(ids.size())),
                                                10 + azar.nextDouble() * 15,
                                                ts));

                assertTrue(potencia(d, hs) <= 3.0 + 1e-9, "superó la contratada en " + ts);
                var local = ts.atZone(Sitio.ZONA_URUGUAY);
                if (PUNTA.incluye(local.getDayOfWeek(), local.toLocalTime())) {
                    assertEquals(0, cantidadEncendidas(d), "encendió en punta en " + ts);
                }
                assertEquals(hs.length, d.size());
            }
            assertNotNull(d);
        }

        @Test
        @DisplayName(
                "Determinismo: la misma secuencia de estímulos produce exactamente las mismas"
                        + " decisiones")
        void mismaSecuencia_mismasDecisiones() {
            List<List<Decision>> primera = correrSecuencia();
            List<List<Decision>> segunda = correrSecuencia();

            assertEquals(primera, segunda);
        }

        private List<List<Decision>> correrSecuencia() {
            CoreAdapter c =
                    nuevoAdaptador(
                            1.5,
                            hab(ROOM1, 21.0, 1.0),
                            hab(ROOM2, 21.0, 1.0),
                            hab(ROOM3, 21.0, 0.5));
            List<List<Decision>> salida = new ArrayList<>();
            salida.add(c.procesar(lectura(ROOM1, 18.0, lunes(10, 0))));
            salida.add(c.procesar(lectura(ROOM2, 17.0, lunes(10, 1))));
            salida.add(c.procesar(lectura(ROOM3, 16.0, lunes(10, 2))));
            salida.add(c.procesar(tick(lunes(17, 0))));
            salida.add(c.procesar(lectura(ROOM1, 16.0, lunes(22, 59))));
            salida.add(c.procesar(tick(lunes(23, 0))));
            return salida;
        }
    }
}
