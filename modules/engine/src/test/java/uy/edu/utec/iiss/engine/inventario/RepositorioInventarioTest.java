package uy.edu.utec.iiss.engine.inventario;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RepositorioInventario (persistencia local, §5.1)")
class RepositorioInventarioTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("Sin archivo no hay inventario")
    void sinArchivo_vacio() {
        assertTrue(new RepositorioInventario(dir.resolve("x.json").toString(), new ObjectMapper()).cargar().isEmpty());
    }

    @Test
    @DisplayName("Lo grabado sobrevive a un 'reinicio' (otra instancia lee lo mismo)")
    void sobreviveAlReinicio() {
        String archivo = dir.resolve("sub/inventario.json").toString();
        new RepositorioInventario(archivo, new ObjectMapper()).guardar(InventarioEjemplo.casaRodriguez());

        assertEquals(InventarioEjemplo.casaRodriguez(),
                new RepositorioInventario(archivo, new ObjectMapper()).cargar().orElseThrow());
    }

    @Test
    @DisplayName("Un archivo corrupto no rompe el arranque: se arranca sin sitio")
    void archivoCorrupto_vacio() throws Exception {
        Path archivo = dir.resolve("inventario.json");
        Files.writeString(archivo, "{ esto no es json");

        assertTrue(new RepositorioInventario(archivo.toString(), new ObjectMapper()).cargar().isEmpty());
    }

    @Test
    @DisplayName("Grabar no deja temporales en el directorio")
    void noDejaTemporales() throws Exception {
        new RepositorioInventario(dir.resolve("inventario.json").toString(), new ObjectMapper())
                .guardar(InventarioEjemplo.casaRodriguez());

        try (var archivos = Files.list(dir)) {
            assertEquals(1, archivos.count());
        }
    }
}
