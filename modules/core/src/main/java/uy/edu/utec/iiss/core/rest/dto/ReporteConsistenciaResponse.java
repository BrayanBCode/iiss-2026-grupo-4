package uy.edu.utec.iiss.core.rest.dto;

import java.util.List;

import uy.edu.utec.iiss.core.model.ReporteConsistencia;

/**
 * Respuesta de GET /habitaciones/validar: chequea que no existan
 * idTermostato/idSwitch duplicados entre habitaciones. El JSON es el mismo
 * de siempre (consistente, idsTermostatoDuplicados, idsSwitchDuplicados).
 */
public class ReporteConsistenciaResponse {

    private final boolean consistente;
    private final List<String> idsTermostatoDuplicados;
    private final List<String> idsSwitchDuplicados;

    private ReporteConsistenciaResponse(boolean consistente, List<String> idsTermostatoDuplicados,
                                        List<String> idsSwitchDuplicados) {
        this.consistente = consistente;
        this.idsTermostatoDuplicados = idsTermostatoDuplicados;
        this.idsSwitchDuplicados = idsSwitchDuplicados;
    }

    public static ReporteConsistenciaResponse desde(ReporteConsistencia reporte) {
        return new ReporteConsistenciaResponse(reporte.consistente(),
                reporte.idsTermostatoDuplicados(), reporte.idsSwitchDuplicados());
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
