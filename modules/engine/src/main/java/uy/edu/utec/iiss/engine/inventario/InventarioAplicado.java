package uy.edu.utec.iiss.engine.inventario;

/** Evento: hay un inventario nuevo persistido y en uso. Lo escuchan el control y el listener MQTT. */
public record InventarioAplicado(Inventario inventario) {
}
