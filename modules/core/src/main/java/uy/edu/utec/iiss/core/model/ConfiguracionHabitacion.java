package uy.edu.utec.iiss.core.model;

/**
 * Lo que el core necesita de una habitación del inventario (estándar v2, §1): temperatura objetivo
 * y potencia mientras está encendida. Termostato y switch son direccionamiento del engine y no
 * están acá.
 *
 * <p>La valida el engine ({@code ValidadorInventario}) antes de aplicar el inventario, para poder
 * rechazarlo entero.
 */
public record ConfiguracionHabitacion(
        String id, String nombre, double temperaturaEsperada, double potenciaKW) {}
