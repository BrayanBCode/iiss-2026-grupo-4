package api.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centraliza el mapeo de excepciones a respuestas HTTP, para que los
 * controllers no tengan try/catch repetido en cada operación.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(HabitacionNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> handleNoEncontrada(HabitacionNoEncontradaException ex) {
        ErrorResponse body = new ErrorResponse(HttpStatus.NOT_FOUND.value(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicado(DataIntegrityViolationException ex) {
        // nombre, termostato_id y switch_id son UNIQUE en la tabla "habitaciones"
        ErrorResponse body = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Ya existe una habitación con ese nombre, idTermostato o idSwitch");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        ErrorResponse body = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(), "Datos inválidos", errores);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}