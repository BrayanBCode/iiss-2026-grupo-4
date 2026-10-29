package uy.edu.utec.iiss.engine.integracion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.engine.inventario.Inventario;
import uy.edu.utec.iiss.engine.switches.EstadoSwitch;

/**
 * Falla de la comunicación REST con los switches (§3/§4): un switch que contesta error, que no
 * contesta a tiempo o al que no se llega. Un switch con problemas no frena a los demás, se
 * reintenta solo y, mientras tanto, nunca se arriesga a superar la potencia contratada.
 */
@DisplayName("Integración: un switch no responde")
class FallaSwitchTest extends IntegracionBase {

    @Test
    @DisplayName("Un switch que contesta 503 no frena a los demás; cuando vuelve recibe su comando")
    void switchCaido_seReintenta() throws Exception {
        SWITCHES.caido("sw-living", true);
        cargarSitio(
                POTENCIA_SITIO, habitacion("living", 21.0, 1.0), habitacion("cocina", 20.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        temperatura("cocina", 15.0);

        esperarSwitch("cocina", "ON");
        assertSwitch("living", "OFF"); // no se pudo encender
        assertEquals(EstadoSwitch.DESCONOCIDO, estado("living").estadoSwitch());

        SWITCHES.caido("sw-living", false);
        esperarSwitch("living", "ON"); // reintento (cada 1 s en el test)
        esperar(
                () -> estado("living").estadoSwitch() == EstadoSwitch.ON,
                () -> "el engine no registró que el switch volvió");
    }

    @Test
    @DisplayName(
            "Un switch colgado (más lento que el timeout) no traba el control: los demás se"
                    + " comandan igual")
    void switchColgado_timeout() throws Exception {
        SWITCHES.lento("sw-living", 3000); // timeout de lectura del engine en el test: 1 s
        cargarSitio(
                POTENCIA_SITIO, habitacion("living", 21.0, 1.0), habitacion("cocina", 20.0, 1.0));
        iniciar();
        temperatura("cocina", 15.0);
        esperarSwitch("cocina", "ON");

        SWITCHES.lento("sw-living", 0);
        temperatura("living", 16.0);
        esperarSwitch("living", "ON");
    }

    @Test
    @DisplayName(
            "Un switch inalcanzable (conexión rechazada) queda DESCONOCIDO y el engine sigue"
                    + " funcionando")
    void switchInalcanzable() throws Exception {
        Inventario.Habitacion h = habitacion("living", 21.0, 1.0);
        Inventario.Habitacion inalcanzable =
                new Inventario.Habitacion(
                        h.id(),
                        h.nombre(),
                        h.temperaturaEsperada(),
                        h.potenciaKW(),
                        h.idTermostato(),
                        h.topicTermostato(),
                        h.idSwitch(),
                        "http://127.0.0.1:1"); // nadie escucha ahí
        cargarSitio(POTENCIA_SITIO, inalcanzable, habitacion("cocina", 20.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        temperatura("cocina", 15.0);

        esperarSwitch("cocina", "ON");
        assertEquals("CALEFACCIONANDO", motivo("living")); // la decisión es correcta
        assertEquals(EstadoSwitch.DESCONOCIDO, estado("living").estadoSwitch());
        mvc.perform(conClave(get("/diagnostico"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName(
            "Un switch caído al INICIAR (no se puede consultar) se comanda cuando vuelve a"
                    + " responder")
    void caidoAlIniciar() throws Exception {
        SWITCHES.caido("sw-living", true);
        cargarSitio(POTENCIA_SITIO, habitacion("living", 21.0, 1.0));
        iniciar();
        temperatura("living", 16.0);
        seMantiene(
                Duration.ofMillis(500),
                () -> SWITCHES.estado("sw-living").equals("OFF"),
                () -> "cambió un switch caído");

        SWITCHES.caido("sw-living", false);
        esperarSwitch("living", "ON");
    }

    @Test
    @DisplayName(
            "Si falla el OFF que libera potencia, no se enciende la que esperaba: no se supera la"
                    + " contratada mientras el switch no confirme que apagó")
    void offFallido_noSeEnciendeLaSiguiente() throws Exception {
        Inventario inv =
                inventario(
                        POTENCIA_SITIO,
                        habitacion("living", 22.0, 1.5),
                        habitacion("dormitorio", 22.0, 1.5),
                        habitacion("estudio", 22.0, 1.5));
        cargarSitio(inv);
        iniciar();
        temperatura("living", 16.0);
        temperatura("dormitorio", 17.0);
        temperatura("estudio", 20.0);
        esperarSwitch("living", "ON");
        esperarSwitch("dormitorio", "ON");

        SWITCHES.caido("sw-living", true); // sigue encendido y no acepta el OFF
        temperatura("living", 22.0); // el core decide: living OFF, estudio ON
        esperarMotivo("estudio", "CALEFACCIONANDO");

        seMantiene(
                Duration.ofSeconds(2), // varios reintentos
                () -> potenciaEncendida(inv) <= POTENCIA_SITIO,
                () ->
                        "se superó la potencia contratada con el OFF sin confirmar: "
                                + estadosSwitches(inv));
        assertSwitch("estudio", "OFF");

        SWITCHES.caido("sw-living", false); // el OFF por fin llega
        esperarSwitch("living", "OFF");
        esperarSwitch("estudio", "ON");
        assertSwitch("dormitorio", "ON");
    }
}
