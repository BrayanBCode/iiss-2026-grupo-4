package uy.edu.utec.iiss.engine.inventario;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Persistencia local del inventario (estándar §5.1: "se graba localmente y
 * sobrevive al reinicio"). Es un único documento JSON en disco; en Docker el
 * directorio es un volumen.
 *
 * La escritura es atómica: se escribe un temporal en el mismo directorio y se
 * renombra. Si el proceso muere a mitad de camino queda el archivo anterior
 * entero, nunca uno a medio escribir.
 */
@Component
public class RepositorioInventario {

    private static final Logger log = LoggerFactory.getLogger(RepositorioInventario.class);

    private final Path archivo;
    private final ObjectMapper json;

    public RepositorioInventario(@Value("${inventario.archivo:./data/inventario.json}") String archivo,
                                 ObjectMapper json) {
        this.archivo = Path.of(archivo).toAbsolutePath();
        this.json = json;
    }

    /** El inventario guardado, o vacío si nunca se guardó (o el archivo está ilegible). */
    public Optional<Inventario> cargar() {
        if (!Files.exists(archivo)) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(archivo.toFile(), Inventario.class));
        } catch (IOException e) {
            log.error("No se pudo leer el inventario guardado en {}: {}. Se arranca sin sitio.", archivo, e.getMessage());
            return Optional.empty();
        }
    }

    /** @throws UncheckedIOException si no se pudo grabar (el archivo anterior queda intacto). */
    public void guardar(Inventario inventario) {
        try {
            Files.createDirectories(archivo.getParent());
            Path temporal = Files.createTempFile(archivo.getParent(), "inventario-", ".tmp");
            try {
                json.writerWithDefaultPrettyPrinter().writeValue(temporal.toFile(), inventario);
                try {
                    Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporal);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo grabar el inventario en " + archivo, e);
        }
    }

    public Path archivo() {
        return archivo;
    }
}
