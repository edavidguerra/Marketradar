package co.edu.unicesar.marketradar.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agrega cabeceras de seguridad HTTP a todas las respuestas y limita las peticiones a la API por IP
 * (ventana fija de un minuto).
 */
@Component
public class FiltroSeguridad extends OncePerRequestFilter {

    static final int PETICIONES_POR_MINUTO = 60;

    private final Cache<String, AtomicInteger> peticionesPorIp = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        respuesta.setHeader("X-Content-Type-Options", "nosniff");
        respuesta.setHeader("X-Frame-Options", "DENY");
        respuesta.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        if (!esDocumentacionApi(peticion.getRequestURI())) {
            respuesta.setHeader("Content-Security-Policy",
                    "default-src 'self'; img-src 'self' https: data:; style-src 'self'; script-src 'self'");
        }

        if (peticion.getRequestURI().startsWith("/api/") && superaLimite(peticion.getRemoteAddr())) {
            respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
            respuesta.setCharacterEncoding("UTF-8");
            respuesta.getWriter().write("{\"codigo\":\"DEMASIADAS_PETICIONES\","
                    + "\"mensaje_usuario\":\"Hizo muchas consultas seguidas. Espere un minuto y vuelva a intentar.\"}");
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private boolean esDocumentacionApi(String ruta) {
        return ruta.startsWith("/api/docs") || ruta.startsWith("/swagger-ui") || ruta.startsWith("/v3/api-docs");
    }

    private boolean superaLimite(String ip) {
        AtomicInteger contador = peticionesPorIp.get(ip, clave -> new AtomicInteger());
        return contador.incrementAndGet() > PETICIONES_POR_MINUTO;
    }
}
