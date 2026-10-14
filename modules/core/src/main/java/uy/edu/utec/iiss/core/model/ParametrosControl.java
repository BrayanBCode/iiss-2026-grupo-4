package uy.edu.utec.iiss.core.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Parámetros de la función de decisión que NO vienen del inventario del
 * sitio (el estándar dice que la histéresis es "decisión del controlador, no
 * dato del sitio").
 *
 * @param histeresisC             banda (°C) por debajo de la esperada en la que
 *                                una habitación apagada NO se enciende. Una
 *                                encendida sigue hasta llegar a la esperada.
 *                                Evita que el relé conmute con cada décima.
 * @param vigenciaLectura         cuánto tiempo REAL vale una lectura. Pasado
 *                                eso (escalado por el factor de tiempo vigente)
 *                                la habitación se trata como sin dato y se apaga:
 *                                si el termostato murió no se calefacciona a ciegas.
 * @param temperaturaMinimaValida lecturas por debajo se descartan (sensor roto).
 * @param temperaturaMaximaValida lecturas por encima se descartan (sensor roto).
 */
public record ParametrosControl(double histeresisC,
                                Duration vigenciaLectura,
                                double temperaturaMinimaValida,
                                double temperaturaMaximaValida) {

    /** 0.3 °C de histéresis, 10 min de vigencia, rango físico -40..85 °C (el del Shelly H&T). */
    public static final ParametrosControl PREDETERMINADOS =
            new ParametrosControl(0.3, Duration.ofMinutes(10), -40.0, 85.0);

    public ParametrosControl {
        Objects.requireNonNull(vigenciaLectura, "vigenciaLectura");
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
