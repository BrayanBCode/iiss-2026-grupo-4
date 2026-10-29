package uy.edu.utec.iiss.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uy.edu.utec.iiss.core.CoreTestFixtures.*;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.model.Accion;
import uy.edu.utec.iiss.core.model.ConfiguracionHabitacion;
import uy.edu.utec.iiss.core.model.Decision;
import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.EstadoHabitacion;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Motivo;
import uy.edu.utec.iiss.core.model.Sitio;

/**
 * Tests de la función de decisión {@link Core#decidir}, llamada directamente: cada test describe
 * una situación completa (potencia contratada, hora, y para cada habitación su temperatura
 * esperada, su potencia, su temperatura actual y si está encendida) y verifica cuáles reciben
 * energía y por qué.
 *
 * <p>Valores por defecto: histéresis 0.3 °C, vigencia de lecturas 10 min, margen de intercambio 0.5
 * °C, punta 17:00-23:00 en días hábiles (Uruguay).
 */
@DisplayName("Función de decisión: dada una situación, qué habitaciones prenden")
class DecidirTest {

    /** Lunes 10:00: hábil y fuera de la punta. */
    private static final Instant AHORA = lunes(10, 0);

    private final Core core = new Core(new MayorDeficitPrimero());

    // ---------- cómo se describe una situación ----------

    /** Una habitación de la situación: su configuración y cómo está en este momento. */
    private record Hab(
            String id,
            double esperada,
            double potenciaKW,
            Double actual,
            boolean encendida,
            Instant lectura) {

        /** La misma habitación, con su última lectura tomada en otro instante. */
        Hab leidaEn(Instant instante) {
            return new Hab(id, esperada, potenciaKW, actual, encendida, instante);
        }
    }

    private static Hab apagada(String id, double esperada, double potenciaKW, double actual) {
        return new Hab(id, esperada, potenciaKW, actual, false, AHORA);
    }

    private static Hab encendida(String id, double esperada, double potenciaKW, double actual) {
        return new Hab(id, esperada, potenciaKW, actual, true, AHORA);
    }

    private static Hab sinLectura(String id, double esperada, double potenciaKW) {
        return new Hab(id, esperada, potenciaKW, null, false, null);
    }

    private List<Decision> decidir(double contratadaKW, Instant ahora, Hab... habitaciones) {
        return decidir(new Sitio("sitio-test", contratadaKW, PUNTA), ahora, habitaciones);
    }

    private List<Decision> decidir(Sitio sitio, Instant ahora, Hab... habitaciones) {
        List<ConfiguracionHabitacion> config =
                Arrays.stream(habitaciones)
                        .map(
                                h ->
                                        new ConfiguracionHabitacion(
                                                h.id(), h.id(), h.esperada(), h.potenciaKW()))
                        .toList();
        Map<String, EstadoHabitacion> estados = new HashMap<>();
        for (Hab h : habitaciones) {
            estados.put(
                    h.id(), new EstadoHabitacion(h.id(), h.actual(), h.lectura(), h.encendida()));
        }
        return core.decidir(sitio, config, estados, ahora);
    }

    private static List<String> prendidas(List<Decision> decisiones) {
        return decisiones.stream()
                .filter(d -> d.accion() == Accion.ON)
                .map(Decision::idHabitacion)
                .toList();
    }

    // =================================================================
    @Nested
    @DisplayName("Una habitación")
    class UnaHabitacion {

        @Test
        @DisplayName("19 °C con esperada 21.5 -> prende (calefaccionando)")
        void bajoLaEsperada_prende() {
            List<Decision> d = decidir(3.7, AHORA, apagada("living", 21.5, 1.2, 19.0));

            assertEquals(Accion.ON, accionDe(d, "living"));
            assertEquals(Motivo.CALEFACCIONANDO, motivoDe(d, "living"));
        }

        @Test
        @DisplayName("Exactamente en la esperada -> no prende (satisfecha)")
        void enLaEsperada_noPrende() {
            List<Decision> d = decidir(3.7, AHORA, apagada("living", 21.5, 1.2, 21.5));

            assertEquals(Accion.OFF, accionDe(d, "living"));
            assertEquals(Motivo.SATISFECHA, motivoDe(d, "living"));
        }

        @Test
        @DisplayName("Por encima de la esperada, aunque esté encendida -> se apaga (satisfecha)")
        void porEncimaDeLaEsperada_seApaga() {
            List<Decision> d = decidir(3.7, AHORA, encendida("living", 21.5, 1.2, 23.0));

            assertEquals(Accion.OFF, accionDe(d, "living"));
            assertEquals(Motivo.SATISFECHA, motivoDe(d, "living"));
        }

        @Test
        @DisplayName("Sin ninguna lectura todavía -> no prende (sin lectura)")
        void sinLectura_noPrende() {
            List<Decision> d = decidir(3.7, AHORA, sinLectura("living", 21.5, 1.2));

            assertEquals(Accion.OFF, accionDe(d, "living"));
            assertEquals(Motivo.SIN_LECTURA, motivoDe(d, "living"));
        }

        @Test
        @DisplayName(
                "Su potencia (4 kW) supera la contratada (3 kW) -> nunca prende (excede"
                        + " contratada)")
        void masGrandeQueLaContratada_noPrende() {
            List<Decision> d = decidir(3.0, AHORA, apagada("living", 21.5, 4.0, 10.0));

            assertEquals(Accion.OFF, accionDe(d, "living"));
            assertEquals(Motivo.EXCEDE_CONTRATADA, motivoDe(d, "living"));
        }

        @Test
        @DisplayName("Un sitio sin habitaciones -> ninguna decisión")
        void sinHabitaciones_ningunaDecision() {
            assertEquals(List.of(), decidir(3.7, AHORA));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Varias habitaciones con déficit y la potencia no alcanza")
    class RepartoDePotencia {

        @Test
        @DisplayName(
                "5 con déficit, 1 kW cada una, 3 kW contratados -> prenden las 3 de mayor déficit")
        void cincoConDeficit_prendenLasTresMasFrias() {
            List<Decision> d =
                    decidir(
                            3.0,
                            AHORA,
                            apagada("cocina", 21.0, 1.0, 20.0), // déficit 1
                            apagada("living", 21.0, 1.0, 16.0), // déficit 5
                            apagada("bano", 21.0, 1.0, 19.0), // déficit 2
                            apagada("dormitorio", 21.0, 1.0, 17.0), // déficit 4
                            apagada("estudio", 21.0, 1.0, 18.0)); // déficit 3

            assertEquals(List.of("living", "dormitorio", "estudio"), prendidas(d));
            assertEquals(Motivo.SIN_POTENCIA_DISPONIBLE, motivoDe(d, "bano"));
            assertEquals(Motivo.SIN_POTENCIA_DISPONIBLE, motivoDe(d, "cocina"));
        }

        @Test
        @DisplayName(
                "5 con déficit y potencias distintas: la que no entra no bloquea a las siguientes"
                        + " que sí entran")
        void potenciasDistintas_laQueNoEntraNoBloquea() {
            // 3 kW contratados. Por déficit: a(2.0 kW) entra [2.0], b(1.5) no [3.5], c(1.0) entra
            // [3.0], d y e ya no.
            List<Decision> d =
                    decidir(
                            3.0,
                            AHORA,
                            apagada("a", 21.0, 2.0, 16.0), // déficit 5
                            apagada("b", 21.0, 1.5, 17.0), // déficit 4
                            apagada("c", 21.0, 1.0, 18.0), // déficit 3
                            apagada("d", 21.0, 0.5, 19.0), // déficit 2
                            apagada("e", 21.0, 1.0, 20.0)); // déficit 1

            assertEquals(List.of("a", "c"), prendidas(d));
            assertEquals(Motivo.SIN_POTENCIA_DISPONIBLE, motivoDe(d, "b"));
            assertEquals(Motivo.SIN_POTENCIA_DISPONIBLE, motivoDe(d, "d"));
            assertEquals(Motivo.SIN_POTENCIA_DISPONIBLE, motivoDe(d, "e"));
        }

        @Test
        @DisplayName(
                "3 con déficit que suman exactamente la contratada (1.2 + 0.8 + 1.7 = 3.7) ->"
                        + " prenden las 3")
        void sumaIgualALaContratada_prendenTodas() {
            List<Decision> d =
                    decidir(
                            3.7,
                            AHORA,
                            apagada("a", 21.0, 1.2, 18.0),
                            apagada("b", 21.0, 0.8, 18.0),
                            apagada("c", 21.0, 1.7, 18.0));

            assertEquals(3, prendidas(d).size());
        }

        @Test
        @DisplayName(
                "Suma igual a la contratada con redondeo binario (0.1 + 0.2 en 0.3 kW) -> prenden"
                        + " las 2")
        void sumaConRedondeoBinario_prendenTodas() {
            List<Decision> d =
                    decidir(
                            0.3,
                            AHORA,
                            apagada("a", 21.0, 0.1, 18.0),
                            apagada("b", 21.0, 0.2, 18.0));

            assertEquals(2, prendidas(d).size());
        }

        @Test
        @DisplayName(
                "Una de 5 kW con el mayor déficit no entra nunca (3 kW contratados): las demás se"
                        + " reparten la potencia")
        void unaQueExcedeLaContratada_noLeQuitaLugarALasDemas() {
            List<Decision> d =
                    decidir(
                            3.0,
                            AHORA,
                            apagada("galpon", 21.0, 5.0, 5.0), // déficit 16, pero excede
                            apagada("living", 21.0, 1.5, 18.0),
                            apagada("cocina", 21.0, 1.5, 19.0));

            assertEquals(Motivo.EXCEDE_CONTRATADA, motivoDe(d, "galpon"));
            assertEquals(List.of("living", "cocina"), prendidas(d));
        }

        @Test
        @DisplayName(
                "5 habitaciones mezcladas (satisfecha, sin lectura, vencida, 2 con déficit) y"
                        + " potencia para 1")
        void situacionMezclada_cadaUnaConSuMotivo() {
            List<Decision> d =
                    decidir(
                            1.0,
                            AHORA,
                            apagada("living", 21.0, 1.0, 22.0),
                            sinLectura("cocina", 21.0, 1.0),
                            apagada("bano", 21.0, 1.0, 10.0).leidaEn(lunes(9, 30)), // hace 30 min
                            apagada("dormitorio", 21.0, 1.0, 19.0),
                            apagada("estudio", 21.0, 1.0, 17.0));

            assertEquals(List.of("estudio"), prendidas(d));
            assertEquals(Motivo.SATISFECHA, motivoDe(d, "living"));
            assertEquals(Motivo.SIN_LECTURA, motivoDe(d, "cocina"));
            assertEquals(Motivo.LECTURA_VENCIDA, motivoDe(d, "bano"));
            assertEquals(Motivo.SIN_POTENCIA_DISPONIBLE, motivoDe(d, "dormitorio"));
            assertEquals(Motivo.CALEFACCIONANDO, motivoDe(d, "estudio"));
        }

        @Test
        @DisplayName("Hay potencia para todas -> prenden todas las que tienen déficit")
        void alcanzaParaTodas_prendenTodas() {
            List<Decision> d =
                    decidir(
                            10.0,
                            AHORA,
                            apagada("a", 21.0, 1.0, 16.0),
                            apagada("b", 21.0, 2.0, 17.0),
                            apagada("c", 21.0, 1.5, 18.0),
                            apagada("d", 21.0, 0.5, 19.0),
                            apagada("e", 21.0, 1.0, 21.0)); // satisfecha

            assertEquals(List.of("a", "b", "c", "d"), prendidas(d));
        }

        @Test
        @DisplayName("Las decisiones salen una por habitación y en el orden del inventario")
        void unaDecisionPorHabitacion_enOrden() {
            List<Decision> d =
                    decidir(
                            1.0,
                            AHORA,
                            apagada("z", 21.0, 1.0, 18.0),
                            apagada("a", 21.0, 1.0, 18.5),
                            apagada("m", 21.0, 1.0, 22.0));

            assertEquals(List.of("z", "a", "m"), d.stream().map(Decision::idHabitacion).toList());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Criterio: mayor déficit primero")
    class Criterio {

        @Test
        @DisplayName(
                "El déficit se mide contra la esperada de cada una, no contra la temperatura más"
                        + " baja")
        void deficitRelativoALaEsperada() {
            List<Decision> d =
                    decidir(
                            1.0,
                            AHORA,
                            apagada("living", 22.0, 1.0, 19.0), // déficit 3
                            apagada("dormitorio", 18.0, 1.0, 16.0)); // más fría, pero déficit 2

            assertEquals(List.of("living"), prendidas(d));
        }

        @Test
        @DisplayName(
                "Mismo déficit y potencia para 1 -> prende la de id menor, sin importar el orden"
                        + " del inventario")
        void empate_desempataPorId() {
            List<Decision> unOrden =
                    decidir(
                            1.0,
                            AHORA,
                            apagada("room2", 21.0, 1.0, 18.0),
                            apagada("room1", 21.0, 1.0, 18.0));
            List<Decision> otroOrden =
                    decidir(
                            1.0,
                            AHORA,
                            apagada("room1", 21.0, 1.0, 18.0),
                            apagada("room2", 21.0, 1.0, 18.0));

            assertEquals(List.of("room1"), prendidas(unOrden));
            assertEquals(List.of("room1"), prendidas(otroOrden));
        }

        @Test
        @DisplayName(
                "La encendida (déficit 3.0) conserva la energía ante otra apenas más fría (déficit"
                        + " 3.3)")
        void encendida_conservaLaEnergiaDentroDelMargen() {
            List<Decision> d =
                    decidir(
                            1.0,
                            AHORA,
                            encendida("living", 21.0, 1.0, 18.0),
                            apagada("cocina", 21.0, 1.0, 17.7));

            assertEquals(List.of("living"), prendidas(d));
            assertEquals(Motivo.SIN_POTENCIA_DISPONIBLE, motivoDe(d, "cocina"));
        }

        @Test
        @DisplayName(
                "La encendida (déficit 3.0) cede la energía si otra la supera por más del margen"
                        + " (déficit 4.0)")
        void encendida_cedeLaEnergiaFueraDelMargen() {
            List<Decision> d =
                    decidir(
                            1.0,
                            AHORA,
                            encendida("living", 21.0, 1.0, 18.0),
                            apagada("cocina", 21.0, 1.0, 17.0));

            assertEquals(List.of("cocina"), prendidas(d));
        }

        @Test
        @DisplayName(
                "2 encendidas y 3 esperando, potencia para 2: siguen las encendidas salvo que otra"
                        + " las supere por más de 0.5")
        void variasEncendidas_conMargen() {
            List<Decision> d =
                    decidir(
                            2.0,
                            AHORA,
                            encendida("a", 21.0, 1.0, 18.0), // déficit 3.0 + 0.5 de margen = 3.5
                            encendida("b", 21.0, 1.0, 19.0), // déficit 2.0 + 0.5 de margen = 2.5
                            apagada(
                                    "c", 21.0, 1.0,
                                    18.3), // déficit 2.7: supera a b por más del margen
                            apagada("d", 21.0, 1.0, 19.5), // déficit 1.5
                            apagada("e", 21.0, 1.0, 20.0)); // déficit 1.0

            assertEquals(List.of("a", "c"), prendidas(d));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Histéresis (0.3 °C)")
    class Histeresis {

        @Test
        @DisplayName("Apagada a 21.3 con esperada 21.5 (dentro de la banda) -> sigue apagada")
        void apagadaDentroDeLaBanda_noPrende() {
            List<Decision> d = decidir(3.7, AHORA, apagada("living", 21.5, 1.2, 21.3));

            assertEquals(Accion.OFF, accionDe(d, "living"));
            assertEquals(Motivo.SATISFECHA, motivoDe(d, "living"));
        }

        @Test
        @DisplayName("Apagada a 21.1 con esperada 21.5 (debajo de la banda) -> prende")
        void apagadaDebajoDeLaBanda_prende() {
            assertEquals(
                    Accion.ON,
                    accionDe(decidir(3.7, AHORA, apagada("living", 21.5, 1.2, 21.1)), "living"));
        }

        @Test
        @DisplayName(
                "Encendida a 21.4 con esperada 21.5 -> sigue prendida hasta llegar a la esperada")
        void encendidaDentroDeLaBanda_siguePrendida() {
            assertEquals(
                    Accion.ON,
                    accionDe(decidir(3.7, AHORA, encendida("living", 21.5, 1.2, 21.4)), "living"));
        }

        @Test
        @DisplayName(
                "Misma temperatura (21.3), distinto estado: la encendida sigue y la apagada no"
                        + " arranca")
        void mismaTemperatura_dependeDeSiEstabaEncendida() {
            List<Decision> d =
                    decidir(
                            3.7,
                            AHORA,
                            encendida("living", 21.5, 1.2, 21.3),
                            apagada("cocina", 21.5, 1.2, 21.3));

            assertEquals(List.of("living"), prendidas(d));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Tarifa punta (17:00-23:00, días hábiles)")
    class TarifaPunta {

        private final Hab[] cinco = {
            apagada("a", 21.0, 1.0, 15.0),
            encendida("b", 21.0, 1.0, 16.0),
            apagada("c", 21.0, 1.0, 17.0),
            apagada("d", 21.0, 1.0, 22.0),
            sinLectura("e", 21.0, 1.0)
        };

        private Hab[] leidasEn(Instant instante) {
            return Arrays.stream(cinco)
                    .map(h -> h.lectura() == null ? h : h.leidaEn(instante))
                    .toArray(Hab[]::new);
        }

        @Test
        @DisplayName(
                "Lunes 18:00, 3 con déficit -> ninguna prende (tarifa punta); la satisfecha y la"
                        + " sin lectura con su motivo")
        void enPunta_ningunaPrende() {
            List<Decision> d = decidir(10.0, lunes(18, 0), leidasEn(lunes(18, 0)));

            assertEquals(List.of(), prendidas(d));
            assertEquals(Motivo.TARIFA_PUNTA, motivoDe(d, "a"));
            assertEquals(Motivo.TARIFA_PUNTA, motivoDe(d, "b"));
            assertEquals(Motivo.TARIFA_PUNTA, motivoDe(d, "c"));
            assertEquals(Motivo.SATISFECHA, motivoDe(d, "d"));
            assertEquals(Motivo.SIN_LECTURA, motivoDe(d, "e"));
        }

        @Test
        @DisplayName("16:59 todavía prenden; 17:00 ya no")
        void bordeDeEntrada() {
            assertEquals(
                    List.of("a", "b", "c"),
                    prendidas(decidir(10.0, lunes(16, 59), leidasEn(lunes(16, 59)))));
            assertEquals(List.of(), prendidas(decidir(10.0, lunes(17, 0), leidasEn(lunes(17, 0)))));
        }

        @Test
        @DisplayName("22:59 todavía no prenden; 23:00 se restituye")
        void bordeDeSalida() {
            assertEquals(
                    List.of(), prendidas(decidir(10.0, lunes(22, 59), leidasEn(lunes(22, 59)))));
            assertEquals(
                    List.of("a", "b", "c"),
                    prendidas(decidir(10.0, lunes(23, 0), leidasEn(lunes(23, 0)))));
        }

        @Test
        @DisplayName("Viernes 18:00 con tarifa HABILES -> punta; sábado y domingo 18:00 -> prenden")
        void diasHabiles() {
            assertEquals(
                    List.of(), prendidas(decidir(10.0, viernes(18, 0), leidasEn(viernes(18, 0)))));
            assertEquals(
                    3, prendidas(decidir(10.0, sabado(18, 0), leidasEn(sabado(18, 0)))).size());
            assertEquals(
                    3, prendidas(decidir(10.0, domingo(18, 0), leidasEn(domingo(18, 0)))).size());
        }

        @Test
        @DisplayName("Sábado 18:00 con tarifa TODOS -> punta, ninguna prende")
        void todosLosDias() {
            Sitio todos =
                    new Sitio(
                            "s",
                            10.0,
                            new FranjaHoraria(
                                    LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.TODOS));

            assertEquals(
                    List.of(), prendidas(decidir(todos, sabado(18, 0), leidasEn(sabado(18, 0)))));
        }

        @Test
        @DisplayName(
                "La punta se mide en la zona del sitio: el mismo instante es punta en UTC (18:00Z)"
                        + " y no en Uruguay (15:00)")
        void zonaDelSitio() {
            Instant instante = lunes(15, 0); // 15:00 en Uruguay = 18:00Z
            Sitio enUtc = new Sitio("s", 10.0, PUNTA, ZoneOffset.UTC);
            Sitio enUruguay = new Sitio("s", 10.0, PUNTA);

            assertEquals(List.of(), prendidas(decidir(enUtc, instante, leidasEn(instante))));
            assertEquals(3, prendidas(decidir(enUruguay, instante, leidasEn(instante))).size());
        }

        @Test
        @DisplayName("Sin hora del control conocida no se considera punta")
        void sinHora_noHayPunta() {
            assertEquals(3, prendidas(decidir(10.0, null, cinco)).size());
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Lecturas vencidas (vigencia 10 min)")
    class LecturasVencidas {

        @Test
        @DisplayName("Lectura de hace 10 min -> todavía vale, prende")
        void enElLimite_vale() {
            List<Decision> d =
                    decidir(3.7, AHORA, apagada("living", 21.5, 1.2, 15.0).leidaEn(lunes(9, 50)));

            assertEquals(Accion.ON, accionDe(d, "living"));
        }

        @Test
        @DisplayName("Lectura de hace 11 min -> no prende (lectura vencida)")
        void pasadoElLimite_vencida() {
            List<Decision> d =
                    decidir(3.7, AHORA, apagada("living", 21.5, 1.2, 15.0).leidaEn(lunes(9, 49)));

            assertEquals(Accion.OFF, accionDe(d, "living"));
            assertEquals(Motivo.LECTURA_VENCIDA, motivoDe(d, "living"));
        }

        @Test
        @DisplayName(
                "La más fría tiene la lectura vencida y hay potencia para 1 -> prende la siguiente")
        void laVencidaLiberaSuLugar() {
            List<Decision> d =
                    decidir(
                            1.0,
                            AHORA,
                            encendida("living", 21.0, 1.0, 10.0).leidaEn(lunes(9, 30)),
                            apagada("cocina", 21.0, 1.0, 18.0),
                            apagada("bano", 21.0, 1.0, 19.0));

            assertEquals(Motivo.LECTURA_VENCIDA, motivoDe(d, "living"));
            assertEquals(List.of("cocina"), prendidas(d));
        }

        @Test
        @DisplayName(
                "La vigencia se mide en tiempo de control: con factor 30, una lectura de hace 601 s"
                        + " de control está vencida")
        void vigenciaEnTiempoDeControl_hace601sVencida() {
            // El core ya no recibe el factor: ahora y la lectura vienen en hora del control.
            Instant ahora = lunes(10, 0);

            List<Decision> d =
                    decidir(
                            3.7,
                            ahora,
                            apagada("living", 21.5, 1.2, 15.0).leidaEn(ahora.minusSeconds(601)));

            assertEquals(Motivo.LECTURA_VENCIDA, motivoDe(d, "living"));
        }

        @Test
        @DisplayName("La vigencia se mide en tiempo de control: una lectura de hace 599 s no vence")
        void vigenciaEnTiempoDeControl_hace599sVale() {
            Instant ahora = lunes(10, 0);

            List<Decision> d =
                    decidir(
                            3.7,
                            ahora,
                            apagada("living", 21.5, 1.2, 15.0).leidaEn(ahora.minusSeconds(599)));

            assertEquals(Accion.ON, accionDe(d, "living"));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Nunca se supera la contratada")
    class TopeDePotencia {

        @Test
        @DisplayName(
                "10 habitaciones con déficit y potencias variadas, 4.5 kW contratados -> lo"
                        + " encendido suma <= 4.5")
        void diezHabitaciones_respetanElTope() {
            Hab[] hs = new Hab[10];
            double[] potencias = {1.2, 0.8, 2.0, 1.5, 0.5, 1.0, 2.5, 0.7, 1.3, 0.9};
            for (int i = 0; i < hs.length; i++) {
                hs[i] = apagada("h" + i, 21.0, potencias[i], 12.0 + i * 0.7);
            }

            List<Decision> d = decidir(4.5, AHORA, hs);

            double usada = 0;
            for (int i = 0; i < hs.length; i++) {
                if (accionDe(d, "h" + i) == Accion.ON) {
                    usada += potencias[i];
                }
            }
            assertTrue(usada <= 4.5 + 1e-9, "se encendieron " + usada + " kW");
            assertTrue(usada > 0);
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Lectura válida y situaciones incompletas")
    class LecturaValidaYSituacionIncompleta {

        @Test
        @DisplayName("Una temperatura del sensor vale entre -40 y 85 °C, bordes incluidos")
        void lecturaValida_rangoDelSensor() {
            assertTrue(core.lecturaValida(21.5));
            assertTrue(core.lecturaValida(-40.0));
            assertTrue(core.lecturaValida(85.0));
            assertFalse(core.lecturaValida(-40.1));
            assertFalse(core.lecturaValida(85.1));
            assertFalse(core.lecturaValida(Double.NaN));
            assertFalse(core.lecturaValida(Double.POSITIVE_INFINITY));
        }

        @Test
        @DisplayName(
                "Sin hora del control (antes del primer estímulo) no hay punta ni vencimientos")
        void sinHora_noHayPuntaNiVencimiento() {
            Hab vieja = apagada("living", 21.5, 1.2, 15.0).leidaEn(lunes(0, 0));

            List<Decision> d = decidir(3.7, null, vieja);

            assertEquals(Motivo.CALEFACCIONANDO, motivoDe(d, "living"));
        }

        @Test
        @DisplayName("Con lectura pero sin instante, la lectura no vence")
        void lecturaSinInstante_noVence() {
            Hab sinInstante = apagada("living", 21.5, 1.2, 15.0).leidaEn(null);

            List<Decision> d = decidir(3.7, AHORA, sinInstante);

            assertEquals(Motivo.CALEFACCIONANDO, motivoDe(d, "living"));
        }

        @Test
        @DisplayName("Una habitación sin entrada en los estados se toma como sin lectura")
        void sinEstado_esSinLectura() {
            List<Decision> d =
                    core.decidir(
                            new Sitio("sitio-test", 3.7, PUNTA),
                            List.of(hab("living", 21.5, 1.2)),
                            Map.of(),
                            AHORA);

            assertEquals(Motivo.SIN_LECTURA, motivoDe(d, "living"));
        }
    }
}
