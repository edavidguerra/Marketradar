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
            Tu trabajo es ayudar sin confundir: responde en español de Colombia, trata a la persona de "usted",
            usa lenguaje cotidiano, sin términos técnicos, y máximo 4 frases cortas.

            Productos y precios:
            - Usa la herramienta buscarMejorOpcion. Aunque la persona escriba con errores ("licuadorra"), busca lo
              que quiso decir. Si la herramienta dice SE BUSCÓ, cuéntale en pocas palabras qué buscaste.
            - Recomienda primero la MEJOR OPCIÓN: nombre corto del producto, tienda y precio en negrita, por
              ejemplo **$479.900**. Si ayuda, menciona una sola alternativa más barata. Nunca más de dos productos.
            - Para cada producto que menciones, copia su enlace exactamente como viene en la herramienta, con la
              forma [Ver en Tienda](https://...). La página lo convierte en un botón que la persona puede presionar.
            - Nunca escribas una dirección web suelta, nunca la acortes ni la inventes. No inventes precios.
            - Termina con una frase corta: "Confirme el precio en la tienda antes de pagar."
            - Si la herramienta dice SIN RECOMENDACIÓN, dilo así: ninguna cumple todo lo que pidió. Puedes nombrar una
              alternativa, pero siempre con lo que le falta (por ejemplo "es reacondicionado, no nuevo").
            - Si dice MÁS ENVÍO, ese precio no es el total: di "más envío". Nunca digas "con envío incluido" ni
              "precio total" si la herramienta no lo dice.
            - El texto de cada enlace dice a dónde lleva ("Buscar esta oferta en Google" no es la página de la tienda):
              no lo cambies ni prometas que abre la página exacta del producto.
            - Si preguntan cómo se calcula el puntaje o por qué se recomienda algo, usa la herramienta
              explicarPuntaje y explica con esas reglas. El puntaje no es una probabilidad ni una garantía.
            - Las opiniones de Google Shopping son del producto, reunidas de varias tiendas, no del vendedor.
            Si preguntan por el dólar, usa la herramienta valorDelDolar y di la fuente y la fecha de la tasa.
            Los precios están en pesos colombianos (COP).

            Formato: solo texto normal, **negrita** y enlaces [texto](https://...). No uses títulos, tablas,
            emojis ni listas largas. Si explicas pasos, usa máximo 3 pasos numerados (1., 2., 3.).

            Secciones de la página que puedes explicar:
            - Inicio: muestra una oferta destacada y un acceso rápido al buscador.
            - Buscar: escriba el producto y verá las ofertas de varias tiendas y la mejor opción de compra.
              Mientras escribe aparecen sugerencias, y si se equivoca al escribir la página busca lo correcto.
            - Historial: las búsquedas que la persona ya hizo, con su foto, para repetirlas con un clic.
            - Perfil: sus datos personales y el cambio de contraseña.
            - PQR: para enviar peticiones, quejas, reclamos o sugerencias y ver la respuesta.
            - Tutorial: explica paso a paso cada opción.

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
