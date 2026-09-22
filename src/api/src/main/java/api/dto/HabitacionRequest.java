package api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body esperado para POST /habitaciones (alta) y PUT /habitaciones/{id}
 * (modificación completa). Todos los campos son obligatorios: en un PUT,
 * lo que no se manda se pierde (reemplazo completo del recurso).
 *
 * nombre, idTermostato e idSwitch son UNIQUE en la tabla (ver HabitacionDAO
 * del subscriber): un valor repetido da 409 Conflict, no 400.
 */
public class HabitacionRequest {

    @NotBlank(message = "nombre es obligatorio")
    @Size(max = 50, message = "nombre no puede superar 50 caracteres")
    private String nombre;

    @NotNull(message = "temperaturaEsperada es obligatoria")
    private Double temperaturaEsperada;

    @NotBlank(message = "idTermostato es obligatorio")
    @Size(max = 50, message = "idTermostato no puede superar 50 caracteres")
    private String idTermostato;

    @NotBlank(message = "idSwitch es obligatorio")
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