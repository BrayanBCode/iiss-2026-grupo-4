package uy.edu.utec.iiss.engine.integracion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP de switches que habla el estándar v2 de verdad (§3 {@code POST
 * /switches/{id}/comandos}, §4 {@code GET /switches/{id}}), así el engine usa su {@code
 * ClienteSwitchRest} real. Cada switch recuerda su estado; se puede fijar el estado inicial (la
 * calefacción que ya estaba encendida) o hacer que un switch deje de responder.
 */
public final class SwitchesHttpFalsos {

    /** Un comando recibido, en orden de llegada. */
    public record Comando(String idSwitch, String accion) {}

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    private final Map<String, String> estados = new ConcurrentHashMap<>();
    private final Set<String> caidos = ConcurrentHashMap.newKeySet();
    private final List<Comando> comandos = new CopyOnWriteArrayList<>();
    private final List<String> consultas = new CopyOnWriteArrayList<>();

    public SwitchesHttpFalsos() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/switches", this::atender);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Vuelve a cero: sin estados, sin historial, todos responden. */
    public void reiniciar() {
        estados.clear();
        caidos.clear();
        comandos.clear();
        consultas.clear();
    }

    /** Estado del switch; {@code "OFF"} si nunca se tocó. */
    public String estado(String idSwitch) {
        return estados.getOrDefault(idSwitch, "OFF");
    }

    /** Fija el estado sin pasar por el controlador (estado inicial, cambio manual). */
    public void fijar(String idSwitch, String estado) {
        estados.put(idSwitch, estado);
    }

    /** {@code true}: el switch contesta 503 a todo; {@code false}: vuelve a responder. */
    public void caido(String idSwitch, boolean caido) {
        if (caido) {
            caidos.add(idSwitch);
        } else {
            caidos.remove(idSwitch);
        }
    }

    public List<Comando> comandos() {
        return List.copyOf(comandos);
    }

    public List<Comando> comandosA(String idSwitch) {
        return comandos.stream().filter(c -> c.idSwitch().equals(idSwitch)).toList();
    }

    public List<String> consultas() {
        return List.copyOf(consultas);
    }

    public void detener() {
        server.stop(0);
    }

    private void atender(HttpExchange ex) throws IOException {
        try (ex) {
            // /switches/{id} o /switches/{id}/comandos
            String[] partes = ex.getRequestURI().getPath().split("/");
            if (partes.length < 3) {
                responder(ex, 404, "{}");
                return;
            }
            String id = partes[2];
            if (caidos.contains(id)) {
                responder(ex, 503, "{\"mensaje\":\"switch caído\"}");
                return;
            }
            boolean esComando = partes.length == 4 && partes[3].equals("comandos");
            if (esComando && ex.getRequestMethod().equals("POST")) {
                JsonNode cuerpo = JSON.readTree(ex.getRequestBody());
                String accion = cuerpo.path("accion").asText();
                if (!accion.equals("ON") && !accion.equals("OFF")) {
                    responder(ex, 400, "{\"mensaje\":\"accion inválida\"}");
                    return;
                }
                estados.put(id, accion);
                comandos.add(new Comando(id, accion));
            } else if (partes.length == 3 && ex.getRequestMethod().equals("GET")) {
                consultas.add(id);
            } else {
                responder(ex, 405, "{}");
                return;
            }
            responder(ex, 200, "{\"id\":\"%s\",\"estado\":\"%s\"}".formatted(id, estado(id)));
        }
    }

    private static void responder(HttpExchange ex, int status, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
    }
}
