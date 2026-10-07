package uy.edu.utec.iiss.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.Estimulo;
import uy.edu.utec.iiss.core.model.Sitio;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uy.edu.utec.iiss.core.CoreTestFixtures.*;

/**
 * Tests unitarios (TDD) complementarios del core, Iteración 4.
 *
 * Complementan a {@link CoreTest} cubriendo lo que la letra pide y ahí no
 * está cubierto: arranque sin información, estado interno del modelo,
 * reconfiguración en caliente, casos adicionales de tarifa punta (días,
 * cambio de día, restitución con tope), tope de consumo (punto flotante,
 * tope 0), criterio de selección con más de dos habitaciones, invariantes
 * sobre una secuencia larga de estímulos y aislamiento del core respecto
 * del exterior.
 */
class CoreComplementarioTest {

    // =================================================================
    // A. Arranque: sin configuración o sin información
    // =================================================================
    @Nested
    @DisplayName("A. Arranque sin configuración o sin lecturas")
    class Arranque {

        @Test
        @DisplayName("Sin configuración, una lectura no produce decisiones ni falla")
        void sinConfiguracion_lecturaNoDecideNada() {
            Core core = new Core(new MayorDeficitPrimero());

            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, lunes(10, 0)));

            assertTrue(d.isEmpty());
        }

        @Test
        @DisplayName("Sin configuración, un Tick no produce decisiones ni falla")
        void sinConfiguracion_tickNoDecideNada() {
            Core core = new Core(new MayorDeficitPrimero());

            assertTrue(core.procesar(tick(lunes(10, 0))).isEmpty());
        }

        @Test
        @DisplayName("Configuración sin habitaciones -> sin decisiones")
        void configuracionSinHabitaciones_noDecideNada() {
            Core core = nuevoCore(3.7);

            assertTrue(core.procesar(tick(lunes(10, 0))).isEmpty());
        }

        @Test
        @DisplayName("Habitación sin ninguna lectura todavía -> OFF (no hay dato para calefaccionar)")
        void sinLecturas_todasOff() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8));

            List<Decision> d = core.procesar(tick(lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Accion.OFF, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Lectura de una sola habitación: la que no tiene lectura queda OFF")
        void unaConLecturaYOtraSin_soloEnciendeLaPrimera() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8));

            List<Decision> d = core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.OFF, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Lectura de una habitación no configurada: se ignora y no altera al resto")
        void lecturaDeHabitacionDesconocida_seIgnora() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8));

            List<Decision> d = core.procesar(lectura("roomX", 5.0, lunes(10, 0)));

            assertEquals(2, d.size());
            assertTrue(d.stream().noneMatch(x -> x.idHabitacion().equals("roomX")));
            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Accion.OFF, accionDe(d, ROOM2));
        }
    }

    // =================================================================
    // B. Estado interno (modelo del estado)
    // =================================================================
    @Nested
    @DisplayName("B. Estado interno del core")
    class EstadoInterno {

        @Test
        @DisplayName("Estado inicial: sin temperatura conocida y apagada")
        void estadoInicial() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2));

            EstadoHabitacion e = core.estadoDe(ROOM1);

            assertNotNull(e);
            assertEquals(ROOM1, e.idHabitacion());
            assertNull(e.temperaturaActual());
            assertFalse(e.encendida());
        }

        @Test
        @DisplayName("Tras una lectura con déficit: guarda la temperatura y queda encendida")
        void trasLecturaConDeficit() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2));
            core.procesar(lectura(ROOM1, 19.0, lunes(10, 0)));

            EstadoHabitacion e = core.estadoDe(ROOM1);

            assertEquals(19.0, e.temperaturaActual(), 0.0001);
            assertTrue(e.encendida());
        }

        @Test
        @DisplayName("Prevalece la última lectura recibida")
        void prevaleceLaUltimaLectura() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2));
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            core.procesar(lectura(ROOM1, 22.0, lunes(10, 5)));

            EstadoHabitacion e = core.estadoDe(ROOM1);

            assertEquals(22.0, e.temperaturaActual(), 0.0001);
            assertFalse(e.encendida());
        }

        @Test
        @DisplayName("Dentro de la punta el estado marca la habitación apagada aunque conserve la temperatura")
        void enPunta_estadoApagadoConTemperatura() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2));
            core.procesar(lectura(ROOM1, 15.0, lunes(18, 0)));

            EstadoHabitacion e = core.estadoDe(ROOM1);

            assertEquals(15.0, e.temperaturaActual(), 0.0001);
            assertFalse(e.encendida());
        }

        @Test
        @DisplayName("Habitación inexistente -> estadoDe devuelve null")
        void habitacionInexistente_devuelveNull() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2));

            assertNull(core.estadoDe("roomX"));
        }
    }

    // =================================================================
    // C. Reconfiguración en caliente
    // =================================================================
    @Nested
    @DisplayName("C. Reconfiguración del sitio")
    class Reconfiguracion {

        @Test
        @DisplayName("Reconfigurar conserva la temperatura de las habitaciones que siguen existiendo")
        void conservaLasTemperaturasDeLasQueSiguen() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8));
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            core.procesar(config(3.7, hab(ROOM1, 21.5, 1.2)));            // se quita room2
            List<Decision> d = core.procesar(tick(lunes(10, 5)));          // sin lectura nueva

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Una habitación quitada y vuelta a agregar arranca sin temperatura conocida")
        void habitacionQuitadaYReagregada_pierdeSuTemperatura() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8));
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            core.procesar(config(3.7, hab(ROOM2, 20.0, 0.8)));             // se quita room1
            core.procesar(config(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8)));  // vuelve
            List<Decision> d = core.procesar(tick(lunes(10, 5)));

            assertNull(core.estadoDe(ROOM1).temperaturaActual());
            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Reconfigurar con una lista vacía deja de decidir sobre todas")
        void configuracionVacia_sinDecisiones() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2));
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));

            List<Decision> d = core.procesar(config(3.7));

            assertTrue(d.isEmpty());
        }

        @Test
        @DisplayName("Reconfigurar durante la punta no enciende nada")
        void reconfigurarEnPunta_siguePermaneciendoApagado() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2));
            core.procesar(lectura(ROOM1, 10.0, lunes(18, 0)));

            List<Decision> d = core.procesar(config(3.7, hab(ROOM1, 21.5, 1.2)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Si el nuevo consumo de una habitación supera la contratada, deja de encenderse")
        void cambiaElConsumoDeUnaHabitacion_seRespetaElTope() {
            Core core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2));
            assertEquals(Accion.ON, accionDe(core.procesar(lectura(ROOM1, 15.0, lunes(10, 0))), ROOM1));

            List<Decision> d = core.procesar(config(3.7, hab(ROOM1, 21.5, 5.0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Si sube la potencia contratada, pueden encenderse habitaciones que antes no entraban")
        void subeLaContratada_entranMasHabitaciones() {
            Core core = nuevoCore(1.0, hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0));
            core.procesar(lectura(ROOM1, 15.0, lunes(10, 0)));
            List<Decision> antes = core.procesar(lectura(ROOM2, 15.0, lunes(10, 1)));
            assertEquals(1, cantidadEncendidas(antes));

            List<Decision> despues = core.procesar(config(3.0, hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0)));

            assertEquals(2, cantidadEncendidas(despues));
        }
    }

    // =================================================================
    // D. Tarifa punta: casos adicionales
    // =================================================================
    @Nested
    @DisplayName("D. Tarifa punta: casos adicionales")
    class PuntaAdicional {

        private Core core;

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            core = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2), hab(ROOM2, 20.0, 0.8));
        }

        @Test
        @DisplayName("Cada día hábil (lunes a viernes) a las 18:00 hay punta")
        void todosLosDiasHabiles_hayPunta() {
            for (int dia = 5; dia <= 9; dia++) {
                Core c = nuevoCore(3.7, hab(ROOM1, 21.5, 1.2));
                List<Decision> d = c.procesar(lectura(ROOM1, 10.0, dia(dia, 18, 0)));
                assertEquals(Accion.OFF, accionDe(d, ROOM1), "día " + dia + " de octubre");
            }
        }

        @Test
        @DisplayName("El domingo a las 18:00 no hay punta (tarifa HABILES)")
        void domingo_noHayPunta() {
            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, domingo(18, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Último instante de la punta (22:59) sigue cortado")
        void a22_59_siguePunta() {
            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, lunes(22, 59)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Madrugada de un día hábil (03:00) no es punta")
        void madrugada_noEsPunta() {
            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, lunes(3, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Ticks repetidos dentro de la punta mantienen todo apagado")
        void ticksRepetidosEnPunta_siguenApagadas() {
            core.procesar(lectura(ROOM1, 10.0, lunes(16, 30)));           // ON
            core.procesar(tick(lunes(17, 0)));                            // corte

            for (int minuto = 1; minuto < 60; minuto += 7) {
                List<Decision> d = core.procesar(tick(lunes(18, minuto)));
                assertEquals(0, cantidadEncendidas(d), "minuto " + minuto);
            }
        }

        @Test
        @DisplayName("Viernes en punta con déficit y sábado de madrugada (sin punta): se restituye")
        void cambioDeDia_viernesPuntaSabadoSinPunta() {
            core.procesar(lectura(ROOM1, 10.0, viernes(22, 0)));          // en punta: OFF

            List<Decision> d = core.procesar(tick(sabado(0, 30)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("Al salir de la punta la restitución respeta el tope de consumo")
        void alSalirDePunta_respetaElTope() {
            Core c = nuevoCore(3.7,
                    hab(ROOM1, 21.0, 1.5), hab(ROOM2, 21.0, 1.5), hab(ROOM3, 21.0, 1.5));
            c.procesar(lectura(ROOM1, 10.0, lunes(18, 0)));
            c.procesar(lectura(ROOM2, 10.0, lunes(18, 1)));
            c.procesar(lectura(ROOM3, 10.0, lunes(18, 2)));

            List<Decision> d = c.procesar(tick(lunes(23, 0)));

            // 3 x 1.5 = 4.5 kW > 3.7 kW: solo pueden volver 2 (3.0 kW)
            assertEquals(2, cantidadEncendidas(d));
        }

        @Test
        @DisplayName("Al salir de la punta, con tope para una sola, vuelve la de mayor déficit")
        void alSalirDePunta_vuelveLaDeMayorDeficit() {
            Core c = nuevoCore(1.0, hab(ROOM1, 21.0, 1.0), hab(ROOM2, 21.0, 1.0));
            c.procesar(lectura(ROOM1, 19.0, lunes(18, 0)));               // déficit 2
            c.procesar(lectura(ROOM2, 12.0, lunes(18, 1)));               // déficit 9

            List<Decision> d = c.procesar(tick(lunes(23, 0)));

            assertEquals(Accion.ON, accionDe(d, ROOM2));
            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }
    }

    // =================================================================
    // E. Consumo máximo: casos adicionales
    // =================================================================
    @Nested
    @DisplayName("E. Consumo máximo: casos adicionales")
    class ConsumoMaximoAdicional {

        @Test
        @DisplayName("Contratada 0 kW: nada se enciende")
        void contratadaCero_nadaEnciende() {
            Core core = nuevoCore(0.0, hab(ROOM1, 21.5, 1.2));

            List<Decision> d = core.procesar(lectura(ROOM1, 10.0, lunes(10, 0)));

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
        }

        @Test
        @DisplayName("La suma igual a la contratada no se rechaza por error de punto flotante (0.1 + 0.2 vs 0.3)")
        void sumaIgualConErrorDePuntoFlotante_esValida() {
            Core core = nuevoCore(0.3, hab(ROOM1, 21.0, 0.1), hab(ROOM2, 21.0, 0.2));
            core.procesar(lectura(ROOM1, 10.0, lunes(10, 0)));

            List<Decision> d = core.procesar(lectura(ROOM2, 10.0, lunes(10, 1)));

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
        }

        @Test
        @DisplayName("Una habitación que no entra por potencia no bloquea a las que sí entran")
        void laQueNoEntra_noBloqueaALasQueSiEntran() {
            // tope 2.0: room1 (1.5) entra; room2 (0.8) ya no entra (2.3); room3 (0.5) sí (2.0)
            Core core = nuevoCore(2.0,
                    hab(ROOM1, 21.0, 1.5), hab(ROOM2, 21.0, 0.8), hab(ROOM3, 21.0, 0.5));
            core.procesar(lectura(ROOM1, 12.0, lunes(10, 0)));            // déficit 9
            core.procesar(lectura(ROOM2, 13.0, lunes(10, 1)));            // déficit 8
            List<Decision> d = core.procesar(lectura(ROOM3, 14.0, lunes(10, 2))); // déficit 7

            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.OFF, accionDe(d, ROOM2));
            assertEquals(Accion.ON, accionDe(d, ROOM3));
        }
    }

    // =================================================================
    // F. Criterio de selección con más de dos habitaciones
    // =================================================================
    @Nested
    @DisplayName("F. Criterio de selección (3 habitaciones)")
    class CriterioTresHabitaciones {

        @Test
        @DisplayName("Tope para dos de tres: reciben energía las dos de mayor déficit")
        void topeParaDos_gananLasDosDeMayorDeficit() {
            Core core = nuevoCore(3.0,
                    hab(ROOM1, 21.0, 1.5), hab(ROOM2, 21.0, 1.5), hab(ROOM3, 21.0, 1.5));
            core.procesar(lectura(ROOM1, 20.0, lunes(10, 0)));            // déficit 1
            core.procesar(lectura(ROOM2, 16.0, lunes(10, 1)));            // déficit 5
            List<Decision> d = core.procesar(lectura(ROOM3, 18.0, lunes(10, 2))); // déficit 3

            assertEquals(Accion.OFF, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM2));
            assertEquals(Accion.ON, accionDe(d, ROOM3));
        }

        @Test
        @DisplayName("Cuando una de las favorecidas llega a su objetivo, entra la que esperaba")
        void unaLlegaAlObjetivo_entraLaQueEsperaba() {
            Core core = nuevoCore(3.0,
                    hab(ROOM1, 21.0, 1.5), hab(ROOM2, 21.0, 1.5), hab(ROOM3, 21.0, 1.5));
            core.procesar(lectura(ROOM1, 20.0, lunes(10, 0)));
            core.procesar(lectura(ROOM2, 16.0, lunes(10, 1)));
            core.procesar(lectura(ROOM3, 18.0, lunes(10, 2)));            // room1 queda OFF

            List<Decision> d = core.procesar(lectura(ROOM2, 21.0, lunes(10, 30)));  // room2 llegó

            assertEquals(Accion.OFF, accionDe(d, ROOM2));
            assertEquals(Accion.ON, accionDe(d, ROOM1));
            assertEquals(Accion.ON, accionDe(d, ROOM3));
        }

        @Test
        @DisplayName("Empate total entre tres con lugar para dos: siempre se encienden dos y la elección es estable")
        void empateTotal_dosEncendidasYEstable() {
            Core core = nuevoCore(3.0,
                    hab(ROOM1, 21.0, 1.5), hab(ROOM2, 21.0, 1.5), hab(ROOM3, 21.0, 1.5));
            core.procesar(lectura(ROOM1, 18.0, lunes(10, 0)));
            core.procesar(lectura(ROOM2, 18.0, lunes(10, 1)));
            List<Decision> referencia = core.procesar(lectura(ROOM3, 18.0, lunes(10, 2)));

            assertEquals(2, cantidadEncendidas(referencia));
            for (int i = 0; i < 10; i++) {
                List<Decision> d = core.procesar(tick(lunes(10, 3 + i)));
                assertEquals(accionDe(referencia, ROOM1), accionDe(d, ROOM1));
                assertEquals(accionDe(referencia, ROOM2), accionDe(d, ROOM2));
                assertEquals(accionDe(referencia, ROOM3), accionDe(d, ROOM3));
            }
        }
    }

    // =================================================================
    // G. Invariantes sobre una secuencia larga de estímulos
    // =================================================================
    @Nested
    @DisplayName("G. Invariantes en una secuencia larga")
    class Invariantes {

        @Test
        @DisplayName("500 estímulos (semilla fija, varios días): tope, punta y déficit siempre se respetan")
        void invariantesEnSecuenciaLarga() {
            List<ConfiguracionHabitacion> habs = List.of(
                    hab(ROOM1, 21.0, 1.2), hab(ROOM2, 20.0, 0.8), hab(ROOM3, 19.0, 1.5));
            double[] contratadas = {1.0, 2.0, 3.7};
            double contratada = 3.7;

            Core core = new Core(new MayorDeficitPrimero());
            core.procesar(new Estimulo.ConfiguracionActualizada(new Sitio("s", contratada, PUNTA), habs));

            Random rnd = new Random(42);                       // semilla fija: reproducible
            Map<String, Double> temperaturas = new HashMap<>();
            Instant ahora = lunes(0, 0);

            for (int paso = 0; paso < 500; paso++) {
                ahora = ahora.plusSeconds(60L * (1 + rnd.nextInt(90)));
                int tipo = rnd.nextInt(100);
                Estimulo e;
                if (tipo < 70) {
                    ConfiguracionHabitacion h = habs.get(rnd.nextInt(habs.size()));
                    double t = 5 + rnd.nextInt(24);
                    temperaturas.put(h.id(), t);
                    e = lectura(h.id(), t, ahora);
                } else if (tipo < 95) {
                    e = tick(ahora);
                } else {
                    contratada = contratadas[rnd.nextInt(contratadas.length)];
                    e = new Estimulo.ConfiguracionActualizada(new Sitio("s", contratada, PUNTA), habs);
                }

                List<Decision> d = core.procesar(e);

                String ctx = "paso " + paso + " (" + e + ")";
                assertTrue(potenciaEncendida(d, habs) <= contratada + 1e-9,
                        "Tope superado en " + ctx + ": " + potenciaEncendida(d, habs) + " kW > " + contratada);

                var local = ahora.atZone(ZoneId.systemDefault());
                boolean enPunta = PUNTA.incluye(local.getDayOfWeek(), local.toLocalTime());
                if (enPunta) {
                    assertEquals(0, cantidadEncendidas(d), "Hay habitaciones ON en punta en " + ctx);
                }

                for (ConfiguracionHabitacion h : habs) {
                    if (accionDe(d, h.id()) == Accion.ON) {
                        Double t = temperaturas.get(h.id());
                        assertNotNull(t, h.id() + " encendida sin lectura en " + ctx);
                        assertTrue(t < h.temperaturaEsperada(),
                                h.id() + " encendida sin déficit en " + ctx);
                    }
                }
            }
        }
    }

    // =================================================================
    // H. El core no se comunica con el exterior
    // =================================================================
    @Nested
    @DisplayName("H. Aislamiento del core")
    class Aislamiento {

        @Test
        @DisplayName("Core, criterio y modelo solo usan tipos de java.* o del propio core (sin REST/MQTT/Spring)")
        void elCoreNoDependeDeTiposExternos() {
            Class<?>[] clases = {
                    Core.class, CriterioPrioridad.class, MayorDeficitPrimero.class,
                    Estimulo.class, Estimulo.NuevaLectura.class, Estimulo.Tick.class,
                    Estimulo.ConfiguracionActualizada.class, Decision.class,
                    EstadoHabitacion.class, ConfiguracionHabitacion.class, Sitio.class
            };

            for (Class<?> c : clases) {
                for (var f : c.getDeclaredFields()) {
                    assertPermitido(c, f.getType());
                }
                for (Method m : c.getDeclaredMethods()) {
                    assertPermitido(c, m.getReturnType());
                    for (Class<?> p : m.getParameterTypes()) assertPermitido(c, p);
                }
                for (var k : c.getDeclaredConstructors()) {
                    for (Class<?> p : k.getParameterTypes()) assertPermitido(c, p);
                }
            }
        }

        private void assertPermitido(Class<?> duenio, Class<?> tipo) {
            Class<?> base = tipo.isArray() ? tipo.getComponentType() : tipo;
            String nombre = base.getName();
            boolean ok = base.isPrimitive()
                    || nombre.startsWith("java.")
                    || nombre.startsWith("uy.edu.utec.iiss.core.");
            assertTrue(ok, duenio.getSimpleName() + " depende de un tipo externo al core: " + nombre);
        }
    }
}