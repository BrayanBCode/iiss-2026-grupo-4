package uy.edu.utec.iiss.engine.rest.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uy.edu.utec.iiss.engine.rest.error.ErrorResponse;

/**
 * Autenticación del estándar: header {@code X-API-Key} en todas las operaciones; si falta o no
 * coincide → 401 con el cuerpo de error uniforme. Es la clave que el controlador declara al
 * registrarse (§6). Sin usuarios ni roles, no hace falta Spring Security.
 */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    static final String HEADER_API_KEY = "X-API-Key";

    private final byte[] apiKeyEsperada;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(
            @Value("${api.security.api-key}") String apiKeyEsperada, ObjectMapper objectMapper) {
        if (apiKeyEsperada == null || apiKeyEsperada.isBlank()) {
            throw new IllegalStateException(
                    "API_KEY no puede estar vacía: el §6 exige declarar una clave al registrarse");
        }
        this.apiKeyEsperada = apiKeyEsperada.getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String recibida = request.getHeader(HEADER_API_KEY);
        // Comparación en tiempo constante: no filtra por timing cuántos caracteres coinciden.
        if (recibida == null
                || !MessageDigest.isEqual(
                        recibida.getBytes(StandardCharsets.UTF_8), apiKeyEsperada)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(
                    response.getWriter(),
                    new ErrorResponse(
                            HttpServletResponse.SC_UNAUTHORIZED,
                            "falta o no coincide el header " + HEADER_API_KEY));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
