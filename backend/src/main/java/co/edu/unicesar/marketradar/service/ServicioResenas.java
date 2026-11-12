package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.adapter.googleshopping.AdaptadorGoogleShopping;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
import co.edu.unicesar.marketradar.domain.RequisitosBusqueda;
import co.edu.unicesar.marketradar.domain.ResenasProducto;
import co.edu.unicesar.marketradar.domain.ResultadoBusqueda;
import co.edu.unicesar.marketradar.exception.MarketplaceException;
import co.edu.unicesar.marketradar.exception.RecursoNoEncontradoException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Reseñas de los productos que aparecieron en una búsqueda.
 *
 * <p>Google Shopping entrega con cada producto una clave para pedir después sus reseñas. La clave se recuerda en
 * memoria con el id de la oferta (no se guarda en la base de datos) y, cuando la persona presiona "Ver reseñas",
 * se piden a SerpApi. Cada consulta gasta una búsqueda del plan, así que las reseñas quedan en caché un día.</p>
 */
@Service
public class ServicioResenas {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioResenas.class);
    private static final Duration VIGENCIA = Duration.ofHours(24);

    private final AdaptadorGoogleShopping googleShopping;
    private final Cache<Long, Recordada> tokens = Caffeine.newBuilder()
            .expireAfterWrite(VIGENCIA).maximumSize(20_000).build();
    private final Cache<Long, ResenasProducto> resenas = Caffeine.newBuilder()
            .expireAfterWrite(VIGENCIA).maximumSize(2_000).build();

    public ServicioResenas(AdaptadorGoogleShopping googleShopping) {
        this.googleShopping = googleShopping;
    }

    /**
     * Recuerda las claves de reseñas de las ofertas de una búsqueda.
     *
     * @param resultado resultado que se va a mostrar
     */
    public void recordar(ResultadoBusqueda resultado) {
        resultado.getOfertas().forEach(oferta -> recordar(oferta, resultado.idPersistido(oferta)));
        resultado.getProductoMvp().ifPresent(mvp -> recordar(mvp.oferta(), resultado.idPersistido(mvp.oferta())));
        resultado.getDetalle().alternativas()
                .forEach(alternativa -> recordar(alternativa.oferta(), resultado.idPersistido(alternativa.oferta())));
    }

    private void recordar(OfertaNormalizada oferta, Long id) {
        if (id != null && oferta.getTokenResenas() != null) {
            tokens.put(id, new Recordada(oferta.getTokenResenas(), oferta.getTitulo(), oferta.getVendedor()));
        }
    }

    /**
     * Revisa que las opiniones que entregó Google sean del mismo producto que la oferta: el mismo número de modelo y
     * la misma versión (un iPhone 13 Pro no es un iPhone 13). La capacidad y el color no se exigen, porque Google
     * junta las opiniones de todas las capacidades y colores de un modelo, y así se le dice a la persona.
     *
     * @param id oferta
     * @param resenas opiniones que entregó Google
     * @return si la asociación se pudo confirmar, y la explicación para la persona
     */
    public Procedencia procedencia(long id, ResenasProducto resenas) {
        Recordada recordada = tokens.getIfPresent(id);
        String vendedor = recordada == null || recordada.vendedor() == null ? "esta tienda" : recordada.vendedor();
        String deQuien = "Son opiniones del producto, reunidas por Google de varias tiendas; no son opiniones sobre "
                + vendedor + ".";
        if (recordada == null || resenas.tituloProducto() == null) {
            return new Procedencia(Procedencia.Estado.SIN_VERIFICAR, deQuien
                    + " Google no dijo a qué producto corresponden, así que pueden ser de otra versión.");
        }
        RequisitosBusqueda modelo = RequisitosBusqueda.de(recordada.titulo());
        if (modelo.modelos().isEmpty()) {
            // Sin número de modelo en el título ("Olla arrocera") no hay con qué comparar: se dice a qué producto
            // las asocia Google y la persona juzga.
            return new Procedencia(Procedencia.Estado.SIN_VERIFICAR, deQuien + " Google las asocia a \""
                    + resenas.tituloProducto() + "\".");
        }
        RequisitosBusqueda soloModelo = new RequisitosBusqueda(modelo.texto(), Optional.empty(), java.util.Set.of(),
                modelo.modelos(), modelo.versiones(), true);
        RequisitosBusqueda.Evaluacion evaluacion = soloModelo.evaluar(resenas.tituloProducto(), Optional.empty());
        return switch (evaluacion.nivel()) {
            case CUMPLE -> new Procedencia(Procedencia.Estado.VERIFICADA, deQuien + " Google las asocia a \""
                    + resenas.tituloProducto() + "\", el mismo modelo; pueden incluir otras capacidades o colores.");
            case SIN_CONFIRMAR -> new Procedencia(Procedencia.Estado.SIN_VERIFICAR, deQuien
                    + " Google las asocia a \"" + resenas.tituloProducto()
                    + "\" y no pudimos confirmar que sea el mismo modelo.");
            case INCUMPLE -> new Procedencia(Procedencia.Estado.RECHAZADA, "Google entregó opiniones de \""
                    + resenas.tituloProducto() + "\", que es otro modelo o versión. Por eso no las mostramos.");
        };
    }

    /**
     * De dónde son las opiniones de una oferta.
     *
     * @param estado si se pudo confirmar que son del mismo producto
     * @param nota explicación para la persona
     */
    public record Procedencia(Estado estado, String nota) {

        /** Si las opiniones son del mismo producto que la oferta. */
        public enum Estado { VERIFICADA, SIN_VERIFICAR, RECHAZADA }
    }

    /** Lo que se recuerda de una oferta para pedir y revisar sus opiniones. */
    private record Recordada(String token, String titulo, String vendedor) {
    }

    /**
     * @param id oferta
     * @return {@code true} si se le pueden pedir las reseñas a la tienda
     */
    public boolean disponibles(Long id) {
        return id != null && tokens.getIfPresent(id) != null && googleShopping.estaHabilitado();
    }

    /**
     * Reseñas de una oferta que apareció en una búsqueda reciente.
     *
     * @param id oferta
     * @return reseñas de la tienda, o vacío si la tienda no da reseñas de ese producto
     * @throws RecursoNoEncontradoException si la oferta no salió en una búsqueda reciente
     */
    public Optional<ResenasProducto> obtener(long id) {
        ResenasProducto guardadas = resenas.getIfPresent(id);
        if (guardadas != null) {
            return guardadas.estaVacia() ? Optional.empty() : Optional.of(guardadas);
        }
        Recordada recordada = tokens.getIfPresent(id);
        if (recordada == null || !googleShopping.estaHabilitado()) {
            throw new RecursoNoEncontradoException(
                    "No tenemos las reseñas de este producto. Vuelva a buscarlo y presione \"Ver reseñas\".");
        }
        try {
            ResenasProducto nuevas = googleShopping.resenas(recordada.token());
            resenas.put(id, nuevas);
            return nuevas.estaVacia() ? Optional.empty() : Optional.of(nuevas);
        } catch (MarketplaceException e) {
            // El manejador de errores le muestra a la persona un mensaje sencillo, sin estos detalles.
            LOG.warn("No se pudieron traer las reseñas de la oferta {}: {}", id, e.getMessage());
            throw e;
        }
    }
}
