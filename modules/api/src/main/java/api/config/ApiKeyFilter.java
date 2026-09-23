package api.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import api.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Esquema de autenticación simple pedido por la letra de la Iteración 3:
 * "adoptar un esquema simple (p. ej. API key o token Bearer)". Se eligió
 * API key por header, tal cual queda declarado en el securityScheme
 * "ApiKeyAuth" de docs/api/openapi.yaml (header X-API-KEY).
 *
 * Se implementa como filtro de Servlet (y no con spring-boot-starter-security)
 * a propósito: no hay gestión de usuarios ni roles pedida, así que una
 * dependencia entera de autenticación/autorización sería sobreingeniería
 * para "proteger el acceso al API" con una única clave compartida.
 *
 * Al ser un @Component que implementa Filter, Spring Boot lo registra
 * automáticamente para TODAS las rutas del contexto (no hace falta un
 * FilterRegistrationBean): cubre por igual /habitaciones/** y /controlador/**,
 * que es justo lo que pide la consigna ("proteger el acceso al API" en
 * general, sin distinguir CRUD de comandos).
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER_API_KEY = "X-API-KEY";

    private final String apiKeyEsperada;
    private final ObjectMapper objectMapper;

    /**
     * El default "changeme-dev-key" está DOS veces a propósito: acá en la
     * anotación (red de seguridad si "api.security.api-key" no existiera
     * en el classpath por algún problema de build) y también en
     * application.properties (donde además resuelve la variable de
     * entorno API_KEY real de docker-compose). Si algún día ambos
     * defaults coinciden en producción, es señal de que la property del
     * archivo no se está cargando -- revisar el build antes que el código.
     */
    public ApiKeyFilter(@Value("${api.security.api-key:changeme-dev-key}") String apiKeyEsperada, ObjectMapper objectMapper) {
        this.apiKeyEsperada = apiKeyEsperada;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKeyRecibida = request.getHeader(HEADER_API_KEY);

        if (apiKeyRecibida == null || !apiKeyRecibida.equals(apiKeyEsperada)) {
            responderNoAutorizado(response);
            return; // no se llama a filterChain.doFilter: la petición no llega al controller
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Se escribe la respuesta acá mismo (no se puede delegar en
     * ApiExceptionHandler): un filtro corre ANTES del DispatcherServlet,
     * así que una excepción tirada acá nunca pasa por un @RestControllerAdvice.
     * Se arma el mismo ErrorResponse que usa el resto del API para que el
     * cliente reciba siempre el mismo formato de error, sea cual sea la causa.
     */
    private void responderNoAutorizado(HttpServletResponse response) throws IOException {
        String mensaje = "Falta el header " + HEADER_API_KEY + " o el valor enviado es incorrecto.";
        ErrorResponse body = new ErrorResponse(HttpServletResponse.SC_UNAUTHORIZED, mensaje);

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
