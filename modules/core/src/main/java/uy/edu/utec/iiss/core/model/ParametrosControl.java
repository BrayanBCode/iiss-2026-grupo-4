package uy.edu.utec.iiss.core.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Parámetros de la función de decisión que son del controlador, no del inventario.
 *
 * @param histeresisC banda (°C) bajo la esperada en la que una habitación apagada no se enciende;
 *     una encendida sigue hasta la esperada.
 * @param vigenciaLectura cuánto tiempo de control (simulado) vale una lectura; vencida, la
 *     habitación se apaga.
 * @param temperaturaMinimaValida lecturas por debajo se descartan (sensor roto).
 * @param temperaturaMaximaValida lecturas por encima se descartan (sensor roto).
 * @param anticipacionPunta cuánto tiempo de control antes del comienzo de la franja punta ya se
 *     corta, para que los OFF lleguen a los switches antes del comienzo y no después (el tick y los
 *     comandos tardan; con tiempo acelerado cada milisegundo real son decenas de ms simulados). La
 *     salida de la punta no se adelanta.
 */
public record ParametrosControl(
        double histeresisC,
        Duration vigenciaLectura,
        double temperaturaMinimaValida,
        double temperaturaMaximaValida,
        Duration anticipacionPunta) {

    /**
     * 0.3 °C de histéresis, 10 min de vigencia, rango físico -40..85 °C (el del Shelly H&T), sin
     * anticipación de la punta (el engine la configura).
     */
    public static final ParametrosControl PREDETERMINADOS =
            new ParametrosControl(0.3, Duration.ofMinutes(10), -40.0, 85.0);

    /** Sin anticipación de la punta: corta exactamente al comienzo de la franja. */
    public ParametrosControl(
            double histeresisC,
            Duration vigenciaLectura,
            double temperaturaMinimaValida,
            double temperaturaMaximaValida) {
        this(
                histeresisC,
                vigenciaLectura,
                temperaturaMinimaValida,
                temperaturaMaximaValida,
                Duration.ZERO);
    }

    public ParametrosControl {
        Objects.requireNonNull(vigenciaLectura, "vigenciaLectura");
        Objects.requireNonNull(anticipacionPunta, "anticipacionPunta");
        if (anticipacionPunta.isNegative()) {
            throw new IllegalArgumentException("anticipacionPunta debe ser >= 0");
        }
        if (!(histeresisC >= 0) || Double.isInfinite(histeresisC)) {
            throw new IllegalArgumentException("histeresisC debe ser >= 0");
        }
        if (vigenciaLectura.isNegative() || vigenciaLectura.isZero()) {
            throw new IllegalArgumentException("vigenciaLectura debe ser > 0");
        }
        if (!(temperaturaMinimaValida < temperaturaMaximaValida)) {
            throw new IllegalArgumentException("El rango de temperaturas válidas está vacío");
        }
    }
}
