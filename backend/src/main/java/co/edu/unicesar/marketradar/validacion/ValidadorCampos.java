package co.edu.unicesar.marketradar.validacion;

import co.edu.unicesar.marketradar.domain.TipoDocumento;
import co.edu.unicesar.marketradar.exception.ValidacionException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validaciones estándar de los campos que escribe el usuario. Cada método limpia el valor
 * (espacios, mayúsculas) y lo devuelve listo para guardar, o lanza {@link ValidacionException}
 * con un mensaje sencillo que se muestra tal cual en pantalla.
 */
public final class ValidadorCampos {

    public static final int EDAD_MINIMA = 18;
    public static final int EDAD_MAXIMA = 120;

    private static final Pattern CORREO =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final Pattern NOMBRE = Pattern.compile("^[\\p{L}]+([ '\\-][\\p{L}]+)*$");
    private static final Pattern CELULAR_COLOMBIA = Pattern.compile("^3\\d{9}$");
    private static final Pattern SOLO_DIGITOS = Pattern.compile("^\\d+$");
    private static final Pattern ALFANUMERICO = Pattern.compile("^[A-Z0-9]+$");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    private ValidadorCampos() {
    }

    /**
     * @param correo correo escrito por el usuario
     * @return correo en minúsculas y sin espacios
     */
    public static String correo(String correo) {
        String limpio = obligatorio(correo, "el correo electrónico").toLowerCase(Locale.ROOT);
        if (limpio.length() > 150 || !CORREO.matcher(limpio).matches()) {
            throw new ValidacionException("Escriba un correo válido, por ejemplo nombre@correo.com.");
        }
        return limpio;
    }

    /**
     * Valida nombres o apellidos: solo letras (con tildes y ñ), espacios, apóstrofo o guion.
     *
     * @param valor texto escrito
     * @param campo nombre del campo para el mensaje, por ejemplo "los nombres"
     * @return texto sin espacios repetidos
     */
    public static String nombre(String valor, String campo) {
        String limpio = ESPACIOS.matcher(obligatorio(valor, campo)).replaceAll(" ");
        if (limpio.length() < 2 || limpio.length() > 80) {
            throw new ValidacionException("Revise " + campo + ": debe tener entre 2 y 80 letras.");
        }
        if (!NOMBRE.matcher(limpio).matches()) {
            throw new ValidacionException("Revise " + campo + ": use solo letras, sin números ni símbolos.");
        }
        return limpio;
    }

    /**
     * @param ciudad ciudad de residencia; es opcional
     * @return ciudad limpia, o {@code null} si no la escribió
     */
    public static String ciudad(String ciudad) {
        if (estaVacio(ciudad)) {
            return null;
        }
        return nombre(ciudad, "la ciudad");
    }

    /**
     * @param tipo código del tipo de documento (CC, CE o PA); es opcional
     * @return tipo de documento, o {@code null} si no lo eligió
     */
    public static TipoDocumento tipoDocumento(String tipo) {
        if (estaVacio(tipo)) {
            return null;
        }
        try {
            return TipoDocumento.valueOf(tipo.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("Elija un tipo de documento de la lista.");
        }
    }

    /**
     * Valida el número según el tipo: cédulas de 6 a 10 dígitos y pasaporte de 6 a 12 letras o números.
     *
     * @param tipo tipo de documento ya validado
     * @param numero número escrito
     * @return número sin espacios ni puntos, o {@code null} si ambos están vacíos
     */
    public static String numeroDocumento(TipoDocumento tipo, String numero) {
        if (tipo == null && estaVacio(numero)) {
            return null;
        }
        if (tipo == null) {
            throw new ValidacionException("Elija el tipo de documento.");
        }
        String limpio = obligatorio(numero, "el número de documento")
                .replace(".", "").replace(" ", "").toUpperCase(Locale.ROOT);
        boolean valido = switch (tipo) {
            case CC, CE -> SOLO_DIGITOS.matcher(limpio).matches() && limpio.length() >= 6 && limpio.length() <= 10;
            case PA -> ALFANUMERICO.matcher(limpio).matches() && limpio.length() >= 6 && limpio.length() <= 12;
        };
        if (!valido) {
            throw new ValidacionException(tipo == TipoDocumento.PA
                    ? "El pasaporte debe tener entre 6 y 12 letras o números."
                    : "El número de cédula debe tener entre 6 y 10 números, sin puntos.");
        }
        return limpio;
    }

    /**
     * @param celular número escrito; es opcional
     * @return celular de 10 dígitos que empieza por 3, o {@code null}
     */
    public static String celular(String celular) {
        if (estaVacio(celular)) {
            return null;
        }
        String limpio = celular.replace(" ", "").replace("-", "");
        if (limpio.startsWith("+57")) {
            limpio = limpio.substring(3);
        }
        if (!CELULAR_COLOMBIA.matcher(limpio).matches()) {
            throw new ValidacionException("El celular debe tener 10 números y empezar por 3, por ejemplo 3001234567.");
        }
        return limpio;
    }

    /**
     * Exige mayoría de edad y una fecha razonable.
     *
     * @param fecha fecha de nacimiento; es opcional
     * @param reloj reloj del sistema, para poder probar
     * @return la misma fecha, o {@code null}
     */
    public static LocalDate fechaNacimiento(LocalDate fecha, Clock reloj) {
        if (fecha == null) {
            return null;
        }
        LocalDate hoy = LocalDate.now(reloj);
        if (fecha.isAfter(hoy)) {
            throw new ValidacionException("La fecha de nacimiento no puede ser posterior a hoy.");
        }
        int edad = Period.between(fecha, hoy).getYears();
        if (edad < EDAD_MINIMA) {
            throw new ValidacionException("Debe ser mayor de edad para usar Marketradar.");
        }
        if (edad > EDAD_MAXIMA) {
            throw new ValidacionException("Revise el año de la fecha de nacimiento.");
        }
        return fecha;
    }

    /**
     * Valida un texto libre con longitud mínima y máxima.
     *
     * @param valor texto escrito
     * @param campo nombre del campo para el mensaje
     * @param minimo cantidad mínima de caracteres
     * @param maximo cantidad máxima de caracteres
     * @return texto sin espacios al inicio ni al final
     */
    public static String texto(String valor, String campo, int minimo, int maximo) {
        String limpio = obligatorio(valor, campo);
        if (limpio.length() < minimo || limpio.length() > maximo) {
            throw new ValidacionException("Revise " + campo + ": debe tener entre " + minimo + " y " + maximo
                    + " caracteres.");
        }
        return limpio;
    }

    /**
     * Convierte el texto de una lista en su valor de enumeración.
     *
     * @param valor texto recibido
     * @param tipo enumeración esperada
     * @param campo nombre del campo para el mensaje
     * @param <E> tipo de la enumeración
     * @return valor de la enumeración
     */
    public static <E extends Enum<E>> E opcion(String valor, Class<E> tipo, String campo) {
        String limpio = obligatorio(valor, campo);
        try {
            return Enum.valueOf(tipo, limpio.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("Elija " + campo + " de la lista.");
        }
    }

    private static String obligatorio(String valor, String campo) {
        if (estaVacio(valor)) {
            throw new ValidacionException("Escriba " + campo + ".");
        }
        return valor.strip();
    }

    private static boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
