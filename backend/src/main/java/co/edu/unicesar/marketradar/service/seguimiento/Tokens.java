package co.edu.unicesar.marketradar.service.seguimiento;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Claves aleatorias para los enlaces de los correos.
 */
final class Tokens {

    private static final SecureRandom AZAR = new SecureRandom();

    private Tokens() {
    }

    /** @return 32 bytes al azar en Base64 para URL (43 caracteres) */
    static String nuevo() {
        byte[] bytes = new byte[32];
        AZAR.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** @return SHA-256 del texto, en hexadecimal (64 caracteres) */
    static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }

    /** @return {@code true} si tiene la forma de una clave de {@link #nuevo()} */
    static boolean pareceToken(String texto) {
        return texto != null && texto.matches("[A-Za-z0-9_-]{43}");
    }

    /** Compara dos textos sin revelar por el tiempo cuántos caracteres coinciden. */
    static boolean iguales(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
