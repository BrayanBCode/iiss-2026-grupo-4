package uy.edu.utec.iiss.engine.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import uy.edu.utec.iiss.core.model.DiasTarifa;
import uy.edu.utec.iiss.core.model.FranjaHoraria;
import uy.edu.utec.iiss.core.model.Sitio;

import java.time.LocalTime;

/**
 * Datos del sitio que el core necesita para decidir y que la base no guarda:
 * el tope de potencia contratada, la franja de tarifa punta y cuánto consume
 * una habitación encendida.
 *
 * PROVISORIO: según la letra vienen de site_config.json (servicio REST de
 * configuración del sitio), que todavía no está integrado. Mientras tanto se
 * leen de propiedades de Spring, con estos valores por defecto, y TODAS las
 * habitaciones consumen lo mismo. Se pueden cambiar con variables de entorno:
 *
 *   SITE_ID                       (site.id)                       ecowarm
 *   SITE_POTENCIA_CONTRATADA_KW   (site.potencia-contratada-kw)   3.7
 *   SITE_POTENCIA_HABITACION_KW   (site.potencia-habitacion-kw)   1.0
 *   SITE_PUNTA_DESDE / _HASTA     (site.punta.desde / .hasta)     17:00 / 23:00
 *   SITE_PUNTA_DIAS               (site.punta.dias)               HABILES (o TODOS)
 *
 * La zona horaria del sitio es siempre la de Uruguay (UTC-3).
 */
@Component
public class SitioConfig {

    private final Sitio sitio;
    private final double potenciaHabitacionKW;

    public SitioConfig(@Value("${site.id:ecowarm}") String id,
                       @Value("${site.potencia-contratada-kw:3.7}") double potenciaContratadaKW,
                       @Value("${site.potencia-habitacion-kw:1.0}") double potenciaHabitacionKW,
                       @Value("${site.punta.desde:17:00}") String desde,
                       @Value("${site.punta.hasta:23:00}") String hasta,
                       @Value("${site.punta.dias:HABILES}") String dias) {
        this.potenciaHabitacionKW = potenciaHabitacionKW;
        this.sitio = new Sitio(id, potenciaContratadaKW,
                new FranjaHoraria(LocalTime.parse(desde.trim()), LocalTime.parse(hasta.trim()),
                        DiasTarifa.valueOf(dias.trim().toUpperCase())));
    }

    public Sitio sitio() {
        return sitio;
    }

    /** Consumo (kW) de cada habitación mientras está encendida. */
    public double potenciaHabitacionKW() {
        return potenciaHabitacionKW;
    }
}