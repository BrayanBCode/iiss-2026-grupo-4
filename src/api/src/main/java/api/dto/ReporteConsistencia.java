package api.dto;

import java.util.List;

/**
 * Respuesta de GET /habitaciones/validar: chequea que no existan
 * idTermostato/idSwitch duplicados entre habitaciones.
 *
 * En la práctica nunca debería encontrar nada, porque nombre, termostato_id
 * y switch_id son UNIQUE en la tabla (ver Habitacion) — una violación ya se
 * rechaza en el momento de crear/modificar (409, ver ApiExceptionHandler).
 * Este endpoint es el comando explícito que pide la letra de todos modos,
 * por si alguna vez se inserta un registro sin pasar por este API.
 */
public class ReporteConsistencia {

    private boolean consistente;
    private List<String> idsTermostatoDuplicados;
    private List<String> idsSwitchDuplicados;

    public ReporteConsistencia() {
    }

    public ReporteConsistencia(boolean consistente, List<String> idsTermostatoDuplicados,
                                List<String> idsSwitchDuplicados) {
        this.consistente = consistente;
        this.idsTermostatoDuplicados = idsTermostatoDuplicados;
        this.idsSwitchDuplicados = idsSwitchDuplicados;
    }

    public boolean isConsistente() {
        return consistente;
    }

    public List<String> getIdsTermostatoDuplicados() {
        return idsTermostatoDuplicados;
    }

    public List<String> getIdsSwitchDuplicados() {
        return idsSwitchDuplicados;
    }
}
