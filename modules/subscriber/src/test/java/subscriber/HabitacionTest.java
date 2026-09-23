package subscriber;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas del record Habitacion. Al ser un record, equals/hashCode/toString
 * los genera Java automáticamente, pero igual conviene validarlos: si algún
 * día alguien lo convierte en clase normal, este test avisa si se rompe el
 * contrato esperado.
 */
class HabitacionTest {

    @Test
    void losAccesoresDevuelvenLosValoresDelConstructor() {
        Habitacion habitacion = new Habitacion(1, "living", 21.5);

        assertEquals(1, habitacion.id());
        assertEquals("living", habitacion.nombre());
        assertEquals(21.5, habitacion.temperaturaObjetivo());
    }

    @Test
    void dosHabitacionesConLosMismosValoresSonIguales() {
        Habitacion a = new Habitacion(1, "living", 21.5);
        Habitacion b = new Habitacion(1, "living", 21.5);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void dosHabitacionesConDistintoIdNoSonIguales() {
        Habitacion a = new Habitacion(1, "living", 21.5);
        Habitacion b = new Habitacion(2, "living", 21.5);

        assertNotEquals(a, b);
    }
}
