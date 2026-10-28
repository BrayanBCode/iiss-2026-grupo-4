package uy.edu.utec.iiss.engine.adaptador;

/**
 * Por qué se descartó una lectura de temperatura. Se cuentan por habitación y se muestran en {@code
 * /diagnostico}.
 */
public enum MotivoDescarte {
    /** El {@code ts}, convertido a la hora del control, es más viejo que la vigencia (D8). */
    TS_VENCIDO,
    /** El {@code ts} es anterior al de la última lectura guardada: llegó fuera de orden. */
    FUERA_DE_ORDEN
}
