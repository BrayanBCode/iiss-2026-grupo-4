package uy.edu.utec.iiss.engine.inventario;

import java.util.List;

/** El inventario de ejemplo del §1 (casa-rodriguez), para los tests. */
public final class InventarioEjemplo {

    private InventarioEjemplo() {
    }

    public static Inventario.Habitacion habitacion(String id, double esperada, double potencia) {
        return habitacion(id, "Hab " + id, esperada, potencia);
    }

    public static Inventario.Habitacion habitacion(String id, String nombre, double esperada, double potencia) {
        return new Inventario.Habitacion(id, nombre, esperada, potencia,
                "ht-sim-" + id, "ht-sim-" + id + "/status/temperature:0", "sw-sim-" + id, "http://switches:8081");
    }

    public static Inventario casaRodriguez() {
        return new Inventario("2.0",
                new Inventario.Sitio("casa-rodriguez", "Casa Rodríguez", 3.7,
                        new Inventario.Tarifa(new Inventario.Franja("17:00", "23:00", "HABILES"))),
                List.of(habitacion("room1", "Living", 21.5, 1.2), habitacion("room2", "Dormitorio principal", 20.0, 0.8)));
    }

    public static final String JSON = """
            {
              "schemaVersion": "2.0",
              "sitio": {
                "id": "casa-rodriguez",
                "nombre": "Casa Rodríguez",
                "potenciaContratadaKW": 3.7,
                "tarifa": { "punta": { "desde": "17:00", "hasta": "23:00", "dias": "HABILES" } }
              },
              "habitaciones": [
                { "id": "room1", "nombre": "Living", "temperaturaEsperada": 21.5, "potenciaKW": 1.2,
                  "idTermostato": "ht-sim-room1", "topicTermostato": "ht-sim-room1/status/temperature:0",
                  "idSwitch": "sw-sim-room1", "urlSwitch": "http://switches:8081" },
                { "id": "room2", "nombre": "Dormitorio principal", "temperaturaEsperada": 20.0, "potenciaKW": 0.8,
                  "idTermostato": "ht-sim-room2", "topicTermostato": "ht-sim-room2/status/temperature:0",
                  "idSwitch": "sw-sim-room2", "urlSwitch": "http://switches:8081" }
              ]
            }
            """;
}
