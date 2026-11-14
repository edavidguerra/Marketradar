package co.edu.unicesar.marketradar.service.seguimiento;

import co.edu.unicesar.marketradar.correo.MensajeCorreo;
import co.edu.unicesar.marketradar.domain.EnlaceCompra;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Textos de los correos, en español sencillo, con texto plano y HTML. Todo lo que viene de las tiendas se escapa.
 */
final class PlantillasCorreo {

    private static final Locale COLOMBIA = Locale.forLanguageTag("es-CO");
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a", COLOMBIA);

    private PlantillasCorreo() {
    }

    /** Datos de la oferta que se muestran en los correos. */
    record DatosOferta(String producto, String tienda, String urlOferta) {
    }

    static MensajeCorreo verificacion(String para, String nombre, String enlace) {
        List<String> parrafos = List.of(
                "Hola " + nombre + ":",
                "Para recibir avisos de precio en este correo, confirme que es suyo abriendo el enlace de abajo. "
                        + "El enlace sirve durante 24 horas y solo una vez.",
                "Si usted no lo pidió, no haga nada: no le mandaremos avisos.");
        return armar(para, "Confirme su correo en Marketradar", parrafos, Map.of(), List.of(
                new Enlace("Confirmar mi correo", enlace)));
    }

    static MensajeCorreo confirmacion(String para, String nombre, DatosOferta oferta, BigDecimal precio, String moneda,
                                      String queAvisamos, int frecuenciaMinutos, String urlSeguimientos,
                                      String urlCancelar) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Producto", oferta.producto());
        datos.put("Tienda", oferta.tienda());
        datos.put("Precio actual", precio(precio, moneda) + " (sin el envío)");
        datos.put("Moneda", moneda);
        datos.put("Le avisaremos", queAvisamos);
        datos.put("Revisamos el precio", "cada " + frecuencia(frecuenciaMinutos));
        List<String> parrafos = List.of(
                "Hola " + nombre + ":",
                "Empezó a seguir este producto en Marketradar. Le escribiremos cuando ocurra lo que eligió.",
                "Solo comparamos el precio de esta misma oferta: si cambia de vendedor, de condición o de versión, no lo "
                        + "tomamos como un descuento. Si la tienda falla o no muestra el precio, tampoco le avisamos.");
        return armar(para, "Empezó a seguir: " + corto(oferta.producto()), parrafos, datos,
                enlaces(oferta.urlOferta(), urlSeguimientos, urlCancelar));
    }

    static MensajeCorreo avisoPrecio(String para, String nombre, boolean objetivo, BigDecimal precioObjetivo,
                                     DatosOferta oferta, BigDecimal anterior, BigDecimal actual, String moneda,
                                     OffsetDateTime comprobadoEn, String urlSeguimientos, String urlCancelar) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("Producto", oferta.producto());
        datos.put("Tienda", oferta.tienda());
        datos.put("Precio anterior", anterior == null ? "No disponible" : precio(anterior, moneda));
        datos.put("Precio actual", precio(actual, moneda) + " (sin el envío)");
        if (objetivo && precioObjetivo != null) {
            datos.put("Su precio objetivo", precio(precioObjetivo, moneda));
        }
        datos.put("Moneda", moneda);
        datos.put("Fecha de comprobación", FECHA.format(comprobadoEn.atZoneSameInstant(ZONA)) + " (hora de Colombia)");
        List<String> parrafos = List.of(
                "Hola " + nombre + ":",
                objetivo ? "El producto que sigue llegó al precio que usted eligió."
                        : "El producto que sigue bajó de precio.",
                "El precio puede volver a cambiar en la tienda en cualquier momento. Revíselo allí antes de comprar. "
                        + "Marketradar nunca le pedirá datos bancarios.");
        String asunto = (objetivo ? "Llegó a su precio: " : "Bajó de precio: ") + corto(oferta.producto());
        return armar(para, asunto, parrafos, datos, enlaces(oferta.urlOferta(), urlSeguimientos, urlCancelar));
    }

    private record Enlace(String texto, String url) {
    }

    private static List<Enlace> enlaces(String urlOferta, String urlSeguimientos, String urlCancelar) {
        List<Enlace> enlaces = new ArrayList<>();
        if (EnlaceCompra.esSeguro(urlOferta)) {
            enlaces.add(new Enlace("Ver la oferta", urlOferta));
        }
        enlaces.add(new Enlace("Mis seguimientos", urlSeguimientos));
        enlaces.add(new Enlace("Dejar de seguir este producto", urlCancelar));
        return enlaces;
    }

    private static MensajeCorreo armar(String para, String asunto, List<String> parrafos, Map<String, String> datos,
                                       List<Enlace> enlaces) {
        StringBuilder texto = new StringBuilder();
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"es\"><body style=\"font-family:Arial,"
                + "sans-serif;font-size:18px;line-height:1.5;color:#1a1a1a\">");
        for (String parrafo : parrafos) {
            texto.append(parrafo).append("\n\n");
            html.append("<p>").append(HtmlUtils.htmlEscape(parrafo)).append("</p>");
        }
        if (!datos.isEmpty()) {
            html.append("<table role=\"presentation\" style=\"border-collapse:collapse\">");
            datos.forEach((clave, valor) -> {
                texto.append(clave).append(": ").append(valor).append('\n');
                html.append("<tr><th style=\"text-align:left;padding:4px 12px 4px 0\">")
                        .append(HtmlUtils.htmlEscape(clave)).append("</th><td style=\"padding:4px 0\">")
                        .append(HtmlUtils.htmlEscape(valor)).append("</td></tr>");
            });
            html.append("</table>");
            texto.append('\n');
        }
        for (Enlace enlace : enlaces) {
            texto.append(enlace.texto()).append(": ").append(enlace.url()).append('\n');
            html.append("<p><a href=\"").append(HtmlUtils.htmlEscape(enlace.url())).append("\">")
                    .append(HtmlUtils.htmlEscape(enlace.texto())).append("</a></p>");
        }
        texto.append("\nMarketradar · Comparador de precios");
        html.append("<p style=\"color:#555;font-size:15px\">Marketradar · Comparador de precios</p></body></html>");
        return new MensajeCorreo(para, asunto, texto.toString(), html.toString());
    }

    static String precio(BigDecimal valor, String moneda) {
        NumberFormat formato = NumberFormat.getNumberInstance(COLOMBIA);
        formato.setMaximumFractionDigits("COP".equals(moneda) ? 0 : 2);
        return ("COP".equals(moneda) ? "$ " : moneda + " ") + formato.format(valor);
    }

    static String frecuencia(int minutos) {
        if (minutos % 60 != 0) {
            return minutos + " minutos";
        }
        int horas = minutos / 60;
        return horas == 1 ? "hora" : horas + " horas";
    }

    private static String corto(String texto) {
        return texto.length() <= 70 ? texto : texto.substring(0, 67) + "...";
    }
}
