package switchstub;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Contrato del POST hacia el stub.
 */
public record SwitchAccionRequest(
        @NotBlank(message = "switchId es obligatorio")
        String switchId,

        @NotNull(message = "accion es obligatoria y debe ser ON u OFF")
        Accion accion
) { }
