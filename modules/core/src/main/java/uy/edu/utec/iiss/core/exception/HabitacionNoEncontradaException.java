package uy.edu.utec.iiss.core.exception;

public class HabitacionNoEncontradaException extends RuntimeException {

    public HabitacionNoEncontradaException(Long id) {
        super("No existe una habitación con id " + id);
    }
}