package api.exception;

public class SwitchStubNoDisponibleException extends RuntimeException {

    public SwitchStubNoDisponibleException(String switchId, Throwable cause) {
        super("No se pudo contactar al stub del switch para el switch '" + switchId + "'", cause);
    }
}
