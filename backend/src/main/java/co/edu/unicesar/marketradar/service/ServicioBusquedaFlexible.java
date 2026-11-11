package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.domain.ConsultaBusqueda;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Búsqueda tolerante, al estilo de Google: entiende lo que la persona quiso buscar aunque escriba con errores
 * o con frases completas.
 *
 * <ol>
 *   <li>Quita el relleno: "quiero comprar una olla arrocera" busca "olla arrocera".</li>
 *   <li>Corrige las palabras mal escritas y busca directamente lo corregido: "licuadorra" busca "licuadora".
 *       La persona puede pedir buscar exactamente lo que escribió.</li>
 *   <li>Si no hay resultados y la búsqueda tiene varias palabras, prueba con la palabra principal:
 *       "licuadora oster roja 10 velocidades" busca "licuadora".</li>
 * </ol>
 *
 * Solo la búsqueda que se muestra queda en el historial. Los intentos quedan en caché, así que no gastan
 * consultas de más a las tiendas cuando se repiten.
 */
@Service
public class ServicioBusquedaFlexible {

    private final ServicioBusqueda servicioBusqueda;
    private final ServicioSugerencias servicioSugerencias;

    public ServicioBusquedaFlexible(ServicioBusqueda servicioBusqueda, ServicioSugerencias servicioSugerencias) {
        this.servicioBusqueda = servicioBusqueda;
        this.servicioSugerencias = servicioSugerencias;
    }

    /**
     * Busca entendiendo errores y frases.
     *
     * @param consulta lo que pidió la persona
     * @param usuarioId persona con sesión, o {@code null}
     * @param exacto {@code true} para buscar el texto tal como se escribió, sin corregirlo
     * @return resultado y qué se buscó en realidad
     */
    public Resultado buscar(ConsultaBusqueda consulta, Long usuarioId, boolean exacto) {
        if (exacto || consulta.getPagina() > 1) {
            return new Resultado(consulta, servicioBusqueda.buscar(consulta, usuarioId), null, false);
        }
        String escrito = consulta.getTermino();
        String limpio = ServicioSugerencias.limpiarFrase(escrito);
        String corregido = servicioSugerencias.corregir(limpio).orElse(limpio);
        ConsultaBusqueda elegida = consulta.conTermino(corregido);
        boolean ampliada = false;

        ResultadoBusqueda prueba = servicioBusqueda.consultar(elegida);
        // Si una tienda falló, el 0 no quiere decir que el producto no exista: se muestra el aviso y no se cambia
        // la búsqueda por otra más general (antes "licuadora oster" terminaba mostrando cualquier licuadora).
        if (prueba.getTotalResultados() == 0 && prueba.getAdvertencias().isEmpty()) {
            Optional<String> principal = ServicioSugerencias.nucleo(corregido);
            if (principal.isPresent() && principal.get().length() >= ConsultaBusqueda.LONGITUD_MINIMA) {
                ConsultaBusqueda amplia = consulta.conTermino(principal.get());
                if (servicioBusqueda.consultar(amplia).getTotalResultados() > 0) {
                    elegida = amplia;
                    ampliada = true;
                }
            }
        }
        // Viene de la caché: no se vuelve a consultar a las tiendas, solo se guarda en el historial.
        ResultadoBusqueda resultado = servicioBusqueda.buscar(elegida, usuarioId);
        boolean cambio = !mismoTexto(elegida.getTermino(), limpio);
        return new Resultado(elegida, resultado, cambio ? escrito : null, ampliada);
    }

    private static boolean mismoTexto(String a, String b) {
        return a.strip().toLowerCase(Locale.ROOT).equals(b.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * Lo que se buscó y lo que se encontró.
     *
     * @param consulta consulta que se buscó de verdad (con el texto corregido, si hubo corrección)
     * @param resultado ofertas encontradas
     * @param terminoOriginal lo que escribió la persona, si se buscó otra cosa; {@code null} si se buscó tal cual
     * @param ampliada {@code true} si no hubo resultados y se buscó solo la palabra principal
     */
    public record Resultado(ConsultaBusqueda consulta, ResultadoBusqueda resultado, String terminoOriginal,
                            boolean ampliada) {
    }
}
