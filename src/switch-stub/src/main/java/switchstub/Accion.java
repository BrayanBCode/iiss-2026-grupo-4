package switchstub;

/**
 * Acciones que el switch (real o, en este caso, el stub) sabe ejecutar.
 * Se restringe a estos dos valores para que un typo en el body ("On",
 * "encender", etc.) se rechace con un 400 en vez de silenciarse.
 */
public enum Accion {
    ON,
    OFF
}
