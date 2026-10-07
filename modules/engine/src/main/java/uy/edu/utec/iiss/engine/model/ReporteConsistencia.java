package uy.edu.utec.iiss.engine.model;

import java.util.List;

/**
 * Resultado del chequeo de consistencia entre habitaciones: lista los
 * idTermostato / idSwitch que aparecen duplicados.
 *
 * En la práctica nunca debería encontrar nada, porque nombre, termostato_id
 * y switch_id son UNIQUE en la tabla (ver Habitacion) -- una violación ya se
 * rechaza al crear/modificar (409). Existe por el comando explícito que pide
 * la letra, por si alguna vez se inserta un registro sin pasar por este API.
 *
 * Es un "record" (clase inmutable de solo datos). El formato JSON que ve el
 * cliente lo define rest.dto.ReporteConsistenciaResponse.
 */
public record ReporteConsistencia(boolean consistente,
                                  List<String> idsTermostatoDuplicados,
                                  List<String> idsSwitchDuplicados) {
}
