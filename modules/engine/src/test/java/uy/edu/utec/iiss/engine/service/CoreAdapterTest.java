package uy.edu.utec.iiss.engine.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.MayorDeficitPrimero;
import uy.edu.utec.iiss.core.cliente.CoreClient;
import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Sitio;
import uy.edu.utec.iiss.engine.client.AccionadorSwitch;
import uy.edu.utec.iiss.engine.exception.SwitchStubNoDisponibleException;
import uy.edu.utec.iiss.engine.model.AccionSwitch;
import uy.edu.utec.iiss.engine.service.CoreAdapter.HabitacionControlada;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del adaptador entre el engine y el core. Usan el core REAL y un
 * "switch" falso que solo anota los comandos recibidos: no hace falta Spring,
 * ni el broker, ni el stub REST.
 */
@DisplayName("CoreAdapter (engine <-> core)")
class CoreAdapterTest {

    private static final HabitacionControlada LIVING = new HabitacionControlada("living", 21.0, 1.2, "sw-living");
    private static final HabitacionControlada DORMITORIO = new HabitacionControlada("dormitorio", 20.0, 0.8, "sw-dorm");

    /** Lunes 5/10/2026 en hora de Uruguay, como epoch en milisegundos (así llega del termostato). */
    private static long lunes(int hora, int minuto) {
        return LocalDateTime.of(2026, 10, 5, hora, minuto).atZone(Sitio.ZONA_URUGUAY).toInstant().toEpochMilli();
    }

    private static Sitio sitio(double tope) {
        return new Sitio("s", tope, new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES));
    }

    /** Switch falso: anota "idSwitch:ACCION" y puede simular que ciertos switches están caídos. */
    static class SwitchesFalsos implements AccionadorSwitch {
        final List<String> comandos = new ArrayList<>();
        final Set<String> caidos = new HashSet<>();

        @Override
        public void accionar(String idSwitch, AccionSwitch accion) {
            if (caidos.contains(idSwitch)) {
                throw new SwitchStubNoDisponibleException(idSwitch, new RuntimeException("caído"));
            }
            comandos.add(idSwitch + ":" + accion);
        }
    }

    private final SwitchesFalsos switches = new SwitchesFalsos();
    private final CoreAdapter adaptador = new CoreAdapter(new CoreClient(new MayorDeficitPrimero()), switches);

    private List<String> ordenados() {
        List<String> copia = new ArrayList<>(switches.comandos);
        copia.sort(null);
        return copia;
    }

    @Test
    @DisplayName("Al configurar, las habitaciones sin lectura quedan apagadas (estado inicial conocido)")
    void configurar_sinLecturas_apagaTodas() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));

        assertEquals(List.of("sw-dorm:OFF", "sw-living:OFF"), ordenados());
    }

    @Test
    @DisplayName("Una lectura bajo la esperada enciende solo el switch de esa habitación")
    void lecturaBajoLaEsperada_enciendeSoloEseSwitch() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        switches.comandos.clear();

        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));

        assertEquals(List.of("sw-living:ON"), switches.comandos);
    }

    @Test
    @DisplayName("Si la decisión no cambia, no se repite el comando al switch")
    void mismaDecision_noRepiteElComando() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));
        switches.comandos.clear();

        adaptador.alLlegarLectura("living", 15.5, lunes(10, 1));

        assertTrue(switches.comandos.isEmpty(), "no debía mandar nada: " + switches.comandos);
    }

    @Test
    @DisplayName("Cuando la habitación llega a su temperatura, se apaga su switch")
    void alLlegarAlObjetivo_apagaElSwitch() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));
        switches.comandos.clear();

        adaptador.alLlegarLectura("living", 21.0, lunes(10, 30));

        assertEquals(List.of("sw-living:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("El ts llega en milisegundos y la punta se mide en Uruguay: 16:59:59.999 enciende, 17:00:00.000 apaga")
    void tsEnMilisegundos_laPuntaSeMideAlMilisegundo() {
        long inicioPunta = 1_791_230_400_000L;   // 2026-10-05T20:00:00Z = lunes 17:00 en Uruguay (UTC-3)
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        switches.comandos.clear();

        adaptador.alLlegarLectura("living", 15.0, inicioPunta - 1);
        assertEquals(List.of("sw-living:ON"), switches.comandos);

        switches.comandos.clear();
        adaptador.alLlegarLectura("living", 15.0, inicioPunta);
        assertEquals(List.of("sw-living:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Un tick al entrar en la franja punta apaga lo que estaba encendido")
    void tick_alEntrarEnPunta_apaga() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        adaptador.alLlegarLectura("living", 15.0, lunes(16, 30));
        switches.comandos.clear();

        adaptador.alTick(lunes(17, 0));

        assertEquals(List.of("sw-living:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Un tick sin cambios de decisión no manda ningún comando")
    void tick_sinCambios_noManda() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        adaptador.alLlegarLectura("living", 15.0, lunes(16, 30));
        switches.comandos.clear();

        adaptador.alTick(lunes(16, 40));

        assertTrue(switches.comandos.isEmpty(), "no debía mandar nada: " + switches.comandos);
    }

    @Test
    @DisplayName("Una lectura de una habitación desconocida se ignora sin romper nada")
    void habitacionDesconocida_seIgnora() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        switches.comandos.clear();

        assertDoesNotThrow(() -> adaptador.alLlegarLectura("roomX", 15.0, lunes(10, 0)));

        assertTrue(switches.comandos.isEmpty(), "no debía mandar nada: " + switches.comandos);
    }

    @Test
    @DisplayName("Si un switch está caído, no impide comandar a los demás ni lanza excepción")
    void switchCaido_noImpideComandarAlResto() {
        switches.caidos.add("sw-living");
        assertDoesNotThrow(() -> adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO)));
        switches.comandos.clear();

        assertDoesNotThrow(() -> adaptador.alLlegarLectura("living", 15.0, lunes(10, 0)));
        assertDoesNotThrow(() -> adaptador.alLlegarLectura("dormitorio", 15.0, lunes(10, 1)));

        assertEquals(List.of("sw-dorm:ON"), switches.comandos);
    }

    @Test
    @DisplayName("Un comando que falló se reintenta en la próxima decisión, cuando el switch vuelve")
    void comandoFallido_seReintenta() {
        switches.caidos.add("sw-living");
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));   // el OFF de living falla
        switches.caidos.clear();
        switches.comandos.clear();

        adaptador.alLlegarLectura("dormitorio", 21.0, lunes(10, 0));      // decisión nueva: living sigue OFF

        assertEquals(List.of("sw-living:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Reconfigurar con un tope más bajo apaga de inmediato lo que ya no entra")
    void reconfigurar_conTopeMasBajo_apagaLoQueNoEntra() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        adaptador.alLlegarLectura("living", 15.0, lunes(10, 0));          // déficit 6
        adaptador.alLlegarLectura("dormitorio", 15.0, lunes(10, 1));      // déficit 5 -> ambas ON
        switches.comandos.clear();

        adaptador.configurar(sitio(1.5), List.of(LIVING, DORMITORIO));    // 1.2 + 0.8 = 2.0 > 1.5

        assertEquals(List.of("sw-dorm:OFF"), switches.comandos);
    }

    @Test
    @DisplayName("Una habitación quitada de la configuración deja de recibir comandos")
    void reconfigurar_quitandoUnaHabitacion_noSeLeEnviaMas() {
        adaptador.configurar(sitio(3.7), List.of(LIVING, DORMITORIO));
        adaptador.configurar(sitio(3.7), List.of(LIVING));
        switches.comandos.clear();

        adaptador.alLlegarLectura("dormitorio", 15.0, lunes(10, 0));

        assertTrue(switches.comandos.isEmpty(), "no debía mandar nada: " + switches.comandos);
    }

    @Test
    @DisplayName("Si una habitación cambia de switch, el estado se vuelve a enviar al switch nuevo")
    void reconfigurar_cambiandoElSwitch_vuelveAEnviar() {
        adaptador.configurar(sitio(3.7), List.of(LIVING));
        switches.comandos.clear();

        adaptador.configurar(sitio(3.7), List.of(new HabitacionControlada("living", 21.0, 1.2, "sw-nuevo")));

        assertEquals(List.of("sw-nuevo:OFF"), switches.comandos);
    }
}