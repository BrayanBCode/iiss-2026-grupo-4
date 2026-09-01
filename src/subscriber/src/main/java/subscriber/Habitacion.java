package subscriber;

/**
 * Representa una habitación ya dada de alta por el cliente, con el
 * termostato y el switch que tiene asignados. Ver HabitacionDAO.
 */
public record Habitacion(int id, String nombre, double temperaturaObjetivo) { }