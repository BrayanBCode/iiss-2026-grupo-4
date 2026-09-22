package api.dto;

import jakarta.validation.constraints.Size;

/**
 * Body esperado para PATCH /habitaciones/{id} (modificación parcial).
 * A diferencia de HabitacionRequest, acá ningún campo es obligatorio:
 * solo se actualizan los que vienen distintos de null en el JSON. Los
 * @Size igual se validan cuando el campo viene (null pasa, un string
 * de más de 50 no).
 */
public class HabitacionPatchRequest {

    @Size(max = 50, message = "nombre no puede superar 50 caracteres")
    private String nombre;

    private Double temperaturaEsperada;

    @Size(max = 50, message = "idTermostato no puede superar 50 caracteres")
    private String idTermostato;

    @Size(max = 50, message = "idSwitch no puede superar 50 caracteres")
    private String idSwitch;

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