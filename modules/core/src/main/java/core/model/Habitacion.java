package core.model;

/**
 * Datos de configuración de una habitación, tal como llegan en el inventario
 * del sitio (estándar de interoperabilidad v2, §1 "Habitación").
 *
 * No incluye idTermostato/topicTermostato/idSwitch/urlSwitch: esos son
 * detalles de direccionamiento que le importan al engine (MQTT, REST), no al
 * core — el core solo necesita saber "esta habitación quiere esta
 * temperatura y consume esta potencia mientras está encendida".
 */
public record Habitacion(
        String id,
        String nombre,
        double temperaturaEsperada,
        double potenciaKW
) {
}
