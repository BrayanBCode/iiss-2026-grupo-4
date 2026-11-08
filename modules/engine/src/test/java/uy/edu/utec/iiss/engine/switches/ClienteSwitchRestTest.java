package uy.edu.utec.iiss.engine.switches;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.model.Accion;

@DisplayName("ClienteSwitchRest (§3/§4): respuestas que no son el JSON esperado")
class ClienteSwitchRestTest {

    private HttpServer switchFalso;
    private String contentType;
    private String cuerpo;
    private final ClienteSwitchRest cliente = new ClienteSwitchRest("", 1000, 1000);

    @BeforeEach
    void levantar() throws IOException {
        switchFalso = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        switchFalso.createContext(
                "/switches",
                ex -> {
                    byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().set("Content-Type", contentType);
                    ex.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
                    if (bytes.length > 0) {
                        ex.getResponseBody().write(bytes);
                    }
                    ex.close();
                });
        switchFalso.start();
    }

    @AfterEach
    void bajar() {
        switchFalso.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + switchFalso.getAddress().getPort();
    }

    @Test
    @DisplayName(
            "JSON válido con Content-Type text/plain -> se lee igual (el switch respondió bien)")
    void jsonConOtroContentType_seLee() {
        contentType = "text/plain;charset=UTF-8";
        cuerpo = "{\"idSwitch\":\"sw-1\",\"estado\":\"ON\"}";
        assertEquals(EstadoSwitch.ON, cliente.consultar(url(), "sw-1"));
    }

    @Test
    @DisplayName(
            "Respuesta que no es JSON (como el switch caído del simulador) -> no disponible, y el"
                    + " mensaje dice qué mandó")
    void respuestaQueNoEsJson_diceQueMando() {
        contentType = "text/plain;charset=UTF-8";
        cuerpo = "switch fuera de servicio";
        SwitchNoDisponibleException e =
                assertThrows(
                        SwitchNoDisponibleException.class, () -> cliente.consultar(url(), "sw-1"));
        assertTrue(e.getMessage().contains("no es JSON"), e.getMessage());
        assertTrue(e.getMessage().contains("switch fuera de servicio"), e.getMessage());
    }

    @Test
    @DisplayName("JSON sin 'estado' válido -> no disponible (igual que antes)")
    void jsonSinEstado_noDisponible() {
        contentType = "application/json";
        cuerpo = "{\"idSwitch\":\"sw-1\",\"estado\":\"ROTO\"}";
        assertThrows(SwitchNoDisponibleException.class, () -> cliente.consultar(url(), "sw-1"));
    }

    @Test
    @DisplayName("Comando con respuesta vacía -> se asume el estado pedido (igual que antes)")
    void comandoSinCuerpo_asumeLaAccion() {
        contentType = "application/json";
        cuerpo = "";
        assertEquals(EstadoSwitch.OFF, cliente.comandar(url(), "sw-1", Accion.OFF));
    }

    @Test
    @DisplayName("Respuesta muy larga -> el mensaje la recorta")
    void respuestaLarga_seRecorta() {
        String larga = "x".repeat(500);
        SwitchNoDisponibleException e =
                assertThrows(
                        SwitchNoDisponibleException.class,
                        () -> ClienteSwitchRest.leerJson("sw-1", "GET /switches/sw-1", larga));
        assertTrue(e.getMessage().length() < 300, e.getMessage());
    }
}
