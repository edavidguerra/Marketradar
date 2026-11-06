package co.edu.unicesar.marketradar.adapter.mercadolibre;

import co.edu.unicesar.marketradar.dao.CredencialDao;
import co.edu.unicesar.marketradar.dao.CredencialDao.CredencialGuardada;
import co.edu.unicesar.marketradar.exception.AccesoDatosException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Guarda el refresh token en la tabla {@code credencial_marketplace}. Es la opción por defecto porque
 * en Render el disco se borra cada vez que la web se reinicia, pero la base de datos se conserva.
 *
 * <p>Junto al token se guarda el SHA-256 del refresh token configurado. Si alguien genera uno nuevo y
 * cambia la variable {@code MERCADOLIBRE_REFRESH_TOKEN}, el guardado se descarta y se usa el nuevo.</p>
 */
class AlmacenTokenBaseDatos implements AlmacenToken {

    static final String CODIGO = "mercadolibre.refresh_token";
    private static final Logger LOG = LoggerFactory.getLogger(AlmacenTokenBaseDatos.class);

    private final CredencialDao dao;

    AlmacenTokenBaseDatos(CredencialDao dao) {
        this.dao = dao;
    }

    @Override
    public Optional<String> leer(String tokenInicial) {
        try {
            String hash = sha256(tokenInicial);
            return dao.buscar(CODIGO)
                    .filter(guardada -> guardada.valorInicialHash().equals(hash))
                    .map(CredencialGuardada::valor);
        } catch (AccesoDatosException e) {
            LOG.warn("No se pudo leer el refresh token de Mercado Libre de la base de datos", e);
            return Optional.empty();
        }
    }

    @Override
    public void guardar(String token, String tokenInicial) {
        try {
            dao.guardar(CODIGO, new CredencialGuardada(token, sha256(tokenInicial)));
        } catch (AccesoDatosException e) {
            LOG.error("No se pudo guardar el refresh token de Mercado Libre en la base de datos. "
                    + "Al reiniciar habrá que generar otro.", e);
        }
    }

    static String sha256(String texto) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest((texto == null ? "" : texto).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("La JVM no tiene SHA-256", e);
        }
    }
}
