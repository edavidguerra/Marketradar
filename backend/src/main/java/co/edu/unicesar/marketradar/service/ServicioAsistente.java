package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.asistente.MensajeChat;
import co.edu.unicesar.marketradar.asistente.ProveedorIA;
import co.edu.unicesar.marketradar.asistente.RolMensaje;
import co.edu.unicesar.marketradar.exception.ValidacionException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Atiende las preguntas al chatbot: valida el mensaje, agrega las instrucciones del asistente y
 * limita el historial que se envía al proveedor de IA.
 */
@Service
public class ServicioAsistente {

    public static final int LONGITUD_MAXIMA_MENSAJE = 500;
    public static final int MENSAJES_DE_CONTEXTO = 10;

    static final String INSTRUCCIONES = """
            Eres el asistente de Marketradar, un comparador de precios para adultos mayores en Colombia.
            Responde en español de Colombia, con frases cortas, lenguaje cotidiano y sin términos técnicos.
            Ayuda a buscar productos, entender los resultados, el historial de precios y cómo ir a la tienda.
            Los precios están en pesos colombianos y pueden cambiar en la tienda.
            Nunca pidas contraseñas, datos bancarios ni datos personales.
            Si no sabes algo, dilo con honestidad y sugiere usar el buscador.
            """;

    private final ProveedorIA proveedor;

    public ServicioAsistente(ProveedorIA proveedor) {
        this.proveedor = proveedor;
    }

    /**
     * Responde el último mensaje del usuario teniendo en cuenta la conversación previa.
     *
     * @param historial mensajes anteriores del usuario y del asistente
     * @param mensajeUsuario pregunta nueva
     * @return respuesta del asistente
     * @throws ValidacionException si el mensaje está vacío o es demasiado largo
     */
    public String responder(List<MensajeChat> historial, String mensajeUsuario) {
        String limpio = mensajeUsuario == null ? "" : mensajeUsuario.strip();
        if (limpio.isEmpty()) {
            throw new ValidacionException("Escriba su pregunta para que el asistente pueda ayudarle.");
        }
        if (limpio.length() > LONGITUD_MAXIMA_MENSAJE) {
            throw new ValidacionException("La pregunta es muy larga. Escríbala en menos de 500 letras.");
        }
        List<MensajeChat> conversacion = new ArrayList<>();
        conversacion.add(new MensajeChat(RolMensaje.SISTEMA, INSTRUCCIONES));
        conversacion.addAll(ultimosMensajes(historial));
        conversacion.add(new MensajeChat(RolMensaje.USUARIO, limpio));
        return proveedor.responder(conversacion);
    }

    /**
     * Indica si el proveedor de IA está listo.
     *
     * @return {@code true} si está configurado
     */
    public boolean estaDisponible() {
        return proveedor.estaConfigurado();
    }

    /**
     * Nombre del proveedor de IA configurado.
     *
     * @return nombre del proveedor
     */
    public String nombreProveedor() {
        return proveedor.nombre();
    }

    private static List<MensajeChat> ultimosMensajes(List<MensajeChat> historial) {
        if (historial == null) {
            return List.of();
        }
        List<MensajeChat> sinSistema = historial.stream()
                .filter(mensaje -> mensaje.getRol() != RolMensaje.SISTEMA)
                .toList();
        int desde = Math.max(0, sinSistema.size() - MENSAJES_DE_CONTEXTO);
        return sinSistema.subList(desde, sinSistema.size());
    }
}
