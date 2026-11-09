package uy.edu.utec.iiss.core.model;

/**
 * Por qué el core tomó una {@link Decision}. El engine solo mira ON/OFF; esto es para logs,
 * diagnóstico y tests.
 */
public enum Motivo {
    /** ON: tiene déficit térmico y la potencia disponible alcanzó para ella. */
    CALEFACCIONANDO,
    /** OFF: está en su temperatura esperada (o dentro de la banda de histéresis). */
    SATISFECHA,
    /** OFF: todavía no llegó ninguna lectura válida de su termostato. */
    SIN_LECTURA,
    /** OFF: la última lectura es demasiado vieja; no se calefacciona a ciegas. */
    LECTURA_VENCIDA,
    /** OFF: tiene déficit, pero es horario de energía cara. */
    TARIFA_PUNTA,
    /**
     * OFF: tiene déficit, pero la potencia contratada ya está asignada a otras con más prioridad.
     */
    SIN_POTENCIA_DISPONIBLE,
    /** OFF: su potencia sola ya supera la contratada; encenderla haría saltar la llave. */
    EXCEDE_CONTRATADA,
    /**
     * OFF: su switch no prende (el ON falló o sigue apagado). Lo decide el engine, no {@code
     * decidir}: mientras espera para reintentar, su potencia queda para las demás.
     */
    SWITCH_NO_RESPONDE
}
