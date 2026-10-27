package uy.edu.utec.iiss.core.model;

/**
 * Lo que el core necesita de una habitación del inventario (estándar v2, §1): temperatura objetivo
 * y potencia mientras está encendida. Termostato y switch son direccionamiento del engine y no
 * están acá.
 *
 * <p>La valida el {@link uy.edu.utec.iiss.core.Core} al recibir la configuración completa, para
 * poder rechazarla entera.
 */
public record ConfiguracionHabitacion(
        String id, String nombre, double temperaturaEsperada, double potenciaKW) {}
