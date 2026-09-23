package api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad JPA para el recurso "habitacion".
 *
 * La tabla "habitaciones" ya la crea el subscriber (HabitacionDAO.crearTablaSiNoExiste,
 * con ddl-auto=none del lado del API), con este DDL:
 *
 *   CREATE TABLE IF NOT EXISTS habitaciones (
 *       id                    SERIAL       PRIMARY KEY,
 *       nombre                VARCHAR(50)  NOT NULL UNIQUE,
 *       termostato_id         VARCHAR(50)  NOT NULL UNIQUE,
 *       switch_id             VARCHAR(50)  NOT NULL UNIQUE,
 *       temperatura_objetivo  NUMERIC(4,1)
 *   )
 *
 * Los nombres de atributo Java (idTermostato, idSwitch, temperaturaEsperada) siguen
 * la terminología de la letra del laboratorio; los @Column apuntan a los nombres
 * reales de columna (termostato_id, switch_id, temperatura_objetivo). nombre,
 * termostato_id y switch_id tienen UNIQUE en la base: una violación se traduce a
 * 409 Conflict en el ApiExceptionHandler.
 */
@Entity
@Table(name = "habitaciones")
public class Habitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 50)
    private String nombre;

    @Column(name = "temperatura_objetivo")
    private Double temperaturaEsperada;

    @Column(name = "termostato_id", nullable = false, length = 50)
    private String idTermostato;

    @Column(name = "switch_id", nullable = false, length = 50)
    private String idSwitch;

    public Habitacion() {
        // requerido por JPA
    }

    public Habitacion(String nombre, Double temperaturaEsperada, String idTermostato, String idSwitch) {
        this.nombre = nombre;
        this.temperaturaEsperada = temperaturaEsperada;
        this.idTermostato = idTermostato;
        this.idSwitch = idSwitch;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Double getTemperaturaEsperada() {
        return temperaturaEsperada;
    }

    public void setTemperaturaEsperada(Double temperaturaEsperada) {
        this.temperaturaEsperada = temperaturaEsperada;
    }

    public String getIdTermostato() {
        return idTermostato;
    }

    public void setIdTermostato(String idTermostato) {
        this.idTermostato = idTermostato;
    }

    public String getIdSwitch() {
        return idSwitch;
    }

    public void setIdSwitch(String idSwitch) {
        this.idSwitch = idSwitch;
    }
}