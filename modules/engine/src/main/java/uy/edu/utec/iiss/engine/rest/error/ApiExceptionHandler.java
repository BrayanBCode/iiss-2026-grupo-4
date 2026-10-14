package uy.edu.utec.iiss.engine.rest.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import uy.edu.utec.iiss.engine.inventario.ConflictoException;
import uy.edu.utec.iiss.engine.inventario.InventarioInvalidoException;
import uy.edu.utec.iiss.engine.inventario.NoEncontradoException;

import java.util.List;

/**
 * Mapeo de excepciones a HTTP, siempre con el cuerpo de error uniforme del
 * estándar. Códigos: 400 payload inválido, 404 inexistente, 409 duplicado.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(InventarioInvalidoException.class)
    public ResponseEntity<ErrorResponse> inventarioInvalido(InventarioInvalidoException ex) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.errores());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalidaException ex) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoIlegible(HttpMessageNotReadableException ex) {
        String detalle = ex.getMostSpecificCause().getMessage();
        return responder(HttpStatus.BAD_REQUEST, "cuerpo inválido: no es el JSON esperado",
                detalle == null ? null : List.of(detalle.lines().findFirst().orElse(detalle)));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> tipoNoSoportado(HttpMediaTypeNotSupportedException ex) {
        return responder(HttpStatus.BAD_REQUEST, "se espera Content-Type: application/json", null);
    }

    @ExceptionHandler(NoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(NoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaInexistente(NoResourceFoundException ex) {
        return responder(HttpStatus.NOT_FOUND, "No existe " + ex.getResourcePath(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoSoportado(HttpRequestMethodNotSupportedException ex) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponse> conflicto(ConflictoException ex) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception ex) {
        log.error("Error no previsto atendiendo una solicitud", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "error interno: " + ex.getMessage(), null);
    }

    private static ResponseEntity<ErrorResponse> responder(HttpStatus status, String mensaje, List<String> errores) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), mensaje, errores));
    }
}
