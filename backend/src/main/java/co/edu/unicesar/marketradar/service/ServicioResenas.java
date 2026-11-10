package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.adapter.googleshopping.AdaptadorGoogleShopping;
import co.edu.unicesar.marketradar.domain.OfertaNormalizada;
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
    private final Cache<Long, String> tokens = Caffeine.newBuilder()
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
    }

    private void recordar(OfertaNormalizada oferta, Long id) {
        if (id != null && oferta.getTokenResenas() != null) {
            tokens.put(id, oferta.getTokenResenas());
        }
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
        String token = tokens.getIfPresent(id);
        if (token == null || !googleShopping.estaHabilitado()) {
            throw new RecursoNoEncontradoException(
                    "No tenemos las reseñas de este producto. Vuelva a buscarlo y presione \"Ver reseñas\".");
        }
        try {
            ResenasProducto nuevas = googleShopping.resenas(token);
            resenas.put(id, nuevas);
            return nuevas.estaVacia() ? Optional.empty() : Optional.of(nuevas);
        } catch (MarketplaceException e) {
            // El manejador de errores le muestra a la persona un mensaje sencillo, sin estos detalles.
            LOG.warn("No se pudieron traer las reseñas de la oferta {}: {}", id, e.getMessage());
            throw e;
        }
    }
}
