package uy.edu.utec.iiss.engine.inventario;

import java.util.List;

/** El inventario recibido no cumple el §1: se rechaza entero, con el detalle de cada problema. */
public class InventarioInvalidoException extends RuntimeException {

    private final List<String> errores;

    public InventarioInvalidoException(List<String> errores) {
        super("inventario inválido");
        this.errores = List.copyOf(errores);
    }

    public List<String> errores() {
        return errores;
    }
}
