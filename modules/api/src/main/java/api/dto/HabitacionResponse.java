package api.dto;

import api.model.Habitacion;

/**
 * Representación de una habitación que devuelve el API. Se usa un DTO de
 * salida separado de la entidad para no acoplar el contrato REST al modelo
 * de persistencia.
 */
public class HabitacionResponse {

    private Long id;
    private String nombre;
    private Double temperaturaEsperada;
    private String idTermostato;
    private String idSwitch;

    public HabitacionResponse() {
    }

    public static HabitacionResponse desde(Habitacion habitacion) {
        HabitacionResponse dto = new HabitacionResponse();
        dto.id = habitacion.getId();
        dto.nombre = habitacion.getNombre();
        dto.temperaturaEsperada = habitacion.getTemperaturaEsperada();
        dto.idTermostato = habitacion.getIdTermostato();
        dto.idSwitch = habitacion.getIdSwitch();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Double getTemperaturaEsperada() {
        return temperaturaEsperada;
    }

    public String getIdTermostato() {
        return idTermostato;
    }

    public String getIdSwitch() {
        return idSwitch;
    }
}