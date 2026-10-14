package uy.edu.utec.iiss.engine.adaptador;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.MayorDeficitPrimero;
import uy.edu.utec.iiss.core.cliente.CoreClient;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Sitio;
import uy.edu.utec.iiss.engine.adaptador.CoreAdapter.HabitacionControlada;
import uy.edu.utec.iiss.engine.switches.ClienteSwitch;
import uy.edu.utec.iiss.engine.switches.EstadoSwitch;
import uy.edu.utec.iiss.engine.switches.SwitchNoDisponibleException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del adaptador entre el engine y el core. Usan el core REAL y switches
 * falsos en memoria (recuerdan su estado como manda el §4): no hace falta
 * Spring, ni el broker, ni red. Salvo donde se prueba el hilo de switches, las
 * pasadas corren en un executor sincrónico ({@code Runnable::run}), así cada
 * comando ya se envió cuando vuelve la llamada.
 */
@DisplayName("CoreAdapter (engine <-> core <-> switches)")
class CoreAdapterTest {

    private static final String URL = "http://switches:8081";
    private static final HabitacionControlada LIVING = new HabitacionControlada("living", 21.0, 1.2, "sw-living", URL);
    private static final HabitacionControlada DORMITORIO = new HabitacionControlada("dormitorio", 20.0, 0.8, "sw-dorm", URL);

    /** Lunes 5/10/2026 en hora de Uruguay. */
    private static Instant lunes(int hora, int minuto) {
        return LocalDateTime.of(2026, 10, 5, hora, minuto).atZone(Sitio.ZONA_URUGUAY).toInstant();
    }

    private static Sitio sitio(double tope) {
        return new Sitio("s", tope, new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES));
    }

    /** Switches falsos: recuerdan su estado, anotan comandos y consultas, y se pueden "caer". */
    static class SwitchesFalsos implements ClienteSwitch {
        final List<String> comandos = new ArrayList<>();
        final List<String> consultas = new ArrayList<>();
        final Map<String, EstadoSwitch> estado = new HashMap<>();
        final Set<String> caidos = new HashSet<>();

        @Override
        public EstadoSwitch comandar(String url, String idSwitch, Accion accion) {
            if (caidos.contains(idSwitch)) throw new SwitchNoDisponibleException(idSwitch, "caído", null);
            comandos.add(idSwitch + ":" + accion);
            EstadoSwitch nuevo = accion == Accion.ON ? EstadoSwitch.ON : EstadoSwitch.OFF;
            estado.put(idSwitch, nuevo);
            return nuevo;
        }

        @Override
        public EstadoSwitch consultar(String url, String idSwitch) {
            if (caidos.contains(idSwitch)) throw new SwitchNoDisponibleException(idSwitch, "caído", null);
            consultas.add(idSwitch);
            return estado.getOrDefault(idSwitch, EstadoSwitch.DESCONOCIDO);
        }
    }

    /** Reloj real controlable, para probar la espera entre reintentos. */
    static class RelojManual extends Clock {
        Instant ahora = Instant.parse("2026-10-05T12:00:00Z");

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private final SwitchesFalsos switches = new SwitchesFalsos();
    private final RelojManual relojReal = new RelojManual();
    private CoreAdapter adaptador;

    @BeforeEach
    void setUp() {
        adaptador = new CoreAdapter(new CoreClient(new MayorDeficitPrimero()), switches, relojReal, Duration.ZERO, Runnable::run);
    }

    private void configurarYHabilitar() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        adaptador.habilitar();
        switches.comandos.clear();
        switches.consultas.clear();
    }

    private List<String> ordenados() {
        List<String> copia = new ArrayList<>(switches.comandos);
        copia.sort(null);
        return copia;
    }

    @Test
    @DisplayName("Deshabilitado (control DETENIDO) no comanda ni consulta ningún switch")
    void deshabilitado_noTocaLosSwitches() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));

        assertTrue(switches.comandos.isEmpty());
        assertTrue(switches.consultas.isEmpty());
    }

    @Test
    @DisplayName("Al habilitar consulta cada switch (§4) y, si está DESCONOCIDO, lo lleva a la decisión vigente")
    void habilitar_consultaYLuegoComanda() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));

        adaptador.habilitar();

        assertEquals(List.of("sw-living", "sw-dorm"), switches.consultas);
        assertEquals(List.of("sw-dorm:OFF", "sw-living:OFF"), ordenados());
    }

    @Test
    @DisplayName("Al habilitar, un switch que ya está como se decidió no se vuelve a comandar")
    void habilitar_switchYaCorrecto_noSeComanda() {
        switches.estado.put("sw-living", EstadoSwitch.OFF);
        switches.estado.put("sw-dorm", EstadoSwitch.OFF);
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));

        adaptador.habilitar();

        assertTrue(switches.comandos.isEmpty(), "no debía mandar nada: " + switches.comandos);
    }

    @Test
    @DisplayName("Una lectura bajo la esperada enciende solo el switch de esa habitación")
    void lecturaBajoLaEsperada_enciendeSoloEseSwitch() {
        configurarYHabilitar();

        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));

        assertEquals(List.of("sw-living:ON"), switches.comandos);
    }

    @Test
    @DisplayName("Si la decisión no cambia, no se repite el comando al switch")
    void mismaDecision_noRepiteElComando() {
        configurarYHabilitar();
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));
        switches.comandos.clear();

        adaptador.alLlegarLectura("living", 15.5, lunes(10, 1));

        assertTrue(switches.comandos.isEmpty(), "no debía mandar nada: " + switches.comandos);
    }

    @Test
    @DisplayName("Cuando la habitación llega a su temperatura, se apaga su switch")
    void alLlegarAlObjetivo_apagaElSwitch() {
        configurarYHabilitar();
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));
        switches.comandos.clear();

        adaptador.alLlegarLectura("living", 21.0, lunes(10, 5));

        assertEquals(List.of("sw-living:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Un tick al entrar en la franja punta apaga lo que estaba encendido")
    void tick_alEntrarEnPunta_apaga() {
        configurarYHabilitar();
        adaptador.alLlegarLectura("living", 15.0, lunes(16, 55));
        switches.comandos.clear();

        adaptador.alTick(lunes(17, 0));

        assertEquals(List.of("sw-living:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Un tick sin cambios de decisión no manda ningún comando")
    void tick_sinCambios_noManda() {
        configurarYHabilitar();
        adaptador.alLlegarLectura("living", 15.0, lunes(16, 30));
        switches.comandos.clear();

        adaptador.alTick(lunes(16, 35));

        assertTrue(switches.comandos.isEmpty(), "no debía mandar nada: " + switches.comandos);
    }

    @Test
    @DisplayName("Re-sincronizar el reloj hacia la punta apaga lo encendido")
    void sincronizarEnPunta_apaga() {
        configurarYHabilitar();
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));
        switches.comandos.clear();

        adaptador.sincronizar(lunes(18, 0), 1);

        assertEquals(List.of("sw-living:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Una lectura de una habitación desconocida se ignora sin romper nada")
    void habitacionDesconocida_seIgnora() {
        configurarYHabilitar();

        assertDoesNotThrow(() -> adaptador.alLlegarLectura("roomX", 15.0, lunes(10, 0)));

        assertTrue(switches.comandos.isEmpty(), "no debía mandar nada: " + switches.comandos);
    }

    @Test
    @DisplayName("Si un switch está caído, no impide comandar a los demás ni lanza excepción")
    void switchCaido_noImpideComandarAlResto() {
        switches.caidos.add("sw-living");
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        assertDoesNotThrow(adaptador::habilitar);
        switches.comandos.clear();

        assertDoesNotThrow(() -> adaptador.alLlegarLectura("living", 15.0, lunes(10, 0)));
        assertDoesNotThrow(() -> adaptador.alLlegarLectura("dormitorio", 15.0, lunes(10, 1)));

        assertEquals(List.of("sw-dorm:ON"), switches.comandos);
    }

    @Test
    @DisplayName("Un comando que falló se reintenta en la próxima decisión, cuando el switch vuelve")
    void comandoFallido_seReintenta() {
        switches.caidos.add("sw-living");
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        adaptador.habilitar();                                               // el OFF de living falla
        switches.caidos.clear();
        switches.comandos.clear();

        adaptador.alLlegarLectura("dormitorio", 21.0, lunes(10, 0));        // living sigue OFF

        assertEquals(List.of("sw-living:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("A un switch que viene fallando no se lo reintenta antes de la espera (no traba el lazo)")
    void switchQueFalla_seEsperaAntesDeReintentar() {
        adaptador = new CoreAdapter(new CoreClient(new MayorDeficitPrimero()), switches, relojReal, Duration.ofSeconds(5), Runnable::run);
        switches.caidos.add("sw-living");
        adaptador.configurar(sitio(3.7), List.of(LIVING));
        adaptador.habilitar();                                               // falla: espera hasta +5 s
        switches.caidos.clear();

        adaptador.alTick(lunes(10, 0));
        assertTrue(switches.comandos.isEmpty(), "reintentó antes de la espera: " + switches.comandos);

        relojReal.ahora = relojReal.ahora.plusSeconds(6);
        adaptador.alTick(lunes(10, 1));
        assertEquals(List.of("sw-living:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Con el hilo de switches real, habilitar y decidir no esperan a un switch lento; los comandos salen después")
    void hiloDeSwitches_unSwitchLentoNoDemoraAlQueLlama() throws Exception {
        CountDownLatch liberar = new CountDownLatch(1);
        List<String> comandos = new CopyOnWriteArrayList<>();
        ClienteSwitch lento = new ClienteSwitch() {
            @Override
            public EstadoSwitch comandar(String url, String idSwitch, Accion accion) {
                comandos.add(idSwitch + ":" + accion);
                return accion == Accion.ON ? EstadoSwitch.ON : EstadoSwitch.OFF;
            }

            @Override
            public EstadoSwitch consultar(String url, String idSwitch) {
                try {
                    liberar.await();                                         // el switch no responde hasta que se libera
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return EstadoSwitch.DESCONOCIDO;
            }
        };
        try (CoreAdapter asincrono = new CoreAdapter(new CoreClient(new MayorDeficitPrimero()), lento, relojReal, Duration.ZERO)) {
            asincrono.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));

            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                asincrono.habilitar();
                asincrono.alLlegarLectura("living", 15.0, lunes(10, 0));
                assertEquals(Accion.ON, asincrono.estados().get(0).decision());
            });
            assertTrue(comandos.isEmpty(), "comandó antes de que el switch respondiera: " + comandos);

            liberar.countDown();
            long limite = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (comandos.size() < 2 && System.nanoTime() < limite) {
                Thread.sleep(10);
            }
            assertEquals(List.of("sw-living:ON", "sw-dorm:OFF"), comandos);
        }
    }

    @Test
    @DisplayName("Reconciliar: si el switch se reinició (quedó DESCONOCIDO) se le vuelve a mandar la decisión")
    void reconciliar_switchReiniciado_seVuelveAComandar() {
        configurarYHabilitar();
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));             // ON
        switches.estado.remove("sw-living");                                 // el switch reinició
        switches.comandos.clear();

        adaptador.reconciliar();

        assertEquals(List.of("sw-living:ON"), switches.comandos);
    }

    @Test
    @DisplayName("Reconciliar: si alguien movió el switch a mano, se lo devuelve a lo decidido")
    void reconciliar_switchMovidoAMano_seCorrige() {
        configurarYHabilitar();
        switches.estado.put("sw-dorm", EstadoSwitch.ON);                     // lo prendieron a mano
        switches.comandos.clear();

        adaptador.reconciliar();

        assertEquals(List.of("sw-dorm:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Reconfigurar con un tope más bajo apaga de inmediato lo que ya no entra")
    void reconfigurar_conTopeMasBajo_apagaLoQueNoEntra() {
        configurarYHabilitar();
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));
        adaptador.alLlegarLectura("dormitorio", 15.0, lunes(10, 1));       // ambas ON
        switches.comandos.clear();

        adaptador.configurar(sitio(1.5), List.of(LIVING, DORMITORIO));      // 1.2 + 0.8 = 2.0 > 1.5

        assertEquals(List.of("sw-dorm:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Una habitación quitada de la configuración deja de recibir comandos")
    void reconfigurar_quitandoUnaHabitacion_noSeLeEnviaMas() {
        configurarYHabilitar();
        adaptador.configurar(sitio(3.7), List.of(LIVING));
        switches.comandos.clear();

        adaptador.alLlegarLectura("dormitorio", 15.0, lunes(10, 0));
        adaptador.reconciliar();

        assertTrue(switches.comandos.isEmpty(), "no debía mandar nada: " + switches.comandos);
    }

    @Test
    @DisplayName("Si una habitación cambia de switch, el estado se envía al switch nuevo")
    void reconfigurar_cambiandoElSwitch_vuelveAEnviar() {
        configurarYHabilitar();

        adaptador.configurar(sitio(3.7), List.of(new HabitacionControlada("living", 21.0, 1.2, "sw-nuevo", URL), DORMITORIO));

        assertEquals(List.of("sw-nuevo:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Una configuración que el core rechaza no cambia nada en el adaptador")
    void configuracionRechazada_noCambiaNada() {
        configurarYHabilitar();
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));             // ON

        assertThrows(IllegalArgumentException.class, () -> adaptador.configurar(sitio(3.7),
                List.of(new HabitacionControlada("living", 21.0, -1, "sw-x", URL))));

        assertEquals(List.of("living", "dormitorio"), adaptador.estados().stream().map(CoreAdapter.EstadoControlado::idHabitacion).toList());
        assertEquals(Accion.ON, adaptador.estados().get(0).decision());
    }

    @Test
    @DisplayName("El diagnóstico informa decisión, motivo y estado conocido del switch de cada habitación")
    void estados_informaDecisionYMotivo() {
        configurarYHabilitar();
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));

        CoreAdapter.EstadoControlado living = adaptador.estados().get(0);

        assertEquals(Accion.ON, living.decision());
        assertEquals("CALEFACCIONANDO", living.motivo());
        assertEquals(EstadoSwitch.ON, living.estadoSwitch());
        assertEquals(15.0, living.temperaturaActual(), 1e-9);
    }
}
