package uy.edu.utec.iiss.engine.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Sitio;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SitioConfig (datos del sitio que el core necesita)")
class SitioConfigTest {

    @Test
    @DisplayName("Arma el Sitio con el tope y la franja punta configurados, en hora de Uruguay")
    void armaElSitio() {
        SitioConfig config = new SitioConfig("casa", 3.7, 1.0, "17:00", "23:00", "HABILES");

        Sitio sitio = config.sitio();

        assertEquals("casa", sitio.id());
        assertEquals(3.7, sitio.potenciaContratadaKW(), 1e-9);
        assertEquals(new FranjaHoraria(LocalTime.of(17, 0), LocalTime.of(23, 0), DiasTarifa.HABILES), sitio.puntaTarifa());
        assertEquals(Sitio.ZONA_URUGUAY, sitio.zona());
    }

    @Test
    @DisplayName("Expone el consumo por defecto de cada habitación")
    void exponeElConsumoPorHabitacion() {
        assertEquals(1.0, new SitioConfig("casa", 3.7, 1.0, "17:00", "23:00", "TODOS").potenciaHabitacionKW(), 1e-9);
    }

    @Test
    @DisplayName("Un valor inválido en los días de la tarifa se rechaza al arrancar")
    void diasInvalidos_seRechazan() {
        assertThrows(IllegalArgumentException.class,
                () -> new SitioConfig("casa", 3.7, 1.0, "17:00", "23:00", "FERIADOS"));
    }
}