package api.model;

/**
 * Acciones que el api puede pedirle al switch (real o al stub): prender
 * o apagar. Se usa tanto para el comando manual "accionar switch" como
 * para la lógica automática del Controlador (termostato simple).
 *
 * Es el mismo contrato (ON/OFF) que espera switchstub.Accion del lado
 * del stub; se declara acá porque son dos módulos/artefactos separados.
 */
public enum AccionSwitch {
    ON,
    OFF
}
