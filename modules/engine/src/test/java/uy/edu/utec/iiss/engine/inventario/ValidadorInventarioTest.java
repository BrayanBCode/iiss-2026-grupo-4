package uy.edu.utec.iiss.engine.inventario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uy.edu.utec.iiss.engine.inventario.InventarioEjemplo.casaRodriguez;
import static uy.edu.utec.iiss.engine.inventario.InventarioEjemplo.habitacion;

@DisplayName("ValidadorInventario (reglas de consistencia del §1)")
class ValidadorInventarioTest {

    private static Inventario conHabitaciones(Inventario.Habitacion... hs) {
        return casaRodriguez().conHabitaciones(List.of(hs));
    }

    private static Inventario conSitio(Inventario.Sitio s) {
        Inventario base = casaRodriguez();
        return new Inventario(base.schemaVersion(), s, base.habitaciones());
    }

    private static Inventario.Sitio sitio(double potencia, String desde, String hasta, String dias) {
        return new Inventario.Sitio("s", "S", potencia, new Inventario.Tarifa(new Inventario.Franja(desde, hasta, dias)));
    }

    @Test
    @DisplayName("El ejemplo del estándar es válido")
    void ejemploValido() {
        assertTrue(ValidadorInventario.validar(casaRodriguez()).isEmpty());
    }

    @Test
    @DisplayName("La suma de potencias puede superar la contratada (no es error)")
    void sumaMayorQueContratada_esValida() {
        assertTrue(ValidadorInventario.validar(conHabitaciones(
                habitacion("a", 20, 3.0), habitacion("b", 20, 3.0))).isEmpty());
    }

    @Test
    @DisplayName("Un major distinto de 2 se rechaza; 2.x se acepta")
    void schemaVersion() {
        Inventario base = casaRodriguez();
        assertEquals(1, ValidadorInventario.validar(new Inventario("1.0", base.sitio(), base.habitaciones())).size());
        assertEquals(1, ValidadorInventario.validar(new Inventario("3.0", base.sitio(), base.habitaciones())).size());
        assertTrue(ValidadorInventario.validar(new Inventario("2.7", base.sitio(), base.habitaciones())).isEmpty());
    }

    @Test
    @DisplayName("Duplicados de id, idTermostato e idSwitch: se reportan todos juntos")
    void duplicados() {
        Inventario.Habitacion a = habitacion("a", 20, 1);
        List<String> errores = ValidadorInventario.validar(conHabitaciones(a, a));

        assertTrue(errores.stream().anyMatch(e -> e.startsWith("id duplicado")));
        assertTrue(errores.stream().anyMatch(e -> e.startsWith("idTermostato duplicado")));
        assertTrue(errores.stream().anyMatch(e -> e.startsWith("idSwitch duplicado")));
    }

    @Test
    @DisplayName("Potencias no positivas o ausentes se rechazan")
    void potencias() {
        assertEquals(1, ValidadorInventario.validar(conHabitaciones(habitacion("a", 20, 0))).size());
        assertEquals(1, ValidadorInventario.validar(conSitio(sitio(0, "17:00", "23:00", "HABILES"))).size());
    }

    @Test
    @DisplayName("Campos obligatorios vacíos de la habitación se rechazan")
    void camposObligatorios() {
        Inventario.Habitacion vacia = new Inventario.Habitacion("a", " ", null, 1.0, "", "", "", "");
        List<String> errores = ValidadorInventario.validar(conHabitaciones(vacia));

        assertEquals(6, errores.size(), errores.toString());
    }

    @Test
    @DisplayName("urlSwitch debe ser una URL http(s)")
    void urlSwitch() {
        Inventario.Habitacion h = new Inventario.Habitacion("a", "A", 20.0, 1.0, "t", "t/x", "s", "switches:8081");
        assertEquals(1, ValidadorInventario.validar(conHabitaciones(h)).size());
    }

    @Test
    @DisplayName("Horas HH:MM válidas, desde < hasta y días HABILES/TODOS")
    void tarifa() {
        assertEquals(1, ValidadorInventario.validar(conSitio(sitio(3, "25:00", "23:00", "HABILES"))).size());
        assertEquals(1, ValidadorInventario.validar(conSitio(sitio(3, "23:00", "17:00", "HABILES"))).size());
        assertEquals(1, ValidadorInventario.validar(conSitio(sitio(3, "17:00", "17:00", "HABILES"))).size());
        assertEquals(1, ValidadorInventario.validar(conSitio(sitio(3, "17:00", "23:00", "FERIADOS"))).size());
        assertTrue(ValidadorInventario.validar(conSitio(sitio(3, "00:00", "23:59", "TODOS"))).isEmpty());
    }

    @Test
    @DisplayName("Inventario nulo o sin partes se rechaza sin excepciones")
    void incompleto() {
        assertEquals(1, ValidadorInventario.validar(null).size());
        assertEquals(3, ValidadorInventario.validar(new Inventario(null, null, null)).size());
    }
}
