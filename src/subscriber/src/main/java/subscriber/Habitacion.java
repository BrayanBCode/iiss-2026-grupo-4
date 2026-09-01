package subscriber;

/** Equivalente a un DataType, "record" permite crear una clase que solo transporta datos inmutables **/
public record Habitacion(int id, String nombre, double temperaturaObjetivo) { }