package switchstub;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Contrato del POST hacia el stub (G1234-67): el cliente HTTP interno del
 * api (G1234-70) manda, para el switch de una habitación, qué acción
 * ejecutar y qué switch identifica.
 *
 * "record" porque es un simple transporte de datos inmutable, igual que
 * Habitacion en el módulo subscriber.
 */
public record SwitchAccionRequest(
        @NotBlank(message = "switchId es obligatorio")
        String switchId,

        @NotNull(message = "accion es obligatoria y debe ser ON u OFF")
        Accion accion
) { }
