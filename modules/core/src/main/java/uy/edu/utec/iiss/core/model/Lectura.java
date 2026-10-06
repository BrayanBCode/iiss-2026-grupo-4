package uy.edu.utec.iiss.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidad JPA de la tabla "lecturas" (esquema en db/migration/V1): una medida
 * de temperatura de un termostato, ya resuelta a la habitación dueña.
 *
 * epochMili es el instante de la medida en epoch milisegundos (UTC), tal como
 * lo guardaba el subscriber (campo "ts" del mensaje MQTT, en segundos, por 1000).
 */
@Entity
@Table(name = "lecturas")
public class Lectura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "habitacion_id", nullable = false)
    private Habitacion habitacion;

    @Column(name = "temperatura_c", nullable = false)
    private Double temperaturaC;

    @Column(name = "temperatura_f", nullable = false)
    private Double temperaturaF;

    @Column(name = "epoch_mili", nullable = false)
    private Long epochMili;

    public Lectura() {
        // requerido por JPA
    }

    public Lectura(Habitacion habitacion, Double temperaturaC, Double temperaturaF, Long epochMili) {
        this.habitacion = habitacion;
        this.temperaturaC = temperaturaC;
        this.temperaturaF = temperaturaF;
        this.epochMili = epochMili;
    }

    public Long getId() {
        return id;
    }

    public Habitacion getHabitacion() {
        return habitacion;
    }

    public Double getTemperaturaC() {
        return temperaturaC;
    }

    public Double getTemperaturaF() {
        return temperaturaF;
    }

    public Long getEpochMili() {
        return epochMili;
    }
}
