package co.edu.unicesar.marketradar.service;

import co.edu.unicesar.marketradar.adapter.ProveedorTasas;
import co.edu.unicesar.marketradar.adapter.tasas.FuenteTasaCambio;
import co.edu.unicesar.marketradar.config.PropiedadesMarketradar;
import co.edu.unicesar.marketradar.dao.TasaCambioDao;
import co.edu.unicesar.marketradar.domain.TasaCambio;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mantiene al día las tasas de cambio a pesos colombianos. Las consulta al arrancar y todos los días
 * (por defecto a las 6:00 a. m.), las guarda en la base de datos y, si las fuentes no responden, usa
 * la última tasa guardada o, como último recurso, la tasa configurada en {@code TASA_USD_COP}.
 */
@Service
public class ServicioTasasCambio implements ProveedorTasas {

    private static final Logger LOG = LoggerFactory.getLogger(ServicioTasasCambio.class);
    static final String FUENTE_RESPALDO = "Tasa configurada (respaldo)";

    private final List<FuenteTasaCambio> fuentes;
    private final TasaCambioDao tasaCambioDao;
    private final PropiedadesMarketradar.Moneda configuracion;
    private final Map<String, TasaCambio> vigentes = new ConcurrentHashMap<>();

    /**
     * @param fuentes fuentes externas, en orden de prioridad
     * @param tasaCambioDao histórico de tasas
     * @param propiedades configuración con la tasa de respaldo
     */
    public ServicioTasasCambio(List<FuenteTasaCambio> fuentes, TasaCambioDao tasaCambioDao,
                               PropiedadesMarketradar propiedades) {
        this.fuentes = fuentes;
        this.tasaCambioDao = tasaCambioDao;
        this.configuracion = propiedades.moneda();
    }

    /**
     * Consulta todas las fuentes; para cada moneda se queda con la de mayor prioridad.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${marketradar.moneda.cron-actualizacion:0 0 6 * * *}", zone = "America/Bogota")
    public void actualizar() {
        Set<String> actualizadas = new HashSet<>();
        for (FuenteTasaCambio fuente : fuentes) {
            try {
                for (TasaCambio tasa : fuente.consultar()) {
                    if (actualizadas.add(tasa.monedaOrigen())) {
                        vigentes.put(tasa.monedaOrigen(), tasa);
                        guardarSinInterrumpir(tasa);
                    }
                }
            } catch (RuntimeException e) {
                LOG.warn("No se pudieron consultar las tasas de {}: {}", fuente.nombre(), e.getMessage());
            }
        }
        LOG.info("Tasas de cambio actualizadas: {}", actualizadas);
    }

    @Override
    public Optional<TasaCambio> tasaVigente(String moneda) {
        TasaCambio enMemoria = vigentes.get(moneda);
        if (enMemoria != null) {
            return Optional.of(enMemoria);
        }
        Optional<TasaCambio> guardada = buscarGuardadaSinInterrumpir(moneda);
        if (guardada.isPresent()) {
            vigentes.put(moneda, guardada.get());
            return guardada;
        }
        return respaldo(moneda);
    }

    /**
     * @return tasas conocidas en este momento, ordenadas por moneda
     */
    public List<TasaCambio> listarVigentes() {
        Map<String, TasaCambio> todas = new TreeMap<>(vigentes);
        respaldo("USD").ifPresent(tasa -> todas.putIfAbsent("USD", tasa));
        return new ArrayList<>(todas.values());
    }

    private Optional<TasaCambio> respaldo(String moneda) {
        BigDecimal tasaUsd = configuracion.tasaUsdCop();
        if (!"USD".equals(moneda) || tasaUsd == null || tasaUsd.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new TasaCambio("USD", tasaUsd, configuracion.fechaTasa(), FUENTE_RESPALDO));
    }

    private void guardarSinInterrumpir(TasaCambio tasa) {
        try {
            tasaCambioDao.guardar(tasa);
        } catch (AccesoDatosException e) {
            LOG.warn("No se pudo guardar la tasa de {}", tasa.monedaOrigen(), e);
        }
    }

    private Optional<TasaCambio> buscarGuardadaSinInterrumpir(String moneda) {
        try {
            return tasaCambioDao.buscarVigente(moneda);
        } catch (AccesoDatosException e) {
            LOG.warn("No se pudo leer la tasa guardada de {}", moneda, e);
            return Optional.empty();
        }
    }
}
