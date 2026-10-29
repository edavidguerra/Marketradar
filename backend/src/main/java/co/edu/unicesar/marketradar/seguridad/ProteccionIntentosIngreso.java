package co.edu.unicesar.marketradar.seguridad;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regla de negocio contra adivinar contraseñas: después de 5 intentos fallidos seguidos,
 * la cuenta queda bloqueada 15 minutos.
 */
@Component
public class ProteccionIntentosIngreso {

    public static final int INTENTOS_MAXIMOS = 5;
    public static final Duration TIEMPO_BLOQUEO = Duration.ofMinutes(15);

    private final Cache<String, AtomicInteger> fallosPorCorreo = Caffeine.newBuilder()
            .expireAfterWrite(TIEMPO_BLOQUEO)
            .maximumSize(10_000)
            .build();

    /**
     * @param correo correo con el que se intenta ingresar
     * @return {@code true} si la cuenta está bloqueada temporalmente
     */
    public boolean estaBloqueado(String correo) {
        AtomicInteger fallos = fallosPorCorreo.getIfPresent(clave(correo));
        return fallos != null && fallos.get() >= INTENTOS_MAXIMOS;
    }

    /**
     * Suma un intento fallido.
     *
     * @param correo correo usado
     */
    public void registrarFallo(String correo) {
        fallosPorCorreo.asMap().computeIfAbsent(clave(correo), c -> new AtomicInteger()).incrementAndGet();
    }

    /**
     * Borra los fallos después de un ingreso correcto.
     *
     * @param correo correo usado
     */
    public void registrarExito(String correo) {
        fallosPorCorreo.invalidate(clave(correo));
    }

    private static String clave(String correo) {
        return correo == null ? "" : correo.strip().toLowerCase(Locale.ROOT);
    }
}
